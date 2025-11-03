/**
 * Módulo de Notificaciones y Alertas
 * Gestiona alertas para eventos próximos, vencimientos, etc.
 */

import { loadEvents } from './events.js';

/**
 * Configuración de notificaciones guardada en localStorage
 */
export function loadNotificationSettings() {
    try {
        const settings = localStorage.getItem('notificationSettings');
        return settings ? JSON.parse(settings) : getDefaultNotificationSettings();
    } catch (e) {
        console.error('Error al cargar configuración de notificaciones:', e);
        return getDefaultNotificationSettings();
    }
}

export function saveNotificationSettings(settings) {
    try {
        localStorage.setItem('notificationSettings', JSON.stringify(settings));
    } catch (e) {
        console.error('Error al guardar configuración de notificaciones:', e);
    }
}

function getDefaultNotificationSettings() {
    return {
        enabled: true,
        browserNotifications: false, // requiere permiso
        emailNotifications: false,
        alerts: {
            eventReminder: true,      // recordatorio de eventos
            loanDue: true,            // vencimiento de préstamos
            recurringEvent: true,     // eventos recurrentes próximos
            customAlerts: []          // alertas personalizadas
        },
        timing: {
            daysBefore: 1,            // días antes del evento
            hoursBefore: 24,          // horas antes del evento
            showOnStartup: true       // mostrar alertas al cargar
        }
    };
}

/**
 * Guarda una alerta personalizada para un evento
 */
export function addEventAlert(dateISO, eventIndex, alertConfig) {
    const alerts = loadEventAlerts();
    const key = `${dateISO}-${eventIndex}`;
    
    if (!alerts[key]) {
        alerts[key] = [];
    }
    
    alerts[key].push({
        ...alertConfig,
        createdAt: new Date().toISOString()
    });
    
    saveEventAlerts(alerts);
}

/**
 * Carga todas las alertas de eventos
 */
export function loadEventAlerts() {
    try {
        const alerts = localStorage.getItem('eventAlerts');
        return alerts ? JSON.parse(alerts) : {};
    } catch (e) {
        console.error('Error al cargar alertas:', e);
        return {};
    }
}

export function saveEventAlerts(alerts) {
    try {
        localStorage.setItem('eventAlerts', JSON.stringify(alerts));
    } catch (e) {
        console.error('Error al guardar alertas:', e);
    }
}

/**
 * Obtiene todas las alertas pendientes para hoy y próximos días
 */
export function getPendingAlerts() {
    const events = loadEvents();
    const alerts = loadEventAlerts();
    const settings = loadNotificationSettings();
    
    if (!settings.enabled) return [];
    
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    
    const pendingAlerts = [];
    const daysToCheck = settings.timing.daysBefore + 1;
    
    for (let i = 0; i <= daysToCheck; i++) {
        const checkDate = new Date(today);
        checkDate.setDate(checkDate.getDate() + i);
        const dateISO = checkDate.toISOString().slice(0, 10);
        
        const dayEvents = events[dateISO] || [];
        
        dayEvents.forEach((event, idx) => {
            // Alerta de evento normal
            if (settings.alerts.eventReminder && i <= settings.timing.daysBefore) {
                pendingAlerts.push({
                    type: 'event',
                    priority: i === 0 ? 'high' : 'medium',
                    date: dateISO,
                    eventIndex: idx,
                    event: event,
                    message: i === 0 
                        ? `Hoy: ${event.title}` 
                        : `En ${i} día${i > 1 ? 's' : ''}: ${event.title}`,
                    daysUntil: i
                });
            }
            
            // Alerta de préstamo por vencer
            if (settings.alerts.loanDue && event.loan && !event.loan.isCounterpart) {
                if (i <= 3) { // 3 días antes del vencimiento
                    pendingAlerts.push({
                        type: 'loan',
                        priority: i === 0 ? 'critical' : 'high',
                        date: dateISO,
                        eventIndex: idx,
                        event: event,
                        message: i === 0
                            ? `⚠️ Vence hoy: Préstamo ${event.title}`
                            : `💰 Vence en ${i} día${i > 1 ? 's' : ''}: ${event.title}`,
                        daysUntil: i
                    });
                }
            }
            
            // Alertas personalizadas
            const customKey = `${dateISO}-${idx}`;
            if (alerts[customKey]) {
                alerts[customKey].forEach(alert => {
                    if (shouldTriggerAlert(alert, dateISO)) {
                        pendingAlerts.push({
                            type: 'custom',
                            priority: alert.priority || 'medium',
                            date: dateISO,
                            eventIndex: idx,
                            event: event,
                            message: alert.message || `Alerta: ${event.title}`,
                            daysUntil: i,
                            customConfig: alert
                        });
                    }
                });
            }
        });
    }
    
    // Ordenar por prioridad y fecha
    const priorityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
    pendingAlerts.sort((a, b) => {
        if (priorityOrder[a.priority] !== priorityOrder[b.priority]) {
            return priorityOrder[a.priority] - priorityOrder[b.priority];
        }
        return a.daysUntil - b.daysUntil;
    });
    
    return pendingAlerts;
}

