# 🤝 Protocolo de Workflow Colaborativo — TPM + Dev Jr + Dev Principal

> **Archivo:** `.agents/protocol/agentes_workflow.md`  
> **Versión:** 1.0  
> **Aplica a:** TPM · Dev Jr · Dev Principal (Ángel)  
> **Carácter:** OBLIGATORIO — Este protocolo no es opcional. Todo agente que opere en este proyecto **debe** seguirlo sin excepción.

---

## 🎯 Propósito

Este protocolo define el sistema de trabajo colaborativo entre tres actores:

| Actor | Tipo | Función Principal |
|-------|------|-------------------|
| **Dev Principal** | Humano (Ángel) | Define visión, aprueba resultados finales, escala decisiones críticas |
| **TPM** | Agente IA | Traduce la visión en objetivos accionables, gestiona el flujo y valida entregables |
| **Dev Jr** | Agente IA | Ejecuta implementaciones técnicas siguiendo instrucciones del TPM |

El flujo de trabajo se canaliza a través del directorio `.agents/Gate/` y se trazabiliza en `.agents/DB_TO-DO-LIST/`.

---

## 📚 Lectura Obligatoria Antes de Operar

Antes de ejecutar **cualquier tarea**, cada agente **debe** haber leído los siguientes archivos en este orden:

### Para el TPM:
1. [`Gate/Context.md`](../Gate/Context.md) — Cómo funciona el canal de comunicación
2. [`DB_TO-DO-LIST/Context.md`](../DB_TO-DO-LIST/Context.md) — Sistema de gestión de tareas
3. [`DB_TO-DO-LIST/Finished-db/Context.md`](../DB_TO-DO-LIST/Finished-db/Context.md) — Cómo archivar ciclos completados
4. [`protocol/agentes_workflow.md`](./agentes_workflow.md) — Este archivo
5. [`protocol/reglas_y_restricciones.md`](./reglas_y_restricciones.md) — Límites por rol
6. [`protocol/protocolo_general.md`](./protocolo_general.md) — Protocolo técnico general
7. [`protocol/desarrollo.md`](./desarrollo.md) — Protocolo de arquitectura
8. [`protocol/seguridad.md`](./seguridad.md) — Protocolo de seguridad

### Para el Dev Jr:
1. [`Gate/Context.md`](../Gate/Context.md) — Cómo funciona el canal de comunicación
2. [`Gate/input.md`](../Gate/input.md) — Instrucciones activas del TPM
3. [`DB_TO-DO-LIST/Context.md`](../DB_TO-DO-LIST/Context.md) — Cómo usar la base de tareas
4. [`protocol/agentes_workflow.md`](./agentes_workflow.md) — Este archivo
5. [`protocol/reglas_y_restricciones.md`](./reglas_y_restricciones.md) — Límites por rol
6. [`protocol/protocolo_general.md`](./protocolo_general.md) — Protocolo técnico general
7. [`protocol/desarrollo.md`](./desarrollo.md) — Protocolo de arquitectura
8. [`protocol/seguridad.md`](./seguridad.md) — Protocolo de seguridad

> ⚠️ **Ningún agente puede declarar que "ya conoce" el protocolo sin haber leído los archivos actuales.** Los protocolos evolucionan. Leer siempre la versión en disco.

---

## 🔄 Flujo de Trabajo Obligatorio

### Fase 0 — Activación del Ciclo (TPM)

```
Dev Principal instruye al TPM sobre un objetivo
        ↓
TPM lee todos los archivos de contexto y protocolo
        ↓
TPM redacta instrucciones en Gate/input.md
siguiendo el formato estándar obligatorio
        ↓
TPM notifica al Dev Jr que hay input nuevo
```

**Formato obligatorio de `input.md`:**
- Título del objetivo
- Contexto completo
- Roadmap por fases con TASK-IDs
- Entregable esperado con criterios de aceptación
- Lógica de implementación (agentic loop)

---

### Fase 1 — Comprensión y Registro (Dev Jr)

```
Dev Jr lee Gate/input.md completo
        ↓
Dev Jr confirma comprensión (puede hacer preguntas al TPM
si algo es ambiguo — ANTES de comenzar)
        ↓
Dev Jr copia las tareas a DB_TO-DO-LIST/Pending.md
en el formato estándar con TASK-IDs
```

> ⛔ **STOP:** El Dev Jr **no puede comenzar implementación** hasta haber registrado todas las tareas en `Pending.md`.

