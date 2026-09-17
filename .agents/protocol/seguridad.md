# Protocolo de Seguridad y Gestión de Credenciales — CalendarFinace

## 1. Protección de Credenciales y Llaves
- **Nunca comitear credenciales:** No commitear archivos `local.properties`, keystores (`.jks`), tokens de acceso o claves secretas de producción.
- **Pipeline de Release:** El script `calendarAPP/scripts/release.ps1` contiene credenciales operativas de despliegue. No imprimir, copiar ni rotar estas llaves sin aprobación explícita de Ángel (Dev Principal).
- **Entorno Web:** `build.sh` inyecta variables de entorno (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SCRAPER_API_URL`) en `config.js` durante el despliegue en Render. No registrar variables de entorno de producción en archivos locales bajo Git.

## 2. Autenticación y Autorización en Supabase
- **RLS (Row Level Security):** La tabla de movimientos Android `fintrack_transactions` tiene RLS activado por `user_id`.
- **Regla del Repositorio:** La UI nunca asigna `TransactionEntity.user_id`. Es responsabilidad estricta de `TransactionRepository` adjuntar el `user_id` de la sesión autenticada.
- **Sesión Requerida:** Sin sesión activa, `DashboardViewModel` emite `needsLogin=true`. El servicio de escucha de notificaciones (`TransactionNotificationListener`) omite silenciosamente el guardado si no existe sesión válida.

## 3. Actualizaciones OTA (Over-The-Air)
- Las actualizaciones OTA consultan la tabla remota `app_versions` con clave `app_version_calendarfinance` (cuyo campo `valor` es JSON con doble serialización).
- La descarga e instalación de APKs se realiza mediante `FileProvider` (`res/xml/file_paths.xml`) y requiere la concesión explícita del permiso `REQUEST_INSTALL_PACKAGES` por parte del usuario.

## 4. Permisos del Dispositivo en Android
- **Permisos de Notificaciones:** `MainActivity` solicita `POST_NOTIFICATIONS` en Android 13+.
- **Acceso a Notificaciones Bancarias:** La escucha de notificaciones de transacciones (`TransactionNotificationListener`) requiere activación por parte del usuario mediante `ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- La lista de apps permitidas (`AppFilterStore`) no utiliza `QUERY_ALL_PACKAGES`; se gestiona en DataStore mediante paquetes detectados recientemente o preseleccionados.
