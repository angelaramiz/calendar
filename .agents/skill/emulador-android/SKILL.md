# SKILL: Emulador Android para QA — FinTrack / apps Kotlin

## Propósito
Operar el emulador Android (AVD `Medium_Phone_API_35`) por `adb` directo para probar la app móvil Kotlin sin herramienta gráfica: instalar, lanzar, ver, tocar, escribir, verificar flujos y limpiar datos de prueba.

> No requiere Bun ni `preview.ts`: es `adb` puro y funciona en cualquier agente con shell + PowerShell.

---

## Herramienta: `emu.ps1`

Wrapper en esta misma carpeta. Uso desde la raíz del repo:

```powershell
.agents/skill/emulador-android/emu.ps1 <accion> [args]
```

| Acción | Args | Qué hace |
|---|---|---|
| `status` | — | `adb devices` (¿hay emulador vivo?) |
| `boot` | — | Lanza el AVD minimizado y espera hasta `device` |
| `install` | `[apk]` | `install -r` (default: `calendarAPP/app/build/outputs/apk/release/app-release.apk`) |
| `launch` | `[pkg]` | Abre la app (default: `com.fintrack.app`) |
| `shot` | `[salida]` | `screencap` + `pull` (default: `.opencode/emu-shot.png`) |
| `dump` | `[salida]` | `uiautomator dump` + `pull` (default: `.opencode/emu-ui.xml`) |
| `tap` | `x y` | Toque en coordenadas de dispositivo (1080×2400) |
| `type` | `texto…` | Escribe (espacios → `%s`) en el campo con foco |
| `swipe` | `x1 y1 x2 y2 [ms]` | Arrastre (default 500 ms) |
| `key` | `código` | `keyevent` (4=Back, 66=Enter, 67=Borrar, 123=Fin) |
| `tile` | `componente` | `cmd statusbar click-tile` (ej. `com.fintrack.app/com.fintrack.app.ui.tile.QuickExpenseTileService`) |
| `panel` | — | Expande el panel de ajustes rápidos |
| `net` | `on\|off` | Activa/corta wifi+datos (prueba sin conexión) |

Rutas del SDK: usa `$env:ANDROID_HOME` o `%LOCALAPPDATA%\Android\Sdk`.

---

## Workflow de prueba (agentes)

1. `status` → si no hay `device`, `boot` (tarda ~1 min).
2. `install` tras cada `assembleRelease`.
3. `launch` → `shot` (leer el PNG) o `dump` (bounds exactos).
4. `tap`/`type`/`swipe` según bounds del dump. Para campos de texto: tocar campo → `type`.
5. Re-verificar con `shot`/`dump`. Borrar datos de prueba creados (dejar balance $0).
6. Casos especiales: Tile (`panel` + `tile`), sin-red (`net off` … `net on`).

## Reglas

- Capturas **solo** vía `screencap` en el equipo + `pull`. Nunca `exec-out` redirigido en PowerShell (corrompe el PNG a UTF-16).
- Coordenadas = píxeles del dispositivo (1080×2400), no de la imagen miniatura.
- Si el foco cae mal al escribir (texto mezclado): `key 123` (fin) + borrados `key 67…` + reescribir.
- Sesión expirada ⇒ pantalla "FinTrack bloqueado" → "Usar contraseña" → demo `angelaramiz93@gmail.com` / `Demo1234!`.
- `input text` necesita `%s` en vez de espacios (el wrapper `type` ya lo hace).
- El broadcast `AIRPLANE_MODE` está denegado en Android moderno: para sin-red usar `net off`.
- Tras `install -r` los datos locales (DataStore) se conservan; la sesión Supabase puede haber expirado.
- Limpiar al terminar: eliminar transacciones/cuentas de prueba.

## Referencia rápida FinTrack

- Paquete: `com.fintrack.app` · Tabs: Inicio/Calendario/Flujos/Presupuesto/Cuentas.
- FAB `+` (Inicio) = ventana IN (persiana abajo, formulario completo).
- Tile "Registro rápido" = ventana OUT (persiana arriba, wizard; no cierra al tocar fuera).
- Pull-to-refresh en Inicio: `swipe 540 500 540 1300 1200` → "Suelta para actualizar" → "Actualizando…".
