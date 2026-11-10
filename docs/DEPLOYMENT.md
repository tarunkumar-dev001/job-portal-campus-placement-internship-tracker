# Deployment preparation: Render, Netlify, and TiDB Cloud

This guide describes configuration only. No external service has been changed and no deployment has been run. Before any deployment, resolve the historical Flyway baseline and complete provider setup listed below.

## Configuration names used by the application

### Backend

| Variable | Required | Purpose |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE` | production | Set to `prod` to enable strict MySQL TLS truststore settings. |
| `PORT` | No | HTTP listener; defaults to `8080`, uses the platform-provided port when set. |
| `DB_URL` | Yes | TiDB Cloud MySQL JDBC URL, e.g. `jdbc:mysql://<TIDB_HOST>:4000/<DATABASE>?sslMode=VERIFY_IDENTITY&serverTimezone=UTC`. |
| `DB_USERNAME` | Yes | Dedicated least-privilege application database account. |
| `DB_PASSWORD` | Yes | Application database password. |
| `DB_SSL_TRUSTSTORE_PATH` | `prod` only | `/tmp/tidb-cloud-truststore.p12`; the Docker image sets this path after decoding the Render secret file. |
| `DB_SSL_TRUSTSTORE_PASSWORD` | `prod` only | Truststore password. Keep as a secret. |
| `JWT_SECRET` | Yes | Random high-entropy signing secret; provide independently of database credentials. |
| `JWT_EXPIRATION_MS` | No | Access-token lifetime; default is 900000 ms (15 minutes). |
| `CORS_ALLOWED_ORIGINS` | Yes | Comma-separated exact frontend origins, including scheme, with no trailing slash. |
| `RESUME_STORAGE_PATH` | Yes | Writable upload directory; local files are not persistent on Render Free. |
| `JPA_DDL_AUTO` | No | Defaults to `validate`; do not set production to `update` or `create`. |
| `MAILJET_API_KEY` | `prod` only | Mailjet API key; store as a secret. |
| `MAILJET_API_SECRET` | `prod` only | Mailjet API secret; store as a secret. |
| `MAIL_FROM_ADDRESS` | `prod` only | Sender address authorized in Mailjet. |
| `MAIL_FROM_NAME` | `prod` only | Display name for the sender. |
| `FRONTEND_VERIFICATION_URL` | `prod` only | HTTPS URL for the existing `/verify-email` page; the adapter appends the token query parameter. |
| `FRONTEND_PASSWORD_RESET_URL` | `prod` only | HTTPS URL for the existing `/reset-password` page; the adapter appends the token query parameter. |

Create Mailjet API credentials and authorize the sender address in Mailjet. Set `MAILJET_API_KEY` and `MAILJET_API_SECRET` as Render secrets; do not put them in source control or expose them in logs. Set `MAIL_FROM_ADDRESS`, `MAIL_FROM_NAME`, `FRONTEND_VERIFICATION_URL=https://<your-netlify-domain>/verify-email`, and `FRONTEND_PASSWORD_RESET_URL=https://<your-netlify-domain>/reset-password` to the chosen sender and frontend values. The application sends JSON to Mailjet's fixed HTTPS transactional-send endpoint using HTTP Basic Authentication, with redirects disabled and bounded connection/request timeouts. Verify sender/domain status and account sending limits in Mailjet before relying on delivery.

Verification/reset tokens are generated from 256 bits of secure randomness, SHA-256 hashed in storage, expire, and are consumed under a database lock. Raw values are included only in their email link. Delivery errors roll back the token-issuing transaction where applicable and return a generic 503 without logging provider details or returning a token. Non-success HTTP responses, rejected Mailjet message statuses, and transport failures are treated as delivery failures. Production startup fails fast when either Mailjet credential, sender setting, or HTTPS frontend URL setting is absent.

### Frontend

| Variable | Required | Purpose |
|---|---:|---|
| `VITE_API_BASE_URL` | For split hosting | Full API base URL, e.g. `https://<render-service>.onrender.com/api`. |

Vite embeds this value at build time. Configure it before the Netlify build; changing it afterward requires a new build.

## Safe prerequisites

