import assert from 'node:assert/strict';
import test from 'node:test';
import { advisoryDigest, auditUpstream } from './audit-upstream.mjs';

const advisory = { ghsa_id: 'GHSA-reviewed', summary: 'Reviewed issue', severity: 'high', description: 'Details', vulnerabilities: [] };
function fixture(status = 'mitigated') {
    const source = { repository: 'test/server', module: 'example.test/server', version: 'v1.0.0', sum: 'h1:checksum', advisories: {
        [advisory.ghsa_id]: { status, digest: advisoryDigest(advisory), reason: 'Explicit guard' },
    } };
    return { manifest: { builder: 'go:pinned', sources: [source] }, dockerfile: 'go:pinned example.test/server@v1.0.0 h1:checksum' };
}
async function run({ feed = [advisory], osv = {}, status = 'mitigated' } = {}) {
    return auditUpstream({ ...fixture(status), fetchJson: async url => url.includes('github.com') ? feed : osv });
}

test('rejects a new repository advisory', async () => {
    const report = await run({ feed: [advisory, { ...advisory, ghsa_id: 'GHSA-new' }] });
    assert.match(report.failures.join('\n'), /New or changed.*GHSA-new/);
});
test('rejects changed advisory details without re-review', async () => {
    const report = await run({ feed: [{ ...advisory, description: 'Expanded vulnerable scope' }] });
    assert.match(report.failures.join('\n'), /New or changed/);
});
test('rejects an empty or truncated feed missing a reviewed advisory', async () => {
    const report = await run({ feed: [] });
    assert.match(report.failures.join('\n'), /Previously reviewed advisory missing/);
});
test('rejects an unknown OSV main-module advisory', async () => {
    const report = await run({ osv: { vulns: [{ id: 'GO-unknown', aliases: [] }] } });
    assert.match(report.failures.join('\n'), /Unclassified.*GO-unknown/);
});
test('rejects OSV affected version contradicting a claimed upstream fix', async () => {
    const report = await run({ status: 'fixed-upstream', osv: { vulns: [{ id: 'GO-affected', aliases: [advisory.ghsa_id] }] } });
    assert.match(report.failures.join('\n'), /contradicts reviewed classification/);
});
test('accepts an exact reviewed mitigation for an affected upstream version', async () => {
    const report = await run({ osv: { vulns: [{ id: 'GO-affected', aliases: [advisory.ghsa_id] }] } });
    assert.deepEqual(report.failures, []);
    assert.equal(report.results[0].status, 'mitigated');
});
test('rejects a failed external lookup and retains a failure report', async () => {
    const report = await auditUpstream({ ...fixture(), fetchJson: async () => { throw new Error('HTTP 503'); } });
    assert.match(report.failures.join('\n'), /Advisory lookup failed.*HTTP 503/);
});
test('rejects malformed OSV responses and findings', async () => {
    for (const osv of [[], null, { vulns: {} }, { vulns: [null] }, { vulns: [{ id: 123 }] }, { vulns: [{ id: 'GO-id', aliases: 'GHSA-id' }] }, { vulns: [{ id: 'GO-id', aliases: [null] }] }]) {
        const report = await run({ osv });
        assert.match(report.failures.join('\n'), /Invalid OSV/);
    }
});
test('rejects a missing source checksum in Docker build', async () => {
    const inputs = fixture();
    const report = await auditUpstream({ ...inputs, dockerfile: inputs.dockerfile.replace('h1:checksum', ''), fetchJson: async url => url.includes('github.com') ? [advisory] : {} });
    assert.match(report.failures.join('\n'), /Source pin or checksum differs/);
});
test('accepts a genuinely empty feed when no advisory was previously reviewed', async () => {
    const inputs = fixture();
    inputs.manifest.sources[0].advisories = {};
    const report = await auditUpstream({ ...inputs, fetchJson: async url => url.includes('github.com') ? [] : {} });
    assert.deepEqual(report.failures, []);
});

test('rejects paginated or unexpected OSV responses instead of accepting partial results', async () => {
    for (const osv of [{ vulns: [], next_page_token: 'remaining' }, { error: 'temporarily unavailable' }]) {
        const report = await run({ osv });
        assert.match(report.failures.join('\n'), /Incomplete, paginated, or unexpected OSV response/);
    }
});
