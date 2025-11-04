# Calendario Interactivo Financiero

Calendario interactivo avanzado con gestión de ingresos, gastos, **sistema de préstamos con intereses** y **notificaciones inteligentes**. Construido con vanilla JavaScript (ES6 modules), Web Components, HTML5 y CSS3.

## ✨ Nuevas Características (v2.0)

### 💰 Sistema de Préstamos Avanzado
- **Cálculo automático de intereses** ($ y % auto-calculables)
- **Múltiples planes de pago**: único, semanal, quincenal, mensual, fechas personalizadas
- **Generación automática de contrapartes** (eventos de cobro/pago)
- **División inteligente** de montos entre cuotas
- **Tracking completo** con loanId único

### � Sistema de Notificaciones
- **Alertas automáticas** para eventos próximos
- **Notificaciones de vencimiento** de préstamos (críticas y de alta prioridad)
- **Alertas personalizadas** por evento con 4 niveles de prioridad
- **Notificaciones del navegador** (opcional con permiso)
- **Panel centralizado** con badge contador
- **Configuración completa** de timing y tipos de alertas

### 🎨 Mejoras Visuales
- Indicadores diferenciados para ingresos (verde) y gastos (rojo)
- Badge dorado para préstamos activos 💰
- Indicador morado para contrapartes ↩️
- Eventos archivados con estilo atenuado
- Tooltips enriquecidos con toda la información
- Vista detallada completa por evento

## �📁 Estructura del Proyecto

```
calendar/
├── index.html                    # Página principal
├── guia-uso.html                 # Guía de uso interactiva
├── styles.css                    # Estilos globales
├── docs/
│   ├── nuevas-caracteristicas.md # Documentación completa de v2.0
│   └── components-guidelines.md  # Guías de componentes
└── js/                           # Módulos JavaScript
    ├── main.js                   # Punto de entrada + integración notificaciones
    ├── calendar.js               # Clase principal del calendario
    ├── events.js                 # Gestión de eventos + contrapartes
    ├── modal.js                  # Modales + alertas personalizadas
    ├── recurrence.js             # Lógica de eventos recurrentes
    ├── notifications.js          # 🆕 Sistema completo de notificaciones
    ├── components/
    │   ├── financial-form.js     # 🆕 Formulario avanzado con préstamos
    │   └── frequency-toggle.js   # Toggle de frecuencia
    └── librerias/
        └── sweetalert2@11.js     # Librería de modales
```

## 🎯 Características Principales

### Gestión Financiera
- ✅ **Ingresos y Gastos** con categorías
- ✅ **Montos esperados** vs **montos confirmados**
- ✅ **Historial archivado** (eventos confirmados bloqueados)
- ✅ **Categorización** automática por tipo

### Préstamos
- ✅ **Préstamo a favor** (dinero que prestas - gasto)
- ✅ **Préstamo en contra** (dinero que te prestan - ingreso)
- ✅ **Interés auto-calculable** (valor ↔ porcentaje)
- ✅ **5 planes de pago** diferentes
- ✅ **Contrapartes automáticas** con división de montos
- ✅ **Notas adicionales** por préstamo

### Notificaciones
- ✅ **Alertas automáticas** según anticipación configurada
- ✅ **Prioridades** (crítica, alta, media, baja)
- ✅ **Badge contador** en tiempo real
- ✅ **Click para abrir evento** directamente
- ✅ **Actualización cada 5 minutos**
- ✅ **Persistencia en localStorage**

### Eventos Recurrentes
- ✅ **Frecuencias**: semanal, mensual, anual
- ✅ **Intervalo personalizado** (cada X períodos)
- ✅ **Límite de ciclos** configurable
- ✅ **Edición de futuras ocurrencias**

### Persistencia y UI
- ✅ **localStorage** - Sin backend necesario
- ✅ **Indicadores visuales** diferenciados
- ✅ **Tooltips informativos** con toda la metadata
- ✅ **Modales elegantes** con SweetAlert2
- ✅ **Web Components** para UI modular
- ✅ **Responsive design** adaptable

