# Postman collections

Five Postman collections (v2.1) plus a `hvostid-local` environment file for
poking the gateway by hand and for smoke-checking via Newman in CI.

| File | Purpose |
| --- | --- |
| `COL-01-public-api.json` | Public API smoke test: register, login, refresh, profile, list catalog with and without filters. |
| `COL-02-listing-passport.json` | Seller flow: create passport, upload photo and vaccination cert, check trust score, create listing, send to MODERATION, moderator approves. |
| `COL-03-matching.json` | Buyer matching: questionnaire upsert, compatibility score, recommendations, plus negative cases (no questionnaire, invalid listingId). |
| `COL-04-passport-files.json` | Passport documents in MinIO: upload, list, presigned download URL, trust score, delete. |
| `COL-05-frontend-e2e.json` | End-to-end happy path the SPA performs: register, login, browse catalog, view listing, match score, attempt create listing as buyer (403), logout. |
| `hvostid-local.postman_environment.json` | Environment with `baseUrl`, user credentials, and auto-filled token/id variables. |

## One-click import

1. Bring the stack up locally:

    ```bash
    docker compose up -d
    ```

   Gateway must answer at `http://localhost:8080`.

2. In Postman, **File -> Import** and select every `COL-*.json` plus
   `hvostid-local.postman_environment.json` from this directory.
3. Top-right environment dropdown -> select **`hvostid-local`**.
4. Run a collection via the Runner, or just open requests and hit *Send*.

The environment ships with throwaway credentials
(`buyer.t36@hvostid.local` / `seller.t36@hvostid.local` etc.) -- change
them before sharing real data.

## What the collections do for you

- **Login saves tokens.** The login request stores `accessToken` and
  `refreshToken` into the environment via a test script.
- **Subsequent requests** authenticate with
  `Authorization: Bearer {{accessToken}}` from the collection-level auth.
- **Auto-refresh on 401.** Each collection has a `test`-level event hook
  that, when a request returns 401 and a refresh token is present, calls
  `POST /api/v1/auth/refresh`, writes the new pair back, and the next
  request picks it up automatically.
- **IDs flow between requests.** `passportId`, `listingId`, `documentId`
  are written by earlier requests so later steps in the same collection
  pick them up from the environment.

Collections that need a non-default role (SELLER for COL-02 / COL-04,
MODERATOR for the approval step in COL-02) assume the seller and
moderator accounts already exist with the matching credentials. If not,
either edit `sellerEmail` / `moderatorEmail` in the environment to point
at existing accounts, or seed them via auth-service before running.

## CLI runs via Newman

```bash
# install once
npm install -g newman

# run every COL-*.json
./postman/run-newman.sh

# run a single collection
./postman/run-newman.sh COL-01-public-api

# override the gateway URL without editing the env file
BASE_URL=http://localhost:8082 ./postman/run-newman.sh
```

`run-newman.sh` exits non-zero if any collection has failed assertions
and writes a JSON report per collection at
`postman/.newman-<collection>.json` for CI artifact upload.

### CD smoke (optional)

`COL-01-public-api.json` is the lightest of the five and can be run as a
post-deploy smoke check (T26). Suggested invocation in a CD step:

```bash
BASE_URL=https://staging.hvostid.example ./postman/run-newman.sh COL-01-public-api
```

## Notes

- COL-02 and COL-04 expect you to attach a real image / document in the
  `file` formdata field before hitting *Send*; otherwise the upload
  steps return 400 and the test script accepts that as a non-failure.
- Tokens in the environment are marked as `secret` so Postman hides them
  in the UI; they still export in cleartext if you share the env file.
