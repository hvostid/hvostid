import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import test from 'node:test';
import { parseRuntimePackages, validateOsvResponse } from './audit-jvm-dependencies.mjs';

const script = fileURLToPath(new URL('./audit-jvm-dependencies.mjs', import.meta.url));
const modules = ['api-gateway', 'auth-service', 'listing-service', 'passport-service', 'matching-service'];
const runtimeLock = '# Gradle lockfile\r\norg.example:runtime:1.0=compileClasspath,runtimeClasspath\r\n'
  + 'org.example:tests:9.0=testRuntimeClasspath\r\n';
const entries = parseRuntimePackages([{ module: 'fixture', content: runtimeLock }]);

test('runtime parsing handles CRLF, excludes tests and deduplicates shared dependencies', () => {
  const packages = parseRuntimePackages(modules.map(module => ({ module, content: runtimeLock })));
  assert.deepEqual(packages, [['org.example:runtime@1.0', {
    package: { ecosystem: 'Maven', name: 'org.example:runtime' }, version: '1.0',
  }]]);
});

test('empty inventory and an empty service lock both fail closed', () => {
  assert.throws(() => parseRuntimePackages([]), /empty audit/);
  assert.throws(() => parseRuntimePackages([{ module: 'fixture', content: runtimeLock },
    { module: 'missing', content: '# No runtime dependencies\n' }]), /No runtime dependencies/);
  assert.throws(() => parseRuntimePackages([{ module: 'invalid', content: 'org.example:artifact=runtimeClasspath' }]),
    /Invalid runtime dependency/);
});

test('clean response supports omitted or empty vulns field', () => {
  assert.deepEqual(validateOsvResponse({ results: [{}] }, entries), []);
  assert.deepEqual(validateOsvResponse({ results: [{ vulns: [] }] }, entries), []);
});

test('empty, truncated and extra batch results are rejected', () => {
  for (const response of [null, {}, { results: null }, { results: [] }, { results: [{}, {}] }]) {
    assert.throws(() => validateOsvResponse(response, entries), /exactly 1 query/);
  }
  assert.throws(() => validateOsvResponse({ results: [{}] }, [...entries, ...entries]), /exactly 2 query/);
});

test('malformed results, vulnerability lists and advisory IDs fail closed', () => {
  for (const result of [null, [], 'clean', { error: 'upstream unavailable' }, { unexpected: true },
    { vulns: null }, { vulns: {} }, { vulns: 'none' }, { vulns: [null] }, { vulns: [{}] },
    { vulns: [{ id: 12 }] }, { vulns: [{ id: '' }] }, { vulns: [{ id: '  GHSA-invalid' }] },
    { vulns: [{ id: '../invalid' }] }]) {
    assert.throws(() => validateOsvResponse({ results: [result] }, entries), /Invalid OSV/);
  }
});

test('pagination cannot masquerade as a complete clean response', () => {
  assert.throws(() => validateOsvResponse({ results: [{ next_page_token: 'more' }] }, entries), /partial audit/);
  assert.throws(() => validateOsvResponse({ results: [{ next_page_token: null }] }, entries), /partial audit/);
});

async function runCli(t, { payload = { results: [{}] }, lock = runtimeLock, status = 200,
  networkError = false, rawJson } = {}) {
  const fixture = await mkdtemp(path.join(os.tmpdir(), 'hvostid-osv-'));
  t.after(async () => {
    assert.equal(path.dirname(fixture), os.tmpdir());
    assert.ok(path.basename(fixture).startsWith('hvostid-osv-'));
    await rm(fixture, { recursive: true, force: true });
  });
  for (const module of modules) {
    await mkdir(path.join(fixture, module));
    await writeFile(path.join(fixture, module, 'gradle.lockfile'), lock);
  }
  const preload = path.join(fixture, 'fake-osv.mjs');
  await writeFile(preload, networkError ? 'globalThis.fetch = async () => { throw new Error("network unavailable"); };'
    : `globalThis.fetch = async () => new Response(${JSON.stringify(rawJson ?? JSON.stringify(payload))}, { status: ${status} });`);
  const reportPath = path.join(fixture, 'reports/result.json');
  const execution = spawnSync(process.execPath, ['--import', pathToFileURL(preload).href, script], {
    cwd: fixture, encoding: 'utf8', timeout: 10000, env: { ...process.env, OSV_REPORT: reportPath },
  });
  assert.ifError(execution.error);
  const report = JSON.parse(await readFile(reportPath, 'utf8'));
  return { execution, report };
}

test('CLI exits successfully only with a complete clean result and saves the report', async t => {
  const { execution, report } = await runCli(t);
  assert.equal(execution.status, 0, execution.stderr);
  assert.equal(report.status, 'clean');
  assert.equal(report.packages, 1);
  assert.deepEqual(report.findings, []);
  assert.deepEqual(report.errors, []);
});

test('CLI reports a known vulnerability and exits nonzero', async t => {
  const { execution, report } = await runCli(t, { payload: { results: [{ vulns: [{ id: 'GHSA-rcgg-9c38-7xpx' }] }] } });
  assert.equal(execution.status, 1);
  assert.equal(report.status, 'vulnerable');
  assert.equal(report.findings[0].package, 'org.example:runtime@1.0');
  assert.equal(report.findings[0].id, 'GHSA-rcgg-9c38-7xpx');
  assert.deepEqual(report.errors, []);
});

for (const [name, options] of [
  ['empty runtime inventory', { lock: '# Empty lock\n' }],
  ['truncated OSV response', { payload: { results: [] } }],
  ['malformed advisory', { payload: { results: [{ vulns: [{}] }] } }],
  ['invalid JSON', { rawJson: '{broken' }],
  ['failed HTTP lookup', { status: 503 }],
  ['failed network lookup', { networkError: true }],
]) {
  test(`CLI preserves an error report and fails on ${name}`, async t => {
    const { execution, report } = await runCli(t, options);
    assert.equal(execution.status, 1);
    assert.equal(report.status, 'error');
    assert.equal(report.errors.length, 1);
    assert.match(execution.stderr, /OSV audit failed/);
    assert.doesNotMatch(execution.stdout, /advisory matches/);
  });
}
