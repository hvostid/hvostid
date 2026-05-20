// k6 scenario 3: Compatibility-score calculation (POST /api/v1/match/score).
//
//   Constant 10 VU for 1 minute.
//
// `setup()` logs in once as a demo BUYER and fetches the ids of every
// PUBLISHED listing the caller can see, so each iteration scores against
// a real, visible listing. Hitting random ids 1..N would otherwise score
// mostly against DRAFT/MODERATION listings the buyer cannot view, which
// surfaces as 4xx (and used to trip the circuit-breaker too).
//
// Thresholds (per T24 acceptance criteria):
//   p95 < 700 ms for the score call
//   error rate < 1 %
//
// Run:
//   k6 run k6/match-score.js
//
// Environment overrides:
//   BASE_URL    base URL of the api-gateway (default http://localhost:8080)
//   EMAIL       buyer account (default buyer1@demo.hvostid)
//   PASSWORD    buyer password (default demo1234)
//   LISTING_IDS comma-separated listing ids to exercise. When set, overrides
//               the catalog-discovery step in setup() (useful for repro).

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
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
const CATALOG_PAGE_SIZE = 100;

function parseListingIdsEnv(raw) {
  return raw
    .split(',')
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => Number.isInteger(n) && n > 0);
}

function fetchPublishedListingIds(baseUrl, token) {
  const res = http.get(`${baseUrl}/api/v1/listings?page=0&size=${CATALOG_PAGE_SIZE}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'auth-catalog' },
  });
  if (res.status !== 200) {
    fail(`catalog discovery failed: status=${res.status} body=${res.body}`);
  }
  const content = res.json('content');
  if (!Array.isArray(content)) {
    fail(`catalog response missing content array: body=${res.body}`);
  }
  const ids = content.map((item) => item.id).filter((id) => Number.isInteger(id) && id > 0);
  if (ids.length === 0) {
    fail('catalog discovery returned no PUBLISHED listings; seed the demo data first');
  }
  return ids;
}

export function setup() {
  const token = login(BASE_URL, EMAIL, PASSWORD);
  const listingIds = __ENV.LISTING_IDS
    ? parseListingIdsEnv(__ENV.LISTING_IDS)
    : fetchPublishedListingIds(BASE_URL, token);
  return { token, listingIds };
}

export default function (data) {
  const listingId = data.listingIds[Math.floor(Math.random() * data.listingIds.length)];
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
