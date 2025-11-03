# 🚀 Nuevas Características Implementadas

## 1. 💰 Sistema Avanzado de Préstamos

### Características Principales

#### 📋 Campos Avanzados
El formulario de préstamos ahora incluye:

- **Retorno/Pago Esperado**: Monto total a recibir o pagar
- **Interés**:
  - Valor absoluto ($)
  - Porcentaje (%)
  - **Auto-calculable**: Al ingresar uno, el otro se calcula automáticamente
- **Notas adicionales**: Campo de texto libre para información extra

#### 📅 Planes de Pago Flexibles

**Pago Único**
- Especificar días hasta el pago
- Genera un solo evento de contraparte

**Pagos Recurrentes** (Semanal, Quincenal, Mensual)
- Frecuencia de pagos personalizable
- Número total de cuotas
- Monto dividido automáticamente entre cuotas
- Genera múltiples eventos de contraparte

**Fechas Personalizadas**
- Ingresar fechas específicas separadas por coma
- Ej: `2025-12-01, 2025-12-15, 2026-01-01`
- Monto dividido proporcionalmente

#### ⚙️ Comportamiento Especial

**Desactivación de Frecuencia Normal**
- Cuando se activa préstamo, se oculta la opción de frecuencia estándar
- Los préstamos tienen su propio sistema de generación de eventos

**Generación Automática de Contrapartes**
- Sistema inteligente que crea eventos inversos:
  - Gasto con préstamo → genera ingreso(s) futuro(s)
  - Ingreso con préstamo → genera gasto(s) futuro(s)
- Cada contraparte incluye:
  - Referencia al préstamo original (loanId)
  - Indicador de cuota (si aplica)
  - Monto calculado según plan de pagos

**Cálculo Automático de Interés**
```javascript
// Al ingresar interés en $:
porcentaje = (interés / monto) × 100
retornoEsperado = monto + interés

// Al ingresar interés en %:
interés = (monto × porcentaje) / 100
retornoEsperado = monto + interés
```

### 💡 Ejemplo de Uso

**Escenario: Préstamo a un amigo**
1. Crear gasto con préstamo activado
2. Monto: $1000
3. Interés: 5% (se calcula automáticamente $50)
4. Retorno esperado: $1050 (se actualiza automáticamente)
5. Plan: Mensual, 3 pagos
6. Resultado: 3 eventos de ingreso de $350 c/u en fechas futuras

---

## 2. 🔔 Sistema de Alertas y Notificaciones

### Características Principales

#### 📱 Tipos de Notificaciones

**Recordatorios de Eventos**
- Alertas para eventos próximos
- Configurables por días de anticipación

**Vencimiento de Préstamos**
- Alerta crítica el día del vencimiento
- Alerta de alta prioridad 3 días antes
- Incluye información del préstamo

**Alertas Personalizadas**
- Crear alertas específicas para cualquier evento
- Mensaje personalizado
- Timing configurable
- 4 niveles de prioridad

#### ⚙️ Panel de Configuración

Accesible desde el botón 🔔 en la barra superior.

**Opciones Generales**
- ✅ Habilitar/deshabilitar sistema completo
- 🌐 Notificaciones del navegador (requiere permiso)
- 📧 Notificaciones por email (preparado para futuro)

**Tipos de Alertas**
- ☑️ Recordatorios de eventos
- ☑️ Vencimiento de préstamos
- ☑️ Eventos recurrentes

**Timing**
- Días de anticipación (0-30 días)
- ☑️ Mostrar alertas al iniciar la app

#### 🎯 Niveles de Prioridad

```
🔴 CRÍTICA   - Borde rojo (#e74c3c)
🟠 ALTA      - Borde naranja (#e67e22)
🟡 MEDIA     - Borde amarillo (#f39c12)
⚪ BAJA      - Borde gris (#95a5a6)
```

#### 💬 Alertas Personalizadas por Evento

Desde la vista de detalle de cualquier evento:
1. Click en "🔔 Agregar Alerta"
2. Configurar:
   - Mensaje personalizado
   - Anticipación (mismo día hasta 1 mes antes)
   - Prioridad
   - Notificación del navegador

### 📊 Panel de Notificaciones

**Vista Principal**
- Contador en badge sobre icono 🔔
- Lista ordenada por prioridad y fecha
- Click en alerta → abre el evento directamente

**Información Mostrada**
```
📅/💰/🔔 [Icono según tipo]
⚠️ Mensaje de la alerta
💵 Monto: $XXX
📝 Descripción (si existe)
```

#### 🔄 Actualización Automática
- Se revisa cada 5 minutos
- Badge actualizado en tiempo real
- Persiste en localStorage

### 🌐 Notificaciones del Navegador

**Características**
- Solicita permiso al usuario
- Aparece aunque el navegador esté minimizado
- Click en notificación → enfoca la ventana
- Solo para alertas críticas

**Ejemplo**
```
Título: "Alertas Importantes"
Cuerpo: "Tienes 2 alerta(s) crítica(s)"
```

---

## 📂 Estructura de Datos

