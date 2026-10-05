import assert from 'node:assert/strict';
import { createHash, createHmac, randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const image = process.env.MINIO_IMAGE ?? 'hvostid/minio:security-2026-09-30';
const accessKey = `security${randomBytes(6).toString('hex')}`;
const secretKey = randomBytes(24).toString('hex');
const bucket = `security-${randomBytes(6).toString('hex')}`;
const hash = value => createHash('sha256').update(value).digest('hex');
const hmac = (key, value) => createHmac('sha256', key).update(value).digest();
const encode = value => encodeURIComponent(value).replace(/[!'()*]/g, c => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
const queryString = entries => entries.map(([key, value]) => [encode(key), encode(value)])
    .sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0)
    .map(([key, value]) => `${key}=${value}`).join('&');
const docker = args => execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 60000 }).trim();
const checks = [];
let container;
let base;

function signature(method, pathname, query, canonicalHeaders, signedHeaders, digest, date) {
    const scope = `${date.slice(0, 8)}/us-east-1/s3/aws4_request`;
    const canonical = [method, pathname, query, canonicalHeaders, signedHeaders, digest].join('\n');
    const signingKey = hmac(hmac(hmac(hmac(`AWS4${secretKey}`, date.slice(0, 8)), 'us-east-1'), 's3'), 'aws4_request');
    return { scope, value: createHmac('sha256', signingKey).update(`AWS4-HMAC-SHA256\n${date}\n${scope}\n${hash(canonical)}`).digest('hex') };
}

async function request(method, pathname, body = '', query = [], extra = {}) {
    const date = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
    const headers = { host: new URL(base).host, 'x-amz-content-sha256': hash(body), 'x-amz-date': date, ...extra };
    const names = Object.keys(headers).sort();
    const canonicalHeaders = names.map(name => `${name}:${headers[name].trim()}\n`).join('');
    const signedHeaders = names.join(';');
    const queryValue = queryString(query);
    const signed = signature(method, pathname, queryValue, canonicalHeaders, signedHeaders, headers['x-amz-content-sha256'], date);
    headers.Authorization = `AWS4-HMAC-SHA256 Credential=${accessKey}/${signed.scope}, SignedHeaders=${signedHeaders}, Signature=${signed.value}`;
    return fetch(`${base}${pathname}${queryValue ? `?${queryValue}` : ''}`, {
        method, headers, body: ['GET', 'HEAD'].includes(method) ? undefined : body, signal: AbortSignal.timeout(30000),
    });
}

function presigned(method, pathname) {
    const date = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
    const scope = `${date.slice(0, 8)}/us-east-1/s3/aws4_request`;
    const query = queryString([
        ['X-Amz-Algorithm', 'AWS4-HMAC-SHA256'], ['X-Amz-Credential', `${accessKey}/${scope}`],
        ['X-Amz-Date', date], ['X-Amz-Expires', '60'], ['X-Amz-SignedHeaders', 'host'],
    ]);
    const signed = signature(method, pathname, query, `host:${new URL(base).host}\n`, 'host', 'UNSIGNED-PAYLOAD', date);
    return `${base}${pathname}?${query}&X-Amz-Signature=${signed.value}`;
}

async function success(response, expected = 200) {
    if (response.status !== expected) throw new Error(`S3 request failed: ${response.status} ${(await response.text()).slice(0, 300)}`);
    return response;
}

async function guard(name, target, headers = {}) {
    const response = await fetch(`${base}${target}`, { method: 'PUT', headers, body: 'rejected before parsing', signal: AbortSignal.timeout(10000) });
    assert.equal(response.status, 501, `${name}: unexpected status`);
    assert.equal(response.headers.get('x-hvostid-storage-guard'), 'disabled-feature', `${name}: upstream handled request`);
    checks.push(name);
}

