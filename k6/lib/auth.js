// Shared k6 helper: log in once via /api/v1/auth/login and return the
// opaque accessToken issued by auth-service. Intended to be called from
// the `setup()` stage of a k6 scenario so the token is fetched exactly
// once and shared across every VU iteration.

import http from 'k6/http';
import { check, fail } from 'k6';

export function login(baseUrl, email, password) {
  const res = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth-login' } },
  );

  const ok = check(res, {
    'login returns 200': (r) => r.status === 200,
    'login returns access token': (r) => {
      try {
        return Boolean(r.json('accessToken'));
      } catch (_e) {
        return false;
      }
    },
  });

  if (!ok) {
    fail(`login failed for ${email}: status=${res.status} body=${res.body}`);
  }

  return res.json('accessToken');
}
