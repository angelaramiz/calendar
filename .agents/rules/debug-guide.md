# Regla: Flujo de Debug Estandarizado

Cuando un error ocurra en calendarAPP, seguir este orden:

## 1. Compilacion falla

```
ERROR: "Unresolved reference: X"
  → Verificar version del SDK/dependencia
  → Ejecutar .\gradlew.bat clean
  → Verificar imports en el archivo

ERROR: "metadata version X, expected Y"
  → Version de Kotlin incompatible con dependencias
  → Actualizar Kotlin en build.gradle.kts raiz

ERROR: "SDK location not found"
  → Crear local.properties con sdk.dir

ERROR: "lint checkReleaseBuilds FAILED"
  → Agregar: lint { checkReleaseBuilds = false; abortOnError = false }
```

## 2. App crashea al abrir

```
1. Ejecutar: adb logcat -d | Select-String "CalendarFinApp|FATAL|AndroidRuntime"
2. Buscar el tag "CalendarFinApp" o "MainActivity"
3. Identificar la exception y el stack trace
4. La pantalla de error (MainActivity) muestra el mensaje sin cerrar

Causas comunes:
- Koin no pudo resolver un ViewModel → verificar AppModule.kt
- BiometricPrompt con Activity incorrecta → usar FragmentActivity
- Composable sin imports → agregar imports faltantes
```

## 3. Login falla

```
1. Verificar que la API key en SupabaseClientProvider.kt es correcta
   → Debe empezar con "sb_publishable_..."
2. Verificar que la tabla users existe en Supabase
3. Verificar RLS policies en tabla users
4. Verificar logs: adb logcat | grep "AuthRepository"
5. Error "List is empty" → perfil no existe, se auto-crea
6. Error "401" → API key incorrecta o expirada
```

## 4. Build exitoso pero crash en runtime

```
1. Verificar que config.js tiene las credenciales correctas
2. Verificar que Supabase project esta activo
3. Verificar que las migraciones SQL estan ejecutadas
4. Verificar Logcat con tags:
   - CalendarFinApp
   - AuthRepository
   - SupabaseClient
   - MainActivity
```

## 5. OTA no funciona

```
1. Verificar que tabla app_versions existe y tiene datos
2. Verificar RLS: la tabla debe tener policies de SELECT/INSERT/UPDATE
3. Verificar que el APK esta en la URL correcta en Supabase
4. Verificar que Render puede servir el APK
5. Verificar logs: adb logcat | grep "OtaUpdate"
```

## Comandos de Debug Rapido

```powershell
# Ver logs en tiempo real
adb logcat -d

# Filtrar por tags
adb logcat -d | Select-String "CalendarFinApp|Auth|Supabase|OTA"

# Verificar APK instalada
adb shell pm dump com.calendarfinance.app | Select-String "versionName"

# Desinstalar y reinstalar
adb uninstall com.calendarfinance.app
.\calendarAPP\scripts\release.ps1 -SkipBuild
adb install calendarAPP\app\build\outputs\apk\release\app-release.apk
```

## Tags de Log en Codigo

| Tag | Ubicacion | Que logea |
|-----|-----------|-----------|
| `CalendarFinApp` | Application | Koin init, uncaught exceptions |
| `MainActivity` | Entry point | setContent errors |
| `AuthRepository` | Repository | Login, register, logout, fetchProfile |
| `SupabaseClient` | Provider | Client init |
| `NavGraph` | Navigation | Screen render errors |
