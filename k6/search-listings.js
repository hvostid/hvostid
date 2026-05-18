// k6 scenario 1: Public catalog search (GET /api/v1/listings).
//
//   Ramp up   : 0  -> 50 VU over 30s
//   Steady    : 50 VU for 1 minute
//   Ramp down : 50 -> 0  VU over 30s
//
// Thresholds (per T24 acceptance criteria):
//   p95 < 500 ms for read traffic
//   error rate < 1 %
//
// Run:
//   k6 run k6/search-listings.js
//
// Environment overrides:
//   BASE_URL  base URL of the api-gateway (default http://localhost:8080)

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    search: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{name:listings-search}': ['p(95)<500'],
    'http_req_failed{name:listings-search}': ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const FILTERS = [
  '',
  'species=dog',
  'species=cat',
  'species=dog&city=Moscow',
  'species=cat&priceMax=10000',
  'q=friendly',
];

export default function () {
  const filter = FILTERS[Math.floor(Math.random() * FILTERS.length)];
  const query = filter ? `${filter}&page=0&size=20` : 'page=0&size=20';
  const res = http.get(`${BASE_URL}/api/v1/listings?${query}`, {
    tags: { name: 'listings-search' },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'body has content array': (r) => {
      try {
        return Array.isArray(r.json('content'));
      } catch (_e) {
        return false;
      }
    },
  });

  sleep(1);
}
