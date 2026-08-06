# SKILL: OTA Android Updates — CalendarFinace

## Proposito
Sistema OTA para calendarAPP. Auto-check al iniciar, download + install via FileProvider, version tracking en Supabase.

---

## Mapeo del Proyecto

```yaml
# ─── Backend / DB ───────────────────────────────────────
db_table:              app_versions
db_key_column:         clave
db_value_column:       valor (JSONB)
apk_directory:         URL publica (GitHub Releases / servidor)

# ─── App Android ────────────────────────────────────────
app:
  name:                CalendarFinance
  package:             com.calendarfinance.app
  db_key:              app_version_calendarfinance
  apk_filename:        calendarfinance.apk
  source_path:         calendarAPP/
  main_activity:       MainActivity.kt

# ─── Configuracion ──────────────────────────────────────
comparison_field:      versionCode
cache_busting:         true
clean_old_apks:        true
auto_check_on_start:   true
manual_check_button:   true
```

---

## Arquitectura

```
┌─────────────────┐    Supabase PostgREST    ┌─────────────────┐
│  calendarAPP    │ ──────────────────────►  │  Supabase       │
│  (on-create)    │ ◄──────────────────────  │  app_versions   │
│                 │    { versionCode, url }  │  table          │
└───────┬─────────┘                          └─────────────────┘
        │ versionCode > local?
        ▼
┌─────────────────┐
│  UpdateDialog   │  "Nueva version disponible"
│  Download       │
└───────┬─────────┘
        │
        ▼
┌─────────────────┐
│  OkHttp GET     │  Full URL → bytes → file.apk
│  Download       │
└───────┬─────────┘
        │
        ▼
┌─────────────────┐
│  Intent         │  ACTION_VIEW + FileProvider URI
│  Install        │
└─────────────────┘
```

---

## DB Schema (Supabase)

```sql
CREATE TABLE app_versions (
  clave TEXT PRIMARY KEY,
  valor JSONB NOT NULL
);

INSERT INTO app_versions (clave, valor) VALUES
  ('app_version_calendarfinance', '{"versionCode": 1, "versionName": "1.0.0", "apkUrl": ""}');
```

---

## Pipeline de Scripts PowerShell

### `scripts/release.ps1` — Pipeline unificado

