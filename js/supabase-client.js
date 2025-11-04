/**
 * Cliente de Supabase para Calendario Financiero
 * Producción: NO publiques tus credenciales reales en el repositorio.
 * Usa variables de entorno inyectadas en tiempo de despliegue (window.__ENV__) o un config local ignorado por git.
 */

// Importar desde CDN
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

// Intentar leer desde window.__ENV__ (de js/config.js) o usar placeholders
const DEFAULT_URL = 'https://YOUR-PROJECT-ref.supabase.co';
const DEFAULT_ANON_KEY = 'YOUR_SUPABASE_ANON_KEY';

const SUPABASE_URL = (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.SUPABASE_URL)
  ? window.__ENV__.SUPABASE_URL
  : DEFAULT_URL;

const SUPABASE_ANON_KEY = (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.SUPABASE_ANON_KEY)
  ? window.__ENV__.SUPABASE_ANON_KEY
  : DEFAULT_ANON_KEY;

// Crear cliente
export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

// Aviso si siguen los placeholders
if (SUPABASE_URL === DEFAULT_URL || SUPABASE_ANON_KEY === DEFAULT_ANON_KEY) {
  console.warn('⚠️ Configura tus credenciales de Supabase. Crea js/config.js desde js/config.example.js o edita js/supabase-client.js');
}

// Exportar configuración actual
export const SUPABASE_CONFIG = { url: SUPABASE_URL, anonKey: SUPABASE_ANON_KEY };
