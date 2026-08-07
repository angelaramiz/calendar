# AGENTS.md — CalendarFinace

## Stack Tecnologico

| Capa | Tecnologia | Version |
|------|-----------|---------|
| **Web** | Vanilla JS + HTML/CSS + Supabase | - |
| **Mobile** | Kotlin + Jetpack Compose | Kotlin 2.4.0 |
| **Backend** | Supabase PostgreSQL + Flask (scraper) | - |
| **Scraper** | Python Flask + Selenium (Fly.io) | - |
| **CI/CD** | Render (web) + GitHub | - |
| **AI Index** | CodeGraph (SQLite) | v1.1.3 |

### Dependencias Criticas Android

| Dependencia | Version | Notas |
|-------------|---------|-------|
| AGP | 8.7.3 | Requiere compileSdk 35 |
| Kotlin | 2.4.0 | Requerido por Supabase v3.7 |
| Compose BOM | 2024.06 | |
| Supabase BOM | 3.7.0 | auth-kt + postgrest-kt |
| Ktor | 3.0.3 | Requerido por Supabase v3 |
| Biometric | 1.2.0-alpha05 | Login con huella |
| Koin | 3.5.3 | DI |

### Dependencias Criticas Web

| Dependencia | Version | Notas |
|-------------|---------|-------|
| Supabase JS | CDN | via esm.sh |
| SweetAlert2 | 11.x | Modales y alertas |
| WebAuthn | Native API | Biometria web |

## Estructura del Proyecto

```
CalendarFinace/
├── calendarWeb/        # App web (produccion)
│   ├── js/             # 30+ modulos JS
│   ├── styles/         # CSS
│   ├── routes/         # HTML pages
│   └── docs/           # Migraciones SQL
├── calendarRN/         # Copia workspace (migracion planeada)
├── calendarAPP/        # App Android nativa (Kotlin)
│   ├── app/src/main/java/com/calendarfinance/app/
│   │   ├── data/
│   │   │   ├── model/          # Data classes
│   │   │   ├── remote/         # Supabase, Biometric, Session
│   │   │   └── repository/     # Auth, Movement, Pattern, OTA
│   │   ├── di/                 # Koin modules
│   │   └── ui/
│   │       ├── auth/           # Login, Register, Biometric
│   │       ├── calendar/       # Calendar grid
│   │       ├── movement/       # Movement form
│   │       ├── pattern/        # Pattern form
│   │       ├── balance/        # Balance screen
│   │       ├── ota/            # OTA update
│   │       ├── navigation/     # NavGraph
│   │       └── theme/          # Material 3
│   └── scripts/        # PowerShell automation
├── .codegraph/         # Indice semantico
└── .agents/            # Memoria y contexto IA
```

## Convenciones

### Nombrado
- **Archivos JS**: `kebab-case.js`
- **Archivos SQL**: `NN-nombre-descripcion.sql`
- **Clases Kotlin**: `PascalCase`
- **Funciones JS**: `camelCase`
- **Tablas DB**: `snake_case`

### Base de Datos (Supabase)
- Todas las tablas tienen RLS con `auth.uid() = user_id`
- UUID como PK via `uuid_generate_v4()`
- Soft-delete con flags `active`/`archived`
- Triggers `updated_at` automaticos

### Arquitectura de Datos
```
Pattern → Projection → Movement
```
1. **Patterns**: Templates recurrentes (income_patterns, expense_patterns)
2. **Projections**: Ocurrencias generadas dinamicamente
3. **Movements**: Transacciones confirmadas

## Comandos

| Comando | Descripcion |
|---------|-------------|
| `codegraph explore "<query>"` | Busqueda semantica en codigo |
| `codegraph index` | Reindexar proyecto |
| `.\calendarAPP\scripts\release.ps1` | Build + deploy + Supabase |
| `.\calendarAPP\scripts\cleanup.ps1` | Limpiar datos de usuario |
| `cd calendarWeb && python -m http.server` | Servir web local |

## Rutas Importantes

