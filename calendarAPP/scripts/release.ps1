# release.ps1 — Release completo: version + git push
# Uso: .\release.ps1
# Con build: .\release.ps1 -Build

param(
    [int]$VersionCode = 0,
    [string]$VersionName = "",
    [switch]$Build
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$AppDir = Join-Path $ScriptDir ".."
$DbKey = "app_version_calendarfinance"
$SupabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co"
$AnonKey = "sb_publishable_KcdYZchjzzpizgM4nhTw8w_Bd6w6-d1"

# Auto-leer version actual
function Get-CurrentVersion {
    try {
        $resp = Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/app_versions?clave=eq.$DbKey&select=valor" -Headers @{"apikey"=$AnonKey;"Authorization"="Bearer $AnonKey"}
        if ($resp -and $resp.Count -gt 0) { return $resp[0].valor }
    } catch {}
    return @{ versionCode = 0; versionName = "0.0.0" }
}

$current = Get-CurrentVersion
if ($VersionCode -eq 0) { $VersionCode = $current.versionCode + 1 }
if ($VersionName -eq "") {
    $parts = $current.versionName -split '\.'
    if ($parts.Count -ge 3) { $parts[2] = [int]$parts[2] + 1; $VersionName = $parts -join '.' }
    else { $VersionName = "1.0.$VersionCode" }
}

Write-Host ""
Write-Host "RELEASE v$VersionName (code=$VersionCode)" -ForegroundColor Magenta
Write-Host ""

# Build (solo con -Build)
if ($Build) {
    Write-Host "BUILD..." -ForegroundColor Cyan
    Push-Location $AppDir
    try {
        & ./gradlew clean 2>&1 | Out-Null
        & ./gradlew assembleRelease
        if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FALLO" -ForegroundColor Red; exit 1 }
        Write-Host "BUILD OK" -ForegroundColor Green
    } finally { Pop-Location }
}

# Actualizar Supabase
Write-Host "SUPABASE..." -ForegroundColor Cyan

$apkUrl = "https://calendar-04yk.onrender.com/calendarfinance.apk"
$valor = @{ versionCode = $VersionCode; versionName = $VersionName; apkUrl = $apkUrl } | ConvertTo-Json -Compress
$body = @{ valor = $valor } | ConvertTo-Json
$headers = @{ "apikey"=$AnonKey; "Authorization"="Bearer $AnonKey"; "Content-Type"="application/json"; "Prefer"="return=minimal" }

try {
    Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/app_versions?clave=eq.$DbKey" -Method PATCH -Headers $headers -Body $body | Out-Null
    Write-Host "OK: v$VersionName" -ForegroundColor Green
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
}

# Git push
Write-Host "GIT PUSH..." -ForegroundColor Cyan
Push-Location $RootDir
try {
    git add -A 2>&1 | Out-Null
    $commitMsg = "release: v$VersionName (code=$VersionCode)"
    git commit -m $commitMsg 2>&1 | Out-Null
    git push 2>&1 | Out-Null
    Write-Host "OK" -ForegroundColor Green
} catch {
    Write-Host "SIN CAMBIOS O ERROR" -ForegroundColor Yellow
} finally { Pop-Location }

Write-Host ""
Write-Host "LISTO v$VersionName" -ForegroundColor Green
