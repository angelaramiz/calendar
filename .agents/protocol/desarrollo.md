# Protocolo de Desarrollo y Arquitectura — CalendarFinace

## 1. Fuente de verdad por capa

- **Frontend Web de Producción:** `calendarWeb/` (entry: `calendarWeb/index.html`, lógica: `calendarWeb/js/`, servidor local: `python -m http.server`).
- **Aplicación Android (FinTrack):** `calendarAPP/` (paquete `com.fintrack.app`, entry: `app/src/main/java/com/fintrack/app/MainActivity.kt`, DI: `.../di/AppModule.kt`).
- **Scraper / Backend Auxiliar:** `calendar_backend/` (Flask, desplegado en Fly.io).
- **Esquema de Base de Datos y Migraciones:** 
  - `calendarWeb/docs/migrations/` (`00`–`05`, `03-*`)
  - Tríada de documentación: `docs/DATABASE-SCHEMA.md`, `docs/schema.sql` y `docs/MIGRATION-GUIDE.md`.

## 2. Tecnologías y Restricciones de Versión

### Android (`calendarAPP/`)
- **Versiones fijadas (NO modificar sin autorización):** Kotlin 2.4.0, AGP 8.7.3, compileSdk/targetSdk 35, minSdk 26, Java 17, Compose BOM 2024.06, Supabase BOM 3.7.0, Ktor 3.0.3, Koin 3.5.3.
- **Sin KSP / Hilt / Room:** KSP no tiene soporte para Kotlin 2.4.0; no reintroducir estas dependencias.
- **Supabase v3:** `auth-kt` expone `Auth`. Las inserciones se construyen con `buildJsonObject { put(...) }`, nunca con `Map<String, Any>`.
- **Navegación y Estado:** `DashboardViewModel` es único por Activity (`koinViewModel(viewModelStoreOwner = activity)`). Las pestañas inferiores navegan con `popUpTo + launchSingleTop`.
- **Cálculos Financieros en Cliente:**
  - `domain/PatternExpander`: expansión client-side de frecuencias `weekly`, `biweekly` (14 días), `monthly` (deriva día 31) y `yearly`.
  - `domain/MonthSummary`: resúmenes mensuales.
  - `domain/FlowEngine`: cadena de nodos n8n-style persistida en DataStore (`data/FlowStore.kt`).
  - `domain/BudgetPlanner` y `domain/GoalPlanner`: presupuestos y metas financieras calculadas en memoria del cliente.
- **Conversión de Fechas:** DatePicker ↔ epoch debe usar siempre `ZoneId.of("UTC")` en ambas direcciones.

### Web (`calendarWeb/`)
- Construido con Vanilla JS / HTML5 / Vanilla CSS.
- El build de Render (`build.sh`) genera `dist/js/config.js` inyectando `SUPABASE_URL`, `SUPABASE_ANON_KEY` y `SCRAPER_API_URL`.
- Localmente nunca comitear claves reales en archivos bajo seguimiento.

## 3. Cambios en Base de Datos y Persistencia

Si se altera el esquema de Supabase:
1. Crear el nuevo script de migración en `calendarWeb/docs/migrations/`.
2. Actualizar `docs/schema.sql` reflejando el DDL consolidado.
3. Documentar cambios en `docs/DATABASE-SCHEMA.md` y pasos en `docs/MIGRATION-GUIDE.md`.
4. Verificar sincronización con `TransactionEntity` y modelos en `calendarAPP/`.

## 4. Pruebas y Verificación

Antes de entregar cualquier ciclo:
- **Pruebas unitarias Android:**
  ```powershell
  .\gradlew.bat :app:testReleaseUnitTest
  ```
  Las siguientes suites deben pasar al 100%:
  - `PatternExpanderTest`
  - `NotificationParserTest`
  - `MonthSummaryTest`
  - `FlowEngineTest`
  - `BudgetPlannerTest`
  - `GoalPlannerTest`
- **Compilación de Android:**
  ```powershell
  .\gradlew.bat compileReleaseKotlin
  ```

## 5. CodeGraph

Utilizar la herramienta CodeGraph disponible en el repositorio (`.codegraph/`) para responder consultas sobre arquitectura, dependencias y blast radius:
- `codegraph explore "<consulta>"`
