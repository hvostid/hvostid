// k6 load test: Search listings
// Run: k6 run k6/search-listings.js

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<700'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/listings?page=0&size=20`);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 700ms': (r) => r.timings.duration < 700,
  });

  sleep(1);
}
