# Project Audit

## Scope and stack

- Backend: Java 21, Spring Boot 3.5.7, Spring Security, JWT, Spring Data JPA, Flyway, MySQL.
- Frontend: React 19, Vite, Tailwind CSS, React Router, Axios.
- Roles: Student, Recruiter, Placement Officer, Admin.

## Phase 6 — production-readiness findings and changes

| Verified finding | Severity | Change/status |
|---|---|---|
| Email verification existed, but registration/login still returned usable sessions and the JWT filter accepted unverified accounts. | HIGH | Fixed: registration issues no session, login/refresh and protected APIs require a verified account. Added a repository-backed unverified-account API/refresh test. |
| Production defaults included local MySQL/root, non-TLS URL, `ddl-auto=update`, a development JWT signing secret, and localhost CORS/storage fallbacks. | HIGH | Fixed: removed unsafe defaults; DB credentials, JWT secret, CORS origin, and resume path are required; Hibernate defaults to `validate`; a `prod` profile enforces MySQL TLS hostname verification, truststore configuration, Flyway validation, and no Flyway clean. |
| `/api/jobs` creation accepted client-supplied job IDs, recruiter IDs, and published/closed flags. | HIGH | Fixed: server resets ID/owner/publication state; response uses a DTO without recruiter ID. Added a DB-backed forged-create test. |
| Legacy application endpoints returned JPA entities, lacked a student-role check on student listings, and the old status route could bypass lifecycle validation. | MEDIUM | Fixed: explicit response DTOs, role check, and status changes delegated to the recruiter service lifecycle. Added a database-backed regression test. |
| Admin college/company/audit and recruiter job endpoints returned JPA entities directly. | MEDIUM | Fixed: controllers now return explicit response DTOs, including recruiter job data without internal owner IDs. |
| Password-reset and verification token values were printed to stdout behind a development flag. | HIGH | Fixed: token logging and development token exposure were removed. Values remain hashed in the database; a production email sink is required. |
| CORS allowed credentials although the application uses bearer headers and no cookie-based authentication. | LOW | Fixed: CORS credentials disabled. CSRF remains disabled for stateless bearer-token auth; revisit if cookies are introduced. |
| Resume deletion errors were silently ignored, and resume reads followed symlinks. | MEDIUM | Fixed: cleanup errors now surface and file reads reject symbolic links; path normalization is consistent. |
| Frontend did not refresh expiring access tokens despite having a refresh endpoint. | MEDIUM | Fixed: Axios rotates refresh tokens once on a protected `401`, retries the request, and clears local session on failure. Full-stack browser test exercises refresh/retry. |

### Remaining security limitations

- Mailjet HTTPS delivery is implemented and covered with mocked HTTP responses, but no external provider credentials or sender authorization were available for a live delivery test. Production activation requires owner-supplied provider configuration.
- Verification/reset throttling is in-process; login/registration do not have distributed rate limits. Configure a shared limiter or edge/WAF before a multi-instance deployment.
- Access and refresh tokens remain in browser `localStorage`; successful same-origin script injection can steal them. Consider an HttpOnly-cookie design before production (with explicit CSRF/CORS changes).
- Resume validation checks MIME type, size, and PDF signature, not full PDF parsing/AV scanning. Persistent disk permissions have not been tested on Render.
- CORS/CSRF behavior was reviewed, but no external penetration test was run.

## Phase 6 — migration and infrastructure evidence

- V1–V4 migration files were edited during earlier project phases and V5 was added. **This Phase 6 work did not modify any Flyway migration file.**
- The current directory has no `.git` metadata, so original migration file contents/checksums and whether any persistent database applied them cannot be independently established. Do not treat the current migration files as authoritative historical copies.
- No migration history query or `flyway repair` was run. No persistent database was accessed or changed.
- A local `MySQL80` service is running, but no disposable MySQL credentials were available; no existing service credentials were attempted. Docker is installed but its daemon is unavailable. Therefore **real MySQL fresh/upgrade, TLS, locking, uniqueness, and concurrency validation were not performed**.
- H2 Flyway tests cover fresh V1–V5 and the existing V2-to-V5 path only in H2 MySQL mode.
- Added Render/Netlify/Aiven deployment instructions, placeholder `.env.example`, a Dockerfile, and health check `/api/health`. None has been built/deployed/validated on those services. The production profile is intentionally blocked until an email adapter is added.

## Phase 6 verification (executed in this workspace)