## 🏗️ Arquitectura Modular

### `main.js`
Punto de entrada de la aplicación:
- Inicializa el calendario
- Configura sistema de notificaciones
- Crea panel de alertas en header
- Actualización automática cada 5 minutos

### `calendar.js`
**Clase `Calendar`** - Renderización y navegación:
- Generación del calendario mensual
- Gestión de indicadores visuales
- Tooltips enriquecidos con loan/confirmed info
- Event listeners para interacción

### `events.js`
**Módulo de Eventos** - CRUD y lógica de negocio:
- `addEvent()`, `updateEvent()`, `deleteEvent()`
- `addRecurringEvents()` - Series recurrentes
- `updateFutureOccurrences()` - Edición masiva
- `createLoanCounterpartByLoanId()` - 🆕 Genera contrapartes con plan de pagos
- `removeLoanCounterpartByLoanId()` - 🆕 Limpieza de contrapartes

### `notifications.js` 🆕
**Sistema de Notificaciones** - Alertas completas:
- `initNotificationSystem()` - Inicialización
- `getPendingAlerts()` - Obtiene alertas actuales
- `addEventAlert()` - Crea alerta personalizada
- `displayAlerts()` - Renderiza panel
- `requestBrowserNotificationPermission()` - Permisos
- `showBrowserNotification()` - Notificación nativa

### `modal.js`
**Interfaz de Modales** - Flujos de usuario:
- `openEventModal()` - Modal principal del día
- `openFinancialEventModal()` - Formulario de ingreso/gasto
- `openEventDetailModal()` - Vista completa + botón de alerta
- `openCustomAlertModal()` - 🆕 Crear alerta personalizada
- `handleEventSave()` - Guardado con generación de loanId
- Lógica de confirmación de montos

### `components/financial-form.js` 🆕
**Web Component** - Formulario avanzado:
- Campos de título, descripción, monto, categoría
- **Sección de préstamo colapsable**:
  - Retorno esperado
  - Interés ($ y % con auto-cálculo)
  - Plan de pagos (5 opciones)
  - Campos dinámicos según plan
  - Notas adicionales
- Desactiva frecuencia normal si préstamo activo
- `setInitial()` para edición
- Emite eventos `save` y `cancel`
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

## 🚀 Inicio Rápido

1. **Abre el calendario**
   ```
   Abre index.html en tu navegador
   O consulta guia-uso.html para tutorial interactivo
   ```

2. **Crear un evento simple**
   - Click en cualquier día
   - Selecciona "Agregar ingreso" o "Agregar gasto"
   - Completa título, monto, categoría
   - Guarda

3. **Crear un préstamo**
   - Click en un día → Agregar gasto/ingreso
   - Marca checkbox "Préstamo"
   - Completa campos avanzados:
     - Interés ($ o %)
     - Plan de pagos
     - Días/fechas de recuperación
   - Guarda → Se crean contrapartes automáticamente

4. **Configurar notificaciones**
   - Click en icono 🔔
   - Botón "⚙️ Configuración"
   - Ajusta días de anticipación
   - Activa/desactiva tipos de alertas
   - Guarda configuración

5. **Crear alerta personalizada**
   - Abre un evento existente
   - Click "🔔 Agregar Alerta"
   - Personaliza mensaje y timing
   - Guarda

## 📊 Ejemplos de Uso

### Ejemplo 1: Préstamo Simple
```
Tipo: Gasto
Título: "Préstamo a Juan"
Monto: $1000
Préstamo: ✓ Activado
Interés: 5% (auto-calcula $50)
Plan: Pago único
Días: 30

Resultado: 
- Evento de gasto hoy ($1000)
- Evento de ingreso en 30 días ($1050)
```

### Ejemplo 2: Préstamo con Cuotas
```
Tipo: Ingreso (te prestaron)
Título: "Préstamo banco"
Monto: $5000
Préstamo: ✓ Activado
Interés: $500
Plan: Mensual
Frecuencia: 1
Cuotas: 5

Resultado:
- Evento de ingreso hoy ($5000)
- 5 eventos de gasto mensuales ($1100 c/u)
```

