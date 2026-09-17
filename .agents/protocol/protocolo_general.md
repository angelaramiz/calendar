# Protocolo General de Agentes y Desarrollo — CalendarFinace

## Objetivo

Asegurar que cualquier cambio se haga contra el sistema real, preserve la compatibilidad entre superficies (Web, Android y Backend Scraper) y mantenga trazabilidad formal para el siguiente agente.

## Secuencia obligatoria

1. Leer `.agents/Gate/input.md` o la memoria operativa correspondiente (`roles/tpm-ass/` o `roles/jr/`).
2. Revisar la parte afectada del código en disco.
3. Confirmar impacto cruzado entre superficies:
   - `calendarWeb/` (Static Web App en producción desplegada en Render)
   - `calendarAPP/` (App Android FinTrack en Kotlin + Jetpack Compose + Koin)
   - `calendar_backend/` (Scraper Flask en Fly.io)
   - `docs/` y `calendarWeb/docs/migrations/` (Esquemas y migraciones de Supabase)
4. Registrar o actualizar las tareas en el tablero Kanban (`.agents/DB_TO-DO-LIST/`).
5. Implementar mediante el ciclo agéntico (1 tarea activa en `onProces.md`).
6. Verificar y ejecutar las pruebas automáticas / builds pertinentes.
7. Documentar el cierre en `.agents/Gate/output.md` y actualizar la memoria de rol.

## Reglas de ejecución

### 1. Investigar antes de tocar
- Consultar `.codegraph/` mediante `codegraph explore "<consulta>"` antes de realizar búsquedas amplias o exploraciones de impacto.
- Localizar símbolos, modelos y tablas afectadas sin asumir rutas ficticias.

### 2. Respetar superficies separadas
- `calendarWeb/` es una web app estática servida en producción; se desarrolla en Vanilla JS/CSS/HTML sin TailwindCSS salvo indicación expresa.
- `calendarAPP/` es la versión Android nativa (paquete `com.fintrack.app`). Requiere respetar versiones fijadas: Kotlin 2.4.0, AGP 8.7.3, compile/targetSdk 35, Java 17, Compose BOM 2024.06.
- `calendar_backend/` es un scraper Flask; no tocar los archivos legacy de la raíz sin autorización.
- `calendarRN/` es un workspace obsoleto; ignorarlo salvo instrucción explícita.

### 3. Consistencia de modelos y datos
- Si se modifica el modelo de datos (`TransactionEntity`, movimientos, patrones), verificar la coherencia entre Web, Android y Supabase.
- Toda modificación de base de datos requiere actualizar la tríada documental en `docs/`: `DATABASE-SCHEMA.md`, `schema.sql` y `MIGRATION-GUIDE.md`, además del archivo de migración en `calendarWeb/docs/migrations/`.

### 4. Codificación y formato de archivos
- Los archivos fuente de Kotlin deben guardarse estrictamente en **UTF-8 sin BOM**. Evitar comandos PowerShell que inserten BOM.
- Nomenclatura: Kotlin `PascalCase` para clases y `camelCase` para funciones; JavaScript `kebab-case.js`; Base de datos `snake_case`.
- Toda interacción y documentación debe realizarse en **español**.

### 5. Validación y Criterio de Calidad
- Cambios Android: verificar compilación con `.\gradlew.bat compileReleaseKotlin` en `calendarAPP/` y mantener las suites de tests unitarios al 100% verdes.
- Cambios Web: verificar que la lógica no rompa dependencias del navegador ni llamadas a Supabase.
- Un cambio no está terminado si:
  - Rompe contratos entre cliente y Supabase.
  - Genera archivos con codificación incorrecta (BOM en Kotlin).
  - Deja tareas sin auditar en `.agents/DB_TO-DO-LIST/`.
  - Omite actualizar la documentación viva del proyecto.
