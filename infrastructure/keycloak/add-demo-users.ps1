<#
  Thêm/cập nhật ba tài khoản demo trong realm eventhub mà không xóa dữ liệu khác.

  Chạy từ gốc repo sau khi Keycloak đã chạy:
    powershell -ExecutionPolicy Bypass -File infrastructure/keycloak/add-demo-users.ps1

  Mặc định dùng tài khoản admin local trong docker-compose. Có thể ghi đè bằng
  KEYCLOAK_ADMIN và KEYCLOAK_ADMIN_PASSWORD.
  Chỉ dùng các mật khẩu này trong môi trường local/dev.
#>
param(
    [string]$KeycloakUrl = "http://localhost:8180",
    [string]$Realm = "eventhub"
)
$ErrorActionPreference = "Stop"

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
$businessRoles = @("ADMIN", "ORGANIZER", "CUSTOMER")
$users = @(
    @{ Username = "admin"; Email = "admin-demo@eventhub.local"; FirstName = "Admin"; Role = "ADMIN" },
    @{ Username = "org"; Email = "org-demo@eventhub.local"; FirstName = "Organizer"; Role = "ORGANIZER" },
    @{ Username = "customer"; Email = "customer-demo@eventhub.local"; FirstName = "Customer"; Role = "CUSTOMER" }
)

foreach ($user in $users) {
    $usernameQuery = [uri]::EscapeDataString($user.Username)
    $emailQuery = [uri]::EscapeDataString($user.Email)
    $foundUsers = @(Invoke-RestMethod -Uri "$realmUrl/users?username=$usernameQuery&exact=true" -Headers $headers | Where-Object { $_.id })
    if ($foundUsers.Count -eq 0) {
        $foundUsers = @(Invoke-RestMethod -Uri "$realmUrl/users?email=$emailQuery&exact=true" -Headers $headers | Where-Object { $_.id })
    }
    if ($foundUsers.Count -gt 0 -and $foundUsers[0].username -ne $user.Username) {
        if ($foundUsers[0].email -ne $user.Email) {
            throw "Refusing to replace unexpected Keycloak user '$($foundUsers[0].username)'."
        }
        # Email-as-username may have normalized a previous run. Replace only this demo account to set the requested username.
        Invoke-RestMethod -Method Delete -Uri "$realmUrl/users/$($foundUsers[0].id)" -Headers $headers | Out-Null
        $foundUsers = @()
    }
    $profile = @{
        username = $user.Username
        email = $user.Email
        firstName = $user.FirstName
        lastName = "Demo"
        enabled = $true
        emailVerified = $true
    }

    if ($foundUsers.Count -gt 0) {
        $userId = $foundUsers[0].id
        $updateProfile = $profile.Clone()
        $updateProfile.Remove("username")
        $body = $updateProfile | ConvertTo-Json -Depth 4
        Invoke-RestMethod -Method Put -Uri "$realmUrl/users/$userId" -Headers $headers `
            -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
    } else {
        $profile.credentials = @(@{ type = "password"; value = "1"; temporary = $false })
        $body = $profile | ConvertTo-Json -Depth 5
        Invoke-RestMethod -Method Post -Uri "$realmUrl/users" -Headers $headers `
            -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
        $foundUsers = @(Invoke-RestMethod -Uri "$realmUrl/users?username=$usernameQuery&exact=true" -Headers $headers | Where-Object { $_.id })
        if ($foundUsers.Count -eq 0) {
            throw "Keycloak did not preserve the requested username '$($user.Username)' on creation."
        }
        $userId = $foundUsers[0].id
    }
    $credential = @{ type = "password"; value = "1"; temporary = $false } | ConvertTo-Json -Depth 3
    Invoke-RestMethod -Method Put -Uri "$realmUrl/users/$userId/reset-password" -Headers $headers `
        -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($credential)) | Out-Null

    $mappingsUrl = "$realmUrl/users/$userId/role-mappings/realm"
    $mappings = Invoke-RestMethod -Uri $mappingsUrl -Headers $headers
    $obsolete = @($mappings | Where-Object { $businessRoles -contains $_.name -and $_.name -ne $user.Role })
    if ($obsolete.Count -gt 0) {
        $body = ConvertTo-Json -InputObject ([object[]]$obsolete) -Depth 4
        Invoke-RestMethod -Method Delete -Uri $mappingsUrl -Headers $headers `
            -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
    }

    if (-not ($mappings | Where-Object name -eq $user.Role)) {
        $role = Invoke-RestMethod -Uri "$realmUrl/roles/$($user.Role)" -Headers $headers
        $body = ConvertTo-Json -InputObject ([object[]]@(@{ id = $role.id; name = $role.name })) -Depth 3
        Invoke-RestMethod -Method Post -Uri $mappingsUrl -Headers $headers `
            -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
    }

    Write-Host ("Đã sẵn sàng user {0} ({1}), role {2}, password 1" -f $user.Username, $user.Email, $user.Role)
}
