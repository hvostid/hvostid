import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { validateDatabase, validateReport as validateAggregate } from './validate-dependency-check.mjs';

const script = fileURLToPath(new URL('./validate-dependency-check.mjs', import.meta.url));
const services = ['api-gateway', 'auth-service', 'listing-service', 'passport-service', 'matching-service'];
const hash = 'a'.repeat(64);
const validInventory = () => ({ schema: 1, services, artifacts: services.map(service => ({
  service, group: 'org.example', name: 'example', version: '1.0', fileName: 'example-1.0.jar', sha256: hash,
})) });
const validateReport = report => validateAggregate(report, validInventory());
const validReport = () => ({
  reportSchema: '1.1',
  scanInfo: { engineVersion: '13.0.0', dataSource: [{ name: 'NVD API Last Checked', timestamp: new Date().toISOString() }] },
  projectInfo: { name: 'hvostid', reportDate: new Date().toISOString() },
  dependencies: [{ fileName: 'example-1.0.jar', filePath: '/cache/example-1.0.jar', sha256: hash,
    packages: [{ id: 'pkg:maven/org.example/example@1.0' }],
    projectReferences: services.map(service => `${service}:runtimeClasspath`) }],
});

test('valid JSON with one shared jar cannot hide another resolved runtime artifact', () => {
  const inventory = validInventory();
  inventory.artifacts.push({ ...inventory.artifacts[0], name: 'missing', sha256: 'b'.repeat(64) });
  assert.throws(() => validateAggregate(validReport(), inventory), /Runtime artifacts missing.*org.example:missing/);
});

test('related dependency packageIds and hashes retain merged artifact coverage', () => {
  const report = validReport();
  const inventory = validInventory();
  inventory.artifacts.push({ ...inventory.artifacts[0], name: 'alias', sha256: 'b'.repeat(64) });
  report.dependencies[0].relatedDependencies = [{ fileName: 'alias.jar', filePath: '/cache/alias.jar',
    sha256: 'b'.repeat(64), packageIds: [{ id: 'pkg:maven/org.example/alias@1.0?type=jar' }] }];
  assert.equal(validateAggregate(report, inventory).expectedRuntimeArtifacts, 6);
  report.dependencies[0].relatedDependencies[0].sha256 = 'c'.repeat(64);
  assert.throws(() => validateAggregate(report, inventory), /Runtime artifacts missing/);
});

test('missing, empty, malformed and service-incomplete expected inventories fail closed', () => {
  for (const inventory of [undefined, {}, { ...validInventory(), artifacts: [] },
    { ...validInventory(), artifacts: [{}] }, { ...validInventory(), artifacts: validInventory().artifacts.slice(1) },
    { ...validInventory(), artifacts: [...validInventory().artifacts, validInventory().artifacts[0]] }]) {
    assert.throws(() => validateAggregate(validReport(), inventory));
  }
});

test('complete Java aggregate with all runtime services passes', () => {
  const summary = validateReport(validReport());
  assert.equal(summary.mavenPackages, 1);
  assert.equal(summary.uniqueVulnerabilities, 0);
  assert.deepEqual(summary.runtimeServices, [...services].sort());
});

test('freshness requires API Last Checked, not Last Modified or unrelated metadata', () => {
  const now = Date.now();
  for (const source of [{ name: 'NVD API Last Modified', timestamp: new Date(now).toISOString() },
    { name: 'Other analyzer', timestamp: new Date(now).toISOString() },
    { name: 'NVD API Last Checked', timestamp: 'garbage' },
    { name: 'NVD API Last Checked', timestamp: new Date(now - 25 * 60 * 60 * 1000).toISOString() }]) {
    const report = validReport();
    report.scanInfo.dataSource = [source];
    assert.throws(() => validateAggregate(report, validInventory(), now), /NVD API Last Checked/);
  }
  const stale = validReport();
  stale.projectInfo.reportDate = new Date(now - 3 * 60 * 60 * 1000).toISOString();
  assert.throws(() => validateAggregate(stale, validInventory(), now), /stale or future/);
});