| Ruta | Descripcion |
|------|-------------|
| `calendarWeb/index.html` | Entry point web (login) |
| `calendarWeb/js/main.js` | Orquestador principal |
| `calendarWeb/js/config.js` | Config (Supabase keys) |
| `calendarAPP/.../MainActivity.kt` | Entry point Android |
| `calendarAPP/.../CalendarFinApp.kt` | Koin + global error handler |
| `calendarAPP/scripts/release.ps1` | Pipeline release |
| `calendarWeb/docs/migrations/` | Schema SQL |

## Estado Actual

| Item | Estado | Version |
|------|--------|---------|
| App Web | Produccion | v1.0.20 |
| App Android | En desarrollo | v1.0.21 |
| CodeGraph | Indexado | 1514 nodos, 4883 aristas |
| DB | Supabase | Migrations 01-05 |

## Reglas del Agente

1. **CodeGraph primero** — ANTES de grep/find usar `codegraph explore`
2. **No secretos** — No modificar `config.js` sin confirmacion
3. **Consistencia** — Mantener mismos modelos entre Web y App
4. **Documentar** — Decisiones en `.agents/meetings/decisions/`
5. **Tasks** — Actualizar `tasks.md` al completar tareas
6. **No inventar** — NO generar URLs ni endpoints, solo usar los definidos
7. **Estilo** — Seguir el estilo de codigo existente
8. **Compile check** — SIEMPRE ejecutar `assembleRelease` antes de `release.ps1`

## Guia de Errores Comunes

### Build Android

| Error | Causa | Solucion |
|-------|-------|---------|
| `Unresolved reference: GoTrue` | Supabase SDK v2 vs v3 | Usar `auth-kt` v3.7+ con `Auth` |
| `metadata version 2.4.0, expected 2.0.0` | Kotlin incompatible | Usar Kotlin 2.4.0 |
| `compileSdk 35 required` | AGP incompatible | Usar AGP 8.7.3+ |
| `lint checkReleaseBuilds FAILED` | Lint bloquea build | `lint { checkReleaseBuilds = false }` |
| `List is empty` (login) | Perfil no existe en users | Auto-crear perfil en AuthRepository |
| `HorizontalDivider unresolved` | Compose BOM viejo | BOM 2024.06+ |
| Crash sin mensaje | Exception sin catch | Verificar Logcat con tag `CalendarFinApp` |

### Supabase

| Error | Causa | Solucion |
|-------|-------|---------|
| `401 Unauthorized` | API key incorrecta | Verificar `sb_publishable_...` en config |
| `422 WeakPassword` | Password sin requisitos | 8+ chars, mayuscula, minuscula, numero, especial |
| `table doesn't exist` | Tabla no creada | Ejecutar migracion en SQL Editor |

### Gradle

| Error | Causa | Solucion |
|-------|-------|---------|
| `gradlew not recognized` | No hay wrapper | Abrir proyecto en Android Studio |
| `SDK location not found` | No hay local.properties | Crear con `sdk.dir` |
| `JAVA_HOME not set` | Java no encontrado | Agregar a `gradle.properties` |

### Release Pipeline

| Error | Causa | Solucion |
|-------|-------|---------|
| `No se encontro build.gradle.kts` | Path relativo roto | Ejecutar desde raiz del proyecto |
| `Build failed` pero APK existe | Error en copy | Verificar ruta `calendarWeb/calendarfinance.apk` |
| `Supabase 401` | Key vieja | Usar key `sb_publishable_...` actualizada |
| `Render deploy falla` | Billing issue | Verificar Render dashboard |

## Flujo de Debug

```
1. Si la app crashea sin mensaje:
   → adb logcat | grep -i "calendar\|FATAL\|AndroidRuntime"
   
2. Si el build falla:
   → Verificar versiones en build.gradle.kts
   → Limpiar: .\gradlew.bat clean
   → Verificar JAVA_HOME y ANDROID_HOME

3. Si el login falla:
   → Verificar API key en SupabaseClientProvider.kt
   → Verificar que la tabla users existe en Supabase
   → Verificar RLS policies

4. Si el deploy falla:
   → Verificar Render dashboard (billing status)
   → Verificar secrets en GitHub/Render
   → Ejecutar .\calendarAPP\scripts\release.ps1 -SkipBuild
```