function tarEntry(name, type, contents) {
    const header = Buffer.alloc(512);
    header.write(name, 0, 100);
    header.write('0000644\0', 100, 8);
    header.write('0000000\0', 108, 8);
    header.write('0000000\0', 116, 8);
    header.write(`${contents.length.toString(8).padStart(11, '0')}\0`, 124, 12);
    header.write('00000000000\0', 136, 12);
    header.fill(32, 148, 156);
    header.write(type, 156, 1);
    header.write('ustar\0', 257, 6);
    header.write('00', 263, 2);
    const checksum = header.reduce((sum, byte) => sum + byte, 0);
    header.write(`${checksum.toString(8).padStart(6, '0')}\0 `, 148, 8);
    return Buffer.concat([header, contents, Buffer.alloc((512 - contents.length % 512) % 512)]);
}

function snowballArchive(name, metadataKey) {
    const record = `minio.metadata.${metadataKey}=test-value\n`;
    let length = record.length + 2;
    while (`${length} ${record}`.length !== length) length = `${length} ${record}`.length;
    return Buffer.concat([
        tarEntry('PaxHeaders/object', 'x', Buffer.from(`${length} ${record}`)),
        tarEntry(name, '0', Buffer.from('test object')),
        Buffer.alloc(1024),
    ]);
}

async function postPolicyWithReplicationMetadata() {
    const date = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
    const scope = `${date.slice(0, 8)}/us-east-1/s3/aws4_request`;
    const fields = {
        key: 'denied-form', 'x-amz-algorithm': 'AWS4-HMAC-SHA256',
        'x-amz-credential': `${accessKey}/${scope}`, 'x-amz-date': date,
        'X-Minio-Replication-Server-Side-Encryption-Iv': 'invalid-injected-metadata',
    };
    const policy = Buffer.from(JSON.stringify({ expiration: new Date(Date.now() + 60000).toISOString(),
        conditions: [{ bucket }, ...Object.entries(fields).map(([key, value]) => ({ [key]: value }))],
    })).toString('base64');
    const signingKey = hmac(hmac(hmac(hmac(`AWS4${secretKey}`, date.slice(0, 8)), 'us-east-1'), 's3'), 'aws4_request');
    const form = new FormData();
    for (const [key, value] of Object.entries(fields)) form.append(key, value);
    form.append('policy', policy);
    form.append('x-amz-signature', createHmac('sha256', signingKey).update(policy).digest('hex'));
    form.append('file', new Blob(['test object']), 'test.txt');
    const response = await fetch(`${base}/${bucket}`, { method: 'POST', body: form, signal: AbortSignal.timeout(10000) });
    assert.equal(response.status, 501, 'Signed POST policy must be disabled');
    assert.equal(response.headers.get('x-hvostid-storage-guard'), 'disabled-feature');
    checks.push('Signed POST-policy replication metadata rejected');
}

