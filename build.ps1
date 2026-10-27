param([switch]$Offline)

$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
$previousDebug = $env:DEBUG
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
Push-Location $PSScriptRoot
try {
    $localJdk = Join-Path $env:ProgramFiles 'Java\jdk-25.0.2'
    if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $localJdk 'bin\java.exe'))) {
        $env:JAVA_HOME = $localJdk
    }
    $env:DEBUG = ''
    $gradleSocketDirectory = Join-Path $PSScriptRoot '.gradle'
    New-Item -ItemType Directory -Path $gradleSocketDirectory -Force | Out-Null
    $socketOption = '-Djdk.net.unixdomain.tmpdir="' + $gradleSocketDirectory.Replace('\', '/') + '"'
    $env:JAVA_TOOL_OPTIONS = ($previousJavaOptions + ' ' + $socketOption).Trim()
    $gradleArguments = @('clean', 'build', '--console=plain', '--no-daemon')
    if ($Offline) { $gradleArguments += '--offline' }
    & .\gradlew.bat @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "ZombieApocalypse build failed: exit $LASTEXITCODE" }
    $builtJars = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'build\libs') -Filter 'ZombieApocalypse-*.jar')
    foreach ($builtJar in $builtJars) { Write-Output ('JAR: ' + $builtJar.FullName) }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:DEBUG = $previousDebug
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}