### Préstamo Avanzado
```javascript
{
  loan: {
    kind: 'favor' | 'contra',
    loanId: 'loan-abc123',
    expectedReturn: 1050,
    interestValue: 50,
    interestPercent: 5,
    paymentPlan: 'single' | 'weekly' | 'biweekly' | 'monthly' | 'custom',
    
    // Si single:
    recoveryDays: 30,
    
    // Si weekly/biweekly/monthly:
    paymentFrequency: 1,
    paymentCount: 3,
    
    // Si custom:
    customDates: ['2025-12-01', '2025-12-15'],
    
    notes: 'Préstamo sin garantía'
  }
}
```

### Contraparte de Préstamo
```javascript
{
  loan: {
    ...datosOriginales,
    isCounterpart: true,
    installment: 2,
    totalInstallments: 3
  }
}
```

### Alerta Personalizada
```javascript
{
  message: 'Recordar pagar renta',
  triggerDaysBefore: 3,
  priority: 'high',
  browserNotification: true,
  createdAt: '2025-11-03T...'
}
```

### Configuración de Notificaciones
```javascript
{
  enabled: true,
  browserNotifications: false,
  emailNotifications: false,
  alerts: {
    eventReminder: true,
    loanDue: true,
    recurringEvent: true,
    customAlerts: []
  },
  timing: {
    daysBefore: 1,
    hoursBefore: 24,
    showOnStartup: true
  }
}
```

---

## 🎨 Mejoras Visuales

### Indicadores en el Calendario
- 💰 Badge dorado para préstamos
- ↩️ Indicador morado para contrapartes
- Tooltips enriquecidos con toda la información

### Modal de Evento
- Panel amarillo para info de préstamo
- Desglose completo de interés y plan de pagos
- Panel morado para contrapartes con número de cuota
- Botón "🔔 Agregar Alerta" siempre visible

### Panel de Notificaciones
- Diseño limpio con colores según prioridad
- Hover effects interactivos
- Badges numerados en el header
- Scroll automático si hay muchas alertas

---

## 🔧 Archivos Modificados/Creados

### Nuevos Archivos
- `js/notifications.js` - Sistema completo de notificaciones

### Archivos Modificados
- `js/components/financial-form.js` - Campos avanzados de préstamo
- `js/events.js` - Lógica de contrapartes múltiples
- `js/modal.js` - Vista detallada + alertas personalizadas
- `js/main.js` - Integración del sistema de notificaciones
- `js/calendar.js` - Tooltips mejorados (cambio previo)
- `styles.css` - Estilos de indicadores (cambio previo)

---

## 🧪 Cómo Probar

### Préstamos Avanzados

1. **Pago Único con Interés**
   - Crear gasto de $500
   - Activar préstamo
   - Interés: 10%
   - Plan: Pago único en 30 días
   - ✅ Verificar: ingreso de $550 en 30 días

2. **Pagos Mensuales**
   - Crear gasto de $3000
   - Activar préstamo
   - Interés: $300
   - Plan: Mensual, 3 pagos
   - ✅ Verificar: 3 ingresos de $1100 c/u

3. **Fechas Personalizadas**
   - Crear ingreso de $1000
   - Activar préstamo
   - Plan: Custom
   - Fechas: 2025-12-25, 2026-01-15
   - ✅ Verificar: 2 gastos de $500

### Sistema de Notificaciones

1. **Alertas Automáticas**
   - Crear evento para mañana
   - ✅ Debe aparecer en badge de notificaciones
   - Click en 🔔 para ver detalles

2. **Alerta Personalizada**
   - Abrir evento existente
   - Click "🔔 Agregar Alerta"
   - Configurar con 3 días de anticipación
   - ✅ Aparecerá 3 días antes del evento

3. **Configuración**
   - Click en ⚙️ Configuración dentro del panel
   - Ajustar días de anticipación
   - Activar notificaciones del navegador
   - ✅ Cambios se guardan en localStorage

4. **Préstamo por Vencer**
   - Crear préstamo con pago en 1 día
   - ✅ Alerta de alta prioridad automática
   - ✅ Borde naranja en el panel

---

## 📈 Próximas Mejoras Posibles

- [ ] Historial de alertas vistas
- [ ] Exportar/importar configuración
- [ ] Integración con servicios de email
- [ ] Alertas por SMS (API externa)
- [ ] Recordatorios recurrentes independientes
- [ ] Análisis de préstamos: interés total pagado/recibido
- [ ] Gráficas de flujo de préstamos
- [ ] Plantillas de préstamos frecuentes

---

## 🎯 Resumen Ejecutivo

Se implementaron **2 sistemas mayores**:

1. **Préstamos Avanzados**
   - Cálculo automático de interés
   - 5 tipos de planes de pago
   - Generación inteligente de contrapartes
   - UI intuitiva con campos auto-calculables

2. **Notificaciones Completas**
   - 3 tipos de alertas automáticas
   - Alertas personalizadas por evento
   - Panel de configuración completo
   - Soporte para notificaciones del navegador
   - Persistencia en localStorage

**Total**: ~500 líneas de código nuevo + modificaciones extensas en 6 archivos existentes.

**Resultado**: App completamente funcional para gestión financiera personal con préstamos y recordatorios inteligentes.
