# Calendario Interactivo

Calendario interactivo modular construido con vanilla JavaScript (ES6 modules), HTML5 y CSS3.

## 📁 Estructura del Proyecto

```
calendar/
├── Calendario.html          # Página principal
├── styles.css               # Estilos globales
├── sweetalert2@11.js       # Librería de modales
└── js/                     # Módulos JavaScript
    ├── main.js             # Punto de entrada de la aplicación
    ├── calendar.js         # Clase principal del calendario
    ├── events.js           # Gestión de eventos y localStorage
    ├── modal.js            # Interfaz de modales con SweetAlert2
    └── recurrence.js       # Lógica de eventos recurrentes
```

## 🎯 Características

- ✅ **Navegación mensual** - Navega entre meses con botones
- ✅ **Eventos únicos** - Crea eventos para fechas específicas
- ✅ **Eventos recurrentes** - Configura eventos semanales, mensuales o anuales
- ✅ **Intervalo personalizado** - Define cada cuántas semanas/meses/años se repite
- ✅ **Límite de ciclos** - Controla cuántas veces se repite un evento
- ✅ **Persistencia** - Los eventos se guardan en localStorage
- ✅ **Indicadores visuales** - Puntos en las celdas con tooltips informativos
- ✅ **Modales elegantes** - Interfaz con SweetAlert2

## 🏗️ Arquitectura Modular

### `main.js`
Punto de entrada de la aplicación. Inicializa el calendario cuando el DOM está listo.

### `calendar.js`
**Clase `Calendar`** - Maneja toda la lógica del calendario:
- Renderización del mes actual
- Navegación entre meses
- Gestión de celdas y dates
- Actualización de indicadores de eventos
- Event listeners para clicks en celdas

### `events.js`
**Módulo de Eventos** - Operaciones CRUD y utilidades:
- `loadEvents()` - Carga eventos desde localStorage
- `saveEvents()` - Guarda eventos en localStorage
- `addEvent()` - Añade un evento único
- `addRecurringEvents()` - Añade eventos recurrentes
- `deleteEvent()` - Elimina un evento
- `getEventsForDate()` - Obtiene eventos de una fecha
- `escapeHTML()` - Previene XSS
- `capitalize()` - Formatea strings

### `modal.js`
**Gestión de Modales** - Interfaz con SweetAlert2:
- `openEventModal()` - Abre la modal para crear/ver eventos
- Renderiza formularios con campos dinámicos
- Valida datos de entrada
- Maneja guardado y eliminación de eventos
- Actualiza UI en tiempo real

### `recurrence.js`
**Lógica de Recurrencia** - Generación de fechas:
- `generateRecurringDates()` - Genera array de fechas según:
  - **Frecuencia**: semanal, mensual, anual
  - **Intervalo**: cada N unidades (ej: cada 2 semanas)
  - **Límite**: cantidad total de ocurrencias

## 🚀 Uso

1. Abre `Calendario.html` en tu navegador
2. Haz click en cualquier celda para abrir la modal
3. Completa los campos:
   - **Título** (requerido)
   - **Descripción** (opcional)
   - **Frecuencia**: Ninguna / Semanal / Mensual / Anual
   - **Intervalo de ciclo**: cada cuántas unidades (default: 1)
   - **Límite de ciclos**: cuántas repeticiones (default: 6)
4. Click en "Guardar"
5. Los eventos aparecen como puntos rojos en las celdas
6. Hover sobre el punto para ver detalles en tooltip
7. Click en la celda nuevamente para ver/eliminar eventos

## 🔧 Tecnologías

- **HTML5** - Estructura semántica
- **CSS3** - Estilos y responsive design
- **JavaScript ES6+** - Modules, Classes, Arrow functions
- **SweetAlert2** - Modales elegantes
- **LocalStorage API** - Persistencia de datos

## 📦 Sin Dependencias de Build

Este proyecto usa **ES6 modules nativos** del navegador, no requiere:
- ❌ npm/yarn
- ❌ Webpack/Rollup/Vite
- ❌ Babel/transpilación
- ❌ Build process

Funciona directamente en navegadores modernos que soportan:
- ES6 Modules (`type="module"`)
- Classes
- Arrow functions
- Template literals
- LocalStorage

## 🌐 Compatibilidad

- Chrome 61+
- Firefox 60+
- Safari 11+
- Edge 79+

## 💾 Formato de Datos (localStorage)

```javascript
{
  "2025-11-05": [
    {
      "title": "Reunión equipo",
      "desc": "Reunión semanal de equipo",
      "frequency": "semanal",
      "interval": 1,
      "limit": 6,
      "createdAt": "2025-11-02T10:30:00.000Z",
      "origin": "2025-11-05",
      "occurrenceDate": "2025-11-05"
    }
  ],
  "2025-11-12": [...],
  ...
}
```

## 🎨 Personalización

### Estilos
Edita `styles.css` para cambiar colores, fuentes, tamaños, etc.

### Frecuencias
En `modal.js` y `recurrence.js` puedes añadir nuevas frecuencias (ej: diaria, quincenal).

### Indicadores
En `calendar.js` método `createEventIndicator()` personaliza el aspecto de los puntos.

## 📝 Notas

- Los eventos se guardan **solo en el navegador actual** (localStorage)
- Para sincronización multi-dispositivo necesitarías un backend
- Los datos persisten hasta que se limpie localStorage o cache del navegador

## 🔜 Mejoras Futuras Sugeridas

- [ ] Exportar/Importar eventos (JSON, iCal)
- [ ] Drag & drop de eventos
- [ ] Vista de lista de eventos
- [ ] Filtros y búsqueda
- [ ] Categorías/etiquetas con colores
- [ ] Notificaciones del navegador
- [ ] Backend para sincronización
- [ ] Modo oscuro

---

**Autor**: Refactorizado con arquitectura modular ES6
**Licencia**: MIT
