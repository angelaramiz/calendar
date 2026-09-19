-- schema.sql — Script consolidado de RECREACIÓN (solo crea, nunca borra).
-- Proyecto: ugtlxnrwfipoctckuvfd (Supabase / Postgres).
-- Uso: SQL Editor → pegar → Run. Seguro de re-ejecutar (todo IF NOT EXISTS).
-- Detalle columna-por-columna y vistas/funciones: ver DATABASE-SCHEMA.md y
-- calendarWeb/docs/migrations/ (02, 03-*, 04). Wishlist completa: 03-product-wishlist.sql.

-- ============ 0. Extensiones ============
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============ 1. users + sync con Auth ============
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view own data" ON users;
CREATE POLICY "Users can view own data" ON users FOR SELECT USING (auth.uid() = id);
DROP POLICY IF EXISTS "Users can update own data" ON users;
CREATE POLICY "Users can update own data" ON users FOR UPDATE USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.users (id, email) VALUES (NEW.id, NEW.email)
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Trigger genérico updated_at (equivalente a los per-tabla de las migraciones)
CREATE OR REPLACE FUNCTION public.touch_updated_at()
RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END; $$ LANGUAGE plpgsql;

-- ============ 2. Patrones ============
CREATE TABLE IF NOT EXISTS income_patterns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL, description TEXT, category VARCHAR(100),
    base_amount DECIMAL(12,2) NOT NULL CHECK (base_amount > 0),
    frequency VARCHAR(20) NOT NULL CHECK (frequency IN ('weekly','biweekly','bimonthly','monthly','yearly')),
    interval INTEGER DEFAULT 1 CHECK (interval >= 1),
    day_of_week INTEGER CHECK (day_of_week BETWEEN 0 AND 6),
    day_of_month INTEGER CHECK (day_of_month BETWEEN 1 AND 31),
    start_date DATE NOT NULL, end_date DATE,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS expense_patterns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL, description TEXT, category VARCHAR(100),
    base_amount DECIMAL(12,2) NOT NULL CHECK (base_amount > 0),
    frequency VARCHAR(20) NOT NULL CHECK (frequency IN ('weekly','biweekly','bimonthly','monthly','yearly')),
    interval INTEGER DEFAULT 1 CHECK (interval >= 1),
    day_of_week INTEGER CHECK (day_of_week BETWEEN 0 AND 6),
    day_of_month INTEGER CHECK (day_of_month BETWEEN 1 AND 31),
    start_date DATE NOT NULL, end_date DATE,
    active BOOLEAN DEFAULT true,
    is_essential BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_income_patterns_user ON income_patterns(user_id);
CREATE INDEX IF NOT EXISTS idx_income_patterns_active ON income_patterns(user_id, active);
CREATE INDEX IF NOT EXISTS idx_expense_patterns_user ON expense_patterns(user_id);
CREATE INDEX IF NOT EXISTS idx_expense_patterns_active ON expense_patterns(user_id, active);

CREATE TABLE IF NOT EXISTS expense_pattern_income_sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    expense_pattern_id UUID NOT NULL REFERENCES expense_patterns(id) ON DELETE CASCADE,
    income_pattern_id UUID NOT NULL REFERENCES income_patterns(id) ON DELETE CASCADE,
    allocation_type VARCHAR(20) NOT NULL CHECK (allocation_type IN ('percent','fixed')),
    allocation_value DECIMAL(12,4) NOT NULL CHECK (allocation_value > 0),
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(expense_pattern_id, income_pattern_id),
    CONSTRAINT valid_expense_percent CHECK (allocation_type != 'percent' OR (allocation_value > 0 AND allocation_value <= 1))
);

-- ============ 3. Movimientos y préstamos ============
CREATE TABLE IF NOT EXISTS loans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL, description TEXT,
    type VARCHAR(20) NOT NULL CHECK (type IN ('given','received')),
    counterparty VARCHAR(255) NOT NULL,
    original_amount DECIMAL(12,2) NOT NULL CHECK (original_amount > 0),
    remaining_amount DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (remaining_amount >= 0),
    loan_date DATE NOT NULL, due_date DATE,
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active','paid','defaulted','cancelled')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_loans_user ON loans(user_id);

CREATE TABLE IF NOT EXISTS movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('ingreso','gasto')),
    title VARCHAR(255) NOT NULL, description TEXT, category VARCHAR(100),
    date DATE NOT NULL,
    expected_amount DECIMAL(12,2) CHECK (expected_amount >= 0),
    confirmed_amount DECIMAL(12,2) CHECK (confirmed_amount >= 0),
    confirmed BOOLEAN DEFAULT false, archived BOOLEAN DEFAULT false,
    income_pattern_id UUID REFERENCES income_patterns(id) ON DELETE SET NULL,
    expense_pattern_id UUID REFERENCES expense_patterns(id) ON DELETE SET NULL,
    loan_id UUID REFERENCES loans(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT movement_single_pattern CHECK (income_pattern_id IS NULL OR expense_pattern_id IS NULL)
);
CREATE INDEX IF NOT EXISTS idx_movements_user ON movements(user_id);
CREATE INDEX IF NOT EXISTS idx_movements_date ON movements(user_id, date);
CREATE UNIQUE INDEX IF NOT EXISTS idx_movements_unique_pattern_date
ON movements(user_id, date, COALESCE(income_pattern_id, '00000000-0000-0000-0000-000000000000'::uuid), COALESCE(expense_pattern_id, '00000000-0000-0000-0000-000000000000'::uuid))
WHERE income_pattern_id IS NOT NULL OR expense_pattern_id IS NOT NULL;

