# Reglas del Proyecto

## Reglas Generales
1. **CodeGraph primero**: Antes de modificar codigo, usar CodeGraph para entender dependencias y call paths
2. **No secretos en codigo**: Las API keys y credenciales van en `config.js` (web) o variables de entorno (Android)
3. **RLS siempre**: Toda consulta a Supabase debe filtrar por `user_id`
4. **Soft delete**: Usar flags `active=false` o `archived=true`, nunca DELETE fisico
5. **Consistencia DB**: Los esquemas de calendarWeb y calendarAPP comparten la misma DB

## Convenciones de Codigo

### JavaScript (calendarWeb/calendarRN)
- ES6 modules con `import`/`export`
- Funciones async con `try/catch`
- Nombres de funciones: `camelCase`
- Nombres de archivos: `kebab-case.js`
- Usar SweetAlert2 para modales y alertas
- localStorage solo para cache de sesion y preferencias

### Kotlin (calendarAPP)
- Jetpack Compose para UI
- Koin para DI
- ViewModel + StateFlow para estado
- Repository pattern para acceso a datos
- Kotlinx Serialization para JSON
- Nombres de clase: PascalCase
- Nombres de funcion: camelCase

### SQL (migrations)
- Tablas: `snake_case` plural o descriptivo
- Columnas: `snake_case`
- UUID como PK
- RLS en todas las tablas
- Indices en columnas frecuentemente consultadas

## Seguridad
- No loggear tokens ni credenciales
- Validar input del usuario antes de queries
- Usar parametros en queries SQL (no concatenacion)
- Sanitizar datos antes de mostrar en UI
