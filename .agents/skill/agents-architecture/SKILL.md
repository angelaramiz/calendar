# SKILL: Arquitectura .agents — Sistema de Memoria y Contexto para IA

## Resumen
Esta skill documenta la arquitectura estándar de un directorio `.agents/` para proyectos que usan IA como copiloto. Cubre: estructura de archivos, CodeGraph, skills, memoria del agente, y mapeo del proyecto.

---

## Estructura de Directorios

```
proyecto/
├── .agents/                    # Directorio raiz de la arquitectura
│   ├── skill/                  # Skills reutilizables (capacidades del agente)
│   │   ├── nombre-skill/
│   │   │   └── SKILL.md        # Documentacion de la skill
│   │   └── ...
│   ├── tasks.md                # Tareas activas del agente
│   ├── input.md                # Input pendiente del desarrollador
│   ├── rules/                  # Reglas persistentes
│   │   ├── regla1.md
│   │   └── ...
│   ├── roles/                  # Definiciones de rol
│   │   ├── manifiesto-roles.md
│   │   └── plan-de-rol.md
│   ├── meetings/               # Decisiones de reuniones
│   │   └── decisions/
│   ├── history/                # Historial completado
│   │   └── README.md
│   └── architecture.md         # Arquitectura del proyecto (auto-generado)
│
├── .codegraph/                 # Indice de codigo para busqueda semantica
│   ├── codegraph.db
│   ├── codegraph.db-shm
│   ├── codegraph.db-wal
│   ├── daemon.pid
│   ├── daemon.log
│   └── .gitignore
│
├── AGENTS.md                   # Archivo de referencia central del agente
└── CLAUDE.md                   # Instrucciones para Claude (en ~/.claude/)
```

---

## Componentes Clave

### 1. AGENTS.md — Referencia Central
Archivo raiz que el agente lee antes de cualquier accion. Contiene:
- Stack tecnologico del proyecto
- Comandos disponibles
- Reglas de comportamiento
- Rutas importantes
- Estado actual del proyecto
- Convenciones de codigo

### 2. .codegraph/ — Indice Semantico
Base de datos SQLite que indexa todo el codigo fuente para busquedas rapidas.

**Instalacion:**
```bash
npm install -g codegraph
codegraph init
codegraph index
codegraph explore "nombreFuncion"
```

### 3. .agents/skill/ — Capacidades Reutilizables
Documentos Markdown que definen workflows, patrones o conocimiento especializado.

### 4. .agents/tasks.md — Tareas Activas
```markdown
# TAREAS ACTIVAS
## TODO List Dinamica
- [ ] Tarea pendiente 1
- [x] Tarea completada
```

### 5. .agents/input.md — Input del Desarrollador
Espacio para que el desarrollador deje instrucciones, contexto o tareas para el agente.

### 6. .agents/rules/ — Reglas Persistentes
Archivos Markdown con directrices que el agente debe seguir siempre.

### 7. .agents/architecture.md — Arquitectura del Proyecto
Documento auto-generado que describe la estructura del codigo.

---

## Flujo de Trabajo del Agente

```
1. Leer AGENTS.md → entender contexto
2. Leer .agents/input.md → ver si hay instrucciones pendientes
3. Usar CodeGraph → entender codigo antes de modificar
4. Ejecutar tarea
5. Actualizar .agents/tasks.md
6. Documentar decision en .agents/meetings/decisions/
7. Si hay skill relevante → cargar con skill tool
```

---

## Instalacion de CodeGraph

```bash
npm install -g codegraph
cd mi-proyecto
codegraph init
codegraph index
codegraph explore "main function"
```

---

## Referencias

| Recurso | URL |
|---------|-----|
| CodeGraph | https://github.com/codegraph-ai/codegraph |
| OpenCode | https://opencode.ai |
| AGENTS.md spec | https://github.com/agents-md/agents-md |
