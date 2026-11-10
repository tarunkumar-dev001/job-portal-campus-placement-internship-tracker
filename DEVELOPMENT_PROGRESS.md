# Development Progress

## Combined Phase 7 and Phase 8 — current status

### Phase 7: Production email integration

- Replaced the SMTP adapter with a Mailjet HTTPS API adapter for verification and password-reset messages. Production configuration requires `MAILJET_API_KEY`, `MAILJET_API_SECRET`, `MAIL_FROM_ADDRESS`, `MAIL_FROM_NAME`, `FRONTEND_VERIFICATION_URL`, and `FRONTEND_PASSWORD_RESET_URL`; HTTP Basic Authentication, fixed HTTPS endpoint, disabled redirects, and bounded connection/request timeouts are configured.
- Email links use the existing `/verify-email` and `/reset-password` frontend flows. Tokens remain random, hashed in storage, expiring, single-use, and absent from API responses/logs. Delivery errors return a generic 503 and do not falsely signal delivery.
- Focused Mailjet adapter suite: **4 passed, 0 failed, 0 errors, 0 skipped**, covering verification/reset payloads, Basic Authentication, HTTP rejection, unsuccessful message status, transport failure, and sanitized exceptions. No real email was sent.
- **Implementation complete; Mailjet account, authorized sender, secrets, and live-delivery check require owner setup.**

### Phase 8: Real MySQL validation

- Ran the opt-in suite against a separate temporary MySQL **8.0.41** server, fresh schemas, and generated test-only credentials. Did not connect to the installed MySQL80 service or any production database; no production credentials were used. The disposable server/data and credentials were removed after the run.
- **5 tests passed, 0 failed, 0 errors, 0 skipped**: fresh V1–V5 migration, Flyway validation/checksums, synthetic V2-to-V5 upgrade without repair, Hibernate validation, auth and reset/session revocation, student/recruiter/placement workflow persistence, unique/FK constraints, and concurrent duplicate registration/offer attempts.
- The upgrade fixture represents the current V1/V2 scripts, not an original historical schema. Prior edits to V1–V4 and absent Git metadata mean deployed checksums/schema status are unknown. A safe historical comparison and owner-approved baseline/forward migration strategy remain required before any production upgrade.
- **Current migration-set disposable validation complete; historical/deployed-schema assurance remains unresolved.**

### Required verification (executed)

- Overall status: **Phase 7 partially complete** (live Mailjet delivery awaits provider setup) and **Phase 8 partially complete** (isolated MySQL tests passed; historical/deployed migration history remains unknown).
- `.\mvnw.cmd clean test`: **48 run, 0 failures, 0 errors, 5 skipped** (the MySQL-only tests are opt-in and were run separately).
- `.\mvnw.cmd verify`: **48 run, 0 failures, 0 errors, 5 skipped; BUILD SUCCESS**.
- `frontend\npm.cmd run build`: **passed**, 111 modules transformed.
- `frontend\npm.cmd run test:e2e`: **5 passed**, fixture/API-mocked Playwright.
- `frontend\npm.cmd run test:e2e:fullstack`: **1 passed**, real Spring Boot/Vite with isolated H2.
- Separate disposable MySQL suite: **5 passed** on MySQL 8.0.41.
- Expected duplicate-key messages came from synthetic constraint/concurrency tests. No generated Spring Security password appeared in current test logs.
- No deployment or production database access was performed.

## Phase 6 — Production-readiness audit (historical snapshot)

### Implemented

