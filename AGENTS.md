# AGENTS.md — CalendarFinace

## Stack Tecnologico

| Capa | Tecnologia |
|------|-----------|
| **Web** | Vanilla JS + HTML/CSS + Supabase |
| **Mobile** | Kotlin + Jetpack Compose + Supabase |
| **Backend** | Supabase PostgreSQL + Flask (scraper) |
| **Scraper** | Python Flask + Selenium (Fly.io) |
| **CI/CD** | GitHub Actions (gh-pages, deploy) |
| **AI Index** | CodeGraph (SQLite) |

## Estructura del Proyecto

```
CalendarFinace/
├── calendarWeb/        # App web (produccion)
├── calendarRN/         # Copia workspace (migracion planeada)
├── calendarAPP/        # App Android nativa (Kotlin)
├── .codegraph/         # Indice semantico de codigo
└── .agents/            # Memoria y contexto IA
```

## Convenciones

### Nombrado
- **Archivos JS**: `kebab-case.js` (ej. `product-wishlist.js`)
- **Archivos SQL**: `NN-nombre-descripcion.sql` (ej. `01-users-base.sql`)
- **Clases Kotlin**: `PascalCase` (ej. `MovementRepository`)
- **Funciones JS**: `camelCase` (ej. `loadMovements`)
- **Tablas DB**: `snake_case` (ej. `income_patterns`)

### Base de Datos (Supabase)
- Todas las tablas tienen RLS con `auth.uid() = user_id`
- UUID como PK via `uuid_generate_v4()`
- Soft-delete con flags `active`/`archived`
- Triggers `updated_at` automaticos

### Arquitectura de Datos
```
Pattern → Projection → Movement
```
1. **Patterns**: Templates recurrentes (income_patterns, expense_patterns)
2. **Projections**: Ocurrencias generadas dinamicamente
3. **Movements**: Transacciones confirmadas

## Comandos

| Comando | Descripcion |
|---------|-------------|
| `codegraph explore "<query>"` | Busqueda semantica en codigo |
| `codegraph index` | Reindexar proyecto |
| `cd calendarWeb && python -m http.server` | Servir web local |
| `cd calendarWeb/api-example && python app.py` | Scraper API local |

## Rutas Importantes

| Ruta | Descripcion |
|------|-------------|
| `calendarWeb/index.html` | Entry point web (login) |
| `calendarWeb/js/main.js` | Orquestador principal |
| `calendarWeb/js/config.js` | Config (Supabase keys) |
| `calendarAPP/app/.../MainActivity.kt` | Entry point Android |
| `calendarWeb/docs/migrations/` | Schema SQL |

## Estado Actual

| Item | Estado |
|------|--------|
| App Web | Produccion |
| App Android (calendarAPP) | Nucleo completado |
| CodeGraph | Indexado (1514 nodos, 4883 aristas) |
| DB Migrations | Aplicadas |

## Reglas del Agente

1. Usar CodeGraph ANTES de grep/find para entender codigo
2. No modificar `config.js` sin confirmacion (contiene API keys)
3. Mantener consistencia entre calendarWeb y calendarAPP
4. Documentar decisiones en `.agents/meetings/decisions/`
5. Actualizar `tasks.md` al completar tareas
6. NO generar URLs ni supongas endpoints — usa solo los definidos
7. Seguir el mismo estilo de codigo existente
