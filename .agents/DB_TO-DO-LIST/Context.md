# 🗃️ DB_TO-DO-LIST — Base de Datos de Gestión de Tareas

> **Directorio:** `.agents/DB_TO-DO-LIST/`  
> **Propósito:** Sistema de seguimiento y trazabilidad de todas las tareas que el Dev Jr ejecuta durante un ciclo de desarrollo.

---

## ¿Qué es DB_TO-DO-LIST?

Es la base de datos interna del Dev Jr. Funciona como un **tablero Kanban en markdown**, donde cada tarea viaja a través de tres estados hasta completarse, y luego se archiva como historial permanente.

```
[Pending] → [onProces] → [Audit] → [Finished-db/]
```

Ninguna tarea puede saltarse un estado. El flujo es **siempre lineal y secuencial.**

---

## 📁 Estructura del Directorio

```
DB_TO-DO-LIST/
├── Context.md       ← Este archivo (instrucciones de uso)
├── Pending.md       ← Cola de tareas por ejecutar
├── onProces.md      ← Tarea activa en ejecución (máx. 1 a la vez)
├── Audit.md         ← Tareas completadas listas para revisión del TPM
└── Finished-db/     ← Historial permanente de ciclos completados
    └── Context.md   ← Instrucciones del subdirectorio
```

---

## 📋 Estados de Tarea y sus Archivos

### 1️⃣ `Pending.md` — Cola de Pendientes

**Quién escribe aquí:** Dev Jr (al leer el `input.md`) / TPM (cuando solicita correcciones)  
**Quién lee aquí:** Dev Jr (antes de comenzar cada tarea)

Contiene todas las tareas que deben ejecutarse, ordenadas por fases tal como vienen del `input.md`.

#### Formato de entrada en `Pending.md`:

```markdown
---

## 🔖 Ciclo: [TÍTULO DEL OBJETIVO]
**Fecha de ingreso:** YYYY-MM-DD  
**Origen:** Gate/input.md | TPM-Corrección

### Fase [N] — [Nombre de Fase]

- [ ] **[TASK-ID]** Descripción de la tarea
  - **Criterio de aceptación:** Qué debe cumplir para considerarse completada
  - **Archivos impactados:** Lista de archivos relevantes
  - **Prioridad:** Alta / Media / Baja
  - **Dependencias:** TASK-ID previas (si aplica)

- [ ] **[TASK-ID]** Descripción de la tarea
  - **Criterio de aceptación:** ...
```

> **Nota:** El Dev Jr **corta** (no copia) la tarea de aquí cuando la comienza, pegándola en `onProces.md`.  
> El TPM puede escribir directamente en `Pending.md` cuando detecta que el entregable no cumple los criterios esperados.

---

### 2️⃣ `onProces.md` — Tarea en Ejecución

**Quién escribe aquí:** Dev Jr  
**Máximo de tareas simultáneas:** 1  
**Tiempo de residencia:** Solo mientras la tarea está activamente en ejecución

Cuando el Dev Jr comienza una tarea, la **corta de `Pending.md`** y la **pega aquí**, añadiendo el registro de ejecución.

#### Formato de entrada en `onProces.md`:

```markdown
---

## ⚙️ Tarea en Ejecución

**TASK-ID:** [ID de la tarea]  
**Ciclo:** [TÍTULO DEL OBJETIVO]  
**Fase:** [N] — [Nombre de Fase]  
**Inicio:** YYYY-MM-DD HH:MM  

### Descripción
[Descripción original de la tarea]

**Criterio de aceptación:** [Criterio original]  
**Archivos impactados:** [Lista de archivos]

---

### 📝 Log de Ejecución (Agentic Loop)

#### Iteración 1
- **Acción:** Descripción de lo que se implementó
- **Resultado:** Éxito / Fallo / Parcial
- **Observación:** Hallazgo o nota relevante

#### Iteración 2 (si aplica)
- **Acción:** Ajuste o corrección aplicada
- **Resultado:** Éxito / Fallo / Parcial
- **Observación:** Lección aprendida

### 🔍 Resultado Final
[Descripción del estado final al completar la tarea]
```

> **Nota:** `onProces.md` debe estar **vacío** si no hay una tarea activa.  
> Una vez completada, la tarea se **corta de aquí** y se **pega en `Audit.md`**.

---

### 3️⃣ `Audit.md` — Tareas Completadas para Revisión

**Quién escribe aquí:** Dev Jr (al completar cada tarea)  
**Quién lee aquí:** TPM (para auditoría y QA)

Acumula todas las tareas completadas del ciclo actual, junto con el log completo de ejecución.

#### Formato de entrada en `Audit.md`:

```markdown
---

## ✅ Tarea Completada

**TASK-ID:** [ID de la tarea]  
**Ciclo:** [TÍTULO DEL OBJETIVO]  
**Fase:** [N] — [Nombre de Fase]  
**Inicio:** YYYY-MM-DD HH:MM  
**Fin:** YYYY-MM-DD HH:MM  
**Iteraciones necesarias:** [N]

### Descripción
[Descripción original de la tarea]

**Criterio de aceptación:** [Criterio original]

### 📝 Log de Ejecución Completo
[Log copiado de onProces.md]

### ✔️ Verificación de Criterio
- **Criterio cumplido:** ✅ Sí / ⚠️ Parcial / ❌ No
- **Evidencia:** Descripción de cómo se verificó

### 🧠 Lecciones Aprendidas
- [Hallazgo o mejora identificada durante la ejecución]
```

> **Nota:** El TPM revisa `Audit.md` para aplicar **técnicas de PM y QA**.  
> Si el entregable final cumple expectativas, el TPM mueve el contenido a `Finished-db/`.  
> Si no cumple, el TPM escribe las correcciones directamente en `Pending.md`.

---

### 4️⃣ `Finished-db/` — Historial Permanente

Subdirectorio donde se archivan los ciclos completados y validados por el TPM.  
Cada ciclo genera un archivo `.md` independiente con el historial completo.

**Ver [`Finished-db/Context.md`](./Finished-db/Context.md) para instrucciones detalladas de estructura y nomenclatura.**

---

## 🔄 Reglas de Flujo de Tareas

| Regla | Descripción |
|-------|-------------|
| **Cortar, no copiar** | Al mover una tarea entre estados, siempre se corta del estado anterior |
| **Un estado a la vez** | Una tarea nunca puede estar en dos archivos simultáneamente |
| **`onProces.md` máx. 1 tarea** | Solo se trabaja en una tarea a la vez |
| **No saltar estados** | El flujo es siempre: Pending → onProces → Audit |
| **Audit antes de output** | No se genera `output.md` mientras haya tareas en Pending u onProces |
| **TPM puede escribir en Pending** | Cuando detecta que falta cumplir criterios de aceptación |

---

## 🏷️ Nomenclatura de TASK-ID

Los IDs de tarea siguen este formato: `TASK-[FASE]-[NÚMERO]`

**Ejemplos:**
- `TASK-1-1` → Fase 1, Tarea 1
- `TASK-2-3` → Fase 2, Tarea 3
- `TASK-COR-1` → Corrección 1 (emitida por TPM post-auditoría)

---

*Sistema gestionado por el Dev Jr bajo supervisión del TPM.*  
*Historial de ciclos completados disponible en `Finished-db/`.*
