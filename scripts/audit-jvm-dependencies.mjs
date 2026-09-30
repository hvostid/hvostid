import { readFile, writeFile } from 'node:fs/promises';
const modules = ['api-gateway', 'auth-service', 'listing-service', 'passport-service', 'matching-service'];
const packages = new Map();
for (const module of modules) {
  const lock = await readFile(`${module}/gradle.lockfile`, 'utf8');
  for (const line of lock.split('\n')) {
    if (line.startsWith('#') || !line.includes('=') || !line.split('=')[1].split(',').includes('runtimeClasspath')) continue;
    const [group, artifact, version] = line.split('=')[0].split(':');
    packages.set(`${group}:${artifact}@${version}`, { package: { ecosystem: 'Maven', name: `${group}:${artifact}` }, version });
  }
}
const entries = [...packages.entries()];
const response = await fetch('https://api.osv.dev/v1/querybatch', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ queries: entries.map(([, query]) => query) }), signal: AbortSignal.timeout(60000),
});
if (!response.ok) throw new Error(`OSV query failed: HTTP ${response.status}`);
const data = await response.json();
const findings = data.results.flatMap((result, index) => (result.vulns || []).map(vulnerability => ({
  package: entries[index][0], id: vulnerability.id, url: `https://osv.dev/vulnerability/${vulnerability.id}`,
})));
const report = { checkedAt: new Date().toISOString(), scope: 'runtimeClasspath of five services', packages: entries.length, findings };
await writeFile(process.env.OSV_REPORT || 'build/reports/osv-runtime.json', `${JSON.stringify(report, null, 2)}\n`);
console.log(`OSV runtime dependencies: ${entries.length} packages, ${findings.length} advisory matches`);
for (const finding of findings) console.log(`${finding.package} ${finding.id}`);
if (findings.length) process.exitCode = 1;