-- ============ 4. Planes, alertas, sobres, ahorro ============
CREATE TABLE IF NOT EXISTS plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL, description TEXT, category VARCHAR(100),
    target_amount DECIMAL(12,2) NOT NULL CHECK (target_amount > 0),
    current_amount DECIMAL(12,2) DEFAULT 0 CHECK (current_amount >= 0),
    start_date DATE, target_date DATE,
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active','completed','paused','cancelled')),
    priority INTEGER DEFAULT 5 CHECK (priority BETWEEN 1 AND 10),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS plan_income_sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    income_pattern_id UUID NOT NULL REFERENCES income_patterns(id) ON DELETE CASCADE,
    allocation_type VARCHAR(20) NOT NULL CHECK (allocation_type IN ('percent','fixed')),
    allocation_value DECIMAL(12,4) NOT NULL CHECK (allocation_value > 0),
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plan_id, income_pattern_id),
    CONSTRAINT valid_plan_percent CHECK (allocation_type != 'percent' OR (allocation_value > 0 AND allocation_value <= 1))
);
CREATE TABLE IF NOT EXISTS alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50), reference_id UUID,
    title VARCHAR(255) NOT NULL, message TEXT, trigger_date DATE,
    read BOOLEAN DEFAULT false, dismissed BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_alerts_user ON alerts(user_id);
CREATE TABLE IF NOT EXISTS envelopes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL, description TEXT, category VARCHAR(100),
    budget_amount DECIMAL(12,2) NOT NULL CHECK (budget_amount > 0),
    current_amount DECIMAL(12,2) DEFAULT 0,
    period_type VARCHAR(20) DEFAULT 'monthly' CHECK (period_type IN ('weekly','biweekly','monthly','yearly')),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS savings_patterns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL, description TEXT,
    allocation_type VARCHAR(20) NOT NULL CHECK (allocation_type IN ('percent','fixed','remainder')),
    allocation_value DECIMAL(12,4) CHECK (
        (allocation_type = 'remainder') OR
        (allocation_type = 'percent' AND allocation_value > 0 AND allocation_value <= 1) OR
        (allocation_type = 'fixed' AND allocation_value > 0)),
    target_amount DECIMAL(12,2), current_balance DECIMAL(12,2) DEFAULT 0,
    priority INTEGER DEFAULT 5 CHECK (priority BETWEEN 1 AND 10),
    frequency VARCHAR(20) CHECK (frequency IN ('weekly','biweekly','bimonthly','monthly','yearly')),
    interval_value INTEGER DEFAULT 1 CHECK (interval_value >= 1),
    day_of_week INTEGER CHECK (day_of_week BETWEEN 0 AND 6),
    day_of_month INTEGER CHECK (day_of_month BETWEEN 1 AND 31),
    start_date DATE, end_date DATE, active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS savings_pattern_income_sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    savings_pattern_id UUID NOT NULL REFERENCES savings_patterns(id) ON DELETE CASCADE,
    income_pattern_id UUID NOT NULL REFERENCES income_patterns(id) ON DELETE CASCADE,
    allocation_type VARCHAR(20) CHECK (allocation_type IN ('percent','fixed','remainder')),
    allocation_value DECIMAL(12,4),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(savings_pattern_id, income_pattern_id)
);
CREATE TABLE IF NOT EXISTS savings_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    savings_pattern_id UUID NOT NULL REFERENCES savings_patterns(id) ON DELETE CASCADE,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('deposit','withdrawal')),
    amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    movement_id UUID REFERENCES movements(id) ON DELETE SET NULL,
    source_income_pattern_id UUID REFERENCES income_patterns(id) ON DELETE SET NULL,
    source_movement_id UUID REFERENCES movements(id) ON DELETE SET NULL,
    notes TEXT, transaction_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_savings_transactions_user ON savings_transactions(user_id);

