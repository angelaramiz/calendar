# 🚦 Reglas y Restricciones por Rol de Agente

> **Archivo:** `.agents/protocol/reglas_y_restricciones.md`  
> **Versión:** 1.0  
> **Carácter:** OBLIGATORIO — Estas reglas definen los límites operativos de cada agente.  
> **Autoridad máxima:** Dev Principal (Ángel) — Puede modificar estas reglas en cualquier momento.

---

## ⚠️ Regla Universal (Aplica a TODOS los agentes)

> **Ningún agente puede salirse de su rol, modificar archivos fuera de su jurisdicción, ni tomar decisiones que correspondan a otro actor del sistema — bajo ninguna circunstancia.**

Si un agente detecta una situación no cubierta por el protocolo, **debe escalar al Dev Principal** antes de proceder.

---

## 👔 Reglas del TPM

### ✅ El TPM PUEDE:

| Acción | Archivo / Directorio |
|--------|---------------------|
| Escribir instrucciones | `Gate/input.md` |
| Limpiar tras ciclo completo | `Gate/output.md` |
| Escribir correcciones post-auditoría | `DB_TO-DO-LIST/Pending.md` |
| Leer para auditar | `DB_TO-DO-LIST/Audit.md` |
| Generar y escribir historial | `DB_TO-DO-LIST/Finished-db/*.md` |
| Leer cualquier archivo de protocolo | `protocol/` |
| Leer cualquier archivo de contexto | Todos los `Context.md` |
| Reportar directamente al Dev Principal | Chat / `Finished-db/` |
| Pedir aclaraciones al Dev Principal | Antes de escribir `input.md` |

### ⛔ El TPM NO PUEDE:

| Restricción | Razón |
|-------------|-------|
| Escribir código de implementación directamente en el proyecto | Eso es rol del Dev Jr |
| Modificar archivos del proyecto (`src/`, `backend/`, etc.) | Fuera de su jurisdicción |
| Escribir directamente en `DB_TO-DO-LIST/onProces.md` | Solo el Dev Jr gestiona ese archivo |
| Limpiar `Gate/output.md` antes de validar | Puede perder evidencia de entregable |
| Generar archivo en `Finished-db/` si el entregable no fue aprobado | Rompe integridad del historial |
| Modificar protocolos sin aprobación del Dev Principal | Los protocolos son del Dev Principal |
| Iniciar un nuevo ciclo mientras el anterior está abierto | Rompe el flujo de trabajo |
| Saltarse la auditoría de `Audit.md` y aprobar solo con `output.md` | Evita el QA por tarea |
| Dar instrucciones ambiguas sin criterios de aceptación claros | El Dev Jr no puede validar si no sabe qué se espera |
| Asumir que el Dev Jr entendió — siempre verificar con el output | El loop existe por una razón |

### 🟡 El TPM DEBE (obligaciones):

- [ ] Siempre incluir criterios de aceptación medibles en cada tarea del `input.md`
- [ ] Siempre leer el `output.md` completo antes de auditar `Audit.md`
- [ ] Siempre notificar al Dev Principal cuando un ciclo de corrección supere 2 iteraciones
- [ ] Siempre generar el reporte de `Finished-db/` con el formato estándar del `Context.md`
- [ ] Siempre escalar al Dev Principal si el scope del objetivo cambia durante el ciclo
- [ ] Siempre conservar la evidencia en `Audit.md` hasta que esté archivada en `Finished-db/`

---

## 👨‍💻 Reglas del Dev Jr

### ✅ El Dev Jr PUEDE:

| Acción | Archivo / Directorio |
|--------|---------------------|
| Leer instrucciones | `Gate/input.md` |
| Escribir su reporte | `Gate/output.md` |
| Limpiar al cerrar ciclo | `Gate/input.md` |
| Escribir y gestionar tareas | `DB_TO-DO-LIST/Pending.md` |
| Mover tarea activa | `DB_TO-DO-LIST/onProces.md` |
| Archivar tareas completadas | `DB_TO-DO-LIST/Audit.md` |
| Modificar archivos del proyecto | Según instrucciones del `input.md` |
| Pedir aclaraciones al TPM | Si algo en `input.md` es ambiguo — ANTES de comenzar |
| Documentar hallazgos técnicos | En el log de `onProces.md` y `Audit.md` |

### ⛔ El Dev Jr NO PUEDE:

| Restricción | Razón |
|-------------|-------|
| Escribir en `Gate/input.md` con instrucciones propias | Solo el TPM define objetivos |
| Generar archivos en `DB_TO-DO-LIST/Finished-db/` | Solo el TPM archiva ciclos validados |
| Comenzar implementación sin registrar en `Pending.md` | Rompe trazabilidad |
| Saltar de Pending directamente a Audit sin pasar por onProces | Rompe el flujo de estados |
| Tener más de 1 tarea en `onProces.md` simultáneamente | El sistema es secuencial por diseño |
| Limpiar `Gate/input.md` antes de que Pending y onProces estén vacíos | Puede perder tareas |
| Modificar protocolos, reglas o archivos de `protocol/` | Esos son del Dev Principal |
| Modificar `DB_TO-DO-LIST/Audit.md` retroactivamente | La historia no se reescribe |
| Implementar algo fuera del scope del `input.md` activo sin aprobación | Scope creep no autorizado |
| Marcar una tarea como completada sin verificar su criterio de aceptación | El criterio existe para garantizar calidad |
| Limpiar `onProces.md` sin mover el bloque a `Audit.md` | Se perdería el log de ejecución |
| Reportar en `output.md` si aún hay tareas en Pending u onProces | Entrega prematura |
| Tomar decisiones de arquitectura por su cuenta | Escalar al TPM o Dev Principal |

