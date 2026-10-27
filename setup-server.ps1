param([switch]$Offline, [switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$serverDirectory = Join-Path $PSScriptRoot 'server'
$paperName = 'paper-26.2-129.jar'
$paperHash = 'b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083'
$paperUrl = "https://fill-data.papermc.io/v1/objects/$paperHash/$paperName"
$paperPath = Join-Path $serverDirectory $paperName
$pluginPath = Join-Path $PSScriptRoot 'build\libs\ZombieApocalypse-0.2.0.jar'

if (Test-Path -LiteralPath (Join-Path $serverDirectory 'world\session.lock')) {
    $worldLock = $null
    try {
        $worldLock = [System.IO.File]::Open((Join-Path $serverDirectory 'world\session.lock'), 'Open', 'ReadWrite', 'None')
    } catch {
        throw 'Stop the server before running setup-server.ps1.'
    } finally {
        if ($worldLock) { $worldLock.Dispose() }
    }
}

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build.ps1') -Offline:$Offline
}
if (-not (Test-Path -LiteralPath $pluginPath)) {
    throw 'Build the plugin first: powershell -ExecutionPolicy Bypass -File .\build.ps1 -Offline'
}

New-Item -ItemType Directory -Path (Join-Path $serverDirectory 'plugins') -Force | Out-Null
if (-not (Test-Path -LiteralPath $paperPath)) {
    if ($Offline) { throw "Offline setup requires $paperPath" }
    $temporaryPath = "$paperPath.download"
    $previousProgress = $ProgressPreference
    $previousTls = [Net.ServicePointManager]::SecurityProtocol
    try {
        $ProgressPreference = 'SilentlyContinue'
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -Uri $paperUrl -OutFile $temporaryPath -TimeoutSec 180 `
            -UserAgent 'ZombieApocalypse-ServerSetup/0.2.0 (https://github.com/beddeak)'
        if ((Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash -ne $paperHash) {
            throw 'Paper SHA-256 mismatch. Download was not installed.'
        }
        Move-Item -LiteralPath $temporaryPath -Destination $paperPath
    } finally {
        $ProgressPreference = $previousProgress
        [Net.ServicePointManager]::SecurityProtocol = $previousTls
    }
}
if ((Get-FileHash -LiteralPath $paperPath -Algorithm SHA256).Hash -ne $paperHash) {
    throw 'Existing Paper JAR has an unexpected SHA-256. Check it before continuing.'
}

$otherVersions = @(Get-ChildItem -LiteralPath (Join-Path $serverDirectory 'plugins') -Filter 'ZombieApocalypse-*.jar' |
    Where-Object Name -ne 'ZombieApocalypse-0.2.0.jar')
if ($otherVersions.Count -gt 0) {
    throw 'Another ZombieApocalypse version exists in server\plugins. Move it out before installing this version.'
}
Copy-Item -LiteralPath $pluginPath -Destination (Join-Path $serverDirectory 'plugins\ZombieApocalypse-0.2.0.jar') -Force

$propertiesPath = Join-Path $serverDirectory 'server.properties'
if (-not (Test-Path -LiteralPath $propertiesPath)) {
    @'
# ZombieApocalypse development / recording server
server-port=25565
server-ip=
motd=Zombie Apocalypse - mod by beddeak
gamemode=survival
force-gamemode=false
difficulty=hard
hardcore=false
max-players=10
online-mode=true
white-list=false
enforce-whitelist=false
pvp=false
spawn-protection=0
allow-flight=true
view-distance=8
simulation-distance=6
level-name=world
level-type=minecraft:normal
generate-structures=true
enable-command-block=false
enable-rcon=false
enable-query=false
accepts-transfers=false
'@ | Set-Content -LiteralPath $propertiesPath -Encoding ASCII
}

$eulaPath = Join-Path $serverDirectory 'eula.txt'
if (-not (Test-Path -LiteralPath $eulaPath)) {
    "# Read https://aka.ms/MinecraftEULA and change to true only if you agree.`r`neula=false" |
        Set-Content -LiteralPath $eulaPath -Encoding ASCII
}
Write-Output "Server prepared: $serverDirectory"
Write-Output 'Paper 26.2 build 129 / Java 25 / ZombieApocalypse 0.2.0'
Write-Output 'Review server\eula.txt, then run start-server.bat.'
