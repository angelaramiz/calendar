<# emu.ps1 — Operación del emulador Android por adb directo.
   Uso desde la raíz del repo:
     .agents/skill/emulador-android/emu.ps1 <accion> [args]
   Ver SKILL.md en esta carpeta para el workflow completo. #>
param(
    [Parameter(Position = 0)][string]$Action = "status",
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]]$Rest
)

$Sdk = $env:ANDROID_HOME
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$EmuExe = Join-Path $Sdk "emulator\emulator.exe"
$Avd = "Medium_Phone_API_35"
$Pkg = "com.fintrack.app"
$DefaultApk = "calendarAPP/app/build/outputs/apk/release/app-release.apk"

function AdbOf([string[]]$a) { & $Adb @a }

switch ($Action) {
    "status" {
        AdbOf @("devices")
    }
    "boot" {
        $running = (& $Adb devices) -match "emulator-.*device"
        if (-not $running) {
            Start-Process -FilePath $EmuExe -ArgumentList "-avd $Avd" -WindowStyle Minimized
            Write-Output "AVD lanzado, esperando device..."
        }
        for ($i = 0; $i -lt 24; $i++) {
            Start-Sleep -Seconds 5
            $running = (& $Adb devices) -match "emulator-.*device"
            if ($running) { Write-Output "Emulador listo."; break }
        }
        AdbOf @("devices")
    }
    "install" {
        $apk = if ($Rest.Count -ge 1) { $Rest[0] } else { $DefaultApk }
        AdbOf @("install", "-r", $apk)
    }
    "launch" {
        $pkg = if ($Rest.Count -ge 1) { $Rest[0] } else { $Pkg }
        AdbOf @("shell", "monkey -p $pkg -c android.intent.category.LAUNCHER 1")
    }
    "shot" {
        $out = if ($Rest.Count -ge 1) { $Rest[0] } else { ".opencode/emu-shot.png" }
        AdbOf @("shell", "screencap -p /sdcard/fintrack_preview.png")
        AdbOf @("pull", "/sdcard/fintrack_preview.png", $out)
    }
    "dump" {
        $out = if ($Rest.Count -ge 1) { $Rest[0] } else { ".opencode/emu-ui.xml" }
        AdbOf @("shell", "uiautomator dump /sdcard/ui.xml")
        AdbOf @("pull", "/sdcard/ui.xml", $out)
    }
    "tap" {
        if ($Rest.Count -lt 2) { throw "Uso: emu.ps1 tap <x> <y>" }
        AdbOf @("shell", "input tap $($Rest[0]) $($Rest[1])")
    }
    "type" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 type <texto...>" }
        $text = ($Rest -join " ").Replace(" ", "%s")
        AdbOf @("shell", "input text $text")
    }
    "swipe" {
        if ($Rest.Count -lt 4) { throw "Uso: emu.ps1 swipe <x1> <y1> <x2> <y2> [ms]" }
        $ms = if ($Rest.Count -ge 5) { $Rest[4] } else { "500" }
        AdbOf @("shell", "input swipe $($Rest[0]) $($Rest[1]) $($Rest[2]) $($Rest[3]) $ms")
    }
    "key" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 key <codigo>  (4=Back 66=Enter 67=Borrar 123=Fin)" }
        AdbOf @("shell", "input keyevent $($Rest[0])")
    }
    "tile" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 tile <componente>  (ej. com.fintrack.app/com.fintrack.app.ui.tile.QuickExpenseTileService)" }
        AdbOf @("shell", "cmd statusbar click-tile $($Rest[0])")
    }
    "panel" {
        AdbOf @("shell", "cmd statusbar expand-settings")
    }
    "net" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 net <on|off>" }
        if ($Rest[0] -eq "off") {
            AdbOf @("shell", "svc wifi disable"); AdbOf @("shell", "svc data disable")
            Write-Output "Red cortada (usar 'net on' para restaurar)."
        } else {
            AdbOf @("shell", "svc wifi enable"); AdbOf @("shell", "svc data enable")
            Write-Output "Red restaurada."
        }
    }
    default { throw "Accion desconocida: $Action (status|boot|install|launch|shot|dump|tap|type|swipe|key|tile|panel|net)" }
}
