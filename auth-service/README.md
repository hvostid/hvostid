**English** | [Русский](./README.ru.md)

# Auth Service

Owns users, profiles, opaque sessions and account recovery. Access and refresh
credentials are random 256-bit values; only their SHA-256 digests are stored.
Expired access tokens may still be refreshed until the refresh expiry. Rotation,
password changes and session revocation serialize on the account row.

## Endpoints

| Method | Path | Access | Behavior |
| --- | --- | --- | --- |
| POST | /api/v1/auth/register | public | Create a BUYER |
| POST | /api/v1/auth/login | public | Issue an access/refresh pair |
| POST | /api/v1/auth/refresh | public | Consume and replace a refresh token once |
| POST | /api/v1/auth/logout | bearer | Revoke current session |
| POST | /api/v1/auth/logout-all | bearer | Revoke every session belonging to the caller |
| GET | /api/v1/auth/sessions | bearer | Session IDs, creation and expiry times, no tokens |
| DELETE | /api/v1/auth/sessions/{id} | bearer | Revoke an owned session; other users' IDs are a no-op |
| POST | /api/v1/auth/password-reset/request | public | Accept {email}; generic 202 for registered/unknown addresses |
| POST | /api/v1/auth/password-reset/confirm | public | Accept {token,password}; revoke all sessions after reset |
| POST | /api/v1/auth/email-verification/request | bearer | Send verification link for current account |
| POST | /api/v1/auth/email-verification/confirm | public | Consume verification {token} |
| GET | /api/v1/profile/me | bearer | Read profile including verification and contact-consent state |
| PUT | /api/v1/profile/me | bearer | Update name, phone, city, bio, contactSharingEnabled |
| POST | /api/v1/profile/me/roles | bearer | Self-assign SELLER only; privileged roles cannot be self-assigned |
| GET | /api/v1/users/{id}/contact | bearer | Seller name/phone only when seller explicitly enabled sharing; otherwise 404 |
| POST | /internal/auth/introspect | internal | Validate opaque access token; never gateway-routed |

Email is trimmed and lowercased. Passwords require at least 8 characters and at
most 72 UTF-8 bytes, matching BCrypt. Recovery and verification tokens expire
after 30 minutes, are single-use and hashed in storage; resend is limited to
once per minute per account/purpose. Expired sessions and account tokens are
cleaned every 15 minutes. Existing sessions are hashed in place by migration V4.
If existing accounts differ only by email case/whitespace, V4 stops with a clear
error: resolve that ownership ambiguity before deployment rather than merging accounts.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| SERVER_PORT | 8081 | HTTP port |
| DB_HOST / DB_NAME | localhost / hvostid_auth | PostgreSQL target |
| DB_USER / DB_PASSWORD | hvostid / hvostid | Local development credentials |
| AUTH_MAIL_ENABLED | false | Enable account mail delivery |
| AUTH_MAIL_ENCRYPTION_KEY | empty | Dedicated Base64-encoded 32-byte encryption key; required when mail is enabled |
| AUTH_MAIL_FROM | no-reply@localhost | Verified sender address |
| AUTH_PUBLIC_BASE_URL | http://localhost | Frontend origin used in recovery links; use HTTPS in production |
| SPRING_MAIL_HOST / SPRING_MAIL_PORT | localhost / 1025 | SMTP relay |
| SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD | empty | SMTP credentials |
| SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH | false | Enable SMTP authentication |
| SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE | false | Enable STARTTLS |

Disabled mail delivery returns the same 503 for known and unknown emails. Once configured,
recovery requests return generic 202 and atomically save the token plus an encrypted mail
outbox record. SMTP transport failures never reveal whether an address is registered.
The worker runs after commit, retries with 30-second to 5-minute backoff, and removes
successful or expired (30-minute) messages. The recipient and one-use link are encrypted
with AES-256-GCM; only ciphertext is stored. No credentials or recipients are logged.
The worker holds only an outbox row lock during its bounded SMTP call, not an account lock.
Delivery is at least once: a crash after SMTP accepts a message can resend the same one-use link.
The counters auth.mail.delivery (result=success/failure) and gauge auth.mail.outbox.pending
support monitoring; delivery failures also produce a message-ID-only warning.

Supply AUTH_MAIL_ENCRYPTION_KEY separately from DB/SMTP credentials through your secret
manager. Retain the key until pending messages drain or expire before rotating it; changing
it early makes old queued messages unreadable. Never commit the production key. Configure
a real relay before exposing account recovery to users. Tests mock SMTP and send no real
emails. Verification is requested explicitly from the profile; registration sends no email.

Token TTLs and cleanup interval are Spring properties under hvostid.auth;
defaults are 30 minutes access and 7 days refresh. Spring mail connect/read/write
timeouts are 5 seconds. Auth-service is internal; only the gateway may supply
identity headers. Contact consent is off for existing and newly created accounts.

## Local run

```bash
docker compose up -d postgres
./gradlew :auth-service:bootRun
./gradlew :auth-service:test
```

OpenAPI is available at http://localhost:8081/swagger-ui.html in development.
Production disables API documentation.
