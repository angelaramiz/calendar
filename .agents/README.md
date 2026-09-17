# 🤖 Entorno de Agentes — CalendarFinace

Este directorio contiene el sistema completo de colaboración entre agentes IA.

## Agentes del sistema

| Rol | Archivo de Prompt | Función |
|-----|-------------------|---------|
| **TPM** | [PROMPT_TPM.md](./PROMPT_TPM.md) | Traduce objetivos, gestiona flujo, audita entregables |
| **Dev Jr** | [PROMPT_JR.md](./PROMPT_JR.md) | Implementa, ejecuta agentic loop, reporta resultados |
| **Dev Principal** | Humano / Líder | Define visión y aprueba resultados |

## Inicio rápido

1. Asigna [PROMPT_TPM.md](./PROMPT_TPM.md) al agente TPM para su inicialización.
2. Asigna [PROMPT_JR.md](./PROMPT_JR.md) al agente Dev Jr para su inicialización.
3. Lee [Gate/Context.md](./Gate/Context.md) para entender el canal de comunicación.
4. Lee [DB_TO-DO-LIST/Context.md](./DB_TO-DO-LIST/Context.md) para el sistema de tareas.
5. Lee [protocol/agentes_workflow.md](./protocol/agentes_workflow.md) para el flujo completo.

## Estructura
Generado por el skill gent-collab-system — 2026-09-12
