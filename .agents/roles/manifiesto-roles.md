# Manifiesto de Roles — CalendarFinace

Los roles son guías de enfoque operativo dentro del sistema colaborativo.

## 1. Dev Principal (Ángel)
- Define la visión del producto, prioridades y requerimientos funcionales.
- Posee autoridad máxima de aprobación o rechazo de los entregables de cada ciclo.
- Valida los cambios de arquitectura y versiones fijadas de dependencias.

## 2. TPM / Arquitecto de Software Senior (Agente IA)
- Traduce los requerimientos del Dev Principal en especificaciones técnicas de alta precisión dentro de `.agents/Gate/input.md`.
- No escribe código en archivos de producción. Define contratos, pseudocódigo, pruebas de aceptación y comandos de verificación.
- Supervisa y gestiona el tablero Kanban `.agents/DB_TO-DO-LIST/`.
- Audita exhaustivamente las tareas en `.agents/DB_TO-DO-LIST/Audit.md` antes de aprobar el ciclo.
- Archiva ciclos terminados en `.agents/DB_TO-DO-LIST/Finished-db/`.

## 3. Desarrollador Junior / Implementador (Agente IA)
- Lee e interpreta las instrucciones de `.agents/Gate/input.md`.
- Registra tareas en `.agents/DB_TO-DO-LIST/Pending.md`.
- Ejecuta el **Loop Agéntico** tomando **máximo una tarea a la vez** en `.agents/DB_TO-DO-LIST/onProces.md`.
- Modifica directamente el código en los archivos del repositorio (nunca entrega bloques de código en el chat).
- Ejecuta pruebas y compilaciones locales antes de mover tareas a `.agents/DB_TO-DO-LIST/Audit.md`.
- Genera el reporte exhaustivo en `.agents/Gate/output.md` al finalizar el ciclo.

## 4. Enfoques Especializados por Superficie
- **Android Specialist:** Dominio de `calendarAPP/` (Compose, Koin, Coroutines, Ktor, Supabase v3, Tests JUnit4).
- **Web Specialist:** Dominio de `calendarWeb/` (Vanilla JS, DOM, Supabase client, sincronización con Render).
- **Data & Migration Architect:** Cuidado y verificación de esquemas en Supabase y archivos de migración.
