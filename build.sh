#!/bin/bash
# build.sh — Genera config.js desde variables de entorno de Render
# + versionado anti-caché: ?v=<commit> en assets locales y web-version.json

mkdir -p dist

# Copiar archivos de calendarWeb a dist
cp -r calendarWeb/* dist/

# Versión del deploy: hash corto del commit (o timestamp si no hay git)
VER="$(git rev-parse --short HEAD 2>/dev/null || date +%s)"
echo "Versión del deploy: $VER"

# Generar config.js con secrets de Render
mkdir -p dist/js
cat > dist/js/config.js << EOF
window.__ENV__ = {
  SUPABASE_URL: '${SUPABASE_URL}',
  SUPABASE_ANON_KEY: '${SUPABASE_ANON_KEY}'
};
window.SCRAPER_API_URL = '${SCRAPER_API_URL}';
EOF

# 1) HTML: ?v= en assets locales (js/, styles/, imágenes) + meta de versión.
#    Solo rutas relativas (sin "http", sin "?" previo): los CDN no se tocan.
#    El ?v va DENTRO de las comillas (si queda fuera el navegador lo ignora).
find dist -name '*.html' | while read -r f; do
  sed -i -E \
    -e 's#((src|href)="(\.\./)?(js|styles)/[^"?]+)(")#\1?v='"$VER"'\5#g' \
    -e 's#((src|href)="(\.\./)?[^":?]+\.(png|jpg|jpeg|webp|ico|svg))(")#\1?v='"$VER"'\5#g' \
    -e 's#(<head[^>]*>)#\1\n<meta name="app-version" content="'"$VER"'">#i' \
    "$f"
done

# 2) JS: ?v= en imports relativos (estáticos y dinámicos) para que el
#    grafo completo de módulos ES se renueve junto (si no, el entry nuevo
#    cargaría módulos viejos desde caché). Comillas simples y dobles por
#    separado (sin clases [\'\"]: en GNU sed \' es ancla y rompe el match).
find dist/js -name '*.js' | while read -r f; do
  sed -i -E \
    -e "s#from '(\\.\\.?/[^'?]*)'#from '\1?v=$VER'#g" \
    -e 's#from "(\.\.?/[^"?]*)"#from "\1?v='"$VER"'#g' \
    -e "s#import\([[:space:]]*'(\\.\\.?/[^'?]*)'#import('\1?v=$VER'#g" \
    -e 's#import\([[:space:]]*"(\.\.?/[^"]*)"#import("\1?v='"$VER"'#g' \
    "$f"
done

# 3) Manifiesto de versión para version-check.js
cat > dist/web-version.json << EOF
{"version":"$VER","builtAt":"$(date -u +%FT%TZ)"}
EOF

echo "Build completado: dist/ (v=$VER)"