- `.\mvnw.cmd clean test`: **39 passed, 0 failures, 0 errors, 0 skipped**.
- `.\mvnw.cmd verify`: **39 passed, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
- `frontend\npm.cmd run build`: **passed**, 111 modules transformed.
- `frontend\npm.cmd run test:e2e`: **5 passed** (fixture/API-mocked browser suite).
- `frontend\npm.cmd run test:e2e:fullstack`: **1 passed** against the real Spring Boot and Vite servers with isolated H2, including email verification, refresh-token recovery, and role workflows.
- Fresh and upgrade Flyway checks are H2/MySQL-mode tests, not real MySQL.

## Phase 7 — production email integration

- Replaced SMTP with a Mailjet HTTPS API adapter for verification and password-reset links. Production settings are environment-backed: `MAILJET_API_KEY`, `MAILJET_API_SECRET`, `MAIL_FROM_ADDRESS`, `MAIL_FROM_NAME`, `FRONTEND_VERIFICATION_URL`, and `FRONTEND_PASSWORD_RESET_URL`. Production startup validates required settings and HTTPS frontend URLs; HTTP Basic Authentication, fixed HTTPS endpoint, disabled redirects, and bounded timeouts are configured.
- The project owner must provision Mailjet API credentials and authorize the sender address. No provider account or credential was invented or configured. See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
- Tokens use the existing secure random generation, hashed persistence, expiry, single-use/row-lock handling, and are not returned by the API or logged. Delivery failure does not produce a success-shaped response; the caller receives a generic 503, and token issuance rolls back in its transaction.
- Existing auth workflow coverage includes token persistence hashing, verification, expired/reused tokens, password reset, and refresh-session revocation. The Mailjet adapter test suite now covers successful verification/reset payloads, Basic Authentication, HTTP rejection, unsuccessful message status, transport failure, and sanitized exceptions (**4 passed** in this change). No live provider delivery was attempted.
- Status: **implementation complete; production activation/live-provider verification pending owner configuration**.

## Phase 8 — disposable real-MySQL validation

- Ran the opt-in MySQL compatibility suite against a newly initialized, isolated MySQL **8.0.41** server using a temporary data directory, fresh schemas, and generated test-only credentials. The installed MySQL80 service and all production databases/credentials were untouched. The temporary server, credentials, and data directory were discarded afterward.
- **5 passed, 0 failed, 0 errors, 0 skipped**. Verified fresh Flyway V1–V5, validation/checksums, a synthetic V2-to-V5 upgrade with no repair, Hibernate schema validation, persistence/relationships, auth/password-reset session revocation, student/recruiter/placement workflows, foreign-key/unique constraints, and concurrent duplicate registration/offer attempts.
- The V2 upgrade fixture was recreated from the current V1/V2 scripts. It is not the unknown historical deployed schema. V1–V4 have prior development edits and this workspace has no Git metadata; original checksums and any applied production history cannot be recovered here. Do not repair or connect to a production database based on these results. The owner must obtain a safe read-only history/schema comparison and choose a reviewed baseline or forward-only migration strategy before a production upgrade.
- The regular H2 test suite excludes these five MySQL-only tests unless the explicit loopback disposable URL is set. H2 results are not represented as MySQL validation.
- Status: **disposable current-migration validation complete; historical/deployed-schema compatibility remains unresolved**.

## Combined Phase 7/8 verification

- Overall status: **Phase 7 partially complete** (implementation/tests complete; live provider delivery awaits owner configuration) and **Phase 8 partially complete** (current migration set passed isolated real-MySQL validation; historical/deployed-schema compatibility cannot be established without the missing source history and authorized baseline).
- `.\mvnw.cmd clean test`: **48 run, 0 failures, 0 errors, 5 skipped** (the five opt-in MySQL tests; run separately below).
- `.\mvnw.cmd verify`: **48 run, 0 failures, 0 errors, 5 skipped; BUILD SUCCESS** (same MySQL opt-in skip).
- `frontend\npm.cmd run build`: **passed**, 111 modules transformed.
- `frontend\npm.cmd run test:e2e`: **5 passed** (fixture/API-mocked Playwright suite).
- `frontend\npm.cmd run test:e2e:fullstack`: **1 passed** against the real Spring Boot/Vite servers and isolated H2.
- MySQL suite: **5 passed** against disposable MySQL 8.0.41, as detailed above.
- No generated Spring Security password appeared in the latest targeted/full test logs. Expected duplicate-key errors were logged by tests that intentionally exercise database uniqueness/concurrency; they contained synthetic test data only.
- No deployment or production database access was performed.

## Phase 4 security findings and remediation (historical snapshot)