test('OSV-only artifact, empty inventory and malformed metadata fail closed', () => {
  for (const report of [null, {}, { status: 'clean', packages: 226 },
    { ...validReport(), dependencies: [] }, { ...validReport(), dependencies: {} },
    { ...validReport(), scanInfo: {} }, { ...validReport(), projectInfo: {} }]) {
    assert.throws(() => validateReport(report));
  }
});

test('analyzer failures and malformed dependency fields fail closed', () => {
  const failed = validReport();
  failed.scanInfo.analysisExceptions = [{ exception: { message: 'NVD unavailable' } }];
  assert.throws(() => validateReport(failed), /analysis exceptions/);
  for (const patch of [{ filePath: '' }, { packages: {} }, { projectReferences: [null] },
    { vulnerabilities: null }, { vulnerabilities: [{}] }]) {
    const report = validReport();
    Object.assign(report.dependencies[0], patch);
    assert.throws(() => validateReport(report));
  }
});

test('npm-only or missing service/runtime coverage cannot masquerade as a JVM aggregate', () => {
  const npm = validReport();
  npm.dependencies[0].packages[0].id = 'pkg:npm/example@1.0';
  assert.throws(() => validateReport(npm), /no Maven dependencies/);
  const partial = validReport();
  partial.dependencies[0].projectReferences.pop();
  assert.throws(() => validateReport(partial), /matching-service/);
  const compileOnly = validReport();
  compileOnly.dependencies[0].projectReferences = services.map(service => `${service}:compileClasspath`);
  assert.throws(() => validateReport(compileOnly), /Missing Maven runtime coverage/);
});

test('all severities are summarized, including HIGH below the Gradle CVSS threshold', () => {
  const report = validReport();
  report.dependencies[0].vulnerabilities = [{ name: 'CVE-2026-12345', source: 'NVD', severity: 'HIGH', cvssv3: { baseScore: 8.1 } }];
  report.dependencies[0].suppressedVulnerabilities = [{ name: 'CVE-2026-12346', source: 'NVD' }];
  report.dependencies.push({ ...report.dependencies[0], fileName: 'another.jar' });
  const summary = validateReport(report);
  assert.equal(summary.vulnerabilityMatches, 2);
  assert.equal(summary.uniqueVulnerabilities, 1);
  assert.deepEqual(summary.findings[0].severities, ['HIGH']);
  assert.deepEqual(summary.findings[0].files, ['another.jar', 'example-1.0.jar']);
  assert.deepEqual(summary.suppressedVulnerabilities, ['CVE-2026-12346']);
});

test('database guard rejects missing, empty or directory-shaped H2 files before cache save', async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'dc-cache-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  await assert.rejects(validateDatabase(directory));
  const database = path.join(directory, 'odc.mv.db');
  await writeFile(database, '');
  await assert.rejects(validateDatabase(directory), /empty NVD database/);
  await rm(database);
  await mkdir(database);
  await assert.rejects(validateDatabase(directory), /empty NVD database/);
  await rm(database, { recursive: true });
  await writeFile(database, 'H2 fixture');
  assert.equal((await validateDatabase(directory)).bytes, 10);
});

test('CLI returns nonzero for missing, empty, malformed or incomplete JSON', async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'dc-report-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const report = path.join(directory, 'report.json');
  const inventory = path.join(directory, 'inventory.json');
  await writeFile(inventory, JSON.stringify(validInventory()));
  const args = [script, '--report', report, '--inventory', inventory];
  assert.equal(spawnSync(process.execPath, args).status, 1);
  for (const content of ['', '{', '{}', JSON.stringify({ ...validReport(), dependencies: [] })]) {
    await writeFile(report, content);
    assert.equal(spawnSync(process.execPath, args).status, 1);
  }
  await writeFile(report, JSON.stringify(validReport()));
  const success = spawnSync(process.execPath, args, { encoding: 'utf8' });
  assert.equal(success.status, 0, success.stderr);
  assert.equal(JSON.parse(success.stdout).dependencies, 1);
  assert.equal(spawnSync(process.execPath, [script, '--report', report]).status, 1);
});