```powershell
# release.ps1 — Pipeline: build release + deploy + actualizar DB
# Uso: ./release.ps1 -VersionCode 2 -VersionName "1.0.1" -SupabaseKey "eyJ..."

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

$AppDir = $PSScriptRoot + "\.."
$DbKey = "app_version_calendarfinance"
$ApkName = "calendarfinance.apk"
$failed = $false

Write-Host "═══════════════════════════════════════════════" -ForegroundColor Magenta
Write-Host "  RELEASE CalendarFinance v$VersionName (code=$VersionCode)" -ForegroundColor Magenta
Write-Host "═══════════════════════════════════════════════" -ForegroundColor Magenta

# Step 1: Build
if (-not $SkipBuild) {
    Write-Host ""
    Write-Host "═══ BUILD ═══" -ForegroundColor Cyan
    Push-Location $AppDir
    try {
        & ./gradlew clean 2>&1 | Out-Null
        $task = if ($BuildType -eq "debug") { "assembleDebug" } else { "assembleRelease" }
        $gradleArgs = @($task)
        if ($BuildType -eq "release" -and $KeystorePath) {
            $gradleArgs += "-Pandroid.injected.signing.store.file=$KeystorePath"
            $gradleArgs += "-Pandroid.injected.signing.store.password=$KeystorePassword"
            $gradleArgs += "-Pandroid.injected.signing.key.alias=$KeyAlias"
            $gradleArgs += "-Pandroid.injected.signing.key.password=$KeyPassword"
        }
        & ./gradlew @gradleArgs
        if ($LASTEXITCODE -ne 0) { Write-Host "  Build fallo" -ForegroundColor Red; $failed = $true }
        else { Write-Host "  Build exitoso" -ForegroundColor Green }
    } finally { Pop-Location }
}

# Step 2: Update DB
if (-not $failed -and $SupabaseKey) {
    Write-Host ""
    Write-Host "═══ UPDATE DB ═══" -ForegroundColor Cyan

    $dbUrl = if ($ApkUrl) { $ApkUrl } else { "/public/$ApkName" }
    $valor = @{ versionCode = $VersionCode; versionName = $VersionName; apkUrl = $dbUrl } | ConvertTo-Json -Compress

    try {
        Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/app_versions?clave=eq.$DbKey" `
            -Method PATCH `
            -Headers @{
                "apikey" = $SupabaseKey
                "Authorization" = "Bearer $SupabaseKey"
                "Content-Type" = "application/json"
                "Prefer" = "return=minimal"
            } -Body (@{ valor = $valor } | ConvertTo-Json)
        Write-Host "  DB actualizada: code=$VersionCode" -ForegroundColor Green
    } catch {
        Write-Host "  Error DB: $($_.Exception.Message)" -ForegroundColor Red
        $failed = $true
    }
} elseif (-not $SupabaseKey) {
    Write-Host "  Saltando DB (no SupabaseKey)" -ForegroundColor Yellow
}

# Resumen
Write-Host ""
if (-not $failed) {
    $apkDir = "$AppDir/app/build/outputs/apk/$BuildType"
    $apk = Get-ChildItem "$apkDir/*.apk" | Select-Object -First 1
    $sizeMB = if ($apk) { [math]::Round($apk.Length / 1MB, 2) } else { "?" }
    Write-Host "  APK: $apkDir ($sizeMB MB)" -ForegroundColor Green
    Write-Host "  Version: $VersionName (code=$VersionCode)" -ForegroundColor Green
    Write-Host "  DB: $SupabaseUrl/rest/v1/app_versions" -ForegroundColor Gray
} else {
    Write-Host "  RELEASE FALLIDO" -ForegroundColor Red
}
```

---

## Scripts Individuales

### 1. `scripts/build-debug.ps1` — APK rapido para pruebas
```powershell
param([string]$AppDir = "$PSScriptRoot\..")
Push-Location $AppDir
try {
    & ./gradlew clean 2>&1 | Out-Null
    & ./gradlew assembleDebug
    if ($LASTEXITCODE -eq 0) {
        $apk = Get-ChildItem "app/build/outputs/apk/debug/*.apk" | Select-Object -First 1
        Write-Host "APK generado: $($apk.FullName) ($([math]::Round($apk.Length/1MB,2)) MB)" -ForegroundColor Green
    }
} finally { Pop-Location }
```

### 2. `scripts/update-version.ps1` — Solo actualizar DB
```powershell
param(
    [string]$SupabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co",
    [string]$SupabaseKey,
    [int]$VersionCode,
    [string]$VersionName,
    [string]$ApkUrl = ""
)
$valor = @{ versionCode = $VersionCode; versionName = $VersionName; apkUrl = $ApkUrl } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/app_versions?clave=eq.app_version_calendarfinance" `
    -Method PATCH `
    -Headers @{ "apikey"=$SupabaseKey; "Authorization"="Bearer $SupabaseKey"; "Content-Type"="application/json"; "Prefer"="return=minimal" } `
    -Body (@{ valor=$valor } | ConvertTo-Json)
Write-Host "Version actualizada: $VersionName" -ForegroundColor Green
```

---

## Publicar Nueva Version

```bash
# 1. Build + actualizar DB
cd calendarAPP/scripts
./release.ps1 -VersionCode 2 -VersionName "1.0.1" -SupabaseKey "eyJ..." -ApkUrl "https://TU_SERVIDOR/calendarfinance.apk"

# 2. Subir APK manualmente al hosting publico

# 3. Los usuarios veran el dialogo OTA al abrir la app
```
