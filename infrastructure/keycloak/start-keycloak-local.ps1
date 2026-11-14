# Run Keycloak natively on Windows (no Docker), storing its data in the local MySQL server.
#
# First run: downloads Keycloak into <repo>/.local (git-ignored), creates the MySQL database
# and imports infrastructure/keycloak/eventhub-realm.json. Later runs reuse both; an existing
# realm is never overwritten by the import.
#
# Usage (from the repo root):
#   powershell -ExecutionPolicy Bypass -File infrastructure\keycloak\start-keycloak-local.ps1
#   ... -DbPassword "your-mysql-root-password"
# Or set environment variable KC_LOCAL_DB_PASSWORD instead of -DbPassword.
#
# Requires Java 21+ on PATH and a local MySQL 8 server.

param(
    [string]$KeycloakVersion = "26.8.2",
    [int]$Port = 8180,
    [string]$DbHost = "localhost",
    [int]$DbPort = 3306,
    [string]$DbName = "keycloak",
    [string]$DbUser = "root",
    [string]$DbPassword = $env:KC_LOCAL_DB_PASSWORD,
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin_dev_password"
)

$ErrorActionPreference = "Stop"
if (-not $DbPassword) { $DbPassword = "root" }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$localDir = Join-Path $repoRoot ".local"
$kcHome = Join-Path $localDir "keycloak-$KeycloakVersion"
$realmFile = Join-Path $PSScriptRoot "eventhub-realm.json"

# 1. Download and unpack Keycloak once.
if (-not (Test-Path (Join-Path $kcHome "bin\kc.bat"))) {
    New-Item -ItemType Directory -Force -Path $localDir | Out-Null
    $zip = Join-Path $localDir "keycloak-$KeycloakVersion.zip"
    if (-not (Test-Path $zip)) {
        $url = "https://github.com/keycloak/keycloak/releases/download/$KeycloakVersion/keycloak-$KeycloakVersion.zip"
        Write-Host "Downloading Keycloak $KeycloakVersion (about 170 MB) ..."
        $ProgressPreference = "SilentlyContinue"
        Invoke-WebRequest -Uri $url -OutFile $zip
    }
    Write-Host "Unpacking into $kcHome ..."
    Expand-Archive -Path $zip -DestinationPath $localDir -Force
}

# 2. Create the MySQL database if a mysql client is available.
$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysql) {
    $candidate = Get-ChildItem "C:\Program Files\MySQL" -Recurse -Filter mysql.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $mysql = $candidate.FullName }
}
if ($mysql) {
    $sql = "CREATE DATABASE IF NOT EXISTS ``$DbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    # Password via MYSQL_PWD (not on the command line). Windows PowerShell 5.1 turns any native stderr
    # line into a terminating error under "Stop", so relax it for this call and rely on the exit code.
    $env:MYSQL_PWD = $DbPassword
    $ErrorActionPreference = "Continue"
    & $mysql "-h$DbHost" "-P$DbPort" "-u$DbUser" -e $sql 2>&1 | Out-Null
    $mysqlExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    if ($mysqlExit -ne 0) { throw "Could not create database '$DbName'. Check MySQL is running and the password (-DbPassword)." }
    Write-Host "MySQL database '$DbName' is ready."
} else {
    Write-Warning "mysql client not found. Create the database manually: CREATE DATABASE $DbName CHARACTER SET utf8mb4;"
}

# 3. Stage the realm file for --import-realm.
$importDir = Join-Path $kcHome "data\import"
New-Item -ItemType Directory -Force -Path $importDir | Out-Null
Copy-Item $realmFile (Join-Path $importDir "eventhub-realm.json") -Force

# 4. Start Keycloak in dev mode against MySQL.
$env:KC_BOOTSTRAP_ADMIN_USERNAME = $AdminUser
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = $AdminPassword
$env:KC_DB = "mysql"
$env:KC_DB_URL = "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?characterEncoding=UTF-8"
$env:KC_DB_USERNAME = $DbUser
$env:KC_DB_PASSWORD = $DbPassword

Write-Host ""
Write-Host "Keycloak admin console: http://localhost:$Port/admin  (user: $AdminUser)"
Write-Host "Realm issuer          : http://localhost:$Port/realms/eventhub"
Write-Host ""
& (Join-Path $kcHome "bin\kc.bat") start-dev "--http-port=$Port" --import-realm
