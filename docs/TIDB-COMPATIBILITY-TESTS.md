# TiDB compatibility test harness

The TiDB compatibility tests are standalone JUnit tests. They do not start the
Spring application, load a Spring profile, read the application's datasource,
or fall back to `DB_*` settings. They are skipped when
`TIDB_COMPAT_TESTS` is not `true`. If opted in without
`TIDB_COMPAT_CONFIRM_DISPOSABLE=true` or another required setting, setup fails
closed before any connection is opened.

## Test-only configuration

Set these variables in the test process or an approved secret manager. Do not
put credential values in source files or shell commands that are saved in
history.

| Variable | Required value |
|---|---|
| `TIDB_COMPAT_FRESH_URL` | `jdbc:mysql://gateway01.<region>.tidbcloud.example:4000/careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY` |
| `TIDB_COMPAT_UPGRADE_URL` | `jdbc:mysql://gateway01.<region>.tidbcloud.example:4000/careerconnect_tidb_compat_upgrade?sslMode=VERIFY_IDENTITY` |
| `TIDB_COMPAT_USERNAME` | The complete instance-prefixed username created for `careerconnect-tidb-test`; do not strip or normalize its prefix |
| `TIDB_COMPAT_PASSWORD` | Password for that test-instance account only |
| `TIDB_COMPAT_TRUSTSTORE_PATH` | Path to the dedicated PKCS12 truststore described below |
| `TIDB_COMPAT_TRUSTSTORE_PASSWORD` | Password used when creating that truststore |
| `TIDB_COMPAT_TESTS` | `true` to opt in |
| `TIDB_COMPAT_CONFIRM_DISPOSABLE` | `true` to confirm the schemas are disposable |

The URLs must match the exact shared gateway hostname, port 4000, fixed schema,
and single `sslMode=VERIFY_IDENTITY` setting. Embedded credentials, alternate
hosts or ports, other schemas, duplicate options, conflicting TLS settings,
and weaker TLS modes are rejected. Connector/J 9.4.0 receives the test user,
password, and truststore settings through its `MysqlDataSource` properties, not
through URL credentials.

The configured PKCS12 truststore must be readable, load with the supplied
truststore password, contain exactly one certificate entry, and contain the
verified ISRG Root X1 certificate with SHA-256 fingerprint:

`96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6`

The standalone instructions use a downloaded PEM in the current user's
Downloads directory. Set its location once and verify its fingerprint before
importing it. Create a test-only truststore with `keytool`; omit `-storepass`
so keytool prompts interactively rather than storing the password in shell
history:

```powershell
$caPem = Join-Path (Join-Path $env:USERPROFILE 'Downloads') 'isrgrootx1 (1).pem'
keytool -printcert -file "$caPem"
keytool -importcert -alias isrg-root-x1 `
  -file "$caPem" `
  -keystore "$env:USERPROFILE\tidb-test-truststore.p12" `
  -storetype PKCS12
```

At the trust prompt, accept only after confirming the displayed SHA-256
fingerprint matches the value above. Set `TIDB_COMPAT_TRUSTSTORE_PATH` to the
resulting file. The truststore password is required through
`TIDB_COMPAT_TRUSTSTORE_PASSWORD`; do not place it on the command line.

The harness pins Connector/J to the truststore using
`trustCertificateKeyStoreUrl`, `trustCertificateKeyStoreType=PKCS12`,
`trustCertificateKeyStorePassword`, and
`fallbackToSystemTrustStore=false`, while retaining
`sslMode=VERIFY_IDENTITY`. The same configured `MysqlDataSource` instance for
each schema is used by direct JDBC checks and Flyway. No global JVM truststore
is modified.

## Shared gateway and instance identity

The production and test instances share the public gateway hostname (and may
share gateway IP addresses). Therefore hostname, IP address, and successful
TLS hostname verification do **not** prove which TiDB instance accepted a
connection. The harness requires the complete test-instance username and
dedicated test password and never retries with production credentials or
reads the application's datasource as a fallback. Instance separation depends
on TiDB Cloud binding those credentials to the disposable test instance.

Before any live run, independently verify in TiDB Cloud that the account is
for `careerconnect-tidb-test`, is not valid for production, and its effective
grants are limited to the two test schemas. Confirm both the credential's
instance scope and its grants directly with the provider; a username,
version/schema check, TLS hostname verification, or
`TIDB_COMPAT_CONFIRM_DISPOSABLE=true` cannot prove those conditions. The
confirmation flag is an operator attestation, not a safety check. Do not run
the harness until this verification is complete.

Both schemas must already exist, be empty, and be disposable. The initial
database shown by TiDB (`sys`) is not a harness schema and must not be used.
The harness verifies the selected schema and requires `SELECT VERSION()` to
identify TiDB v8.5.3 before migration; the version check is not an instance
identity check.

## Migration and lock behavior

The harness acquires the schema-specific advisory lock before checking
emptiness and holds it through migration and assertions. It explicitly
requests a one-second `GET_LOCK` timeout. TiDB enforces a one-second minimum;
zero is not a zero-wait request. Missing, null, unsuccessful, or exceptional
lock acquisition fails before schema inspection or migration. The lock
serializes cooperating harness runs, not other database users.

The harness never creates schemas, runs Flyway `clean`, or drops objects. A
failed or interrupted run may leave partial test objects; inspect and dispose
of the test schema manually before retrying.

The fresh path applies V1-V6 and checks migration history, the nullable
`VARCHAR(36)` session column, and its index. The upgrade path applies V1-V5,
inserts synthetic refresh-token rows, applies V6, then verifies revocation
behavior, preserved revocation timestamps, and schema objects.

Run the external tests only after independently confirming the test account,
instance scope, effective grants, schemas, and truststore:

```powershell
.\mvnw.cmd "-Dtest=TidbCompatibilityIntegrationTests" test
```

Flyway logging is suppressed and database exceptions are reported without
their detailed messages. Offline safety tests do not connect to a database:

```powershell
.\mvnw.cmd "-Dtest=TidbCompatibilitySafetyTests" test
```

Offline checks validate configuration and the local truststore only. They
cannot prove provider-side account scoping or effective grants, identify the
remote cluster behind the shared gateway, or verify the remote certificate
chain and hostname in a TLS handshake. The advisory lock also cannot prevent
non-cooperating users from modifying the schemas. Passing H2 or an unverified
TiDB compatibility run does not establish production V6 readiness.
