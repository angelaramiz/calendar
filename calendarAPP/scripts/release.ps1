# release.ps1 — Pipeline unificado: build release + actualizar DB Supabase
# Uso: .\release.ps1 -VersionCode 2 -VersionName "1.0.1" -SupabaseKey "eyJ..."

param(
    [Parameter(Mandatory=$true)]
    [int]$VersionCode,

    [Parameter(Mandatory=$true)]
    [string]$VersionName,

    [string]$BuildType = "release",
    [switch]$SkipBuild,

    [string]$KeystorePath = "",
    [string]$KeystorePassword = "",
    [string]$KeyAlias = "",
    [string]$KeyPassword = "",

    [string]$ApkUrl = "",
    [string]$SupabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co",
    [string]$SupabaseKey = ""
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppDir = Join-Path $ScriptDir ".."
$DbKey = "app_version_calendarfinance"
$ApkName = "calendarfinance.apk"
$failed = $false

Write-Host ""
Write-Host "═══════════════════════════════════════════════" -ForegroundColor Magenta
Write-Host "  CALENDARFINANCE RELEASE" -ForegroundColor Magenta
Write-Host "  v$VersionName (code=$VersionCode)" -ForegroundColor Magenta
Write-Host "═══════════════════════════════════════════════" -ForegroundColor Magenta

# ═══ STEP 1: BUILD ═══
if (-not $SkipBuild) {
    Write-Host ""
    Write-Host "═══ BUILD ═══" -ForegroundColor Cyan

    if (-not (Test-Path $AppDir)) {
        Write-Host "  ERROR: Directorio no encontrado: $AppDir" -ForegroundColor Red
        exit 1
    }

    Push-Location $AppDir
    try {
        Write-Host "  Limpiando builds anteriores..." -ForegroundColor Gray
        & ./gradlew clean 2>&1 | Out-Null

        $task = if ($BuildType -eq "debug") { "assembleDebug" } else { "assembleRelease" }
        Write-Host "  Compilando $task..." -ForegroundColor Yellow

        $gradleArgs = @($task)
        if ($BuildType -eq "release" -and $KeystorePath) {
            Write-Host "  Firmando con keystore: $KeystorePath" -ForegroundColor Gray
            $gradleArgs += "-Pandroid.injected.signing.store.file=$KeystorePath"
            $gradleArgs += "-Pandroid.injected.signing.store.password=$KeystorePassword"
            $gradleArgs += "-Pandroid.injected.signing.key.alias=$KeyAlias"
            $gradleArgs += "-Pandroid.injected.signing.key.password=$KeyPassword"
        }

        & ./gradlew @gradleArgs

        if ($LASTEXITCODE -ne 0) {
            Write-Host "  BUILD FALLO" -ForegroundColor Red
            $failed = $true
        } else {
            $apkDir = "app/build/outputs/apk/$BuildType"
            $apk = Get-ChildItem -Path "$apkDir/*.apk" | Select-Object -First 1
            if ($apk) {
                $sizeMB = [math]::Round($apk.Length / 1MB, 2)
                Write-Host "  APK generado: $($apk.Name) ($sizeMB MB)" -ForegroundColor Green
            } else {
                Write-Host "  APK no encontrado en $apkDir" -ForegroundColor Red
                $failed = $true
            }
        }
    } finally {
        Pop-Location
    }
}

# ═══ STEP 2: UPDATE DB ═══
if (-not $failed) {
    Write-Host ""
    Write-Host "═══ UPDATE DB ═══" -ForegroundColor Cyan

    if (-not $SupabaseKey) {
        Write-Host "  Sin SupabaseKey. Saltando actualizacion DB." -ForegroundColor Yellow
        Write-Host "  Ejecuta manualmente en Supabase SQL Editor:" -ForegroundColor Gray
        Write-Host "  UPDATE app_versions SET valor = '{\"versionCode\": $VersionCode, \"versionName\": \"$VersionName\", \"apkUrl\": \"$ApkUrl\"}'::jsonb WHERE clave = '$DbKey';" -ForegroundColor Gray
    } else {
        $dbUrl = if ($ApkUrl) { $ApkUrl } else { "/public/$ApkName" }
        $valor = @{
            versionCode = $VersionCode
            versionName = $VersionName
            apkUrl = $dbUrl
        } | ConvertTo-Json -Compress

        try {
            $patchUrl = "$SupabaseUrl/rest/v1/app_versions?clave=eq.$DbKey"
            Invoke-RestMethod -Uri $patchUrl `
                -Method PATCH `
                -Headers @{
                    "apikey" = $SupabaseKey
                    "Authorization" = "Bearer $SupabaseKey"
                    "Content-Type" = "application/json"
                    "Prefer" = "return=minimal"
                } `
                -Body (@{ valor = $valor } | ConvertTo-Json) | Out-Null

            Write-Host "  DB actualizada: $DbKey = v$VersionName (code=$VersionCode)" -ForegroundColor Green
        } catch {
            Write-Host "  ERROR DB: $($_.Exception.Message)" -ForegroundColor Red
            $failed = $true
        }
    }
}

# ═══ RESUMEN ═══
Write-Host ""
Write-Host "═══════════════════════════════════════════════" -ForegroundColor $(if ($failed) { "Red" } else { "Green" })
Write-Host "  RESULTADO: $(if ($failed) { 'FALLIDO' } else { 'COMPLETADO' })" -ForegroundColor $(if ($failed) { "Red" } else { "Green" })
if (-not $failed) {
    Write-Host "  Version: $VersionName (code=$VersionCode)" -ForegroundColor Gray
    $apkPath = Join-Path $AppDir "app\build\outputs\apk\$BuildType\$ApkName"
    Write-Host "  APK: $apkPath" -ForegroundColor Gray
    Write-Host "  DB key: $DbKey" -ForegroundColor Gray
}
Write-Host "═══════════════════════════════════════════════" -ForegroundColor $(if ($failed) { "Red" } else { "Green" })
Write-Host ""

exit $(if ($failed) { 1 } else { 0 })