- Removed insecure runtime defaults for local root MySQL, non-TLS database URLs, schema auto-update, development JWT secret, localhost CORS, and temporary resume storage. Added a `prod` profile with MySQL certificate/hostname verification, Flyway validation/clean protection, Hibernate validation, and short access-token default.
- Added an explicit production startup check requiring a configured email token-delivery adapter. No adapter currently exists, so production startup intentionally fails until delivery is implemented.
- Closed email-verification bypass: registration no longer returns access/refresh tokens, it requests verification; login, refresh, and protected APIs reject unverified accounts.
- Removed reset/verification token stdout logging. Added role- and DTO-based handling for legacy application routes and sanitized job creation against client-supplied IDs/owners/publish flags.
- Replaced Admin college/company/audit and Recruiter job response entities with explicit response DTOs.
- Disabled credentialed CORS for the bearer-token client; added public DB-backed `/api/health` check. Test-only mailbox is open only under the `e2e` profile.
- Frontend registration now routes to email verification, API URL is configurable with `VITE_API_BASE_URL`, and Axios performs refresh rotation/retry on `401`.
- Resume storage paths are absolute/normalized, symbolic-link downloads are rejected, and file cleanup errors are no longer silently suppressed.
- Added `.env.example`, `.gitignore` secret exclusions, README, Render/Netlify/Aiven deployment guide, SPA rewrite, and Java 21 Dockerfile. No credentials were added.

### Phase 6 verification

- `.\mvnw.cmd clean test`: **39 passed, 0 failed, 0 errors, 0 skipped**.
- `.\mvnw.cmd verify`: **39 passed, 0 failed, 0 errors, 0 skipped; BUILD SUCCESS**.
- `frontend\npm.cmd run build`: **passed**, 111 modules transformed.
- `frontend\npm.cmd run test:e2e`: **5 passed**; this suite uses API fixtures.
- `frontend\npm.cmd run test:e2e:fullstack`: **1 passed** against actual Spring Boot/Vite with isolated H2. It exercises account verification, login, refresh retry, and workflows across all four roles.
- H2 Flyway fresh and V2-to-V5 upgrade tests passed; not real MySQL validation.

### Outstanding blockers / accurate readiness (as of Phase 6)

- **Real MySQL not tested.** The MySQL80 service is running but there are no authorized disposable credentials; no existing credentials were tried. Docker CLI is present but its daemon is unavailable.
- **Flyway history unresolved.** V1–V4 were modified in earlier phases; V5 was added. No `.git` history is available to recover originals or determine whether they were applied. No migration file was changed in Phase 6, no persistent database was inspected, and no Flyway repair was run. The deployment guide includes the exact safe history inspection and disposable test procedure.
- **Email delivery missing.** The `prod` profile fails fast without a production `AuthTokenSink`; implement and test a provider before attempting deployment.
- Shared/distributed login and registration rate limiting is not configured. Existing verification/reset throttling is in-process only.
- Tokens remain in `localStorage`; HttpOnly cookie migration/XSS mitigation is pending.
- Dockerfile build, Render/Netlify/Aiven configuration, SSL truststore, persistent file storage, and external health checks have not been exercised.
- No deployment or external-service operation was performed. **Production readiness is not established.**

## Phase 4 verification (historical snapshot)

- Fixed recruiter offer authorization so recruiters can withdraw issued offers but cannot accept or decline on a student's behalf.
- Prevented applications from being marked `OFFERED` without creating a persistent offer; the legacy offer endpoint now uses the persistent offer request.
- Added application-row locking plus the existing unique `offers.application_id` constraint to serialize concurrent offer creation. Offer and interview status updates now lock their records.
- Restricted interview feedback to valid, elapsed, non-cancelled interviews; feedback completes the interview, and completed/cancelled interviews cannot be rescheduled or cancelled again.
- Changed student/recruiter interview responses to DTOs and added transactions around offer/interview response mapping to avoid lazy-loading failures.
- Serialized password reset and refresh operations per user; refresh/reset tokens are locked while used. Logout now persists refresh-token revocation in a transaction.
- Added rate limiting to verification requests and made its in-process request counter thread-safe.
- Corrected migration SQL by removing MySQL-incompatible `ADD COLUMN IF NOT EXISTS` statements, removing duplicate column additions, and adding recruiter/application indexes. Added a foreign key for refresh tokens.
- Added Playwright browser tests using synthetic users and API fixtures, with protected-route, Student, Recruiter, Placement Officer, and Admin UI workflows.
- Added an Admin audit-log panel and dashboard links to the Student and Recruiter interview/offer pages.

## Latest verification

