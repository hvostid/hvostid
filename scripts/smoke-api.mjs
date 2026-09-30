import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const base = process.env.SMOKE_BASE_URL || 'http://localhost:3000';
const api = `${base}/api/v1`;
const credentials = { email: `smoke-${Date.now()}@example.com`, password: 'SmokePass123!' };
let token;
async function request(path, { status = 200, body, method = 'GET', anonymous = false, headers = {} } = {}) {
  const response = await fetch(`${api}${path}`, {
    method,
    headers: { ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
      ...(!anonymous && token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(60000),
  });
  assert.equal(response.status, status, `${method} ${path}: ${response.status} ${await response.clone().text()}`);
  return status === 204 ? null : response.json();
}
assert.equal((await fetch(base)).status, 200, 'Frontend is reachable');
await request('/listings', { anonymous: true });
await request('/listings/my', { status: 401, anonymous: true, headers: { 'X-User-Id': '1', 'X-User-Roles': 'ADMIN,SELLER' } });
await request('/listings/draft', { status: 401, anonymous: true, headers: { 'X-User-Id': '1', 'X-User-Roles': 'ADMIN,SELLER' } });
await request('/auth/register', { method: 'POST', status: 201, body: { ...credentials, name: 'Smoke Seller' } });
token = (await request('/auth/login', { method: 'POST', body: credentials })).accessToken;
await request('/profile/me/roles', { method: 'POST', body: { role: 'SELLER' } });
// A fresh token avoids the short-lived introspection cache from before the role change.
const tokens = await request('/auth/login', { method: 'POST', body: credentials });
token = tokens.accessToken;
const passport = await request('/passports', { method: 'POST', status: 201, body: {
  name: 'Smoke Cat', species: 'CAT', breed: 'Siamese', birthDate: '2025-01-01', gender: 'FEMALE',
  temperament: 'FRIENDLY', neutered: true, microchipped: true,
  vaccinations: [{ name: 'Smoke vaccination', date: '2025-04-01', nextDate: '2027-04-01' }],
} });
assert.equal(passport.vaccinations.length, 1, 'Vaccination must persist');
const original = await readFile(new URL('./seed-data/documents/sample-vet-01.pdf', import.meta.url));
for (const megabytes of [1, 5, 10, 11]) {
  const bytes = Buffer.alloc(megabytes * 1024 * 1024, 32);
  original.copy(bytes);
  bytes.write('\n%%EOF\n', bytes.length - 7);
  const form = new FormData();
  form.append('file', new Blob([bytes], { type: 'application/pdf' }), `record-${megabytes}.pdf`);
  form.append('type', 'VET_RECORD');
  const document = await request(`/passports/${passport.id}/docs`, {
    method: 'POST', body: form, status: megabytes > 10 ? 413 : 201,
  });
  if (megabytes <= 10) {
    const ticket = await request(`/passports/${passport.id}/docs/${document.id}`);
    const downloaded = await fetch(`${base}${ticket.url}`, { signal: AbortSignal.timeout(60000) });
    assert.equal(downloaded.status, 200);
    assert.deepEqual(Buffer.from(await downloaded.arrayBuffer()), bytes, `Roundtrip ${megabytes} MB`);
    await request(`/passports/${passport.id}/docs/${document.id}`, { method: 'DELETE', status: 204 });
  }
}
const listing = await request('/listings', { method: 'POST', status: 201, body: {
  title: `Smoke listing ${Date.now()}`, description: 'End-to-end smoke', species: 'CAT', breed: 'Siamese',
  age: 12, price: 0, city: 'Test City', passportId: String(passport.id),
} });
const forgedRead = await fetch(`${api}/listings/${listing.id}`, {
  headers: { 'X-User-Id': String(listing.sellerId), 'X-User-Roles': 'ADMIN,SELLER' },
});
assert.ok([403, 404].includes(forgedRead.status), 'Anonymous forged identity must not expose a draft');
await request(`/listings/${listing.id}/status`, { method: 'PATCH', body: { status: 'MODERATION' } });
await request(`/passports/${passport.id}`, { method: 'DELETE', status: 409 });
await request(`/listings/${listing.id}/status`, { method: 'PATCH', body: { status: 'DRAFT' } });
await request(`/listings/${listing.id}`, { method: 'DELETE', status: 204 });
await request(`/passports/${passport.id}`, { method: 'DELETE', status: 204 });
const refreshed = await request('/auth/refresh', { method: 'POST', body: { refreshToken: tokens.refreshToken } });
assert.ok(refreshed.accessToken);
token = refreshed.accessToken;
await request('/auth/logout', { method: 'POST', status: 204 });
console.log('API smoke passed: auth/permissions/passport/vaccinations/listing/media 1,5,10MB/413/refresh/logout');
