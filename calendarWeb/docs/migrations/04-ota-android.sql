-- 04-ota-android.sql
-- OTA Update system: stores latest APK version info for Android auto-update

CREATE TABLE IF NOT EXISTS app_versions (
  clave TEXT PRIMARY KEY,
  valor JSONB NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION update_app_versions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_app_versions_updated_at ON app_versions;
CREATE TRIGGER trigger_app_versions_updated_at
  BEFORE UPDATE ON app_versions
  FOR EACH ROW EXECUTE FUNCTION update_app_versions_updated_at();

-- Seed: initial version for CalendarFinance Android app
INSERT INTO app_versions (clave, valor) VALUES
  ('app_version_calendarfinance', '{"versionCode": 1, "versionName": "1.0.0", "apkUrl": ""}')
ON CONFLICT (clave) DO NOTHING;

-- RLS: allow public read, allow public write (version table, not user data)
ALTER TABLE app_versions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public read app_versions" ON app_versions;
CREATE POLICY "Public read app_versions" ON app_versions
  FOR SELECT USING (true);

DROP POLICY IF EXISTS "Public insert app_versions" ON app_versions;
CREATE POLICY "Public insert app_versions" ON app_versions
  FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Public update app_versions" ON app_versions;
CREATE POLICY "Public update app_versions" ON app_versions
  FOR UPDATE USING (true);
