# Plan de Rol Vigente — CalendarFinace

Este archivo resume cómo repartir el trabajo por áreas en el estado actual del repositorio.

## 1. Web de Producción (`calendarWeb/`)
- Interfaz estática vanilla en producción (Render).
- Vistas de calendario, flujo de caja, movimientos y sincronización con Supabase.
- Manejo de despliegues y artefactos OTA (`calendarfinance.apk` y `version.json`).

## 2. Android Nativo FinTrack (`calendarAPP/`)
- Módulo nativo en Kotlin + Jetpack Compose + Koin.
- Vistas principales: Inicio (Dashboard), Calendario, Flujos y Presupuesto.
- Motores de dominio: `PatternExpander`, `MonthSummary`, `FlowEngine`, `BudgetPlanner`, `GoalPlanner`.
- Detección de notificaciones financieras (`TransactionNotificationListener` + `NotificationParser`).
- Actualizaciones OTA mediante `OtaUpdateRepository` y `OtaInstaller`.

## 3. Scraper Auxiliar (`calendar_backend/`)
- Scraper Flask en Fly.io para ingesta de datos bancarios o tipos de cambio.
- Mantener aislado de las rutas principales legacy.

## 4. Supabase y Migraciones (`docs/` y `calendarWeb/docs/migrations/`)
- Tablas principales: `fintrack_transactions`, `movements`, `income_patterns`, `expense_patterns`, `app_versions`.
- Sincronización estricta de DDL en migraciones y esquemas documentados.

## Regla de Coordinación
Si una tarea afecta modelos de datos o entidades compartidas, el TPM y Dev Jr deben asegurar la sincronización exacta entre `calendarWeb/` y `calendarAPP/`.
