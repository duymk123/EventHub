<#
  Đổi SMTP của realm eventhub trên Keycloak local theo các biến MAIL_* trong file .env ở gốc repo.
  Keycloak dùng SMTP này cho mail xác minh email và quên mật khẩu (mail vé/đơn hàng do notification-service gửi).

  Chạy từ gốc repo, khi Keycloak đang chạy ở http://localhost:8180:
    powershell -ExecutionPolicy Bypass -File infrastructure/keycloak/set-smtp.ps1          # áp dụng theo .env
    powershell -ExecutionPolicy Bypass -File infrastructure/keycloak/set-smtp.ps1 -Show    # xem cấu hình hiện tại
    powershell -ExecutionPolicy Bypass -File infrastructure/keycloak/set-smtp.ps1 -Mailpit # quay lại Mailpit

  Cấu hình lưu trong DB của Keycloak: chạy một lần là đủ, chỉ cần chạy lại khi đổi .env hoặc xóa volume Keycloak.
  Tài khoản admin mặc định khớp docker-compose (admin/admin_dev_password); ghi đè bằng KEYCLOAK_ADMIN/KEYCLOAK_ADMIN_PASSWORD.
#>
param(
    [switch]$Show,
    [switch]$Mailpit,
    [string]$KeycloakUrl = "http://localhost:8180",
    [string]$Realm = "eventhub",
    [string]$EnvFile = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) ".env")
)
$ErrorActionPreference = "Stop"

function Read-DotEnv([string]$path) {
    $values = @{}
    if (-not (Test-Path $path)) { return $values }
    foreach ($line in Get-Content -Path $path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) { continue }
        $key, $value = $trimmed.Split("=", 2)
        $values[$key.Trim()] = $value.Trim().Trim('"')
    }
    return $values
}

$adminUser = if ($env:KEYCLOAK_ADMIN) { $env:KEYCLOAK_ADMIN } else { "admin" }
$adminPassword = if ($env:KEYCLOAK_ADMIN_PASSWORD) { $env:KEYCLOAK_ADMIN_PASSWORD } else { "admin_dev_password" }

try {
    $token = (Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{ grant_type = "password"; client_id = "admin-cli"; username = $adminUser; password = $adminPassword }).access_token
} catch {
    Write-Error "Không lấy được token admin Keycloak tại $KeycloakUrl (Keycloak chưa chạy hoặc sai tài khoản admin): $($_.Exception.Message)"
}
$headers = @{ Authorization = "Bearer $token" }
$realmUrl = "$KeycloakUrl/admin/realms/$Realm"

if ($Show) {
    $smtp = (Invoke-RestMethod -Uri $realmUrl -Headers $headers).smtpServer
    Write-Host ("Realm {0}: host={1} port={2} from={3} auth={4} starttls={5} user={6}" -f $Realm, $smtp.host, $smtp.port, $smtp.from, $smtp.auth, $smtp.starttls, $smtp.user)
    exit 0
}

$dotenv = Read-DotEnv $EnvFile
$useMailpit = $Mailpit -or -not $dotenv["MAIL_HOST"] -or $dotenv["MAIL_HOST"] -eq "mailpit" -or $dotenv["MAIL_HOST"] -eq "localhost"

if ($useMailpit) {
    $smtp = @{ host = "mailpit"; port = "1025"; from = "noreply@eventhub.local"; fromDisplayName = "EventHub";
               auth = "false"; ssl = "false"; starttls = "false" }
} else {
    foreach ($key in "MAIL_USERNAME", "MAIL_PASSWORD") {
        if (-not $dotenv[$key]) { Write-Error "Thiếu $key trong $EnvFile. Điền App Password Gmail vào MAIL_PASSWORD rồi chạy lại." }
    }
    $port = if ($dotenv["MAIL_PORT"]) { $dotenv["MAIL_PORT"] } else { "587" }
    $from = if ($dotenv["MAIL_FROM"]) { $dotenv["MAIL_FROM"] } else { $dotenv["MAIL_USERNAME"] }
    $smtp = @{
        host = $dotenv["MAIL_HOST"]; port = $port; from = $from; fromDisplayName = "EventHub"
        auth = "true"; user = $dotenv["MAIL_USERNAME"]; password = $dotenv["MAIL_PASSWORD"]
        starttls = $(if ($port -eq "465") { "false" } else { "true" })
        ssl = $(if ($port -eq "465") { "true" } else { "false" })
    }
}

# PUT realm chỉ với smtpServer: Keycloak giữ nguyên các thuộc tính khác của realm.
$body = @{ smtpServer = $smtp } | ConvertTo-Json -Depth 3
Invoke-RestMethod -Method Put -Uri $realmUrl -Headers $headers -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
Write-Host ("Đã đặt SMTP của realm {0}: {1}:{2}, gửi từ {3}" -f $Realm, $smtp.host, $smtp.port, $smtp.from)
