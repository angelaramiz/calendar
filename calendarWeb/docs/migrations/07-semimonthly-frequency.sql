-- ============================================================================
-- 07-semimonthly-frequency.sql — Soporte 'semimonthly' (Quincena 15/fin)
-- ============================================================================
-- La app FinTrack ofrece "Quincena 15/fin" (día 15 y último día del mes;
-- sábado/domingo se recorre al viernes bancario) y el servidor rechazaba
-- guardar esos recurrentes (23514 ..._frequency_check).
--
-- Cómo aplicarlo: Supabase Dashboard → SQL Editor → pegar y Run.
-- ============================================================================

ALTER TABLE income_patterns DROP CONSTRAINT IF EXISTS income_patterns_frequency_check;
ALTER TABLE income_patterns ADD CONSTRAINT income_patterns_frequency_check
  CHECK (frequency IN ('weekly', 'biweekly', 'bimonthly', 'monthly', 'yearly', 'semimonthly'));

ALTER TABLE expense_patterns DROP CONSTRAINT IF EXISTS expense_patterns_frequency_check;
ALTER TABLE expense_patterns ADD CONSTRAINT expense_patterns_frequency_check
  CHECK (frequency IN ('weekly', 'biweekly', 'bimonthly', 'monthly', 'yearly', 'semimonthly'));

ALTER TABLE savings_patterns DROP CONSTRAINT IF EXISTS savings_patterns_frequency_check;
ALTER TABLE savings_patterns ADD CONSTRAINT savings_patterns_frequency_check
  CHECK (frequency IN ('weekly', 'biweekly', 'bimonthly', 'monthly', 'yearly', 'semimonthly'));
