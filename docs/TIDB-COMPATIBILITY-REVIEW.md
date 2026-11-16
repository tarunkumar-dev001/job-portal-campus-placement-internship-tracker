# TiDB Compatibility Harness Review

## Purpose and safety controls

The opt-in TiDB compatibility harness verifies fresh V1-V6 migrations and a
V1-V5-to-V6 upgrade against an isolated TiDB-compatible test database. It runs
as standalone JUnit tests, without starting Spring or inheriting the
application datasource.

The integration tests require `TIDB_COMPAT_TESTS=true` and
`TIDB_COMPAT_CONFIRM_DISPOSABLE=true`. Both JDBC URLs must use the exact
operator-verified numeric IP and port supplied through
`TIDB_COMPAT_ALLOWED_ENDPOINT`, with the two fixed disposable schemas:
`careerconnect_tidb_compat_fresh` and
`careerconnect_tidb_compat_upgrade`. Test URLs require
`sslMode=VERIFY_IDENTITY`, and the certificate must match the endpoint IP.
Configured production endpoints and credentials are checked before the first
JDBC connection. The harness checks the selected database and schema contents,
and uses a schema-scoped advisory lock around the check, migration, and
assertions. It does not create schemas, clean them, or drop objects.

These controls reduce accidental misuse but cannot prove that a manually
allowlisted proxy does not forward to production or prevent a non-cooperating
database user from modifying the schemas. Use a manually verified disposable
TiDB endpoint and credentials restricted to the two test schemas.

## Verification completed

- All 13 offline configuration and safety tests passed with no failures,
  errors, or skips.
- The TiDB integration test source compiled successfully.
- No TiDB connection has been made; no SQL has been executed and no migration
  has been run.
- The MySQL compatibility test and production application configuration were
  not changed for this review.

## Remaining LOW uncertainty

Support for `GET_LOCK` and `RELEASE_LOCK` has not been verified against the
specific TiDB version intended for testing. If lock acquisition is unsupported
or fails, the harness aborts before checking or migrating the schema.

Before any integration test, independently verify that:

1. The endpoint and both schemas are disposable and isolated from production.
2. The endpoint certificate is trusted and includes a matching IP subject
   alternative name for the configured numeric IP.
3. The selected TiDB version supports the advisory-lock behavior required by
   the harness.
