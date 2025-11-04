/**
 * Punto de entrada principal de la aplicación
 */

import { Calendar } from './calendar.js';
import { syncDownMonth } from './events.js';
import { 
    initNotificationSystem, 
    getPendingAlerts, 
    displayAlerts,
    loadNotificationSettings,
    saveNotificationSettings,
    requestBrowserNotificationPermission
} from './notifications.js';

// Current user session
let currentUser = null;

// Inicializar cuando el DOM esté listo
document.addEventListener('DOMContentLoaded', () => {
    // Initialize user session
    initUserSession();
    
    const calendar = new Calendar('calendar-body');
    calendar.init();

    // Sincronizar solo el mes visible desde Supabase y refrescar indicadores
    try {
        const d = calendar.currentDate;
        syncDownMonth(d.getFullYear(), d.getMonth()).then(() => {
            calendar.refreshAllEventIndicators();
        });
    } catch (e) { /* ignore */ }
    
    // Inicializar sistema de notificaciones
    initNotificationSystem();
    
    // Crear panel de notificaciones en el header
    createNotificationPanel();
    
    // Actualizar notificaciones cada 5 minutos
    setInterval(() => {
        updateNotifications();
    }, 5 * 60 * 1000);

    // Re-sincronizar al navegar entre meses
    const reSync = () => {
        const d = calendar.currentDate;
        syncDownMonth(d.getFullYear(), d.getMonth()).then(() => {
            calendar.refreshAllEventIndicators();
        });
    };
    const prevBtn = document.getElementById('prev-month');
    const nextBtn = document.getElementById('next-month');
    if (prevBtn) prevBtn.addEventListener('click', () => setTimeout(reSync, 0));
    if (nextBtn) nextBtn.addEventListener('click', () => setTimeout(reSync, 0));
});

/**
 * Initialize user session and display user info
 */
function initUserSession() {
    try {
        const sessionData = localStorage.getItem('calendar_session');
        if (!sessionData) {
            window.location.href = '../index.html';
            return;
        }

        currentUser = JSON.parse(sessionData);
        
        // Display user name
        const userNameElement = document.getElementById('user-name');
        if (userNameElement) {
            userNameElement.textContent = currentUser.name || currentUser.username;
        }

        // Setup logout button
        const logoutBtn = document.getElementById('logout-btn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', handleLogout);
        }
    } catch (err) {
        console.error('Error initializing session:', err);
        window.location.href = '../index.html';
    }
}

/**
 * Handle user logout
 */
async function handleLogout() {
    const result = await Swal.fire({
        title: '¿Cerrar sesión?',
        text: '¿Estás seguro que deseas salir?',
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: 'Sí, salir',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#667eea',
        cancelButtonColor: '#718096'
    });

    if (result.isConfirmed) {
        // Cerrar sesión de Supabase Auth explícitamente
        try {
            const { supabase } = await import('./supabase-client.js');
            await supabase.auth.signOut();
        } catch (_) { /* ignore */ }
        // Limpiar TODO el almacenamiento del navegador
        try {
            // Limpiar completamente localStorage y sessionStorage
            localStorage.clear();
            sessionStorage.clear();
        } catch (e) {
            // Fallback: al menos eliminar la sesión si clear falla
            try { localStorage.removeItem('calendar_session'); } catch (_) {}
        }
        
        // Show goodbye message
        await Swal.fire({
            icon: 'success',
            title: 'Sesión cerrada',
            text: '¡Hasta pronto!',
            timer: 1500,
            showConfirmButton: false
        });

        // Redirect to login
        window.location.href = '../index.html';
    }
}

/**
 * Get current user ID for database queries
 */
export function getCurrentUserId() {
    return currentUser?.userId || null;
}

/**
 * Crea el panel de notificaciones en la UI
 */
function createNotificationPanel() {
    const header = document.querySelector('.calendar-controls');
    if (!header) return;
    
    // Crear botón de notificaciones
    const notifButton = document.createElement('button');
    notifButton.id = 'notification-button';
    notifButton.innerHTML = `
        🔔 
        <span id="notification-badge" style="
            display: none;
            position: absolute;
            top: -5px;
            right: -5px;
            background: #e74c3c;
            color: white;
            border-radius: 10px;
            padding: 2px 6px;
            font-size: 0.75rem;
            font-weight: bold;
        "></span>
    `;
    notifButton.style.position = 'relative';
    notifButton.title = 'Ver notificaciones';
    
    notifButton.addEventListener('click', () => {
        showNotificationModal();
    });
    
    // Insertar después del título del mes
    const monthYear = header.querySelector('.month-year');
    if (monthYear && monthYear.nextSibling) {
        header.insertBefore(notifButton, monthYear.nextSibling);
    } else {
        header.appendChild(notifButton);
    }
    
    // Actualizar contador inicial
    updateNotifications();
}

/**
 * Actualiza el panel de notificaciones
 */