### Ejemplo 3: Alerta de Pago
```
Evento: "Pago de renta"
Alerta personalizada:
- Mensaje: "Transferir renta hoy"
- Anticipación: 1 día antes
- Prioridad: Alta
- Notificación navegador: ✓

Resultado:
- Badge 🔔 con contador un día antes
- Notificación del navegador (si se otorgó permiso)
```

## 🎨 Guía Visual

### Indicadores del Calendario
| Indicador | Significado |
|-----------|-------------|
| 🟢 Verde | Ingreso |
| 🔴 Rojo | Gasto |
| 💰 Borde dorado | Préstamo activo |
| ↩️ Morado | Contraparte/Pago |
| 📦 Atenuado | Historial (confirmado) |
| 🔔 Badge | Notificaciones pendientes |

### Niveles de Prioridad
| Prioridad | Color | Uso |
|-----------|-------|-----|
| 🔴 Crítica | Rojo | Vencimientos hoy |
| 🟠 Alta | Naranja | Próximos 3 días |
| 🟡 Media | Amarillo | General |
| ⚪ Baja | Gris | Recordatorios suaves |

## 📝 Estructura de Datos

### Evento Básico
```javascript
{
  title: "Salario",
  desc: "Pago mensual",
  type: "ingreso",
  amount: 3000,
  category: "salario",
  frequency: "mensual",
  interval: 1,
  limit: 12,
  origin: "2025-11-01",
  seriesId: "series-abc123",
  createdAt: "2025-11-03T..."
}
```

### Préstamo Completo
```javascript
{
  ...eventoBasico,
  loan: {
    kind: "favor",
    loanId: "loan-xyz789",
    expectedReturn: 1050,
    interestValue: 50,
    interestPercent: 5,
    paymentPlan: "monthly",
    paymentFrequency: 1,
    paymentCount: 3,
    notes: "Préstamo personal"
  }
}
```

### Alerta Personalizada
```javascript
{
  message: "Pagar tarjeta de crédito",
  triggerDaysBefore: 2,
  priority: "high",
  browserNotification: true,
  createdAt: "2025-11-03T..."
}
```

## 🔧 Tecnologías

- **JavaScript ES6+** - Modules, Classes, Arrow Functions
- **Web Components** - Custom Elements, Shadow DOM
- **localStorage** - Persistencia local
- **SweetAlert2** - Modales elegantes
- **CSS3** - Grid, Flexbox, Custom Properties
- **HTML5** - Semantic markup

## 📦 Sin Dependencias de Build

Este proyecto **no requiere**:
- ❌ npm/yarn
- ❌ Webpack/Vite
- ❌ Compilación
- ❌ Backend/Base de datos

Todo funciona directamente en el navegador con ES6 modules nativos.

## 🌐 Compatibilidad

- ✅ Chrome 90+
- ✅ Firefox 88+
- ✅ Safari 14+
- ✅ Edge 90+

Requiere soporte para:
- ES6 Modules
- Custom Elements v1
- localStorage
- Notification API (opcional)

## 📚 Documentación Adicional

- `docs/nuevas-caracteristicas.md` - Documentación completa de v2.0
- `docs/components-guidelines.md` - Guías de Web Components
- `guia-uso.html` - Tutorial interactivo visual

## 🎯 Próximas Mejoras

- [ ] Exportar/Importar datos (JSON, CSV)
- [ ] Filtros avanzados de eventos
- [ ] Gráficas de ingresos/gastos
- [ ] Análisis de préstamos (interés total)
- [ ] Plantillas de eventos frecuentes
- [ ] Modo oscuro
- [ ] PWA con offline support
- [ ] Sincronización en la nube (opcional)

## 🤝 Contribuir

Las contribuciones son bienvenidas. Por favor:
1. Fork el proyecto
2. Crea una rama para tu feature
3. Commit tus cambios
4. Push a la rama
5. Abre un Pull Request

