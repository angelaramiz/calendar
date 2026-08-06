# update-version.ps1 — Actualiza solo la version en Supabase (sin build)
param(
    [Parameter(Mandatory=$true)]
    [int]$VersionCode,

    [Parameter(Mandatory=$true)]
    [string]$VersionName,

    [string]$ApkUrl = "",

    [string]$SupabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co",

    [Parameter(Mandatory=$true)]
    [string]$SupabaseKey
)

$DbKey = "app_version_calendarfinance"

$valor = @{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl = $ApkUrl
} | ConvertTo-Json -Compress

Write-Host "Updating version in Supabase..." -ForegroundColor Cyan
Write-Host "  Key: $DbKey" -ForegroundColor Gray
Write-Host "  Version: $VersionName (code=$VersionCode)" -ForegroundColor Gray
Write-Host "  URL: $ApkUrl" -ForegroundColor Gray

try {
    Invoke-RestMethod `
        -Uri "$SupabaseUrl/rest/v1/app_versions?clave=eq.$DbKey" `
        -Method PATCH `
        -Headers @{
            "apikey" = $SupabaseKey
            "Authorization" = "Bearer $SupabaseKey"
            "Content-Type" = "application/json"
            "Prefer" = "return=minimal"
        } `
        -Body (@{ valor = $valor } | ConvertTo-Json) | Out-Null

    Write-Host "  Version actualizada exitosamente" -ForegroundColor Green
} catch {
    Write-Host "  ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