| Finding | Severity | Remediation / status |
|---|---|---|
| Recruiter offer status endpoint accepted `ACCEPTED` and `DECLINED`, allowing the recruiter to decide for a student. | HIGH | Fixed: recruiters may only withdraw issued offers; students alone may accept/decline their own offers. |
| Password reset could race refresh-token rotation and fail to revoke a replacement session. | MEDIUM | Fixed: reset and refresh serialize on a pessimistic user-row lock and lock token rows before changing state. |
| Logout modified a detached refresh-token entity without a service transaction, so revocation was not reliably persisted. | HIGH | Fixed: logout is transactional and explicitly saves the revoked refresh token. |
| Interview state checks allowed feedback on future/cancelled interviews and repeated modification of terminal interviews. | MEDIUM | Fixed: interviews are row-locked; feedback requires an elapsed, non-cancelled interview, completes it, and terminal interviews cannot be modified again. |
| Interview endpoints returned JPA entities, exposing relationship serialization and relying on lazy-loaded data. | MEDIUM | Fixed: Student/Recruiter interview endpoints return explicit response DTOs and map within read/write transactions. |
| Concurrent offer requests could pass the application-level existence check simultaneously. | MEDIUM | Mitigated: offer creation locks the application row and relies on the existing unique `application_id` database constraint. No concurrent stress test has been executed. |
| Flyway scripts used `ADD COLUMN IF NOT EXISTS`, which is not supported by MySQL's `ALTER TABLE` syntax, and contained duplicate additions already defined by V1. | MEDIUM | Fixed in the migration source: removed unsupported/duplicate alterations, added indexes, and added the refresh-token user foreign key. MySQL execution remains unverified. |
| Lombok builders ignored the `enabled = true` field initializer, so new registrations were persisted as disabled. | HIGH | Fixed by marking the builder default explicitly; a repository-backed test now registers an account and verifies it can log in. |
| Student profile/application and recruiter applicant/placement endpoints mapped lazy JPA relations after their persistence sessions closed. | MEDIUM | Fixed with service transaction boundaries; full-stack tests now exercise these routes against the real HTTP server. |
| Placement Officer assignment and registration/selection APIs serialized JPA entities, exposing relationship fields and triggering lazy-proxy serialization failures. | MEDIUM | Replaced these response bodies with DTOs. Selection now also requires an eligible student registration. |

Authentication tests cover expired/reused reset and verification tokens, refresh rotation/reuse, logout revocation, disabled-account refresh rejection, and the new unverified-account restrictions. Tokens are hashed at rest. Email delivery and shared/distributed rate limiting remain production requirements.

## Phase 4 verification evidence (historical snapshot)

- Maven clean test: **35 passed, 0 failed, 0 skipped**.
- Maven verify: **35 passed, 0 failed, 0 skipped**.
- Frontend production build: passed; **111 modules transformed**.
- Fixture-based Playwright suite: **5 passed** across Student, Recruiter, Placement Officer, Admin, and role-protected routing.
- Full-stack Playwright suite: **1 passed**, starting the actual backend and frontend with a fresh H2 database; it covers synthetic Student, Recruiter, Placement Officer, and Admin workflows.
- Flyway tests: fresh H2 MySQL-mode database migrated V1-V5; a database stopped at V2 upgraded through V5.
- Full-stack profile has Flyway enabled and Hibernate `ddl-auto=validate`. All automated data is synthetic; no production account or personal data is used.

## Phase 4 database caveats (historical snapshot)

- V1–V4 migration contents were modified during earlier work and V5 was added. The current workspace has no `.git` metadata, so the originals and prior deployment/application status cannot be established from repository history. Persistent-database checksum risk remains unresolved; compare migration history and schema before upgrade. No `flyway repair` was run.
- The local MySQL service was available, but MySQL rejected the available root connection; no disposable MySQL test credentials were provided. Therefore the migrations were not actually executed by MySQL.
- Historical V1-V4 migration files were edited for MySQL syntax. Databases that already recorded those migration versions may have checksum differences. Assess migration history and schema before upgrading; do not use `flyway repair` as a blind workaround.
- Unique offer/application constraint and pessimistic locking protect duplicate offer creation. Concurrency behavior still requires a multi-connection integration/stress test.

## Remaining gaps

- Validate fresh install, upgrade, locking, and concurrency against a disposable real MySQL 8 database with authorized test credentials.
- Confirm historical Flyway checksums and deployment status from a real repository clone and the owner of any persistent database.
- Configure production email delivery and shared/distributed abuse protection.
- Complete browser-only Placement Officer eligibility/selection interactions and expand full-stack coverage to resume access, feedback timing, and every workflow edge case.
- Review deployment secrets, JWT configuration, and operational monitoring before production.

Production readiness is **not established**.
