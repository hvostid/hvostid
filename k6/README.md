[English](./README.md) | [Русский](./README.ru.md)

# k6 load tests

Performance regression suite for the platform's hot-path API endpoints.
Every scenario asserts:

- **p95 < 500 ms** for read traffic;
- **p95 < 700 ms** for write traffic;
- **error rate < 1 %** at the documented VU count.

Failing any threshold makes `k6` exit non-zero, so the scripts can gate
CI / release jobs the same way unit tests do.

## Prerequisites

- Install [k6](https://k6.io/docs/get-started/installation/)
  (`brew install k6`, `apt install k6`, or download the binary).
- Bring up the full stack with demo data:

  ```bash
  docker compose up -d
  ./scripts/seed-all.sh
  ```

  The demo seed creates the seller / buyer accounts referenced by the
  authenticated scenarios (password `demo1234` for everyone) and listing
  / passport ids in the `1..99` range that the scripts hit.

## Scenarios

| File                                       | Endpoint               | Stage profile                                                              | Auth                   |
|--------------------------------------------|------------------------|----------------------------------------------------------------------------|------------------------|
| [`search-listings.js`](./search-listings.js) | `GET /api/v1/listings` | ramp `0 -> 50` (30 s), steady `50` (1 min), ramp `50 -> 0` (30 s)          | none (public endpoint) |
| [`create-listing.js`](./create-listing.js)   | `POST /api/v1/listings` | constant `10 VU` for 1 min                                                 | login as demo SELLER   |
| [`match-score.js`](./match-score.js)         | `POST /api/v1/match/score` | constant `10 VU` for 1 min                                                 | login as demo BUYER    |

Authenticated scripts log in once in `setup()` and reuse the access
token across all VU iterations, so the measured percentiles reflect the
endpoint under test rather than login latency.

## Running

Single scenario:

```bash
k6 run k6/search-listings.js
k6 run k6/create-listing.js
k6 run k6/match-score.js
```

Override the target host (e.g. a staging stack):

```bash
BASE_URL=https://staging.example k6 run k6/search-listings.js
```

Override demo credentials when running against a non-demo dataset:

```bash
EMAIL=loadtest-seller@example PASSWORD='...' PASSPORT_ID=12 \
  k6 run k6/create-listing.js
```

Override the listing ids used by the match-score scenario (handy when
the target environment does not have the demo `1..99` range):

```bash
LISTING_IDS='101,102,103' EMAIL=loadtest-buyer@example PASSWORD='...' \
  k6 run k6/match-score.js
```

## Tuning thresholds

The thresholds live in each script's `options.thresholds` block. They
are tagged per scenario (`{name:listings-search}`, `{name:listing-create}`,
`{name:match-score}`), so they only consider the endpoint under test
and ignore the one-off login request from `setup()`.

When you adjust either VU count or threshold, also update the
corresponding row in the table above so the documentation stays in
sync with what the scripts actually enforce.
