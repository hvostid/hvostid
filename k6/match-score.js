// k6 scenario 3: Compatibility-score calculation (POST /api/v1/match/score).
//
//   Constant 10 VU for 1 minute.
//
// `setup()` logs in once as a demo BUYER (who has a questionnaire seeded
// at id 1..99) and returns the access token. Each iteration requests a
// score against a random demo listing id; the matching-service caches
// passport lookups, so this stresses the compatibility calculator and
// downstream client calls rather than just a single hot row.
//
// Thresholds (per T24 acceptance criteria):
//   p95 < 700 ms for the score call
//   error rate < 1 %
//
// Run:
//   k6 run k6/match-score.js
//
// Environment overrides:
//   BASE_URL   base URL of the api-gateway (default http://localhost:8080)
//   EMAIL      buyer account (default buyer1@demo.hvostid)
//   PASSWORD   buyer password (default demo1234)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from './lib/auth.js';

export const options = {
  scenarios: {
    score: {
      executor: 'constant-vus',
      vus: 10,
      duration: '1m',
    },
  },
  thresholds: {
    'http_req_duration{name:match-score}': ['p(95)<700'],
    'http_req_failed{name:match-score}': ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EMAIL = __ENV.EMAIL || 'buyer1@demo.hvostid';
const PASSWORD = __ENV.PASSWORD || 'demo1234';
// Demo seed owns ids 1..99 in every service; pick from a sane subset.
const LISTING_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

export function setup() {
  const token = login(BASE_URL, EMAIL, PASSWORD);
  return { token };
}

export default function (data) {
  const listingId = LISTING_IDS[Math.floor(Math.random() * LISTING_IDS.length)];
  const payload = JSON.stringify({ listingId });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
    tags: { name: 'match-score' },
  };

  const res = http.post(`${BASE_URL}/api/v1/match/score`, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response has score': (r) => {
      try {
        return typeof r.json('score') === 'number';
      } catch (_e) {
        return false;
      }
    },
  });

  sleep(1);
}
