$ErrorActionPreference = 'Stop'
$workspace = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $workspace

& .\mvnw.cmd test-compile dependency:build-classpath '-Dmdep.outputFile=target/e2e-classpath.txt' '-Dmdep.includeScope=test'
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$dependencies = (Get-Content 'target/e2e-classpath.txt' -Raw).Trim()
$classpath = "target\test-classes;target\classes;$dependencies"
& java -cp $classpath com.example.demo.DemoApplication --spring.profiles.active=e2e --server.port=8080
exit $LASTEXITCODE