1. Obtain the actual Flyway history and schema for any existing database. Compare every applied version, script name, checksum, and success state with the exact source release. Do not infer this from the current workspace: it has no `.git` metadata.
2. In an authorized read-only session, inspect history:

   ```sql
   SELECT installed_rank, version, description, type, script, checksum,
          installed_on, success
   FROM flyway_schema_history
   ORDER BY installed_rank;
   ```

3. Check `success=1` for every applied migration. Compare each version and checksum with Flyway `info`/`validate` output from the exact release that originally ran; use Git (when available) to inspect those historical migration files. Compare actual tables, columns, indexes, constraints, and foreign keys with the scripts and entity mappings. Save the results before planning a change.
4. Take a verified backup and restore it to a disposable database before testing an upgrade. Never run these checks against production.
5. Resolve any checksum/schema differences with an explicit forward-only migration reviewed against the restored database. Do not run `flyway repair` merely to make validation pass.
6. Test a fresh install and upgrade on an isolated MySQL-compatible database before connecting TiDB Cloud.

The Phase 8 MySQL suite uses only a separate temporary MySQL data directory and newly generated test credentials; it does not connect to the installed `MySQL80` service. When Docker is available, the following is an alternative disposable setup (do not reuse production credentials):

```powershell
$testPassword = [Guid]::NewGuid().ToString('N')
docker run -d --rm --name careerconnect-mysql-phase8 `
  -e MYSQL_ROOT_PASSWORD=$testPassword `
  -e MYSQL_DATABASE=careerconnect_phase8 `
  -p 127.0.0.1:33306:3306 mysql:8.0
```

In the same PowerShell session, wait until the disposable container accepts connections, then set only disposable test settings and run the app (do not activate the `prod` profile for this local test):

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:33306/careerconnect_phase8?serverTimezone=UTC'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = $testPassword
$env:JWT_SECRET = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
$env:RESUME_STORAGE_PATH = Join-Path $env:TEMP 'careerconnect-phase8-resumes'
$env:JPA_DDL_AUTO = 'validate'
.\mvnw.cmd spring-boot:run
```

Flyway then applies migrations and Hibernate validates mappings. Stop the app and dispose of the container with `docker stop careerconnect-mysql-phase8`. For an upgrade test, restore a reviewed V5 backup into a second disposable schema first. Never substitute a production URL or credentials. Docker was unavailable during Phase 8; instead, the test suite ran against an independently initialized temporary MySQL 8.0.41 data directory, with two fresh schemas and a generated test-only credential. The installed MySQL80 service was not used. To rerun the opt-in suite, set `MYSQL_TEST_URL` to `jdbc:mysql://127.0.0.1:<ephemeral-port>/careerconnect_phase8_fresh?serverTimezone=UTC`, `MYSQL_TEST_UPGRADE_URL` to the matching `careerconnect_phase8_upgrade` URL, and `MYSQL_TEST_USERNAME`/`MYSQL_TEST_PASSWORD` to credentials created solely for that disposable server; then run `.\mvnw.cmd "-Dtest=MySqlCompatibilityIntegrationTests" test`. The test class rejects URLs not matching the loopback fresh-schema pattern. Never set these variables to an existing or production database.

## TLS truststore for TiDB Cloud

Download the CA certificate from the TiDB Cloud console and create and verify a PKCS#12 truststore locally. Keep both the certificate and truststore outside the repository. Base64-encode the verified truststore locally, then add it to the Render backend service under **Environment → Secret Files**:

- Filename: `tidb-cloud-truststore.p12.b64`
- Contents: Base64 text of the PKCS#12 truststore

The Render Docker service exposes this file at `/etc/secrets/tidb-cloud-truststore.p12.b64`. The Docker entrypoint decodes it at startup to `/tmp/tidb-cloud-truststore.p12`, using restrictive file permissions, and exits without starting Java if decoding fails. The application user is in group `1000` to read Render's secret file. The image sets `DB_SSL_TRUSTSTORE_PATH=/tmp/tidb-cloud-truststore.p12`; set `DB_SSL_TRUSTSTORE_PASSWORD` separately as a Render secret. Never put the password, CA certificate, truststore, or Base64 content in Git or the Docker image.