function updateNotifications() {
    const alerts = getPendingAlerts();
    const badge = document.getElementById('notification-badge');
    
    if (badge) {
        if (alerts.length > 0) {
            badge.textContent = alerts.length > 99 ? '99+' : alerts.length;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    }
}

/**
 * Muestra el modal de notificaciones usando SweetAlert2
 */
function showNotificationModal() {
    const alerts = getPendingAlerts();
    const settings = loadNotificationSettings();
    
    if (alerts.length === 0) {
        Swal.fire({
            icon: 'info',
            title: 'Sin notificaciones',
            text: 'No tienes alertas pendientes en este momento.',
            confirmButtonText: 'Entendido'
        });
        return;
    }
    
    const container = document.createElement('div');
    displayAlerts(alerts, container);
    
    // Agregar botón de configuración
    const configButton = `
        <button id="notif-settings-btn" style="
            margin-top: 10px;
            padding: 8px 12px;
            background: #3498db;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        ">⚙️ Configuración de Notificaciones</button>
    `;
    
    container.innerHTML += configButton;
    
    Swal.fire({
        title: '🔔 Notificaciones',
        html: container,
        width: '600px',
        showConfirmButton: false,
        showCancelButton: true,
        cancelButtonText: 'Cerrar',
        didOpen: () => {
            // Añadir listeners a las alertas
            const alertItems = container.querySelectorAll('.alert-item');
            alertItems.forEach(item => {
                item.addEventListener('click', function() {
                    const date = this.dataset.date;
                    const index = this.dataset.index;
                    // Abrir modal del evento
                    import('./modal.js').then(module => {
                        module.openEventModal(date, (dates) => {
                            // Callback de actualización
                            console.log('Evento actualizado:', dates);
                        });
                    });
                    Swal.close();
                });
                
                item.addEventListener('mouseenter', function() {
                    this.style.background = '#e8e8e8';
                });
                
                item.addEventListener('mouseleave', function() {
                    this.style.background = '#f9f9f9';
                });
            });
            
            // Listener del botón de configuración
            const settingsBtn = container.querySelector('#notif-settings-btn');
            if (settingsBtn) {
                settingsBtn.addEventListener('click', () => {
                    showNotificationSettings();
                });
            }
        }
    });
}

/**
 * Muestra el modal de configuración de notificaciones
 */
function showNotificationSettings() {
    const settings = loadNotificationSettings();
    
    const html = `
        <div style="text-align: left; padding: 10px;">
            <label style="display: block; margin-bottom: 10px;">
                <input type="checkbox" id="notif-enabled" ${settings.enabled ? 'checked' : ''}>
                Habilitar notificaciones
            </label>
            
            <label style="display: block; margin-bottom: 10px;">
                <input type="checkbox" id="notif-browser" ${settings.browserNotifications ? 'checked' : ''}>
                Notificaciones del navegador
            </label>
            
            <hr style="margin: 15px 0;">
            
            <h4 style="margin: 10px 0;">Tipos de Alertas</h4>
            
            <label style="display: block; margin-bottom: 8px;">
                <input type="checkbox" id="alert-event" ${settings.alerts.eventReminder ? 'checked' : ''}>
                Recordatorios de eventos
            </label>
            
            <label style="display: block; margin-bottom: 8px;">
                <input type="checkbox" id="alert-loan" ${settings.alerts.loanDue ? 'checked' : ''}>
                Vencimiento de préstamos
            </label>
            
            <label style="display: block; margin-bottom: 8px;">
                <input type="checkbox" id="alert-recurring" ${settings.alerts.recurringEvent ? 'checked' : ''}>
                Eventos recurrentes
            </label>
            
            <hr style="margin: 15px 0;">
            
            <h4 style="margin: 10px 0;">Timing</h4>
            
            <label style="display: block; margin-bottom: 8px;">
                Alertar con 
                <input type="number" id="timing-days" min="0" max="30" value="${settings.timing.daysBefore}" style="width: 60px; padding: 4px;">
                días de anticipación
            </label>
            
            <label style="display: block; margin-bottom: 10px;">
                <input type="checkbox" id="timing-startup" ${settings.timing.showOnStartup ? 'checked' : ''}>
                Mostrar alertas al iniciar
            </label>
        </div>
    `;
    
    Swal.fire({
        title: '⚙️ Configuración de Notificaciones',
        html: html,
        showCancelButton: true,
        confirmButtonText: 'Guardar',
        cancelButtonText: 'Cancelar',
        preConfirm: async () => {
            const newSettings = {
                enabled: document.getElementById('notif-enabled').checked,
                browserNotifications: document.getElementById('notif-browser').checked,
                emailNotifications: settings.emailNotifications,
                alerts: {
                    eventReminder: document.getElementById('alert-event').checked,
                    loanDue: document.getElementById('alert-loan').checked,
                    recurringEvent: document.getElementById('alert-recurring').checked,
                    customAlerts: settings.alerts.customAlerts
                },
                timing: {
                    daysBefore: parseInt(document.getElementById('timing-days').value) || 1,
                    hoursBefore: settings.timing.hoursBefore,
                    showOnStartup: document.getElementById('timing-startup').checked
                }
            };
            
            // Si se habilitaron notificaciones del navegador, pedir permiso
            if (newSettings.browserNotifications && !settings.browserNotifications) {
                const granted = await requestBrowserNotificationPermission();
                if (!granted) {
                    Swal.showValidationMessage('No se otorgó permiso para notificaciones del navegador');
                    newSettings.browserNotifications = false;
                }
            }
            
            return newSettings;
        }
    }).then(result => {
        if (result.isConfirmed && result.value) {
            saveNotificationSettings(result.value);
            updateNotifications();
            Swal.fire({
                icon: 'success',
                title: 'Configuración guardada',
                timer: 1500,
                showConfirmButton: false
            });
        }
    });
}

