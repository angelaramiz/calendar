# build-debug.ps1 — APK rapido para pruebas en dispositivo/emulador
param(
    [string]$AppDir = "$PSScriptRoot\.."
)

Write-Host "Building Debug APK..." -ForegroundColor Cyan

if (-not (Test-Path $AppDir)) {
    Write-Host "ERROR: $AppDir not found" -ForegroundColor Red
    exit 1
}

Push-Location $AppDir
try {
    Write-Host "  Cleaning..." -ForegroundColor Gray
    & ./gradlew clean 2>&1 | Out-Null

    Write-Host "  Building debug..." -ForegroundColor Yellow
    & ./gradlew assembleDebug

    if ($LASTEXITCODE -eq 0) {
        $apk = Get-ChildItem -Path "app/build/outputs/apk/debug/*.apk" | Select-Object -First 1
        if ($apk) {
            $sizeMB = [math]::Round($apk.Length / 1MB, 2)
            Write-Host ""
            Write-Host "  APK: $($apk.FullName)" -ForegroundColor Green
            Write-Host "  Size: $sizeMB MB" -ForegroundColor Gray
        }
    } else {
        Write-Host "  BUILD FAILED" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}
