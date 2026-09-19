# Guía de cambios y migración de base de datos

## 1. Cómo planificar un cambio en la DB

1. Crea la migración en `calendarWeb/docs/migrations/` con el siguiente número: `NN-nombre-corto.sql` (hoy vamos en `06`). Solo `CREATE/ALTER` con `IF NOT EXISTS`; prohibido `DROP/DELETE` fuera de scripts de reset nommbrados.
2. Pruébala en una rama de Supabase o proyecto de staging antes de producción.
3. Actualiza en el mismo commit: `docs/DATABASE-SCHEMA.md` (tabla/columna/RLS/relación) y, si es tabla usada por las apps, `docs/schema.sql`.
4. Si el cambio afecta columnas que lee la app (`TransactionEntity`, `PatternRow`, `MovementRow`), actualiza el modelo en ambas plataformas a la vez.

## 2. Backup

- **Código**: `docs/schema.sql` recrea estructura + RLS + seed OTA (no datos de usuario).
- **Datos**: Supabase Dashboard → Database → Backups (automáticos según plan) o `pg_dump` con la connection string (modo session, puerto 5432). Orden de importación por FKs: `users` → patrones → puentes → `loans` → `movements` → `plans` → resto → `fintrack_transactions` → `app_versions`.
- Antes de cualquier migración con riesgo: snapshot manual + export CSV de `fintrack_transactions` y `movements` (son el dinero real del usuario).

## 3. Cambio de proveedor (salir de Supabase)

Mapa de reemplazos:

| Supabase | Postgres propio / otro proveedor |
|---|---|
| `auth.users(id)` + `auth.uid()` | Tabla `users` propia + columna `owner_id`; RLS con `current_setting('app.user_id')` o mover el filtro a la API |
| `uuid-ossp` / `gen_random_uuid()` | `pgcrypto` (incluido en casi todo Postgres) |
| RLS policies | Mantenerlas si hay Postgres (recomendado) o reimplementar `user_id = X` en cada query del backend |
| `SupabaseClientProvider` (app) | Cambiar base URL + adaptar `TransactionRepository`/`PatternRepository` al nuevo API (REST propio o PostgREST auto-hospedado) |
| `js/supabase-client.js` + `build.sh` (`SUPABASE_URL/ANON_KEY`) | Nuevas env vars del backend propio |
| Realtime / Auth | Auth propio (o Auth0/Clerk) + `handle_new_user` → webhook/trigger equivalente |
| `app_versions` pública | Cualquier hosting estático + JSON (el cliente solo hace GET y compara `versionCode`) |

`docs/schema.sql` está escrito en Postgres estándar salvo el bloque `auth.*`: para migrar, reemplaza `REFERENCES auth.users(id)` por tu tabla de usuarios y `auth.uid()` por tu mecanismo de sesión. Las vistas/funciones del §13–14 de `02-v2-complete-schema.sql` son SQL estándar y se portan tal cual.

## 4. Reglas de seguridad al operar

- Nunca `00-reset-database.sql` ni `05-cleanup-all-data.sql` en producción.
- `app_versions` tiene RLS pública a propósito (lectura anónima para OTA); no le pongas RLS por usuario o rompes las actualizaciones.
- `expense_categories` es catálogo global sin `user_id`: no le agregues RLS por usuario.
- `fintrack_transactions` referencia `auth.users` directo (no `users`): si unificas a `users`, actualiza `TransactionRepository` y el SQL a la vez.
