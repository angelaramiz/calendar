# Reglas de Seguridad — CalendarFinace

1. **Gestión de Secretos:**
   - Prohibido commitear archivos `.jks`, `local.properties`, o claves de API en el código fuente.
   - Proteger los tokens de despliegue incrustados en `calendarAPP/scripts/release.ps1` (no imprimirlos en logs ni modificarlos sin autorización).
2. **Supabase RLS y Sesiones:**
   - La tabla `fintrack_transactions` debe tener RLS activo por `user_id`.
   - La UI nunca debe inyectar `user_id` arbitrariamente; debe provenir de la sesión autenticada en el repositorio.
3. **Manejo Seguro de Notificaciones:**
   - Las transacciones leídas desde notificaciones (`TransactionNotificationListener`) solo deben registrarse si existe una sesión de usuario válida.
   - Las palabras clave de deduplicación y exclusión de promociones deben aplicarse antes del parsing de montos.
4. **Instalación de Actualizaciones:**
   - Las descargas de nuevas versiones se validan contra el número de versión (`versionCode`) y se instalan usando `FileProvider` con permisos restringidos.
