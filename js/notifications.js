/**
 * Módulo de Notificaciones y Alertas
 * Gestiona alertas para eventos próximos, vencimientos, etc.
 */

import { getCalendarDataForMonth } from './pattern-scheduler.js';
import { getPlans } from './plans-v2.js';
import { getLoans } from './loans-v2.js';
import * as ProductWishlist from './product-wishlist.js';

/**
 * Configuración de notificaciones guardada en localStorage
 */
function getCurrentUserId() {
    try {
        const session = JSON.parse(localStorage.getItem('calendar_session') || 'null');
        return session && session.userId ? session.userId : 'anon';
    } catch {
        return 'anon';
    }
}

function userScopedKey(base) {
    const userId = getCurrentUserId();
    return `${base}:${userId}`;
}

export function loadNotificationSettings() {
    try {
        const settings = localStorage.getItem(userScopedKey('notificationSettings'));
        return settings ? JSON.parse(settings) : getDefaultNotificationSettings();
    } catch (e) {
        console.error('Error al cargar configuración de notificaciones:', e);
        return getDefaultNotificationSettings();
    }
}

export function saveNotificationSettings(settings) {
    try {
        localStorage.setItem(userScopedKey('notificationSettings'), JSON.stringify(settings));
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
export function addEventAlert(eventId, eventType, alertConfig) {
    const alerts = loadEventAlerts();
    const key = `${eventType}-${eventId}`;
    
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
        const alerts = localStorage.getItem(userScopedKey('eventAlerts'));
        return alerts ? JSON.parse(alerts) : {};
    } catch (e) {
        console.error('Error al cargar alertas:', e);
        return {};
    }
}

export function saveEventAlerts(alerts) {
    try {
        localStorage.setItem(userScopedKey('eventAlerts'), JSON.stringify(alerts));
    } catch (e) {
        console.error('Error al guardar alertas:', e);
    }
}

/**
 * Obtiene todas las alertas pendientes para hoy y próximos días
 * Sistema V2: usa patrones, movimientos, planes y préstamos
 */
export async function getPendingAlerts() {
    const settings = loadNotificationSettings();
    
    if (!settings.enabled) return [];
    
    const sessionData = localStorage.getItem('calendar_session');
    const userId = sessionData ? JSON.parse(sessionData).userId : null;
    
    if (!userId) return [];
    
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    
    const pendingAlerts = [];
    const daysToCheck = settings.timing.daysBefore + 1;
    
    // Obtener datos del mes actual y siguiente
    const currentYear = today.getFullYear();
    const currentMonth = today.getMonth();
    const nextMonth = currentMonth === 11 ? 0 : currentMonth + 1;
    const nextYear = currentMonth === 11 ? currentYear + 1 : currentYear;
    
    try {
        const currentMonthData = await getCalendarDataForMonth(userId, currentYear, currentMonth);
        const nextMonthData = currentMonth !== nextMonth ? await getCalendarDataForMonth(userId, nextYear, nextMonth) : {};
        const allData = { ...currentMonthData, ...nextMonthData };
        
        // Revisar cada día en el rango
        for (let i = 0; i <= daysToCheck; i++) {
            const checkDate = new Date(today);
            checkDate.setDate(checkDate.getDate() + i);
            const dateISO = checkDate.toISOString().slice(0, 10);
            
            const dayData = allData[dateISO] || {};
            
            // Alertas de ingresos proyectados (patrones)
            if (settings.alerts.recurringEvent && dayData.projected_incomes) {
                dayData.projected_incomes.forEach(projection => {
                    // Solo alertar si NO tiene movimiento confirmado
                    if (!projection.has_confirmed_movement) {
                        pendingAlerts.push({
                            type: 'projected',
                            subtype: 'income',
                            priority: i === 0 ? 'medium' : 'low',
                            date: dateISO,
                            id: projection.pattern_id,
                            title: projection.name,
                            amount: projection.expected_amount,
                            message: i === 0
                                ? `Hoy (proyectado): 💵 ${projection.name}`
                                : `En ${i} día${i > 1 ? 's' : ''} (proyectado): 💵 ${projection.name}`,
                            daysUntil: i,
                            icon: '💵'
                        });
                    }
                });
            }
            
            // Alertas de gastos proyectados (patrones)
            if (settings.alerts.recurringEvent && dayData.projected_expenses) {
                dayData.projected_expenses.forEach(projection => {
                    // Solo alertar si NO tiene movimiento confirmado
                    if (!projection.has_confirmed_movement) {
                        pendingAlerts.push({
                            type: 'projected',
                            subtype: 'expense',
                            priority: i === 0 ? 'high' : 'medium',
                            date: dateISO,
                            id: projection.pattern_id,
                            title: projection.name,
                            amount: projection.expected_amount,
                            message: i === 0
                                ? `Hoy (pendiente): 💸 ${projection.name}`
                                : `En ${i} día${i > 1 ? 's' : ''} (pendiente): 💸 ${projection.name}`,
                            daysUntil: i,
                            icon: '💸'
                        });
                    }
                });
            }
            
            // Alertas de préstamos
            if (settings.alerts.loanDue && dayData.loan_movements) {
                dayData.loan_movements.forEach(loan => {
                    if (i <= 3) { // 3 días antes del vencimiento
                        pendingAlerts.push({
                            type: 'loan',
                            priority: i === 0 ? 'critical' : 'high',
                            date: dateISO,
                            id: loan.loan_id,
                            title: loan.title,
                            amount: loan.confirmed_amount,
                            message: i === 0
                                ? `⚠️ Vence hoy: Préstamo ${loan.title}`
                                : `💰 Vence en ${i} día${i > 1 ? 's' : ''}: ${loan.title}`,
                            daysUntil: i,
                            icon: '💰'
                        });
                    }
                });
            }
        }
        
        // Alertas de metas/planes próximas a vencer
        if (settings.alerts.eventReminder) {
            try {
                const plans = await getPlans();
                const activePlans = plans.filter(p => p.status === 'active');
                
                activePlans.forEach(plan => {
                    if (plan.target_date) {
                        const targetDate = new Date(plan.target_date);
                        targetDate.setHours(0, 0, 0, 0);
                        const diffDays = Math.floor((targetDate - today) / (1000 * 60 * 60 * 24));
                        
                        // Alertar si está dentro del rango de días a revisar
                        if (diffDays >= 0 && diffDays <= daysToCheck) {
                            // Calcular progreso desde current_amount
                            const progress = plan.target_amount > 0 
                                ? (parseFloat(plan.current_amount || 0) / parseFloat(plan.target_amount)) * 100
                                : 0;
                            const isAtRisk = progress < 50 && diffDays <= 7;
                            
                            pendingAlerts.push({
                                type: 'plan',
                                priority: isAtRisk ? 'high' : 'medium',
                                date: plan.target_date,
                                id: plan.id,
                                title: plan.name,
                                amount: plan.target_amount,
                                progress: progress,
                                message: diffDays === 0
                                    ? `🎯 Hoy vence: ${plan.name} (${progress.toFixed(0)}% completado)`
                                    : `🎯 Vence en ${diffDays} día${diffDays > 1 ? 's' : ''}: ${plan.name} (${progress.toFixed(0)}% completado)`,
                                daysUntil: diffDays,
                                icon: '🎯',
                                atRisk: isAtRisk
                            });
                        }
                    }
                });
            } catch (error) {
                console.error('Error loading plans for alerts:', error);
            }
        }
        
    } catch (error) {
        console.error('Error getting pending alerts:', error);
        return [];
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
 * Muestra las alertas en un modal/popup
 */
function showAlertsModal(alerts) {
    // Remover modal existente si hay
    const existingModal = document.getElementById('alerts-modal');
    if (existingModal) existingModal.remove();
    
    const priorityColor = {
        critical: '#e74c3c',
        high: '#e67e22',
        medium: '#f39c12',
        low: '#95a5a6'
    };
    
    const alertsHTML = alerts.map(alert => {
        const color = priorityColor[alert.priority] || '#95a5a6';
        const icon = alert.icon || '🔔';
        const bgColor = alert.atRisk ? '#fee2e2' : '#f9f9f9';
        
        return `
            <div class="alert-item" style="
                border-left: 4px solid ${color};
                padding: 12px 14px;
                margin-bottom: 10px;
                background: ${bgColor};
                border-radius: 6px;
                cursor: pointer;
                transition: all 0.2s;
            " data-date="${alert.date || ''}" data-type="${alert.type || ''}">
                <div style="display: flex; align-items: flex-start; gap: 10px;">
                    <span style="font-size: 1.3em;">${icon}</span>
                    <div style="flex: 1;">
                        <div style="font-weight: 600; color: ${color}; margin-bottom: 4px;">
                            ${alert.message || 'Alerta'}
                        </div>
                        ${alert.amount ? `<div style="font-size: 0.9em; color: #555;">Monto: $${alert.amount.toLocaleString('es-MX')}</div>` : ''}
                        ${alert.progress !== undefined ? `<div style="font-size: 0.85em; color: #666;">Progreso: ${alert.progress.toFixed(0)}%</div>` : ''}
                        ${alert.daysUntil !== undefined ? `<div style="font-size: 0.85em; color: #888;">En ${alert.daysUntil} día(s)</div>` : ''}
                        ${alert.atRisk ? `<div style="font-size: 0.85em; color: #e74c3c; font-weight: 600; margin-top: 4px;">⚠️ En riesgo</div>` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');
    
    const modal = document.createElement('div');
    modal.id = 'alerts-modal';
    modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10000;
        animation: fadeIn 0.2s ease;
    `;
    
    modal.innerHTML = `
        <div style="
            background: white;
            border-radius: 12px;
            max-width: 450px;
            width: 90%;
            max-height: 80vh;
            overflow: hidden;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            animation: slideUp 0.3s ease;
        ">
            <div style="
                padding: 16px 20px;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                display: flex;
                align-items: center;
                justify-content: space-between;
            ">
                <h3 style="margin: 0; font-size: 1.1rem;">🔔 Alertas Pendientes (${alerts.length})</h3>
                <button id="close-alerts-modal" style="
                    background: rgba(255,255,255,0.2);
                    border: none;
                    color: white;
                    width: 30px;
                    height: 30px;
                    border-radius: 50%;
                    cursor: pointer;
                    font-size: 1.2rem;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                ">×</button>
            </div>
            <div style="padding: 16px; max-height: 60vh; overflow-y: auto;">
                ${alertsHTML}
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
    
    // Cerrar modal
    const closeBtn = modal.querySelector('#close-alerts-modal');
    closeBtn.addEventListener('click', () => modal.remove());
    modal.addEventListener('click', (e) => {
        if (e.target === modal) modal.remove();
    });
    
    // Agregar estilos de animación si no existen
    if (!document.getElementById('alerts-modal-styles')) {
        const style = document.createElement('style');
        style.id = 'alerts-modal-styles';
        style.textContent = `
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            @keyframes slideUp {
                from { transform: translateY(20px); opacity: 0; }
                to { transform: translateY(0); opacity: 1; }
            }
            #alerts-modal .alert-item:hover {
                transform: translateX(4px);
                box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            }
        `;
        document.head.appendChild(style);
    }
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
export function displayAlerts(alerts, container = null) {
    if (!alerts || alerts.length === 0) return;
    
    // Si no se proporciona container, crear un modal/popup
    if (!container) {
        showAlertsModal(alerts);
        return;
    }
    
    const alertHTML = alerts.map(alert => {
        const priorityColor = {
            critical: '#e74c3c',
            high: '#e67e22',
            medium: '#f39c12',
            low: '#95a5a6'
        }[alert.priority] || '#95a5a6';
        
        const icon = alert.icon || '🔔';
        
        // Determinar color de fondo para alertas en riesgo
        const bgColor = alert.atRisk ? '#fee2e2' : '#f9f9f9';
        
        return `
            <div class="alert-item" style="
                border-left: 4px solid ${priorityColor};
                padding: 10px 12px;
                margin-bottom: 8px;
                background: ${bgColor};
                border-radius: 4px;
                cursor: pointer;
                transition: background 0.2s;
            " data-date="${alert.date}" data-id="${alert.id}" data-type="${alert.type}">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 1.2em;">${icon}</span>
                    <div style="flex: 1;">
                        <div style="font-weight: 600; color: ${priorityColor};">
                            ${alert.message}
                        </div>
                        ${alert.amount ? `<div style="font-size: 0.9em; color: #666; margin-top: 4px;">Monto: $${alert.amount}</div>` : ''}
                        ${alert.progress !== undefined ? `<div style="font-size: 0.85em; color: #888; margin-top: 2px;">Progreso: ${alert.progress.toFixed(0)}%</div>` : ''}
                        ${alert.atRisk ? `<div style="font-size: 0.85em; color: #e74c3c; margin-top: 2px; font-weight: 600;">⚠️ En riesgo de no cumplirse</div>` : ''}
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
export async function initNotificationSystem() {
    const settings = loadNotificationSettings();
    
    if (settings.enabled && settings.timing.showOnStartup) {
        const alerts = await getPendingAlerts();
        
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
export function markAlertAsRead(eventId, eventType) {
    const readAlerts = loadReadAlerts();
    const key = `${eventType}-${eventId}`;
    
    if (!readAlerts[key]) {
        readAlerts[key] = {
            readAt: new Date().toISOString()
        };
        saveReadAlerts(readAlerts);
    }
}

function loadReadAlerts() {
    try {
        const alerts = localStorage.getItem(userScopedKey('readAlerts'));
        return alerts ? JSON.parse(alerts) : {};
    } catch (e) {
        return {};
    }
}

function saveReadAlerts(alerts) {
    try {
        localStorage.setItem(userScopedKey('readAlerts'), JSON.stringify(alerts));
    } catch (e) {
        console.error('Error al guardar alertas leídas:', e);
    }
}

// ==================== ALERTAS DE PRODUCTOS EN LÍNEA ====================

/**
 * Verificar productos inactivos y mostrar alertas
 * @param {string} userId - ID del usuario
 * @returns {Promise<Array>} - Lista de productos con alertas
 */
export async function checkInactiveProductAlerts(userId) {
    try {
        const updatedProducts = await ProductWishlist.checkInactiveProducts(userId);
        
        if (updatedProducts.length > 0) {
            // Mostrar alerta al usuario
            const productList = updatedProducts.map(item => `
                <li style="margin-bottom: 8px;">
                    <strong>${item.product.product_name?.substring(0, 40)}...</strong><br>
                    <small style="color: #dc2626;">
                        Fecha desplazada ${item.daysDelayed} días 
                        (${formatDateSpanish(item.oldDate)} → ${formatDateSpanish(item.newDate)})
                    </small>
                </li>
            `).join('');

            await Swal.fire({
                icon: 'warning',
                title: '⚠️ Productos sin actividad',
                html: `
                    <div style="text-align: left;">
                        <p>Los siguientes productos no han recibido aportes y su fecha estimada se ha actualizado:</p>
                        <ul style="padding-left: 20px; max-height: 200px; overflow-y: auto;">
                            ${productList}
                        </ul>
                        <p style="margin-top: 16px; color: #6b7280; font-size: 0.875rem;">
                            💡 Realiza aportes regularmente para mantener tu fecha objetivo.
                        </p>
                    </div>
                `,
                confirmButtonText: 'Ver mis productos',
                showCancelButton: true,
                cancelButtonText: 'Entendido'
            }).then(result => {
                if (result.isConfirmed) {
                    // Abrir modal de productos
                    import('./product-wishlist-modals.js').then(module => {
                        module.openProductWishlistModal();
                    });
                }
            });
        }

        return updatedProducts;
    } catch (error) {
        console.error('Error checking inactive products:', error);
        return [];
    }
}

/**
 * Obtener productos próximos a completarse
 * @param {string} userId - ID del usuario
 * @param {number} daysThreshold - Días para considerar "próximo"
 * @returns {Promise<Array>} - Lista de productos próximos
 */
export async function getProductsNearCompletion(userId, daysThreshold = 30) {
    try {
        const products = await ProductWishlist.getProductWishlist(userId, { status: 'active' });
        
        return products.filter(p => 
            p.days_remaining !== null && 
            p.days_remaining > 0 && 
            p.days_remaining <= daysThreshold
        );
    } catch (error) {
        console.error('Error getting products near completion:', error);
        return [];
    }
}

/**
 * Verificar y notificar productos próximos a completarse
 * @param {string} userId - ID del usuario
 */
export async function checkProductCompletionAlerts(userId) {
    try {
        const nearCompletion = await getProductsNearCompletion(userId, 7); // 7 días
        
        if (nearCompletion.length > 0) {
            const productList = nearCompletion.map(p => `
                <div style="display: flex; align-items: center; gap: 12px; padding: 8px; background: #f0fdf4; border-radius: 8px; margin-bottom: 8px;">
                    <span style="font-size: 1.5rem;">🎉</span>
                    <div style="flex: 1;">
                        <strong>${p.product_name?.substring(0, 30)}...</strong>
                        <div style="font-size: 0.875rem; color: #059669;">
                            ¡Solo faltan ${p.days_remaining} día(s)!
                            (${p.progress_percent}% completado)
                        </div>
                    </div>
                </div>
            `).join('');

            await Swal.fire({
                icon: 'success',
                title: '🎊 ¡Casi lo logras!',
                html: `
                    <div style="text-align: left;">
                        <p>Estos productos están muy cerca de completarse:</p>
                        ${productList}
                    </div>
                `,
                confirmButtonText: '¡Genial!'
            });
        }
    } catch (error) {
        console.error('Error checking product completion alerts:', error);
    }
}

function formatDateSpanish(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('es-MX', { day: 'numeric', month: 'short' });
}
