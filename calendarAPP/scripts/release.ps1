# release.ps1 — Release simplificado: build + actualizar Supabase
# Uso simple: .\release.ps1
# Uso con args: .\release.ps1 -VersionCode 2 -VersionName "1.0.1"

param(
    [int]$VersionCode = 0,
    [string]$VersionName = "",
    [switch]$SkipBuild,
    [string]$SupabaseKey = ""
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppDir = Join-Path $ScriptDir ".."
$DbKey = "app_version_calendarfinance"
$SupabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co"

# ═══ AUTO-LEER VERSION ACTUAL DE SUPABASE ═══
function Get-CurrentVersion {
    try {
        $resp = Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/app_versions?clave=eq.$DbKey&select=valor" -Headers @{"apikey"="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVndGx4bnJ3Zmlwb2N0Y2t1dmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzU4Mzk4MDQsImV4cCI6MjA1MTQxNTgwNH0.A5W4rRxYDxyPqFh7a4FX_ejniQl1nBNf1hMQuf7vjm4"} -Method GET
        if ($resp -and $resp.Count -gt 0) {
            return $resp[0].valor
        }
    } catch {}
    return @{ versionCode = 0; versionName = "0.0.0" }
}

# ═══ CONFIGURAR VERSION ═══
$current = Get-CurrentVersion

if ($VersionCode -eq 0) {
    $VersionCode = $current.versionCode + 1
}
if ($VersionName -eq "") {
    $parts = $current.versionName -split '\.'
    if ($parts.Count -ge 3) {
        $parts[2] = [int]$parts[2] + 1
        $VersionName = $parts -join '.'
    } else {
        $VersionName = "1.0.$VersionCode"
    }
}

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Magenta
Write-Host "  CALENDARFINANCE RELEASE" -ForegroundColor Magenta
Write-Host "  v$VersionName (code=$VersionCode)" -ForegroundColor Magenta
Write-Host "═══════════════════════════════════════" -ForegroundColor Magenta

# ═══ BUILD ═══
if (-not $SkipBuild) {
    Write-Host ""
    Write-Host "BUILD..." -ForegroundColor Cyan
    Push-Location $AppDir
    try {
        & ./gradlew clean 2>&1 | Out-Null
        & ./gradlew assembleRelease
        if ($LASTEXITCODE -ne 0) {
            Write-Host "BUILD FALLO" -ForegroundColor Red
            exit 1
        }
        Write-Host "BUILD OK" -ForegroundColor Green
    } finally { Pop-Location }
}

# ═══ ACTUALIZAR SUPABASE ═══
Write-Host ""
Write-Host "ACTUALIZANDO SUPABASE..." -ForegroundColor Cyan

$apkUrl = "https://calendar-04yk.onrender.com/calendarfinance.apk"
$valor = @{ versionCode = $VersionCode; versionName = $VersionName; apkUrl = $apkUrl } | ConvertTo-Json -Compress
$body = @{ valor = $valor } | ConvertTo-Json

$headers = @{
    "apikey" = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVndGx4bnJ3Zmlwb2N0Y2t1dmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzU4Mzk4MDQsImV4cCI6MjA1MTQxNTgwNH0.A5W4rRxYDxyPqFh7a4FX_ejniQl1nBNf1hMQuf7vjm4"
    "Authorization" = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVndGx4bnJ3Zmlwb2N0Y2t1dmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzU4Mzk4MDQsImV4cCI6MjA1MTQxNTgwNH0.A5W4rRxYDxyPqFh7a4FX_ejniQl1nBNf1hMQuf7vjm4"
    "Content-Type" = "application/json"
    "Prefer" = "return=minimal"
}

try {
    Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/app_versions?clave=eq.$DbKey" -Method PATCH -Headers $headers -Body $body | Out-Null
    Write-Host "SUPABASE OK: v$VersionName (code=$VersionCode)" -ForegroundColor Green
} catch {
    Write-Host "SUPABASE ERROR: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "LISTO v$VersionName" -ForegroundColor Green
