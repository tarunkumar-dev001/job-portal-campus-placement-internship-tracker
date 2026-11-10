#!/bin/sh
set -eu

encoded_file=/etc/secrets/tidb-cloud-truststore.p12.b64
truststore=/tmp/tidb-cloud-truststore.p12

if [ "${DB_SSL_TRUSTSTORE_PATH:-}" != "$truststore" ]; then
    echo "DB_SSL_TRUSTSTORE_PATH must be /tmp/tidb-cloud-truststore.p12" >&2
    exit 1
fi

: "${DB_SSL_TRUSTSTORE_PASSWORD:?DB_SSL_TRUSTSTORE_PASSWORD must be set}"

if [ ! -r "$encoded_file" ]; then
    echo "Render truststore secret file is missing or unreadable" >&2
    exit 1
fi

umask 077
temporary_file="$(mktemp /tmp/tidb-cloud-truststore.p12.XXXXXX)"
trap 'rm -f "$temporary_file"' 0 HUP INT TERM

if ! base64 --decode "$encoded_file" > "$temporary_file"; then
    echo "Could not decode the truststore secret file" >&2
    exit 1
fi

if [ ! -s "$temporary_file" ]; then
    echo "Decoded truststore is empty" >&2
    exit 1
fi

chmod 0600 "$temporary_file"
mv -f "$temporary_file" "$truststore"
trap - 0 HUP INT TERM

exec java -jar /app/app.jar
