# Tareas del Desarrollador Junior

> Mis tareas asignadas y su estado. `agents.md` y `.agents/tasks.md` son la fuente del equipo; aquí el seguimiento personal.
> NUEVA REGLA (Angel, 2026-09-05): el TPM me deja indicaciones directas en este archivo. Las ejecuto en `feat/<tema>`, PRs <10 archivos, y reporto a Angel (branch + hash + archivos, sin código en el chat).

## En curso (P1 — asignado por TPM, 2026-09-05)

- [ ] **P1-1 Paso 4 validator estructural** (extensión pendiente de input TPM): lo entregado (`validateEstructural` + 6 tests, mergeado) cubre compuerta AND variable 1. Contrato congelado restante: `concept` como pre-filtro 0 puntos donde haya estructural; quiz `racha>=1 + auto_confirma + quiz>=80`.
- [ ] **P1-3 Bundle**: `manualChunks` (three / sims-data / sims-contable) + `lazy()` en `SimuladorLaboral, PowerBISim, ForecastSim, AgentSim`, `chunkSizeWarningLimit: 800`. Meta: initial <800 kB gzip. Pseudotest: build y `Get-ChildItem alumnos/dist` con chunks separados.
- [ ] **P1-4 Backlog R-10/11/12**: mover a `docs/backlog-futuro.md` (pausa autorizada). Conservar solo `piiScrubber` + telemetría mínima.
- [ ] **P1-5 Obsidian**: queda en modo temporal (consulta MCP, edición disco). Reconstruir embeddings del plugin + decidir `Skills/n8n` solo con orden explícita de Angel. Prohibido concluir "no existe" desde `query_wiki` vacío.

## Completadas
- [x] **P1-0 Sync post-P0** (`feat/p0-higiene` → `8372a86`, merge `679dc1a`): submódulo en `4b8f425`, `.gitignore` +gradle/build, status limpio.
- [x] **P1-2 Vault 301→303**: Mapa línea 26 → 309 (único hit en vault), verificado por MCP `get_wiki`.

## Completadas
- [x] **P0 migraciones + AND + validator** (branches `feat/p0-migraciones-and`, `feat/p0-rename-memoria`, backend PR #1): baseline ASCII, 7 squash resueltos, AND congelado, `validator_estructural` cableado, rename pkgs, vault 309, `tsc` 0, 309/309, audit 106/106, merges `262d6b9` + `46ff999` en main.
- [x] **MCP Obsidian**: config `mcp.obsidian` `type: remote` con `{env:OBSIDIAN_BEARER}`, Bearer rotado, vault `Documents/FinNova` verificado (card + get OK, query con índice vacío).
- [x] **Vault al día**: 14 actualizados + 6 creados (Proyecto General, Arquitectura, Modelo, Portales, Tareas), repo limpio del stray.

## NO TOCAR (decisión Angel/TPM)
- `arboles_remotos`: tabla ajena (infra `proyectofinnova`, workers Tailscale). RLS aplicado y cerrado por el TPM. Ignorar todo lo relacionado — sin policies, sin código, sin queries.
- Carpeta en disco `academicFinace`: se queda como está.
- `FinNova-Staff`: su commit va en su repo, no aquí.

## Regla
Al cerrar cada tarea: actualizar esta lista + `memoria.md`, correr `npm run test` y `npm run audit:story`, y reportar a Angel (branch + hash + archivos, lógica, dudas para el TPM).
