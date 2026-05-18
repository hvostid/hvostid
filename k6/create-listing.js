// k6 scenario 2: Authenticated write traffic (POST /api/v1/listings).
//
//   Constant 10 VU for 1 minute.
//
// `setup()` logs in once as a demo SELLER and returns the access token
// so the steady-state stage does not include login latency in the
// measured percentiles.
//
// Thresholds (per T24 acceptance criteria):
//   p95 < 700 ms for write traffic
//   error rate < 1 %
//
// Run:
//   k6 run k6/create-listing.js
//
// Environment overrides:
//   BASE_URL   base URL of the api-gateway (default http://localhost:8080)
//   EMAIL      seller account (default seller1@demo.hvostid)
//   PASSWORD   seller password (default demo1234)
//   PASSPORT_ID id of an existing passport the listings reference (default 1)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from './lib/auth.js';

export const options = {
  scenarios: {
    create: {
      executor: 'constant-vus',
      vus: 10,
      duration: '1m',
    },
  },
  thresholds: {
    'http_req_duration{name:listing-create}': ['p(95)<700'],
    'http_req_failed{name:listing-create}': ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EMAIL = __ENV.EMAIL || 'seller1@demo.hvostid';
const PASSWORD = __ENV.PASSWORD || 'demo1234';
const PASSPORT_ID = __ENV.PASSPORT_ID || '1';

export function setup() {
  const token = login(BASE_URL, EMAIL, PASSWORD);
  return { token };
}

export default function (data) {
  const payload = JSON.stringify({
    title: `Load test listing ${__VU}-${__ITER}-${Date.now()}`,
    description: 'k6 load-test draft',
    species: 'dog',
    breed: 'Husky',
    age: 12,
    price: 15000,
    city: 'Moscow',
    passportId: PASSPORT_ID,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
    tags: { name: 'listing-create' },
  };

  const res = http.post(`${BASE_URL}/api/v1/listings`, payload, params);

  check(res, {
    'status is 201': (r) => r.status === 201,
    'response has id': (r) => {
      try {
        return Number.isInteger(r.json('id'));
      } catch (_e) {
        return false;
      }
    },
  });

  sleep(1);
}
