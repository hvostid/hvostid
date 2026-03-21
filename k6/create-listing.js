// k6 load test: Create listing
// Run: k6 run k6/create-listing.js

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 10 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<700'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'test-token';

export default function () {
  const payload = JSON.stringify({
    title: `Test listing ${Date.now()}`,
    description: 'Load test listing',
    species: 'dog',
    breed: 'Husky',
    age: 12,
    price: 15000,
    city: 'Moscow',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${AUTH_TOKEN}`,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/listings`, payload, params);

  check(res, {
    'status is 201': (r) => r.status === 201,
    'response time < 700ms': (r) => r.timings.duration < 700,
  });

  sleep(1);
}
