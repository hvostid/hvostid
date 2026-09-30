import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const modules = ['api-gateway', 'auth-service', 'listing-service', 'passport-service', 'matching-service'];
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);

export function parseRuntimePackages(locks) {
  const packages = new Map();
  for (const { module, content } of locks) {
    let runtimeEntries = 0;
    for (const rawLine of content.split('\n')) {
      const line = rawLine.trim();
      const separator = line.indexOf('=');
      if (line.startsWith('#') || separator < 0) continue;
      const configurations = line.slice(separator + 1).split(',').map(value => value.trim());
      if (!configurations.includes('runtimeClasspath')) continue;
      const coordinates = line.slice(0, separator).split(':');
      if (coordinates.length !== 3 || coordinates.some(value => !/^[^\s:=,@]+$/.test(value))) {
        throw new Error(`Invalid runtime dependency coordinates in ${module}/gradle.lockfile`);
      }
      const [group, artifact, version] = coordinates;
      packages.set(`${group}:${artifact}@${version}`, { package: { ecosystem: 'Maven', name: `${group}:${artifact}` }, version });
      runtimeEntries++;
    }
    if (!runtimeEntries) throw new Error(`No runtime dependencies found in ${module}/gradle.lockfile`);
  }
  if (!packages.size) throw new Error('No runtime dependencies were parsed; refusing an empty audit');
  return [...packages.entries()];
}

export function validateOsvResponse(data, entries) {
  if (!entries.length) throw new Error('Cannot validate OSV results for an empty package list');
  if (!isObject(data) || !Array.isArray(data.results) || data.results.length !== entries.length) {
    throw new Error(`OSV results must contain exactly ${entries.length} query responses`);
  }
  return data.results.flatMap((result, index) => {
    if (!isObject(result) || Object.keys(result).some(key => !['vulns', 'next_page_token'].includes(key))) {
      throw new Error(`Invalid OSV result at index ${index}`);
    }
    if (Object.hasOwn(result, 'next_page_token')) {
      if (typeof result.next_page_token !== 'string' || result.next_page_token !== '') {
        throw new Error(`OSV result at index ${index} is incomplete or paginated; refusing a partial audit`);
      }
    }
    if (Object.hasOwn(result, 'vulns') && !Array.isArray(result.vulns)) {
      throw new Error(`Invalid OSV vulnerability list at index ${index}`);
    }
    // OSV omits the vulns field for a clean query; malformed values are not clean.
    return (result.vulns ?? []).map(vulnerability => {
      if (!isObject(vulnerability) || typeof vulnerability.id !== 'string'
          || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(vulnerability.id)) {
        throw new Error(`Invalid OSV advisory ID at index ${index}`);
      }
      return { package: entries[index][0], id: vulnerability.id,
        url: `https://osv.dev/vulnerability/${encodeURIComponent(vulnerability.id)}` };
    });
  });
}

export async function auditRuntimeDependencies({ rootDir = process.cwd(), fetchImpl = fetch,
  reportPath = process.env.OSV_REPORT || path.join(rootDir, 'build/reports/osv-runtime.json') } = {}) {
  const report = { checkedAt: new Date().toISOString(), scope: 'runtimeClasspath of five services',
    packages: 0, status: 'error', findings: [], errors: [] };
  try {
    const locks = await Promise.all(modules.map(async module => ({ module,
      content: await readFile(path.join(rootDir, module, 'gradle.lockfile'), 'utf8') })));
    const entries = parseRuntimePackages(locks);
    report.packages = entries.length;
    const response = await fetchImpl('https://api.osv.dev/v1/querybatch', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ queries: entries.map(([, query]) => query) }), signal: AbortSignal.timeout(60000),
    });
    if (!response.ok) throw new Error(`OSV query failed: HTTP ${response.status}`);
    report.findings = validateOsvResponse(await response.json(), entries);
    report.status = report.findings.length ? 'vulnerable' : 'clean';
  } catch (error) {
    report.errors.push(error instanceof Error ? error.message : String(error));
  }
  await mkdir(path.dirname(reportPath), { recursive: true });
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
  return report;
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  try {
    const report = await auditRuntimeDependencies();
    if (report.status === 'error') console.error(`OSV audit failed: ${report.errors.join('; ')}`);
    else {
      console.log(`OSV runtime dependencies: ${report.packages} packages, ${report.findings.length} advisory matches`);
      for (const finding of report.findings) console.log(`${finding.package} ${finding.id}`);
    }
    process.exitCode = report.status === 'clean' ? 0 : 1;
  } catch (error) {
    console.error(`OSV audit failed: ${error instanceof Error ? error.message : String(error)}`);
    process.exitCode = 1;
  }
}
