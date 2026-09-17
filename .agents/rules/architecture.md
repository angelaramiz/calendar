# Reglas de Arquitectura — CalendarFinace

1. **Separación de Superficies:**
   - `calendarWeb/`: Aplicación web estática en producción.
   - `calendarAPP/`: Nueva versión nativa en Android (`com.fintrack.app`).
   - `calendar_backend/`: Scraper de datos en Flask desplegado en Fly.io.
   - `calendarRN/`: Workspace en desuso; ignorarlo.

2. **Cálculos en Cliente vs Backend:**
   - Proyecciones y expansión de recurrencias (`PatternExpander`, `MonthSummary`, `FlowEngine`, `BudgetPlanner`, `GoalPlanner`) se procesan en el cliente para mantener consistencia idéntica con la versión web, sin tablas adicionales en Supabase.
   - Persistencia local de configuración de flujos y filtros mediante DataStore (`FlowStore`, `AppFilterStore`).

3. **Arquitectura Android FinTrack:**
   - Arquitectura MVVM con Jetpack Compose y Koin.
   - Inserciones en Supabase BOM 3.7.0 utilizando `buildJsonObject { put(...) }`.
   - `DashboardViewModel` tiene ciclo de vida por Activity, nunca por entrada individual del back-stack de navegación.

4. **Persistencia y Migraciones:**
   - Tablas canónicas: `income_patterns`, `expense_patterns`, `movements`, `fintrack_transactions`.
   - Todo cambio DDL requiere sincronización simultánea en `calendarWeb/docs/migrations/`, `docs/schema.sql` y `docs/DATABASE-SCHEMA.md`.