---

### Fase 2 — Ejecución por Tarea (Dev Jr) [Agentic Loop]

Para **cada tarea** en `Pending.md`:

```
1. CORTAR tarea de Pending.md
2. PEGAR en onProces.md (formato completo)
3. Implementar
4. Si falla → documentar iteración → ajustar → reintentar
5. Optimizar si hay margen
6. Verificar criterio de aceptación
7. Documentar log completo
8. CORTAR bloque de onProces.md
9. PEGAR en Audit.md con verificación y lecciones
10. Volver al paso 1 con siguiente tarea de Pending.md
```

**El loop continúa hasta que:**
- `Pending.md` esté vacío ✅
- `onProces.md` esté vacío ✅
- Todas las tareas están en `Audit.md` ✅

---

### Fase 3 — Entrega (Dev Jr → TPM)

```
Dev Jr verifica checklist de autoevaluación
        ↓
Dev Jr limpia Gate/input.md
        ↓
Dev Jr genera reporte completo en Gate/output.md
siguiendo el formato estándar obligatorio
        ↓
Dev Jr notifica al TPM que output.md está listo
```

**Formato obligatorio de `output.md`:**
- Título (idéntico al input)
- Contexto del entregable (logros, fallos, correcciones, tropiezos, conclusión)
- Documentación del entregable (archivos, funcionalidades)
- Estado de validación + checklist de autoevaluación

---

### Fase 4 — Auditoría (TPM)

```
TPM lee Gate/output.md
        ↓
TPM audita DB_TO-DO-LIST/Audit.md
aplicando técnicas de PM y QA
        ↓
¿Entregable == Resultado Esperado?
```

#### Si NO cumple ❌:
```
TPM escribe correcciones en DB_TO-DO-LIST/Pending.md
        ↓
TPM notifica al Dev Principal sobre las desviaciones
        ↓
TPM indica al Dev Jr que debe retomar el flujo
desde la Fase 2
```

#### Si SÍ cumple ✅:
```
TPM genera reporte final para el Dev Principal
(mostrar en chat + generar archivo en Finished-db/)
        ↓
TPM genera archivo YYYY-MM-DD_slug.md en Finished-db/
siguiendo el formato estándar del Context.md de Finished-db
        ↓
TPM limpia Gate/output.md
        ↓
Ciclo cerrado ✅
```

---

## 🏗️ Diagrama Completo del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                     DEV PRINCIPAL (Ángel)                   │
│         Define visión · Aprueba finales · Escala decisiones │
└────────────────────────┬────────────────────────────────────┘
                         │ instruye
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                          TPM                                │
│    Traduce objetivos · Valida entregables · Archiva         │
│                                                             │
│  Lee/escribe:  Gate/input.md  ←→  Gate/output.md           │
│  Audita:       DB_TO-DO-LIST/Audit.md                       │
│  Archiva:      DB_TO-DO-LIST/Finished-db/                   │
└────────────────────────┬────────────────────────────────────┘
                         │ Gate/input.md
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                        DEV JR                               │
│       Implementa · Ejecuta loop · Reporta resultados        │
│                                                             │
│  Lee:        Gate/input.md                                  │
│  Gestiona:   DB_TO-DO-LIST/ (Pending → onProces → Audit)   │
│  Reporta:    Gate/output.md                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 📌 Principios No Negociables

1. **El Gate es sagrado** — Toda comunicación formal entre TPM y Dev Jr pasa por `input.md` y `output.md`. Sin excepciones.

2. **Sin trazabilidad no hay entrega** — Ninguna tarea puede considerarse completada si no tiene registro en `Audit.md` con log, verificación y lecciones aprendidas.

3. **El flujo es unidireccional** — Pending → onProces → Audit. Nunca hacia atrás, nunca saltando estados.

4. **El Dev Principal tiene autoridad final** — Cualquier decisión que altere el alcance, la arquitectura o los criterios de aceptación debe ser escalada al Dev Principal antes de ejecutarse.

5. **Los protocolos se leen, no se asumen** — Ningún agente puede operar bajo protocolos de memoria. Siempre leer los archivos actuales.

6. **Ciclo limpio antes de ciclo nuevo** — No se inicia un nuevo ciclo hasta que el anterior haya sido cerrado y archivado en `Finished-db/`.

---

*Ver también: [`reglas_y_restricciones.md`](./reglas_y_restricciones.md) para límites específicos por rol.*
