$ErrorActionPreference = 'Stop'
$workspace = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $workspace

$inheritedOverrides = [Environment]::GetEnvironmentVariables().Keys | Where-Object {
    "$_" -match '^(SPRING_(DATASOURCE|FLYWAY|JPA|LIQUIBASE)(_|$)|SPRING_APPLICATION_JSON$|SPRING_CONFIG(_|$)|SPRING_PROFILES_ACTIVE$|DB_(URL|USERNAME|PASSWORD)$)'
}
if ($inheritedOverrides) {
    throw 'Full-stack E2E refuses inherited Spring datasource, Flyway, profile, or database overrides.'
}

foreach ($javaOptionsName in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')) {
    $javaOptions = [Environment]::GetEnvironmentVariable($javaOptionsName)
    if ($javaOptions -match '(?i)(spring[._-]?(datasource|flyway|jpa|liquibase|config|profiles)|db[._-]?(url|username|password))') {
        throw 'Full-stack E2E refuses Java options that can override Spring datasource or profile settings.'
    }
}

$env:SPRING_DATASOURCE_URL = 'jdbc:h2:mem:careerconnect-fullstack-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'sa'
$env:SPRING_DATASOURCE_PASSWORD = ''
$env:SPRING_FLYWAY_ENABLED = 'true'
$env:SPRING_FLYWAY_URL = 'jdbc:h2:mem:careerconnect-fullstack-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1'
$env:SPRING_FLYWAY_USER = 'sa'
$env:SPRING_FLYWAY_PASSWORD = ''
$env:SPRING_FLYWAY_DRIVER_CLASS_NAME = 'org.h2.Driver'

& .\mvnw.cmd test-compile dependency:build-classpath '-Dmdep.outputFile=target/e2e-classpath.txt' '-Dmdep.includeScope=test'
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$dependencies = (Get-Content 'target/e2e-classpath.txt' -Raw).Trim()
$classpath = "target\test-classes;target\classes;$dependencies"
& java -cp $classpath com.example.demo.DemoApplication --spring.profiles.active=e2e --server.address=127.0.0.1 --server.port=8080
exit $LASTEXITCODE
