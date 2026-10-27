param(
    [ValidatePattern('^[1-9][0-9]*[MG]$')][string]$MinMemory = '1G',
    [ValidatePattern('^[1-9][0-9]*[MG]$')][string]$MaxMemory = '4G'
)

$ErrorActionPreference = 'Stop'
$serverDirectory = Join-Path $PSScriptRoot 'server'
$paperPath = Join-Path $serverDirectory 'paper-26.2-129.jar'
$propertiesPath = Join-Path $serverDirectory 'server.properties'
$serverPort = 25565
if (Test-Path -LiteralPath $propertiesPath) {
    foreach ($propertyLine in Get-Content -LiteralPath $propertiesPath) {
        if ($propertyLine -cmatch '^\s*server-port\s*=\s*(.*?)\s*$') {
            if (-not [int]::TryParse($Matches[1], [ref]$serverPort) -or $serverPort -lt 1 -or $serverPort -gt 65535) {
                throw 'Invalid server-port in server\server.properties. Use a port from 1 to 65535.'
            }
        }
    }
}
if (-not (Test-Path -LiteralPath $paperPath)) {
    throw 'Run setup-server.ps1 first.'
}
$eulaPath = Join-Path $serverDirectory 'eula.txt'
if (-not (Test-Path -LiteralPath $eulaPath) -or
    -not (Select-String -LiteralPath $eulaPath -Pattern '^\s*eula\s*=\s*true\s*$' -Quiet)) {
    throw 'Read https://aka.ms/MinecraftEULA. If you agree, set eula=true in server\eula.txt, then start again.'
}

$javaCandidates = @((Join-Path $env:ProgramFiles 'Java\jdk-25.0.2\bin\java.exe'))
if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
$pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
if ($pathJava) { $javaCandidates += $pathJava.Source }
$javaPath = $javaCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $javaPath) { throw 'Java 25 is required. Install it and set JAVA_HOME.' }

# Java writes -version to stderr; use a process to avoid PowerShell 5 native stderr errors.
$versionInfo = New-Object System.Diagnostics.ProcessStartInfo
$versionInfo.FileName = $javaPath
$versionInfo.Arguments = '-version'
$versionInfo.UseShellExecute = $false
$versionInfo.CreateNoWindow = $true
$versionInfo.RedirectStandardError = $true
$versionProcess = [System.Diagnostics.Process]::Start($versionInfo)
$versionText = $versionProcess.StandardError.ReadToEnd()
$versionProcess.WaitForExit()
$versionProcess.Dispose()
if ($versionText -notmatch 'version "25(?:\.|\")') {
    throw "Java 25 is required. Selected Java: $javaPath"
}

Push-Location $serverDirectory
try {
    Write-Host "ZombieApocalypse | Paper 26.2 build 129 | localhost:$serverPort"
    Write-Host 'Stop safely with the console command: stop'
    & $javaPath "-Xms$MinMemory" "-Xmx$MaxMemory" '-Dfile.encoding=UTF-8' '-jar' $paperPath '--nogui'
    if ($LASTEXITCODE -ne 0) { throw "Server exited with code $LASTEXITCODE. See server\logs\latest.log." }
} finally {
    Pop-Location
}
