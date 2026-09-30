import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const directory = path.dirname(fileURLToPath(import.meta.url));
const affectedStatuses = new Set(['mitigated', 'not-exposed']);

export function advisoryDigest(advisory) {
    return createHash('sha256').update(JSON.stringify([
        advisory.ghsa_id, advisory.summary, advisory.severity,
        advisory.description, advisory.vulnerabilities,
    ])).digest('hex');
}

export async function auditUpstream({ manifest, dockerfile, fetchJson }) {
    const report = {
        timestamp: new Date().toISOString(),
        mainModuleVersionLimitation: 'Patched go build binaries use (devel); this separate inventory checks pinned upstream versions and reviewed repository advisories.',
        results: [], failures: [],
    };
    if (!dockerfile.includes(manifest.builder)) report.failures.push('Builder pin differs from reviewed manifest');
    for (const source of manifest.sources) {
        if (!dockerfile.includes(`${source.module}@${source.version}`) || !dockerfile.includes(source.sum)) {
            report.failures.push(`Source pin or checksum differs from reviewed manifest: ${source.module}`);
        }
        try {
            const advisories = await fetchJson(`https://api.github.com/repos/${source.repository}/security-advisories?per_page=100`);
            if (!Array.isArray(advisories) || advisories.length >= 100) throw new Error('Invalid advisory feed or pagination requires review');
            const fetchedIds = new Set(advisories.map(advisory => advisory.ghsa_id));
            for (const id of Object.keys(source.advisories)) {
                if (!fetchedIds.has(id)) report.failures.push(`Previously reviewed advisory missing from feed: ${source.repository}/${id}`);
            }
            for (const advisory of advisories) {
                const reviewed = source.advisories[advisory.ghsa_id];
                if (!reviewed || reviewed.digest !== advisoryDigest(advisory)) {
                    report.failures.push(`New or changed upstream advisory: ${source.repository}/${advisory.ghsa_id}`);
                } else if (!affectedStatuses.has(reviewed.status) && reviewed.status !== 'fixed-upstream') {
                    report.failures.push(`Unsupported advisory classification: ${source.repository}/${advisory.ghsa_id}`);
                } else {
                    report.results.push({ id: advisory.ghsa_id, status: reviewed.status, reason: reviewed.reason });
                }
            }
            // Local builds lack a versioned main module. Check the pinned upstream
            // version separately; an alias is never a blanket exclusion.
            const osv = await fetchJson('https://api.osv.dev/v1/query', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ package: { ecosystem: 'Go', name: source.module }, version: source.version }),
            });
            if (!osv || typeof osv !== 'object' || Array.isArray(osv) || (osv.vulns !== undefined && !Array.isArray(osv.vulns))) throw new Error('Invalid OSV response');
            if (Object.keys(osv).some(key => !['vulns', 'next_page_token'].includes(key))
                || (Object.hasOwn(osv, 'next_page_token') && osv.next_page_token !== '')) {
                throw new Error('Incomplete, paginated, or unexpected OSV response');
            }
            for (const vulnerability of osv.vulns ?? []) {
                if (!vulnerability || typeof vulnerability !== 'object' || typeof vulnerability.id !== 'string' || !vulnerability.id ||
                    (vulnerability.aliases !== undefined && (!Array.isArray(vulnerability.aliases) || vulnerability.aliases.some(id => typeof id !== 'string' || !id)))) {
                    throw new Error('Invalid OSV vulnerability');
                }
                const reviewedIds = [vulnerability.id, ...(vulnerability.aliases ?? [])].filter(id => source.advisories[id]);
                if (!reviewedIds.length) {
                    report.failures.push(`Unclassified main-module OSV advisory: ${source.module}/${vulnerability.id}`);
                } else if (reviewedIds.some(id => !affectedStatuses.has(source.advisories[id].status))) {
                    report.failures.push(`OSV affected version contradicts reviewed classification: ${source.module}/${vulnerability.id}`);
                }
            }
        } catch (error) {
            report.failures.push(`Advisory lookup failed for ${source.module}: ${error.message}`);
        }
    }
    return report;
}

async function main() {
    const manifest = JSON.parse(await readFile(path.join(directory, 'source-security.json'), 'utf8'));
    const dockerfile = await readFile(path.join(directory, 'Dockerfile'), 'utf8');
    const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'hvostid-minio-security' };
    if (process.env.GH_TOKEN) headers.Authorization = `Bearer ${process.env.GH_TOKEN}`;
    const report = await auditUpstream({ manifest, dockerfile, fetchJson: async (url, options = {}) => {
        const response = await fetch(url, {
            ...options, headers: url.startsWith('https://api.github.com/') ? headers : options.headers,
            signal: AbortSignal.timeout(30000),
        });
        if (!response.ok) throw new Error(`${response.status} ${url}`);
        return response.json();
    } });
    const reportPath = process.env.MINIO_AUDIT_REPORT ?? path.resolve(directory, '../../build/reports/minio-upstream.json');
    await mkdir(path.dirname(reportPath), { recursive: true });
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
    if (report.failures.length) {
        console.error(report.failures.join('\n'));
        process.exitCode = 1;
    } else {
        console.log(`Reviewed ${report.results.length} exact upstream advisories; no unclassified findings. Local mitigations and archived-upstream limits remain explicit in source-security.json.`);
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
