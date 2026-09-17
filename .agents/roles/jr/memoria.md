# Memoria del Desarrollador Junior

> Bitácora viva de mis sesiones. Última entrada arriba. Formato: fecha, objetivo, archivos, decisiones, pendientes.

## 2026-09-05 — Gate acoplado + base actualizada
- **Objetivo**: acoplar el sistema Gate/DB_TO-DO-LIST a mi operación sin perder hábitos P0, y dejar mi base (`roles/jr/`) como punto de retorno.
- **Archivos**: leídos los 8 de protocolo en orden (Gate/Context+input, DB/Context, workflow, reglas, general, desarrollo, seguridad); actualizados `README.md` (regla 8 branches + sección Gate), `memoria.md` (este), `tareas.md` (P1-0/P1-2 cerrados).
- **Decisiones**: Gate manda en el flujo (nada sin input ni Pending); conservo branches `feat/`, gates tsc+test+audit y reporte branch+hash+archivos como capa de ejecución.
- **Estado tablero**: sin ciclo activo, Pending/onProces/Audit/output vacíos. En espera de input del TPM.

## 2026-09-05 — P0 mergeado + higiene (262d6b9, 46ff999, 679dc1a)
- **Objetivo**: ejecutar merges aprobados y las 2 micro-tareas TPM (submódulo + gradle).
- **Acciones**: backend PR #1 (`feat/p0-validator`→main, `73cab49`); merges en principal en orden; `git submodule update --init backend` (quita `M backend`); `.gitignore` +`android-app/.gradle/` y `build/`.
- **Gates**: tsc 0, 309/309, audit 106/106, `git status` + `diff main origin/main` vacíos.
- **Pendiente**: commit rename en repo `FinNova-Staff`; decisión Angel sobre RLS `arboles_remotos` (TPM sube urgencia: expone red interna vía anon key).

## 2026-09-03 — Alta del rol + espacio personal
- **Objetivo**: asumir rol de Desarrollador Junior y crear mi memoria en `.agents/roles/jr/`.
- **Archivos**: `.agents/roles/jr/README.md` (presentación), `memoria.md` (este), `tareas.md` (mis tareas).
- **Decisiones**: no colisionar con `manifiesto-roles.md`/`plan-de-rol.md` compartidos; anidar en `jr/`.
- **Contexto recibido**: FinNova Academy (post-rename `aurafi`), suite 303/audit 106 verdes, migraciones congeladas (10 vs 6), carrera data R-15 completa, Capa 0/Ecosistema implementados y verificados en prod.
- **Pendiente**: primera tarea asignada por Angel/TPM.

## 2026-09-03 — Regla de oro: `.agents/` solo local
- Angel definió: todo lo de `.agents/` **no va al repo**, se queda en local.
- Acción: agregado `.agents/` a `.gitignore` raíz y ejecutado `git rm -r --cached .agents` (archivos conservados en disco, eliminados del índice). Mi espacio `jr/` también queda solo-local.
- A partir de ahora: mi memoria/tareas **no se commitean ni se pushean**.
