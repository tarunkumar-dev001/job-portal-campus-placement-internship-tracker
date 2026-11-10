FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 app \
    && if ! getent group 1000 >/dev/null; then groupadd --gid 1000 render-secrets; fi \
    && usermod --append --groups 1000 app
COPY --from=build /workspace/target/demo-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod 0755 /usr/local/bin/entrypoint.sh
USER app
ENV DB_SSL_TRUSTSTORE_PATH=/tmp/tidb-cloud-truststore.p12
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