/**
 * Determina si una alerta personalizada debe activarse
 */
function shouldTriggerAlert(alert, eventDateISO) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const eventDate = new Date(eventDateISO + 'T00:00:00');
    const diffDays = Math.floor((eventDate - today) / (1000 * 60 * 60 * 24));
    
    if (alert.triggerDaysBefore !== undefined) {
        return diffDays === alert.triggerDaysBefore;
    }
    
    return diffDays <= 1; // por defecto, alertar el día anterior o el mismo día
}

/**
 * Muestra alertas en la UI
 */
export function displayAlerts(alerts, container) {
    if (!alerts || alerts.length === 0) return;
    
    const alertHTML = alerts.map(alert => {
        const priorityColor = {
            critical: '#e74c3c',
            high: '#e67e22',
            medium: '#f39c12',
            low: '#95a5a6'
        }[alert.priority] || '#95a5a6';
        
        const icon = {
            event: '📅',
            loan: '💰',
            custom: '🔔'
        }[alert.type] || '🔔';
        
        return `
            <div class="alert-item" style="
                border-left: 4px solid ${priorityColor};
                padding: 10px 12px;
                margin-bottom: 8px;
                background: #f9f9f9;
                border-radius: 4px;
                cursor: pointer;
                transition: background 0.2s;
            " data-date="${alert.date}" data-index="${alert.eventIndex}">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 1.2em;">${icon}</span>
                    <div style="flex: 1;">
                        <div style="font-weight: 600; color: ${priorityColor};">
                            ${alert.message}
                        </div>
                        ${alert.event.amount ? `<div style="font-size: 0.9em; color: #666; margin-top: 4px;">Monto: $${alert.event.amount}</div>` : ''}
                        ${alert.event.desc ? `<div style="font-size: 0.85em; color: #888; margin-top: 2px;">${alert.event.desc}</div>` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');
    
    container.innerHTML = `
        <div style="margin-bottom: 16px;">
            <h3 style="margin: 0 0 10px 0; color: #2c3e50; font-size: 1.1rem;">
                🔔 Notificaciones (${alerts.length})
            </h3>
            ${alertHTML}
        </div>
    `;
}

/**
 * Solicita permiso para notificaciones del navegador
 */
export async function requestBrowserNotificationPermission() {
    if (!('Notification' in window)) {
        console.warn('Este navegador no soporta notificaciones.');
        return false;
    }
    
    if (Notification.permission === 'granted') {
        return true;
    }
    
    if (Notification.permission !== 'denied') {
        const permission = await Notification.requestPermission();
        return permission === 'granted';
    }
    
    return false;
}

/**
 * Muestra una notificación del navegador
 */
export function showBrowserNotification(title, options = {}) {
    if (!('Notification' in window) || Notification.permission !== 'granted') {
        return;
    }
    
    const notification = new Notification(title, {
        icon: '/icon.png', // opcional: agregar un icono
        badge: '/badge.png',
        ...options
    });
    
    notification.onclick = () => {
        window.focus();
        notification.close();
    };
    
    return notification;
}

/**
 * Inicializa el sistema de notificaciones
 */
export function initNotificationSystem() {
    const settings = loadNotificationSettings();
    
    if (settings.enabled && settings.timing.showOnStartup) {
        const alerts = getPendingAlerts();
        
        if (alerts.length > 0) {
            // Mostrar badge de contador en la UI
            updateNotificationBadge(alerts.length);
            
            // Si hay notificaciones del navegador habilitadas
            if (settings.browserNotifications && Notification.permission === 'granted') {
                const criticalAlerts = alerts.filter(a => a.priority === 'critical');
                if (criticalAlerts.length > 0) {
                    showBrowserNotification('Alertas Importantes', {
                        body: `Tienes ${criticalAlerts.length} alerta(s) crítica(s)`,
                        tag: 'calendar-alerts'
                    });
                }
            }
        }
    }
}

/**
 * Actualiza el badge de notificaciones en la UI
 */
export function updateNotificationBadge(count) {
    const badge = document.getElementById('notification-badge');
    if (badge) {
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : count;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    }
}

/**
 * Marca una alerta como vista/leída
 */
export function markAlertAsRead(dateISO, eventIndex) {
    const readAlerts = loadReadAlerts();
    const key = `${dateISO}-${eventIndex}`;
    
    if (!readAlerts[key]) {
        readAlerts[key] = {
            readAt: new Date().toISOString()
        };
        saveReadAlerts(readAlerts);
    }
}

function loadReadAlerts() {
    try {
        const alerts = localStorage.getItem('readAlerts');
        return alerts ? JSON.parse(alerts) : {};
    } catch (e) {
        return {};
    }
}

function saveReadAlerts(alerts) {
    try {
        localStorage.setItem('readAlerts', JSON.stringify(alerts));
    } catch (e) {
        console.error('Error al guardar alertas leídas:', e);
    }
}
