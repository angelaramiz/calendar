#!/bin/bash
# build.sh — Genera config.js desde variables de entorno de Render

mkdir -p dist

# Copiar archivos de calendarWeb a dist
cp -r calendarWeb/* dist/

# Generar config.js con secrets de Render
mkdir -p dist/js
cat > dist/js/config.js << EOF
window.__ENV__ = {
  SUPABASE_URL: '${SUPABASE_URL}',
  SUPABASE_ANON_KEY: '${SUPABASE_ANON_KEY}'
};
window.SCRAPER_API_URL = '${SCRAPER_API_URL}';
EOF

echo "Build completado: dist/"
