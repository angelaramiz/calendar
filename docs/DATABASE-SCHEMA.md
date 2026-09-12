# Esquema de base de datos — CalendarFinace / FinTrack

Proyecto Supabase canónico: `https://ugtlxnrwfipoctckuvfd.supabase.co`.
Fuente de verdad del DDL: `calendarWeb/docs/migrations/` (archivos `00`–`05`, `03-*`).
Este documento es el índice vivo: cada cambio de DB debe actualizarlo (ver `MIGRATION-GUIDE.md`).

## Estado de verificación en vivo (2026-09-12, REST con publishable key, 1 tabla por petición)

Verificadas presentes (HTTP 200): `app_versions` (con fila `app_version_calendarfinance`), `users`, `fintrack_transactions`, `income_patterns`, `expense_patterns`, `movements`, `savings_transactions`, `loans`, `plans`, `alerts`, `envelopes`, `savings_patterns`, `expense_categories`, `product_wishlist`. Todas vacías (`[]`) salvo la fila OTA — la limpieza fue de DATOS, ninguna tabla fue eliminada.

> Nota metodológica: pedir varias tablas seguidas en la misma sesión devuelve 404 fantasma incluso para tablas que existen; verificar siempre de a una.
> NUNCA correr `00-reset-database.sql` ni `05-cleanup-all-data.sql` en producción (borran todo).

## Grupos de tablas

### 0. Auth y base
- **`users`** (`01-users-base.sql`): `id UUID PK → auth.users(id)`, `email UNIQUE NOT NULL`, `created_at/updated_at`. Trigger `on_auth_user_created` → `handle_new_user()` la sincroniza con Auth. RLS: cada usuario solo su fila.
- Extensión: `uuid-ossp` (`uuid_generate_v4()`).

### 1. FinTrack (app Android, prefijo `fintrack_`)
- **`fintrack_transactions`**: `id UUID PK DEFAULT gen_random_uuid()`, `user_id UUID NOT NULL → auth.users(id)` ⚠️ (referencia Auth directo, NO `users`), `amount NUMERIC >0`, `type TEXT` (`INCOME`/`EXPENSE` en mayúsculas — distinto de `movements`), `category TEXT DEFAULT 'Otros'`, `description TEXT DEFAULT ''`, `merchant TEXT NULL`, `timestamp BIGINT`, `latitude/longitude DOUBLE`, `address TEXT`, `source TEXT DEFAULT 'MANUAL'` (`AUTO` = listener), `created_at TIMESTAMPTZ DEFAULT now()`. RLS `own rows` (`auth.uid() = user_id`, ALL). La app nunca setea `user_id` en UI — lo pone el repositorio desde la sesión.

### 2. Calendario web V2 (`02-v2-complete-schema.sql`)
- **`income_patterns` / `expense_patterns`** (`expense` suma `is_essential BOOLEAN`): `id`, `user_id → users(id)`, `name`, `description`, `category`, `base_amount >0`, `frequency ∈ weekly|biweekly|monthly|yearly`, `interval ≥1`, `day_of_week 0..6` (0=dom), `day_of_month 1..31`, `start_date NOT NULL`, `end_date NULL=indefinido`, `active`, `created_at/updated_at` + trigger. RLS own-rows. ⚠️ `day_of_week/day_of_month` solo los usa ahorro; patrones parten de `start_date`.
- **`expense_pattern_income_sources`**: puente gasto→ingreso (`expense_pattern_id`, `income_pattern_id`, `allocation_type percent|fixed`, `allocation_value`, `UNIQUE` par, `percent ≤1`). RLS vía subconsultas de propiedad.
- **`movements`**: `id`, `user_id → users(id)`, `type ∈ ingreso|gasto` (minúsculas — distinto de FinTrack), `title NOT NULL`, `description`, `category`, `date DATE NOT NULL`, `expected_amount`, `confirmed_amount`, `confirmed`, `archived`, `income_pattern_id / expense_pattern_id` (FK SET NULL, CHECK solo-uno), `loan_id` (SET NULL). Índice único anti-duplicados `(user_id, date, COALESCE(pattern_ids)) WHERE hay patrón`. RLS own-rows.
- **`loans`**: `user_id`, `name`, `type ∈ given|received`, `counterparty NOT NULL`, `original_amount >0`, `remaining_amount ≥0`, `loan_date NOT NULL`, `due_date`, `status ∈ active|paid|defaulted|cancelled`. RLS own-rows.
- **`plans`** (metas/ahorro): `user_id`, `name`, `target_amount >0`, `current_amount ≥0`, `start_date`, `target_date`, `status ∈ active|completed|paused|cancelled`, `priority 1..10`, `completed_at`. Trigger auto-completa al alcanzar meta. RLS own-rows.
- **`plan_income_sources`**: puente plan→ingreso (`percent|fixed`, UNIQUE par). RLS por propiedad del plan.
- **`alerts`**: `user_id`, `alert_type`, `reference_type` (`movement|plan|loan…`), `reference_id`, `title NOT NULL`, `message`, `trigger_date`, `read`, `dismissed`, `created_at` (sin `updated_at`). RLS own-rows.
- **`envelopes`**: `user_id`, `name NOT NULL`, `description`, `category`, `budget_amount >0`, `current_amount`, `period_type ∈ weekly|biweekly|monthly|yearly DEFAULT monthly`, `active`. RLS own-rows. (No existe `envelope_transactions` en el DDL aunque `00-reset` la nombra — nunca se creó.)
- **`savings_patterns`**: `user_id`, `name NOT NULL`, `allocation_type ∈ percent|fixed|remainder`, `allocation_value` (validada según tipo), `target_amount`, `current_balance`, `priority 1..10`, `frequency/interval_value/day_of_week/day_of_month/start_date/end_date` opcionales, `active`. RLS own-rows.
- **`savings_pattern_income_sources`**: puente ahorro→ingreso (UNIQUE par). RLS por propiedad.
- **`savings_transactions`**: `user_id`, `savings_pattern_id NOT NULL (CASCADE)`, `transaction_type ∈ deposit|withdrawal`, `amount >0`, `movement_id / source_movement_id → movements (SET NULL)`, `source_income_pattern_id → income_patterns (SET NULL)`, `notes`, `transaction_date DEFAULT CURRENT_DATE`, `created_at` (sin `updated_at`). RLS own-rows.