- `.\mvnw.cmd clean test`: **31 tests passed, 0 failed, 0 skipped**.
- `.\mvnw.cmd verify`: **31 tests passed, 0 failed, 0 skipped**.
- `npm.cmd run build`: **passed**, 111 modules transformed.
- `npm.cmd run test:e2e`: **5 browser tests passed**.
- Flyway H2/MySQL-mode tests: fresh database migrations V1-V5 and an existing V2 schema upgraded through V5.

## Phase 4 remaining work / limitations (historical snapshot)

- Fixture-based browser tests still intercept backend APIs. A separate full-stack Playwright setup now starts the real Spring Boot application and Vite against an isolated H2 database.
- MySQL migration execution was not possible: the local MySQL service rejected the available root connection and no disposable test credentials were provided. H2 MySQL-mode migration checks passed, but are not a substitute for execution on MySQL.
- V1-V4 migration scripts were edited to make the sequence MySQL-compatible before deployment. Any persistent database that has already recorded prior versions must be checked for Flyway checksum differences before upgrade; do not run `flyway repair` blindly.
- Concurrent offer creation is protected by pessimistic application-row locking and the database unique constraint, but a multi-connection concurrency stress test has not been run.
- Verification/reset email delivery still needs a production email provider; rate limiting is in-process and should be replaced or supplemented with shared/distributed limiting for a multi-instance deployment.
- The full-stack journey exercises drive registration, eligibility, and selection through real APIs and verifies the role dashboards; a complete click-through of Placement Officer eligibility and selection controls remains outstanding. Production readiness is not established.

## Phase 5 — Full-stack verification

- Added student placement-drive browsing and registration. The backend filters to open, non-expired drives, rejects duplicates, and serializes registration through a pessimistic drive-row lock. Added Placement Officer registration review, eligibility verification, and selection controls.
- Enforced that a student registration must be `ELIGIBLE` before selection and that only student accounts can be selected.
- Replaced entity responses for officer-college assignment and drive registration/selection operations with explicit DTOs. This avoids lazy-proxy serialization failures and limits exposed student fields.
- Fixed newly registered accounts being created disabled because the Lombok builder ignored the entity field initializer; new accounts now default to enabled, and an integration test verifies registration followed by login.
- Full-stack testing uncovered detached lazy-relation failures in student profile/application reads, recruiter applicant/interview operations, and placement drive operations. Added service transactions where DTO mapping accesses lazy relations.
- Added a test-only token sink and owner-guarded mailbox, E2E-only synthetic Admin seed, isolated H2/Flyway profile with Hibernate schema validation, and separate full-stack Playwright configuration. The profile does not expose tokens in logs.
- Full-stack Playwright executes a real Student registration/login, job application, drive registration, interview/offer viewing and acceptance; Recruiter job and offer/interview API workflows; Placement Officer drive registration/eligibility/selection APIs; and Admin setup/dashboard workflows. The browser visits all four role dashboards.

### Phase 5 verification

- `.\mvnw.cmd clean test`: **35 tests passed, 0 failed, 0 skipped**.
- `.\mvnw.cmd verify`: **35 tests passed, 0 failed, 0 skipped**.
- `npm.cmd run build`: passed; 111 modules transformed.
- `npm.cmd run test:e2e`: **5 passed**.
- `npm.cmd run test:e2e:fullstack`: **1 passed** against the actual Spring Boot and Vite servers with synthetic records and an isolated H2 database.
- Fresh H2/Flyway migration tests passed; the full-stack profile also validates the migrated schema against Hibernate mappings.

### Remaining Phase 5 limitations

- V1–V4 migration files were edited in prior work and V5 was added. This workspace has no Git metadata, so original migration contents and whether any persistent database applied them cannot be independently verified. Existing Flyway checksums may differ; compare against an authorized database before upgrading. No migration restoration or `flyway repair` was attempted.
- Real MySQL 8 validation was not run: the local service rejected the available root connection, Docker daemon was unavailable, and no disposable MySQL test credentials were provided. H2 MySQL mode is not evidence of MySQL compatibility.
- The live workflow verifies placement registration and selection through real backend calls, but does not yet drive every Placement Officer selection interaction solely through browser clicks. Offer concurrency/locking also has no multi-connection MySQL stress test.
- Production email delivery, shared rate limiting, production secret management, and operational monitoring remain required. Production readiness is not established.
