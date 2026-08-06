# release.ps1 — Pipeline OTA completo para CalendarFinance
# Uso: .\release.ps1
# Sin build: .\release.ps1 -SkipBuild

param (
    [string]$Version,
    [switch]$SkipBuild
)

$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Resolver PATH de NVM
if ($env:PATH -like '*%NVM_HOME%*' -or $env:PATH -like '*%NVM_SYMLINK%*') {
    $env:PATH = $env:PATH -replace '%NVM_HOME%', $env:NVM_HOME
    $env:PATH = $env:PATH -replace '%NVM_SYMLINK%', $env:NVM_SYMLINK
}
if ($env:NVM_SYMLINK -and -not ($env:PATH -split ';' -contains $env:NVM_SYMLINK)) {
    $env:PATH = "$env:NVM_SYMLINK;$env:PATH"
}

# Config
$gradlePath = "calendarAPP\app\build.gradle.kts"
$publicApkPath = "public\calendarfinance.apk"
$supabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co"
$supabaseKey = "sb_publishable_KcdYZchjzzpizgM4nhTw8w_Bd6w6-d1"
$dbKey = "app_version_calendarfinance"
$renderUrl = "https://calendar-04yk.onrender.com"
$rootDir = $PSScriptRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   CALENDARFINANCE: PUBLICADOR DE ACTUALIZACIONES OTA" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Leer version actual de build.gradle.kts
if (-not (Test-Path $gradlePath)) {
    Write-Error "No se encontro $gradlePath"
    exit 1
}

$gradleContent = Get-Content $gradlePath -Raw
$currentVersion = $null
$currentCode = $null

if ($gradleContent -match 'versionCode\s*=\s*(\d+)') { $currentCode = [int]$Matches[1] }
if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') { $currentVersion = $Matches[1] }

if ($null -eq $currentVersion -or $null -eq $currentCode) {
    Write-Error "No se pudo extraer version de $gradlePath"
    exit 1
}

Write-Host "Version actual: v$currentVersion (code=$currentCode)" -ForegroundColor Yellow

# 2. Calcular nueva version
$newCode = $currentCode + 1
$versionParts = $currentVersion -split '\.'
if ($versionParts.Length -eq 3) {
    $suggestedVersion = "$($versionParts[0]).$($versionParts[1]).$([int]$versionParts[2] + 1)"
} else {
    $suggestedVersion = "$currentVersion.1"
}

$targetVersion = $Version
if ([string]::IsNullOrWhiteSpace($targetVersion)) {
    Write-Host "Nueva version [Sugerida: $suggestedVersion] (Enter para usar): " -NoNewline -ForegroundColor Cyan
    $input = Read-Host
    $targetVersion = if ([string]::IsNullOrWhiteSpace($input)) { $suggestedVersion } else { $input.Trim() }
}

Write-Host "Estableciendo v$targetVersion (code=$newCode)..." -ForegroundColor Cyan

# 3. Actualizar build.gradle.kts
$updatedContent = $gradleContent -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
$updatedContent = $updatedContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$targetVersion`""
Set-Content $gradlePath $updatedContent -NoNewline
Write-Host "build.gradle.kts actualizado" -ForegroundColor Green

# 4. Build APK
if (-not $SkipBuild) {
    Write-Host "`nCompilando APK..." -ForegroundColor Cyan

    # Usar Java 17 de Android Studio (compatible con Gradle 8.x)
    $androidJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $androidJbr) {
        $env:JAVA_HOME = $androidJbr
        Write-Host "JAVA_HOME: $androidJbr" -ForegroundColor Gray
    }

    Push-Location "calendarAPP"
    try {
        .\gradlew.bat clean assembleRelease

        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Build fallo, deteniendo daemons..."
            .\gradlew.bat --stop
            Start-Sleep -Seconds 3
            Write-Host "Reintentando..." -ForegroundColor Yellow
            .\gradlew.bat clean assembleRelease
            if ($LASTEXITCODE -ne 0) { Write-Error "Build fallido"; exit 1 }
        }
    } finally { Pop-Location }

    # Copiar APK
    $apkBuildPath = "calendarAPP\app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path "public")) { New-Item -ItemType Directory -Force -Path "public" | Out-Null }

    if (Test-Path $apkBuildPath) {
        Copy-Item $apkBuildPath $publicApkPath -Force
        Write-Host "APK copiada a $publicApkPath" -ForegroundColor Green
    } else {
        Write-Error "APK no encontrada en $apkBuildPath"
        exit 1
    }
} else {
    Write-Host "`nBuild omitido" -ForegroundColor Yellow
}

# 5. Git Push
Write-Host "`nGIT PUSH..." -ForegroundColor Cyan
git add -f $gradlePath
if (Test-Path $publicApkPath) { git add $publicApkPath }
git add "public\version.json" 2>$null
git commit -m "release: v$targetVersion (code=$newCode)" 2>&1 | Out-Null
git push
Write-Host "Git OK" -ForegroundColor Green

# 6. Generar version.json
$versionJsonPath = "public\version.json"
$versionJson = @{ versionCode = $newCode; versionName = $targetVersion } | ConvertTo-Json
Set-Content -Path $versionJsonPath -Value $versionJson -Force

# 7. Actualizar Supabase
Write-Host "`nSUPABASE..." -ForegroundColor Cyan

$apkUrl = "$renderUrl/calendarfinance.apk"
$valor = @{ versionCode = $newCode; versionName = $targetVersion; apkUrl = $apkUrl } | ConvertTo-Json -Compress
$body = @{ valor = $valor } | ConvertTo-Json
$headers = @{ "apikey"=$supabaseKey; "Authorization"="Bearer $supabaseKey"; "Content-Type"="application/json"; "Prefer"="return=minimal" }

try {
    Invoke-RestMethod -Uri "$supabaseUrl/rest/v1/app_versions?clave=eq.$dbKey" -Method PATCH -Headers $headers -Body $body | Out-Null
    Write-Host "OK: v$targetVersion (code=$newCode)" -ForegroundColor Green
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
}

# 8. Esperar Render (verificar version.json)
Write-Host "`nESPERANDO RENDER..." -ForegroundColor Cyan
$targetCheckUrl = "$renderUrl/public/version.json"
$maxWait = 300
$elapsed = 0
$isLive = $false

Start-Sleep -Seconds 15

while ($elapsed -lt $maxWait) {
    try {
        $resp = Invoke-WebRequest -Uri $targetCheckUrl -Method Get -TimeoutSec 8 -UseBasicParsing
        if ($resp.StatusCode -eq 200) {
            $data = $resp.Content | ConvertFrom-Json
            if ([int]$data.versionCode -eq $newCode) {
                $isLive = $true
                Write-Host "RENDER LIVE: v$targetVersion (code=$newCode)" -ForegroundColor Green
                break
            }
        }
    } catch {}

    $elapsed += 10
    Write-Host "Esperando... ($elapsed/${maxWait}s)" -ForegroundColor Gray
    Start-Sleep -Seconds 10
}

if (-not $isLive) {
    Write-Host "Render no confirmo, pero el deploy puede ser exitoso" -ForegroundColor Yellow
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " LISTO: v$targetVersion (code=$newCode)" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
