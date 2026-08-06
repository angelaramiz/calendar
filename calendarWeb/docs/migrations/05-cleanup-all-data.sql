-- 05-cleanup-all-data.sql
-- Limpieza total de datos de usuario para partir desde 0
-- ADVERTENCIA: Elimina TODOS los datos de usuarios. No se puede deshacer.

-- ═══ PASO 1: Eliminar datos financieros ═══

-- Ahorros (depende de patterns)
DELETE FROM savings_transactions;
DELETE FROM savings_pattern_income_sources;
DELETE FROM savings_patterns;

-- Planes (depende de income_patterns)
DELETE FROM plan_income_sources;
DELETE FROM plans;

-- Gastos vinculados a ingresos
DELETE FROM expense_pattern_income_sources;

-- Movimientos (depende de patterns y loans)
DELETE FROM movements;

-- Patrones
DELETE FROM income_patterns;
DELETE FROM expense_patterns;

-- Prestamos
DELETE FROM loans;

-- Envelopes
DELETE FROM envelopes;

-- Alertas
DELETE FROM alerts;

-- ═══ PASO 2: Financial Engine ═══

DELETE FROM expense_income_links;
DELETE FROM financial_snapshots;
DELETE FROM financial_recommendations;
DELETE FROM expense_categories;

-- ═══ PASO 3: Productos / Wishlist ═══

DELETE FROM product_wishlist;

-- ═══ PASO 4: Usuarios ═══

-- Eliminar usuarios de auth (requiere service_role key)
-- DELETE FROM auth.users WHERE id IN (SELECT id FROM users);

-- Eliminar perfiles
DELETE FROM users;

-- ═══ VERIFICACION ═══

SELECT 'Limpieza completada' AS status;
