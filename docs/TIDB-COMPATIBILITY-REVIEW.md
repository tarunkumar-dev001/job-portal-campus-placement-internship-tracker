# TiDB Compatibility Harness Review

## Current safeguards

The standalone harness remains opt-in through
`TIDB_COMPAT_TESTS=true` and `TIDB_COMPAT_CONFIRM_DISPOSABLE=true`. It accepts
only the fixed TiDB Cloud gateway hostname, port 4000, and the two fixed
schemas: `careerconnect_tidb_compat_fresh` and
`careerconnect_tidb_compat_upgrade`. It requires dedicated test-only username,
password, PKCS12 truststore path, and truststore password environment
variables. It does not read or fall back to `DB_*` or Spring datasource
configuration.

The complete instance-prefixed username is passed unchanged to Connector/J.
Because production and test share the public gateway hostname, hostname, IP,
and successful TLS hostname verification cannot distinguish the clusters.
Instance identity therefore depends on TiDB Cloud scoping the dedicated
credentials to the disposable test instance. Before any live run, and before
relying on a prior result, independently verify provider-side account scope
and effective grants limited to the two test schemas. The v8.5.3 version
check is not an instance identity check; the opt-in confirmation variable is
only an operator attestation.

Connector/J 9.4.0 is configured with `sslMode=VERIFY_IDENTITY`,
`trustCertificateKeyStoreUrl`, `trustCertificateKeyStoreType=PKCS12`,
`trustCertificateKeyStorePassword`, and
`fallbackToSystemTrustStore=false`. The configured truststore must load, be
PKCS12, contain only the expected ISRG Root X1 CA certificate, and match the
verified SHA-256 fingerprint. The same configured `MysqlDataSource` for each
schema is used for direct JDBC and Flyway. The downloaded PEM must first be
imported into a dedicated PKCS12 truststore; it is not passed directly to
Connector/J.

Flyway logging is suppressed and database exceptions are reported without
detailed messages. The harness keeps the fixed schemas, empty-schema checks,
TiDB v8.5.3 check, one-second `GET_LOCK` timeout, fail-closed acquisition, and
`RELEASE_LOCK` lifecycle. It does not create schemas, run `clean`, or drop
objects.

## Offline verification

- The focused `TidbCompatibilitySafetyTests` class passed: 18 tests, zero
  failures, errors, or skips.
- The focused Maven run also compiled the integration test source.
- The offline tests validate endpoint/schema/TLS restrictions, dedicated
  configuration, PKCS12 truststore loading and CA fingerprint, malformed or
  incorrect truststores, secret-safe errors, TiDB version parsing, and
  advisory-lock failure behavior. They also verify that production datasource
  URLs cannot substitute for the dedicated test URLs and that SQL/Flyway
  exception details and causes are suppressed. Lock tests cover successful
  release and release after schema work fails.
- The offline safety-test run made no TiDB connection and executed no SQL or
  migration; the separate live integration run below did.

## Previously reported TiDB v8.5.3 integration result — 2026-11-16

The following is the result recorded for a prior run, not a run performed or
independently revalidated during this release-safety work. The account's
current provider-side instance scope and effective grants were not verified
here. Do not treat the reported result as production readiness evidence.

The prior run was reported as using the `careerconnect-tidb-test` instance
and the two disposable compatibility schemas:

- Fresh schema `careerconnect_tidb_compat_fresh`: **PASS** — V1-V6 applied.
- Upgrade schema `careerconnect_tidb_compat_upgrade`: **PASS** — V1-V5
  applied, followed by V6.
- Both paths validated all six Flyway migrations.
- Live test result: **2 tests, 0 failures, 0 errors, 0 skipped**.
- The production database was not used.
- TLS identity verification used `sslMode=VERIFY_IDENTITY`.

The upgrade test verifies that legacy rows with null session IDs and no
revocation timestamp are revoked, and that an existing revocation timestamp
is preserved. Preservation of a pre-existing non-null `session_id` was not
exercised because the normal V5 schema does not contain that column.

This reported result covers only the specified migration paths and
assertions. It does not prove compatibility of every application workflow
with TiDB, approve production deployment, or independently establish
provider-side account isolation and grants behind the shared gateway.
Production V6 readiness remains unestablished until credentials, effective
grants, production schema history, backup recovery, and a production-like
rehearsal are independently reviewed. Offline checks cannot verify remote
grants or the remote certificate handshake. The advisory lock only serializes
cooperating harness runs.