## 📄 Licencia

Este proyecto es de código abierto y está disponible bajo la Licencia MIT.

## 👨‍💻 Autor

Desarrollado con ❤️ para gestión financiera personal eficiente.

---

**Versión 2.0** - Sistema completo de préstamos con intereses y notificaciones inteligentes.

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

## �️ Migración a Base de Datos

El proyecto incluye una **estructura completa para migrar a PostgreSQL**:

### Archivos Clave
- **`docs/database-schema.sql`**: Schema completo con 6 tablas, índices y triggers
- **`docs/database-migration-guide.md`**: Guía paso a paso con ejemplos
- **`js/data-structure.js`**: Esquemas y lógica de agrupación optimizada
- **`js/database.js`**: Adaptador con modo híbrido (localStorage ↔ DB)

### Ventajas de la Migración
✅ **Sin límite de almacenamiento** (localStorage ~5-10MB máx)  
✅ **Sincronización** entre dispositivos  
✅ **Backup automático** y recuperación  
✅ **Queries optimizados** con agrupación mensual/semanal  
✅ **Escalabilidad** para grandes historiales  
✅ **Separación relacional** (eventos, préstamos, alertas)  

### Agrupación Optimizada
Estructura mensual con **6 semanas** por mes:
- **Semana 1**: Días desde inicio del mes hasta fin de primera semana
- **Semanas 2-5**: Semanas completas de 7 días
- **Semana 6**: Días restantes del mes

Beneficios: reduce tamaño de JSON, acelera queries por período específico.

### Modo Híbrido
Durante la transición, el sistema puede operar en tres modos:

```javascript
// js/database.js
const DB_CONFIG = {
  useLocalStorage: true,  // false = API/DB, true = localStorage
  enableSync: false,       // true = escritura doble (migración)
  apiUrl: 'http://localhost:3000/api'
};
```

### Proceso de Migración Rápido

```javascript
// 1. Inicializar adaptador
import { db } from './js/database.js';
const userId = crypto.randomUUID();
await db.init(userId);

// 2. Ejecutar migración
const result = await db.migrateToDatabase();
console.log(`✅ ${result.eventsCreated} eventos migrados`);

// 3. Cambiar configuración
DB_CONFIG.useLocalStorage = false;
```

Ver guía completa en **`docs/database-migration-guide.md`** con:
- Setup de backend (Node.js + Express o Supabase)
- API REST completa
- Deploy a producción (Heroku, Railway, DigitalOcean)
- Rollback y troubleshooting

## 📝 Notas

- **Modo actual**: Eventos guardados **solo en el navegador actual** (localStorage)
- **Límite**: ~5-10MB de datos en localStorage (aprox. 200-500 eventos)
- **Persistencia**: Hasta que se limpie localStorage o cache del navegador
- **Migración**: Preparado para PostgreSQL sin refactorizar código existente

## ✅ Completado en v2.0

- [x] Sistema de préstamos con intereses y planes de pago
- [x] Notificaciones y alertas personalizadas
- [x] Notificaciones del navegador
- [x] Estructura de base de datos optimizada
- [x] Migración automática desde localStorage
- [x] Schema PostgreSQL completo
- [x] API REST documentada
- [x] Guía de deployment

## 🔜 Mejoras Futuras Sugeridas

- [ ] Exportar/Importar eventos (JSON, iCal)
- [ ] Drag & drop de eventos
- [ ] Vista de lista de eventos
- [ ] Filtros y búsqueda avanzada
- [ ] Categorías/etiquetas con colores personalizados
- [ ] Autenticación de usuarios (OAuth, JWT)
- [ ] Modo oscuro automático
- [ ] PWA con offline support
- [ ] Estadísticas y gráficas (Chart.js)
- [ ] Integración con calendarios externos (Google Calendar, Outlook)

---

**Versión**: 2.0 - Database Ready  
**Autor**: Sistema modular con arquitectura ES6  
**Licencia**: MIT

