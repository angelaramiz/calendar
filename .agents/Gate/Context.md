# 🚪 Gate — Canal de Comunicación TPM ↔ Dev Jr

> **Directorio:** `.agents/Gate/`  
> **Propósito:** Punto de entrada y salida oficial entre el TPM y el Dev Jr para la ejecución de objetivos de desarrollo.

---

## ¿Qué es Gate?

`Gate` es el canal de comunicación estructurado entre dos agentes IA que colaboran en este proyecto:

| Agente | Rol | Responsabilidad |
|--------|-----|-----------------|
| **TPM** | Technical Project Manager | Define objetivos, proporciona contexto, emite instrucciones y valida entregables |
| **Dev Jr** | Desarrollador Junior IA | Lee instrucciones, ejecuta tareas, gestiona la DB de tareas y reporta resultados |

Este directorio contiene **exactamente 2 archivos operativos** que funcionan como bandeja de entrada y bandeja de salida del flujo de trabajo.

---

## 📁 Archivos del Directorio

### 📥 `input.md` — Instrucciones del TPM al Dev Jr

Archivo donde el TPM redacta todas las instrucciones, contexto y tareas que debe ejecutar el Dev Jr.

**El Dev Jr debe leer este archivo al inicio de cada ciclo de trabajo.**

#### Estructura obligatoria de `input.md`:

```
# [TÍTULO DEL OBJETIVO]

## 🎯 Tema / Objetivo
Descripción clara y concisa del objetivo a lograr.

## 📋 Contexto
Todo el contexto relevante que el Dev Jr necesita para comprender el objetivo:
- Estado actual del sistema
- Dependencias existentes
- Restricciones o consideraciones importantes
- Referencias a archivos o documentación relevante

## 🗺️ Roadmap de Tareas

### Fase 1 — [Nombre de Fase]
- [ ] Tarea 1.1 — Descripción
- [ ] Tarea 1.2 — Descripción

### Fase 2 — [Nombre de Fase]
- [ ] Tarea 2.1 — Descripción
- [ ] Tarea 2.2 — Descripción

### Fase N — [Nombre de Fase]
- [ ] Tarea N.1 — Descripción

## 📦 Entregable Esperado en output.md
Descripción precisa del resultado que el TPM espera encontrar en `output.md` al finalizar el ciclo.
Incluir:
- Qué archivos deben estar creados o modificados
- Qué funcionalidades deben estar operativas
- Criterios de aceptación claros

## ⚙️ Lógica de Implementación (Agentic Loop)

El Dev Jr seguirá el siguiente ciclo hasta alcanzar el objetivo:

1. **Comprensión** → Leer y confirmar entendimiento del input
2. **Planificación** → Registrar tareas en `DB_TO-DO-LIST/Pending.md`
3. **Ejecución** → Mover tarea activa a `onProces.md`, implementar
4. **Optimización** → Refinar la implementación si hay margen de mejora
5. **Pruebas** → Verificar que la tarea cumple su criterio de aceptación
6. **Retroalimentación** → Documentar hallazgos, errores y correcciones
7. **Aprendizaje** → Integrar lecciones aprendidas al siguiente ciclo
8. **Cierre de tarea** → Mover tarea completada a `Audit.md`
9. **Repetir** → Con la siguiente tarea hasta vaciar `Pending.md` y `onProces.md`
10. **Entrega** → Limpiar `input.md` y generar reporte en `output.md`
```

---

### 📤 `output.md` — Reporte del Dev Jr al TPM

Archivo donde el Dev Jr genera su reporte de entrega una vez que todas las tareas han sido completadas y auditadas.

**El TPM debe leer este archivo para validar el entregable.**

#### Estructura obligatoria de `output.md`:

```
# [TÍTULO — mismo que input.md]

## 📋 Contexto del Entregable
Resumen ejecutivo del proceso completo:
- ¿Qué se logró?
- Fallos detectados durante el desarrollo
- Correcciones adicionales que fueron necesarias
- Tropiezos superados en el agentic loop
- Conclusión general del ciclo

## 📦 Documentación del Entregable
Lista de todo lo que fue implementado:
- Archivos creados / modificados
- Funcionalidades operativas
- Notas técnicas relevantes

> El TPM puede ir a `DB_TO-DO-LIST/Audit.md` para auditar y aplicar
> técnicas de PM y QA sobre cada tarea completada.

## ✅ Estado de Validación
[ PENDIENTE DE REVISIÓN TPM ]
```

---

## 🔄 Flujo Completo del Protocolo

```
TPM escribe en input.md
        ↓
Dev Jr lee input.md
        ↓
Dev Jr copia tareas → DB_TO-DO-LIST/Pending.md
        ↓
Dev Jr ejecuta tarea (Pending → onProces → Audit)
        ↓
[Agentic Loop: implementar → optimizar → probar → aprender]
        ↓
¿Pending.md y onProces.md vacíos?
    NO → continuar loop
    SÍ → ↓
        ↓
Dev Jr limpia input.md
Dev Jr genera reporte en output.md
        ↓
TPM lee output.md y audita DB_TO-DO-LIST/Audit.md
        ↓
¿Entregable == Resultado Esperado?
    NO → TPM escribe correcciones en Pending.md
          TPM notifica al Dev Principal
          Dev Jr repite flujo
    SÍ → TPM genera reporte final para Dev Principal
          TPM archiva en DB_TO-DO-LIST/Finished-db/
          TPM limpia output.md
```

---

## 📌 Reglas del Canal Gate

1. **`input.md` siempre debe estar limpio** al inicio de un nuevo ciclo (el Dev Jr lo limpia al cerrar el anterior).
2. **`output.md` siempre debe estar limpio** al inicio de un nuevo ciclo (el TPM lo limpia tras validar y archivar).
3. **Solo el TPM escribe en `input.md`.** El Dev Jr solo lee.
4. **Solo el Dev Jr escribe en `output.md`.** El TPM solo lee y valida.
5. **No iniciar un nuevo ciclo** sin haber cerrado y archivado el anterior.
6. **El Dev Jr no borra el `input.md`** hasta que todas las tareas estén en `Audit.md`.

---

*Última actualización del protocolo: Ver `DB_TO-DO-LIST/Finished-db/` para historial completo.*