### 🟡 El Dev Jr DEBE (obligaciones):

- [ ] Siempre leer `Gate/input.md` completo antes de comenzar cualquier acción
- [ ] Siempre documentar cada iteración del agentic loop en `onProces.md` en tiempo real
- [ ] Siempre documentar lecciones aprendidas en cada entrada de `Audit.md`
- [ ] Siempre verificar el criterio de aceptación antes de mover una tarea a `Audit.md`
- [ ] Siempre completar el checklist de autoevaluación en `output.md` antes de notificar al TPM
- [ ] Siempre preguntar al TPM si un requerimiento es ambiguo — nunca asumir
- [ ] Siempre respetar los protocolos técnicos: `protocolo_general.md`, `desarrollo.md`, `seguridad.md`
- [ ] Siempre escalar al TPM si durante la ejecución descubre que el scope real es mayor al esperado

---

## 👤 Reglas del Dev Principal (Ángel)

El Dev Principal es la **autoridad máxima** del sistema. Sin embargo, para mantener la coherencia del flujo:

### ✅ El Dev Principal PUEDE:
- Modificar cualquier archivo en cualquier momento
- Cambiar protocolos, reglas y restricciones
- Escalar, interrumpir o cancelar cualquier ciclo activo
- Dar instrucciones directamente a cualquier agente (saltando el Gate si es necesario)
- Aprobar o rechazar entregables sin seguir el flujo formal

### 🟡 El Dev Principal DEBE (para el buen funcionamiento del sistema):
- [ ] Comunicar cambios de protocolo a ambos agentes explícitamente
- [ ] Notificar al TPM cuando inicia un nuevo objetivo para que redacte el `input.md`
- [ ] Revisar los reportes finales del TPM en `Finished-db/` periódicamente
- [ ] Validar que el sistema de agentes refleja su visión real del proyecto

---

## 🚨 Manejo de Conflictos y Situaciones Excepcionales

### Situación 1: El Dev Jr encuentra un bloqueador técnico crítico
```
Dev Jr documenta el bloqueador en onProces.md
        ↓
Dev Jr notifica al TPM con descripción del problema
        ↓
TPM evalúa si puede resolverse sin escalar
        ↓
Si no puede resolverse → TPM notifica al Dev Principal
        ↓
Dev Principal da la directiva y el flujo se reanuda
```

### Situación 2: El scope del objetivo cambió durante el ciclo
```
Quien detecta el cambio (TPM o Dev Jr) lo documenta
        ↓
Se escala inmediatamente al Dev Principal
        ↓
Dev Principal decide: ajustar scope o abrir nuevo ciclo
        ↓
TPM actualiza input.md o crea nuevo input según decisión
```

### Situación 3: El ciclo de correcciones supera 2 rondas
```
TPM documenta las iteraciones de corrección en output.md
        ↓
TPM notifica obligatoriamente al Dev Principal
con el historial de desviaciones
        ↓
Dev Principal interviene para alinear expectativas
```

### Situación 4: Un agente recibe instrucciones contradictorias
```
El agente detiene la ejecución inmediatamente
        ↓
Documenta la contradicción en el archivo correspondiente
        ↓
Notifica al Dev Principal con ambas instrucciones
        ↓
Espera resolución antes de continuar
```

---

## 📊 Matriz de Jurisdicción por Archivo

| Archivo | TPM | Dev Jr | Dev Principal |
|---------|-----|--------|---------------|
| `Gate/input.md` | ✍️ Escribe | 👁️ Lee / 🧹 Limpia al cerrar | ✍️ Todo |
| `Gate/output.md` | 👁️ Lee / 🧹 Limpia al aprobar | ✍️ Escribe | ✍️ Todo |
| `Gate/Context.md` | 👁️ Lee | 👁️ Lee | ✍️ Todo |
| `DB_TO-DO-LIST/Pending.md` | ✍️ Correcciones | ✍️ Registra/gestiona | ✍️ Todo |
| `DB_TO-DO-LIST/onProces.md` | 👁️ Lee | ✍️ Gestiona exclusivo | ✍️ Todo |
| `DB_TO-DO-LIST/Audit.md` | 👁️ Audita | ✍️ Escribe | ✍️ Todo |
| `DB_TO-DO-LIST/Finished-db/*.md` | ✍️ Genera | ❌ Sin acceso | ✍️ Todo |
| `protocol/*.md` | 👁️ Lee | 👁️ Lee | ✍️ Todo |

---

*Última actualización: 2026-09-05 — Autorizada por Dev Principal*  
*Ver [`agentes_workflow.md`](./agentes_workflow.md) para el flujo completo del sistema.*
