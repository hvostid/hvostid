import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const runtimeServices = ['api-gateway', 'auth-service', 'listing-service', 'passport-service', 'matching-service'];
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const text = value => typeof value === 'string' && value.trim().length > 0;
const checksum = value => typeof value === 'string' && /^[a-f0-9]{64}$/i.test(value);

function requireValue(condition, message) {
  if (!condition) throw new Error(message);
}

/** Validate a real DC13 aggregate, including runtime coverage, independently of its CVSS gate. */
export function validateReport(report, inventory, now = Date.now()) {
  requireValue(object(inventory) && inventory.schema === 1
    && Array.isArray(inventory.services) && inventory.services.length === runtimeServices.length
    && runtimeServices.every(service => inventory.services.includes(service)), 'Missing or malformed runtime inventory services');
  requireValue(Array.isArray(inventory.artifacts) && inventory.artifacts.length > 0, 'Empty expected runtime artifact inventory');
  const expected = new Map();
  for (const artifact of inventory.artifacts) {
    requireValue(object(artifact) && runtimeServices.includes(artifact.service)
      && ['group', 'name', 'version', 'fileName'].every(field => text(artifact[field]))
      && checksum(artifact.sha256), 'Malformed expected runtime artifact');
    const key = `${artifact.service}:${artifact.group}:${artifact.name}:${artifact.version}:${artifact.sha256.toLowerCase()}`;
    requireValue(!expected.has(key), `Duplicate expected runtime artifact: ${key}`);
    expected.set(key, artifact);
  }
  requireValue(runtimeServices.every(service => inventory.artifacts.some(artifact => artifact.service === service)),
    'Expected runtime inventory omits a service');
  requireValue(object(report) && report.reportSchema === '1.1', 'Missing or unsupported Dependency-Check report schema');
  requireValue(object(report.scanInfo) && text(report.scanInfo.engineVersion), 'Missing scan engine metadata');
  requireValue(Array.isArray(report.scanInfo.dataSource) && report.scanInfo.dataSource.length > 0
    && report.scanInfo.dataSource.every(source => object(source) && text(source.name) && text(source.timestamp)),
  'Missing scan data-source metadata');
  requireValue(report.scanInfo.analysisExceptions === undefined
    || (Array.isArray(report.scanInfo.analysisExceptions) && report.scanInfo.analysisExceptions.length === 0),
  'Dependency-Check reports analysis exceptions');
  requireValue(object(report.projectInfo) && text(report.projectInfo.name)
    && Number.isFinite(Date.parse(report.projectInfo.reportDate)), 'Missing project/report date metadata');
  const reportDate = Date.parse(report.projectInfo.reportDate);
  requireValue(now - reportDate <= 2 * 60 * 60 * 1000 && reportDate - now <= 5 * 60 * 1000,
    'Dependency-Check report is stale or future-dated');
  // The workflow always finishes with the API, even after a feed bootstrap.
  // Last Modified describes the data, not when its freshness was checked.
  const lastChecked = report.scanInfo.dataSource.find(source => source.name === 'NVD API Last Checked');
  const checkedDate = Date.parse(lastChecked?.timestamp);
  requireValue(Number.isFinite(checkedDate) && reportDate - checkedDate <= 24 * 60 * 60 * 1000
    && checkedDate - reportDate <= 5 * 60 * 1000, 'Missing, invalid or stale NVD API Last Checked timestamp');
  requireValue(Array.isArray(report.dependencies) && report.dependencies.length > 0, 'Empty dependency inventory');

  const services = new Set();
  const mavenPackages = new Set();
  const findings = new Map();
  const suppressed = new Set();
  const observed = new Set();
  let matches = 0;
  for (const dependency of report.dependencies) {
    requireValue(object(dependency) && text(dependency.fileName) && text(dependency.filePath), 'Malformed dependency');
    const packages = dependency.packages ?? [];
    const references = dependency.projectReferences ?? [];
    requireValue(Array.isArray(packages) && packages.every(pkg => object(pkg) && text(pkg.id)), 'Malformed package inventory');
    requireValue(Array.isArray(references) && references.every(text), 'Malformed project references');
    const related = dependency.relatedDependencies ?? [];
    requireValue(Array.isArray(related) && related.every(object), 'Malformed related dependencies');
    for (const entry of [{ ...dependency, packageIds: packages }, ...related]) {
      const ids = entry.packageIds ?? [];
      requireValue(Array.isArray(ids) && ids.every(pkg => object(pkg) && text(pkg.id)), 'Malformed related package inventory');
      for (const pkg of ids) {
        const match = /^pkg:maven\/([^/]+)\/([^@]+)@([^?#]+)(?:[?#].*)?$/.exec(pkg.id);
        if (!match) continue;
        const [, group, name, version] = match.map(decodeURIComponent);
        mavenPackages.add(`${group}:${name}:${version}`);
        requireValue(checksum(entry.sha256), `Missing Maven artifact checksum: ${pkg.id}`);
        for (const service of runtimeServices) {
          // DC13 merges project references onto the parent. Its related entries
          // carry packageIds/checksums but omit their own projectReferences.
          if (references.includes(`${service}:runtimeClasspath`)) {
            services.add(service);
            observed.add(`${service}:${group}:${name}:${version}:${entry.sha256.toLowerCase()}`);
          }
        }
      }
    }
    for (const field of ['vulnerabilities', 'suppressedVulnerabilities']) {
      if (dependency[field] === undefined) continue;
      requireValue(Array.isArray(dependency[field]), `Malformed ${field}`);
      for (const vulnerability of dependency[field]) {
        requireValue(object(vulnerability) && text(vulnerability.name) && text(vulnerability.source), 'Malformed vulnerability');
        if (field === 'suppressedVulnerabilities') {
          suppressed.add(vulnerability.name);
          continue;
        }
        requireValue(vulnerability.severity === undefined || text(vulnerability.severity), 'Malformed vulnerability severity');
        matches++;
        const key = `${vulnerability.source}:${vulnerability.name}`;
        const entry = findings.get(key) ?? { id: vulnerability.name, source: vulnerability.source, severities: new Set(), files: new Set() };
        entry.severities.add((vulnerability.severity ?? 'UNKNOWN').toUpperCase());
        entry.files.add(dependency.fileName);
        findings.set(key, entry);
      }
    }
  }
  requireValue(mavenPackages.size > 0, 'Report contains no Maven dependencies');
  const missing = runtimeServices.filter(service => !services.has(service));
  requireValue(missing.length === 0, `Missing Maven runtime coverage: ${missing.join(', ')}`);
  const missingArtifacts = [...expected].filter(([key]) => !observed.has(key));
  requireValue(missingArtifacts.length === 0,
    `Runtime artifacts missing from dependency report (${missingArtifacts.length}): ${missingArtifacts.map(([key]) => key).join(', ')}`);
  return {
    dependencies: report.dependencies.length,
    mavenPackages: mavenPackages.size,
    runtimeServices: [...services].sort(),
    expectedRuntimeArtifacts: expected.size,
    vulnerabilityMatches: matches,
    uniqueVulnerabilities: findings.size,
    findings: [...findings.values()].map(entry => ({ ...entry, severities: [...entry.severities].sort(), files: [...entry.files].sort() })),
    suppressedVulnerabilities: [...suppressed].sort(),
  };
}

export async function validateDatabase(directory) {
  const database = path.join(directory, 'odc.mv.db');
  const metadata = await stat(database);
  requireValue(metadata.isFile() && metadata.size > 0, `Missing or empty NVD database: ${database}`);
  return { database, bytes: metadata.size };
}

async function main() {
  const [mode, target, inventoryFlag, inventoryPath, ...extra] = process.argv.slice(2);
  requireValue(target && extra.length === 0
    && ((mode === '--database' && inventoryFlag === undefined)
      || (mode === '--report' && inventoryFlag === '--inventory' && inventoryPath)),
  'Usage: node scripts/validate-dependency-check.mjs --report <json> --inventory <json> | --database <directory>');
  const result = mode === '--database' ? await validateDatabase(target)
    : validateReport(JSON.parse(await readFile(target, 'utf8')), JSON.parse(await readFile(inventoryPath, 'utf8')));
  console.log(JSON.stringify(result, null, 2));
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch(error => {
    console.error(`Dependency-Check validation failed: ${error.message}`);
    process.exitCode = 1;
  });
}
