<#
  Starts every HighVibe service on this machine (Windows PowerShell).

  Prerequisites: Docker Desktop running the infrastructure (docker compose up -d),
  JDK 21 (or the portable one in .tools\jdk-21) and Node.js 20+.

  Usage:   .\scripts\start-services.ps1            # build if needed, start all
           .\scripts\start-services.ps1 -Rebuild   # force a Maven rebuild first
  Logs:    runtime\logs\<service>.log
  Stop:    .\scripts\stop-services.ps1
#>
param([switch]$Rebuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Optional local settings (GEMINI_API_KEY, STRIPE_SECRET_KEY, ...) from .env
if (Test-Path "$root\.env") {
    Get-Content "$root\.env" | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim().Trim('"'), 'Process')
    }
}

if (Test-Path "$root\.tools\jdk-21\bin\java.exe") {
    $env:JAVA_HOME = "$root\.tools\jdk-21"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if (Test-Path 'C:\Program Files\nodejs\node.exe') {
    $env:Path = "C:\Program Files\nodejs;$env:Path"
}

$services = @(
    @{ Name = 'discovery-service';    Port = 8761 },
    @{ Name = 'config-service';       Port = 8888 },
    @{ Name = 'account-service';      Port = 9010 },
    @{ Name = 'workspace-service';    Port = 9020 },
    @{ Name = 'intelligence-service'; Port = 9030 },
    @{ Name = 'api-gateway';          Port = 8080 }
)

$backend = "$root\backend"
$missing = $services | Where-Object { -not (Test-Path "$backend\$($_.Name)\target\$($_.Name)-1.0.0.jar") }
if ($Rebuild -or $missing) {
    Write-Host 'Building services with Maven...' -ForegroundColor Cyan
    Push-Location $backend
    & "$backend\mvnw.cmd" -B -q install -DskipTests
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { throw 'Maven build failed' }
}

New-Item -ItemType Directory -Force "$root\runtime\logs" | Out-Null
$pidFile = "$root\runtime\services.pids"
Set-Content -Path $pidFile -Value ''

function Wait-Port([int]$port, [string]$name, [int]$timeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        # this constructor tries every address of "localhost" (IPv4 and IPv6):
        # Vite on Windows may listen on the IPv6 loopback only
        try {
            $client = New-Object System.Net.Sockets.TcpClient('localhost', $port)
            $client.Close()
            return
        } catch { Start-Sleep -Milliseconds 700 }
    }
    throw "$name did not open port $port in $timeoutSec s (see runtime\logs\$name.log)"
}

foreach ($svc in $services) {
    $name = $svc.Name
    $jar = "$backend\$name\target\$name-1.0.0.jar"
    $log = "$root\runtime\logs\$name.log"
    Write-Host "Starting $name on port $($svc.Port)..." -ForegroundColor Cyan
    $proc = Start-Process -FilePath 'java' -ArgumentList '-jar', "`"$jar`"" -WorkingDirectory $root `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden -PassThru
    Add-Content -Path $pidFile -Value $proc.Id
    # one service at a time: starting every JVM at once overloads a laptop and makes
    # database connections time out; it also guarantees Eureka/config are up first
    Wait-Port $svc.Port $name 240
}

Write-Host 'Starting the frontend (Vite)...' -ForegroundColor Cyan
if (-not (Test-Path "$root\frontend\node_modules")) {
    Push-Location "$root\frontend"; npm install --no-audit --no-fund; Pop-Location
}
$front = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'npm run dev' -WorkingDirectory "$root\frontend" `
    -RedirectStandardOutput "$root\runtime\logs\frontend.log" -RedirectStandardError "$root\runtime\logs\frontend.log.err" `
    -WindowStyle Hidden -PassThru
Add-Content -Path $pidFile -Value $front.Id
Wait-Port 5173 'frontend' 60

Write-Host ''
Write-Host 'HighVibe is running:' -ForegroundColor Green
Write-Host '  App ............ http://localhost:5173'
Write-Host '  API gateway .... http://localhost:8080'
Write-Host '  Eureka ......... http://localhost:8761'
if (-not $env:GEMINI_API_KEY) {
    Write-Host '  AI ............. demo generator (put a free GEMINI_API_KEY in .env)' -ForegroundColor Yellow
}