try {
    for (const protocol of ['ftp', 'sftp']) {
        let rejected = false;
        try {
            docker(['run', '--rm', '--pull=never', '--read-only', image, 'server', '/data', `--${protocol}=address=:8022`]);
        } catch (error) {
            const output = `${error.stdout ?? ''}${error.stderr ?? ''}`;
            rejected = error.status === 1 && output.includes('FTP and SFTP are disabled');
        }
        assert.equal(rejected, true, `${protocol} startup did not reject the unsupported protocol`);
        checks.push(`${protocol.toUpperCase()} startup rejected`);
    }
    container = docker(['run', '--detach', '--pull=never', '--read-only', '--tmpfs', '/data:rw,size=256m',
        '--tmpfs', '/tmp:rw,size=32m', '--tmpfs', '/root/.minio:rw,size=16m', '--publish', '127.0.0.1::9000',
        '--env', `MINIO_ROOT_USER=${accessKey}`, '--env', `MINIO_ROOT_PASSWORD=${secretKey}`, image]);
    assert.match(container, /^[a-f0-9]{64}$/);
    const binding = JSON.parse(docker(['inspect', '--format', '{{json .NetworkSettings.Ports}}', container]))['9000/tcp'][0];
    base = `http://127.0.0.1:${binding.HostPort}`;
    const deadline = Date.now() + 90000;
    while (true) {
        try {
            const response = await fetch(`${base}/minio/health/ready`, { signal: AbortSignal.timeout(1000) });
            if (response.ok) break;
        } catch { /* Startup can briefly refuse connections. */ }
        if (Date.now() > deadline) throw new Error('MinIO did not become ready');
        await new Promise(resolve => setTimeout(resolve, 500));
    }
    await success(await request('PUT', `/${bucket}`));
    const objects = [];
    for (const size of [1, 5, 10]) {
        const bytes = randomBytes(size * 1024 * 1024);
        const object = `/${bucket}/roundtrip-${size}`;
        await success(await request('PUT', object, bytes));
        const downloaded = await (await success(await request('GET', object))).arrayBuffer();
        assert.equal(hash(Buffer.from(downloaded)), hash(bytes));
        objects.push(object);
        checks.push(`${size} MiB S3 round trip`);
        console.log(`${size} MiB S3 round trip passed`);
    }
    const object = `/${bucket}/multipart`;
    const upload = await (await success(await request('POST', object, '', [['uploads', '']]))).text();
    const uploadId = /<UploadId>([^<]+)<\/UploadId>/.exec(upload)?.[1];
    assert.ok(uploadId, 'Multipart upload ID missing');
    const parts = [randomBytes(5 * 1024 * 1024), randomBytes(5 * 1024 * 1024)];
    const etags = [];
    for (let i = 0; i < parts.length; i++) {
        const response = await success(await request('PUT', object, parts[i], [['partNumber', String(i + 1)], ['uploadId', uploadId]]));
        etags.push(response.headers.get('etag'));
    }
    const completion = `<CompleteMultipartUpload>${etags.map((etag, i) => `<Part><PartNumber>${i + 1}</PartNumber><ETag>${etag}</ETag></Part>`).join('')}</CompleteMultipartUpload>`;
    await success(await request('POST', object, completion, [['uploadId', uploadId]]));
    assert.equal(hash(Buffer.from(await (await success(await request('GET', object))).arrayBuffer())), hash(Buffer.concat(parts)));
    objects.push(object);
    checks.push('10 MiB multipart round trip');
    const presignedObject = `/${bucket}/presigned`;
    const bytes = randomBytes(1024 * 1024);
    await success(await fetch(presigned('PUT', presignedObject), { method: 'PUT', body: bytes, signal: AbortSignal.timeout(30000) }));
    const downloaded = await (await success(await fetch(presigned('GET', presignedObject), { signal: AbortSignal.timeout(30000) }))).arrayBuffer();
    assert.equal(hash(Buffer.from(downloaded)), hash(bytes));
    objects.push(presignedObject);
    checks.push('Presigned PUT/GET round trip');

    const unsigned = { 'X-Amz-Content-Sha256': 'STREAMING-UNSIGNED-PAYLOAD-TRAILER' };
    await guard('Unsigned trailer header', `/${bucket}/denied`, unsigned);
    await guard('Unsigned trailer query credentials', `/${bucket}/denied?X-Amz-Credential=${accessKey}`, unsigned);
    await guard('Unsigned multipart upload', `/${bucket}/denied?uploadId=unknown&partNumber=1`, unsigned);
    await guard('Snowball unsigned extraction', `/${bucket}/denied`, { ...unsigned, 'X-Amz-Meta-Snowball-Auto-Extract': 'true' });
    await guard('Unsigned trailer query digest', `/${bucket}/denied?X-Amz-Content-Sha256=STREAMING-UNSIGNED-PAYLOAD-TRAILER`);
    await guard('S3 Select', `/${bucket}/roundtrip-1?%73elect&select-type=2`);
    await guard('SSE replication injection', `/${bucket}/denied`, { 'X-Minio-Replication-Server-Side-Encryption-Iv': 'bad' });
    await guard('SSE replication with source flag', `/${bucket}/denied`, { 'X-Minio-Replication-Server-Side-Encryption-Iv': 'bad', 'X-Minio-Source-Replication-Request': 'true' });
    await guard('Bucket replication configuration', `/${bucket}?replication`);
    await guard('Site replication configuration', '/minio/admin/v3/site-replication/add');
    await guard('Batch replication creation', '/minio/admin/v3/start-job');
    for (const route of ['set-remote-target', 'list-remote-targets', 'remove-remote-target']) {
        await guard(`Remote target ${route}`, `/minio/admin/v3/${route}`);
    }
    const queryInjection = await request('PUT', `/${bucket}/denied-query`, 'test object', [['X-Minio-Replication-Server-Side-Encryption-Iv', 'invalid']]);
    assert.equal(queryInjection.status, 501, 'Signed replication query injection must be rejected');
    checks.push('Signed replication query metadata rejected');
    await postPolicyWithReplicationMetadata();
    // A valid positive PAX fixture proves the negative archive reaches metadata
    // extraction rather than merely failing TAR/Snowball parsing.
    const snowballHeaders = { 'x-amz-meta-snowball-auto-extract': 'true' };
    await success(await request('PUT', `/${bucket}/positive.tar`, snowballArchive('pax-positive', 'X-Amz-Meta-Example'), [], snowballHeaders));
    const positivePax = await success(await request('GET', `/${bucket}/pax-positive`));
    assert.equal(positivePax.headers.get('x-amz-meta-example'), 'test-value');
    assert.equal(await positivePax.text(), 'test object');
    objects.push(`/${bucket}/pax-positive`);
    checks.push('Ordinary Snowball PAX metadata preserved');
    const paxInjection = await request('PUT', `/${bucket}/negative.tar`, snowballArchive('pax-denied', 'X-Minio-Replication-Server-Side-Encryption-Iv'), [], snowballHeaders);
    assert.ok(paxInjection.status >= 400, 'Signed Snowball PAX injection must be rejected');
    assert.equal((await request('HEAD', `/${bucket}/pax-denied`)).status, 404, 'Injected PAX object must not be stored');
    checks.push('Signed Snowball PAX replication metadata rejected');
    await guard('Internode ReadMultiple path', '/minio/storage/data/v63/rmpl');
    await guard('Legacy STS token header', `/${bucket}/denied`, { 'X-Amz-Security-Token': 'previous-session-token' });
    await guard('Legacy STS token query', `/${bucket}/denied?X-Amz-Security-Token=previous-session-token`);
    const sts = await request('POST', '/', '', [['Action', 'AssumeRole'], ['Version', '2011-06-15']]);
    assert.ok(sts.status >= 400, 'STS issuance must be disabled');
    checks.push('STS issuance disabled');
    let consoleAvailable = false;
    try { docker(['exec', container, 'wget', '-q', '-T', '2', '-O', '-', 'http://127.0.0.1:9001/']); consoleAvailable = true; } catch { /* No console listener is expected. */ }
    assert.equal(consoleAvailable, false, 'Unsupported console is listening');
    checks.push('Console disabled');
    for (const stored of objects) await success(await request('DELETE', stored), 204);
    await success(await request('DELETE', `/${bucket}`), 204);
    checks.push('Object and bucket cleanup');
    const report = process.env.MINIO_SMOKE_REPORT ?? 'build/reports/minio-smoke.json';
    await mkdir(path.dirname(report), { recursive: true });
    await writeFile(report, `${JSON.stringify({ image, timestamp: new Date().toISOString(), checks }, null, 2)}\n`);
    console.log(`MinIO security smoke passed: ${checks.length} checks`);
} finally {
    if (container && /^[a-f0-9]{64}$/.test(container)) docker(['rm', '--force', container]);
}