### 3. Motor financiero (`03-financial-engine.sql`)
- **`expense_income_links`**: como el puente de §2 pero `user_id → auth.users(id)` directo, `allocation_type +percent|fixed|auto` (con `allocation_percent 0..100`, `fixed_amount`, `priority`, `notes`, `is_active`). ⚠️ Tabla paralela a `expense_pattern_income_sources` — no duplicar usos.
- **`financial_snapshots`**: `user_id`, `period_start/end`, `snapshot_type ∈ daily|weekly|monthly|yearly`, `income_data/expense_data/health_factors/allocation_data JSONB`, `total_income/expenses/balance`, `savings_rate`, `health_score 0..100`, UNIQUE(user,period,type).
- **`financial_recommendations`**: `user_id`, `recommendation_type`, `priority ∈ critical|high|medium|low`, `title NOT NULL`, `message NOT NULL`, `actions/context_data JSONB`, `status ∈ active|dismissed|completed|expired`, fechas, `hash NOT NULL` (anti-repetidas).
- **`expense_categories`**: catálogo global (SIN `user_id`): `name UNIQUE`, `display_name`, `expense_group ∈ necessities|wants|savings|debt|other`, `recommended_min/max_percent`, `icon`, `color`, `is_system`, `is_active`, `sort_order`. Con seeds del sistema en la migración.
- Funciones/vistas: `calculate_monthly_equivalent`, `get_user_monthly_totals`, `v_expense_income_summary`.

### 4. Wishlist (`03-product-wishlist.sql`, `03-product-price-monitoring.sql`)
`product_wishlist` (+ `target_amount/current_amount`, `plan_type short|medium|long`), `product_wishlist_income_sources`, `product_wishlist_contributions`, `product_price_history`, `product_plan_config`. Detalle completo en el archivo original.

### 5. OTA (`04-ota-android.sql`)
- **`app_versions`**: `clave TEXT PK`, `valor JSONB NOT NULL`, `created_at/updated_at` + trigger. Seed `('app_version_calendarfinance', '{"versionCode":1,...}')`. RLS **pública** (SELECT/INSERT/UPDATE `USING (true)`) — es la única tabla sin RLS por usuario. ⚠️ `valor` llega **doble-codificado** (JSON dentro de string); el cliente parsea dos veces.

### Vistas (§13 de `02-...`) y funciones (§14)
`movements_with_patterns`, `plans_with_progress`, `expense_patterns_with_income`, `confirmed_balance_summary`, `monthly_confirmed_balance`, `income_pattern_allocations`, `calculate_available_balance_for_savings`. Definiciones en el archivo original.

## Mapa de relaciones (FK)

```
auth.users ─┬─ users ─┬─ income_patterns ─┬─ expense_pattern_income_sources
            │         │                   ├─ plan_income_sources ── plans ── plan_income_sources
            │         │                   ├─ savings_pattern_income_sources ── savings_patterns ── savings_transactions
            │         │                   └─ movements.income_pattern_id
            │         ├─ expense_patterns ─┬─ expense_pattern_income_sources
            │         │                    └─ movements.expense_pattern_id
            │         ├─ movements ─┬─ (loan_id) loans
            │         │             └─ savings_transactions.(movement_id|source_movement_id)
            │         ├─ plans, alerts, envelopes, savings_patterns, savings_transactions
            │         └─ expense_income_links, financial_snapshots, financial_recommendations
            ├─ fintrack_transactions (directo, sin pasar por users)
            └─ (04) app_versions no tiene user_id
```

## Divergencias conocidas código ↔ DB
- `movements.difference`, `plan_id`, `envelope_id`, `is_loan_counterpart`: los menciona el JS (`calendar.js`, `pattern-scheduler.js`) pero NO existen en el DDL.
- `TransactionEntity.user_id` nunca lo setea la UI Android (lo pone el repositorio).
- Tipos: FinTrack `INCOME/EXPENSE` vs web `ingreso/gasto`.