-- ============ 5. Motor financiero ============
CREATE TABLE IF NOT EXISTS expense_income_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expense_pattern_id UUID NOT NULL REFERENCES expense_patterns(id) ON DELETE CASCADE,
    income_pattern_id UUID NOT NULL REFERENCES income_patterns(id) ON DELETE CASCADE,
    allocation_type VARCHAR(20) DEFAULT 'percent' CHECK (allocation_type IN ('percent','fixed','auto')),
    allocation_percent DECIMAL(5,2) CHECK (allocation_percent >= 0 AND allocation_percent <= 100),
    fixed_amount DECIMAL(12,2) CHECK (fixed_amount >= 0),
    priority INTEGER DEFAULT 1, notes TEXT, is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_expense_income_link UNIQUE (expense_pattern_id, income_pattern_id)
);
CREATE TABLE IF NOT EXISTS financial_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    period_start DATE NOT NULL, period_end DATE NOT NULL,
    snapshot_type VARCHAR(20) DEFAULT 'monthly' CHECK (snapshot_type IN ('daily','weekly','monthly','yearly')),
    income_data JSONB DEFAULT '{}', expense_data JSONB DEFAULT '{}',
    total_income DECIMAL(12,2) DEFAULT 0, total_expenses DECIMAL(12,2) DEFAULT 0,
    balance DECIMAL(12,2) DEFAULT 0, savings_rate DECIMAL(5,2) DEFAULT 0,
    health_score INTEGER CHECK (health_score >= 0 AND health_score <= 100),
    health_factors JSONB DEFAULT '[]', allocation_data JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_snapshot_period UNIQUE (user_id, period_start, period_end, snapshot_type)
);
CREATE TABLE IF NOT EXISTS financial_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    recommendation_type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) DEFAULT 'medium' CHECK (priority IN ('critical','high','medium','low')),
    title VARCHAR(200) NOT NULL, message TEXT NOT NULL, actions JSONB DEFAULT '[]',
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active','dismissed','completed','expired')),
    context_data JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(), expires_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
    hash VARCHAR(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS expense_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE, display_name VARCHAR(100) NOT NULL,
    expense_group VARCHAR(20) DEFAULT 'other' CHECK (expense_group IN ('necessities','wants','savings','debt','other')),
    recommended_min_percent DECIMAL(5,2) DEFAULT 0,
    recommended_max_percent DECIMAL(5,2) DEFAULT 100,
    icon VARCHAR(50), color VARCHAR(20),
    is_system BOOLEAN DEFAULT false, is_active BOOLEAN DEFAULT true, sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);
-- (Seeds de expense_categories: ver 03-financial-engine.sql §insert.)

-- ============ 6. FinTrack (Android) ============
CREATE TABLE IF NOT EXISTS fintrack_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    amount NUMERIC NOT NULL CHECK (amount > 0),
    type TEXT NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    category TEXT NOT NULL DEFAULT 'Otros',
    description TEXT NOT NULL DEFAULT '',
    merchant TEXT,
    timestamp BIGINT NOT NULL,
    latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, address TEXT,
    source TEXT NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE fintrack_transactions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "own rows" ON fintrack_transactions;
CREATE POLICY "own rows" ON fintrack_transactions
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ============ 7. OTA ============
CREATE TABLE IF NOT EXISTS app_versions (
    clave TEXT PRIMARY KEY,
    valor JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
DROP TRIGGER IF EXISTS trigger_app_versions_updated_at ON app_versions;
CREATE TRIGGER trigger_app_versions_updated_at BEFORE UPDATE ON app_versions
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();
ALTER TABLE app_versions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Public read app_versions" ON app_versions;
CREATE POLICY "Public read app_versions" ON app_versions FOR SELECT USING (true);
DROP POLICY IF EXISTS "Public insert app_versions" ON app_versions;
CREATE POLICY "Public insert app_versions" ON app_versions FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS "Public update app_versions" ON app_versions FOR UPDATE USING (true);
INSERT INTO app_versions (clave, valor) VALUES
    ('app_version_calendarfinance', '{"versionCode": 1, "versionName": "1.0.0", "apkUrl": ""}')
ON CONFLICT (clave) DO NOTHING;

-- ============ 8. RLS own-rows + triggers updated_at (V2) ============
-- Aplica a: income_patterns, expense_patterns, loans, movements, plans,
-- alerts, envelopes, savings_patterns, savings_transactions.
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'income_patterns','expense_patterns','loans','movements','plans',
        'alerts','envelopes','savings_patterns','savings_transactions'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t || '_select', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR SELECT USING (auth.uid() = user_id)', t || '_select', t);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t || '_insert', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR INSERT WITH CHECK (auth.uid() = user_id)', t || '_insert', t);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t || '_update', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id)', t || '_update', t);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t || '_delete', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR DELETE USING (auth.uid() = user_id)', t || '_delete', t);
        IF t <> 'alerts' AND t <> 'savings_transactions' THEN
            EXECUTE format('DROP TRIGGER IF EXISTS trg_touch_updated_at ON %I', t);
            EXECUTE format('CREATE TRIGGER trg_touch_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at()', t);
        END IF;
    END LOOP;
END $$;
-- Puentes con RLS por propiedad (definición exacta en 02-v2-complete-schema.sql):
-- expense_pattern_income_sources, plan_income_sources, savings_pattern_income_sources.