The `prod` profile configures Connector/J with `sslMode=VERIFY_IDENTITY`, the PKCS#12 truststore, and certificate hostname verification. Keep this setting enabled and ensure the JDBC URL hostname matches the database certificate.

## Render backend

Use the repository root as the service root and Render's Docker runtime. Configure:

- Dockerfile path: `./Dockerfile`
- Image build runs `mvn -B -DskipTests package` in the build stage.
- Container start command: use the Dockerfile entrypoint; do not override it with a direct Java command.
- Health check path: `/api/health`
- Runtime: Java 21
- Environment variables: the backend variables above, including `SPRING_PROFILES_ACTIVE=prod`.
- Render Free does not support persistent disks; uploaded files on the local filesystem can be lost when the service restarts, redeploys, or spins down. Use external persistent storage for resumes or a Render plan with disk support.
- Configure `CORS_ALLOWED_ORIGINS` to the exact Netlify site origin.

The health endpoint runs `SELECT 1`; it returns success only when the database is reachable. Flyway runs on startup and Hibernate validates the schema.

The Dockerfile pins Java 21 runtime/build images and runs the service as a non-root user. Render image build, secret-file readability, and database TLS must be tested before deployment.

## Netlify frontend

- Base directory: `frontend`
- Build command: `npm ci && npm run build`
- Publish directory: `dist`
- Set `VITE_API_BASE_URL=https://<render-service>.onrender.com/api`
- Configure the production site domain first, then set the identical origin in backend `CORS_ALLOWED_ORIGINS`.
- SPA routing fallback is supplied by `frontend/public/_redirects`.

## Migration and deployment order

1. Review/backup the database and establish the Flyway version/checksum baseline.
2. Create a least-privilege TiDB Cloud application user; download the CA and validate TLS in a disposable test.
3. Configure the Render disk and backend secret/environment variables. Do not point the service at an existing database until the migration plan is approved.
4. Apply migrations to a fresh/disposable database first, then review the upgrade path. On production, take a backup and run the reviewed forward migration during an approved maintenance window.
5. Deploy the backend and verify `/api/health`, migration history, and application logs contain no secrets.
6. Build/deploy Netlify with `VITE_API_BASE_URL`, set the exact CORS origin, and verify login/role workflows using synthetic test accounts.

## Post-deployment smoke checks

- `GET https://<render-service>.onrender.com/api/health` returns `200` and `{"status":"UP"}`.
- Browser network requests target the configured API origin; CORS allows only the intended Netlify origin.
- Test login, refresh rotation, logout, disabled-account rejection, and one workflow for each role with synthetic accounts.
- Verify resume upload/download ownership, persistent storage across a service restart, and TLS certificate verification.
- Confirm Flyway schema history and inspect logs for credentials, JWTs, refresh/reset/verification tokens, and personal data.

## Not yet production-ready

- A disposable MySQL 8.0.41 instance was created with a temporary data directory, fresh schemas, and newly generated test credentials. The Phase 8 suite passed fresh V1–V5 migration, schema validation, synthetic V2-to-V5 upgrade, auth/workflow persistence, unique/FK constraints, and concurrent duplicate registration/offer tests. The instance and credentials were discarded. Docker's daemon is unavailable.
- Git migration history and any persistent database status are unknown. The synthetic V2 schema was generated from current scripts; it cannot establish compatibility with any previously deployed V1/V2 checksum. The original deployed schema/history must be reviewed safely before a production upgrade.
- Mailjet HTTPS delivery is implemented but has only been verified with mock HTTP responses; the project owner must provision API credentials and authorize the sender before use.
- `AuthContext` persists bearer and refresh tokens in `localStorage`; this exposes tokens to successful same-origin script injection. An HttpOnly-cookie design requires deliberate CSRF/CORS changes and has not been implemented.
- Render/Netlify/TiDB Cloud commands, TLS truststore handling, resume storage, and health check have not been exercised on those services.
- The `prod` Spring profile intentionally fails startup until Mailjet credentials, sender, and HTTPS frontend URL configuration is supplied.
- Local non-`prod` startup has no default email sender. Requests for an existing account fail explicitly with a generic 503 unless an email sink is supplied (the isolated E2E profile supplies a synthetic test mailbox).
