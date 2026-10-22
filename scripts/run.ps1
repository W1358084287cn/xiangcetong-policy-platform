# XCT 2.0: backend 8080 + H5 5173 + unified admin 5177
# 用法：在项目根执行 powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run.ps1
# 统一管理端登录时选择身份（村管/镇管/系统管理），替代原来的三端独立启动

$ErrorActionPreference = 'Continue'

function Get-RepoRoot {
    $here = $PSScriptRoot
    if (-not $here) { $here = Split-Path -Parent $MyInvocation.MyCommand.Path }
    return (Resolve-Path (Join-Path $here '..')).Path
}

function Test-PortListen([int]$Port) {
    try {
        $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -First 1
        return $null -ne $c
    } catch {
        $line = netstat -ano 2>$null | Select-String -Pattern (":$Port\s+.*LISTENING")
        return $null -ne $line
    }
}

function Wait-Port([int]$Port, [int]$TimeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListen $Port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Wait-Http([string]$Url, [int]$TimeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
        } catch { }
        Start-Sleep -Seconds 1
    }
    return $false
}

$Root = Get-RepoRoot
$ServerDir = Join-Path $Root 'server'
$AdminDir = Join-Path $Root 'web-admin'
$LogDir = Join-Path $Root 'scripts\logs'
$Jar = Join-Path $ServerDir 'xct-boot\target\xiangcetong-server-1.0.0.jar'

$JavaHome = 'D:\JAVA17'
$Mvn = 'D:\maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd'
$MvnSettings = 'C:\Users\W\.m2\settings.xml'
$MvnRepo = 'D:\maven\repository'

if (-not (Test-Path -LiteralPath $ServerDir) -or
    -not (Test-Path -LiteralPath $AdminDir) -or
    -not (Test-Path -LiteralPath (Join-Path $Root 'web-h5'))) {
    Write-Host "[ERR] not found server / web-admin / web-h5"
    exit 1
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$env:JAVA_HOME = $JavaHome

Write-Host "==== XCT 2.0 ===="
Write-Host "Root: $Root"

# ---------- backend 8080 ----------
if (Test-PortListen 8080) {
    Write-Host "[OK] 8080 already listening, reuse"
} else {
    Write-Host "[..] starting backend..."
    if (-not (Test-Path -LiteralPath $Jar)) {
        if (-not (Test-Path -LiteralPath $Mvn)) {
            Write-Host "[ERR] maven not found: $Mvn"
            exit 1
        }
        Write-Host "[..] mvn package -DskipTests ..."
        $pkgOut = Join-Path $LogDir 'mvn-package.out.log'
        $pkgErr = Join-Path $LogDir 'mvn-package.err.log'
        $p = Start-Process -FilePath $Mvn -ArgumentList @(
            '-s', $MvnSettings,
            "-Dmaven.repo.local=$MvnRepo",
            '-q', 'package', '-Dmaven.test.skip'
        ) -WorkingDirectory $ServerDir -PassThru -Wait -NoNewWindow `
            -RedirectStandardOutput $pkgOut -RedirectStandardError $pkgErr
        if ($p.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $Jar)) {
            Write-Host "[ERR] build failed, see $pkgOut / $pkgErr"
            exit 1
        }
    }
    $javaExe = Join-Path $JavaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe)) {
        Write-Host "[ERR] java not found: $javaExe"
        exit 1
    }
    $backendOut = Join-Path $LogDir 'backend.out.log'
    $backendErr = Join-Path $LogDir 'backend.err.log'
    Start-Process -FilePath $javaExe -ArgumentList @('-jar', $Jar, '--server.port=8080') `
        -WorkingDirectory $ServerDir -WindowStyle Hidden `
        -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr | Out-Null
    if (-not (Wait-Port 8080 180)) {
        Write-Host "[ERR] backend 8080 not ready, see $backendOut / $backendErr"
        exit 1
    }
    Write-Host "[OK] backend started 8080"
}

# health check
[void](Wait-Http 'http://localhost:8080/api/policy/list?pageNum=1&pageSize=1' 30)

# ---------- frontends ----------
function Start-NpmDev([string]$Dir, [int]$Port, [string]$Name, [string]$Script = 'dev') {
    if (Test-PortListen $Port) {
        Write-Host "[OK] $Port already listening, reuse $Name"
        return
    }
    $nm = Join-Path $Dir 'node_modules'
    if (-not (Test-Path -LiteralPath $nm)) {
        Write-Host "[..] $Name npm install ..."
        $installOut = Join-Path $LogDir "$Name-npm-install.out.log"
        $installErr = Join-Path $LogDir "$Name-npm-install.err.log"
        $ip = Start-Process -FilePath 'npm.cmd' -ArgumentList @('install') `
            -WorkingDirectory $Dir -PassThru -Wait -NoNewWindow `
            -RedirectStandardOutput $installOut -RedirectStandardError $installErr
        if ($ip.ExitCode -ne 0) {
            Write-Host "[ERR] $Name npm install failed, see $installOut / $installErr"
            exit 1
        }
    }
    Write-Host "[..] starting $Name (port $Port) ..."
    $devOut = Join-Path $LogDir "$Name-dev.out.log"
    $devErr = Join-Path $LogDir "$Name-dev.err.log"
    Start-Process -FilePath 'npm.cmd' -ArgumentList @('run', $Script) `
        -WorkingDirectory $Dir -WindowStyle Hidden `
        -RedirectStandardOutput $devOut -RedirectStandardError $devErr | Out-Null
    if (-not (Wait-Port $Port 120)) {
        Write-Host "[ERR] $Name port $Port not ready, see $devOut / $devErr"
        exit 1
    }
    Write-Host "[OK] $Name started $Port"
}

Start-NpmDev -Dir (Join-Path $Root 'web-h5') -Port 5173 -Name 'web-h5'
Start-NpmDev -Dir $AdminDir -Port 5177 -Name 'web-admin-unified' -Script 'dev:unified'

# ---------- open browser ----------
Start-Process 'http://localhost:5173/'
Start-Process 'http://localhost:5177/login'

Write-Host ""
Write-Host "==== done ===="
Write-Host "backend API : http://localhost:8080"
Write-Host "H5(villager): http://localhost:5173"
Write-Host "admin(unified): http://localhost:5177/login"
Write-Host "admin account: admin / 123456"
Write-Host "logs        : $LogDir"
exit 0