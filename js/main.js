/**
 * Punto de entrada principal de la aplicación
 */

import { Calendar } from './calendar.js';
import { showCreateEventDialog, showBalanceSummaryDialog } from './calendar-modals-v2.js';
import { computeDailyStats, computeWeeklyStatsForMonth, computeMonthlyFutureStats, computeAnnualStatsGroup, renderMoney } from './stats.js';
import { 
    initNotificationSystem, 
    getPendingAlerts, 
    displayAlerts,
    loadNotificationSettings,
    saveNotificationSettings,
    requestBrowserNotificationPermission
} from './notifications.js';
import { openPlanningModal, setUserId } from './planning-modals.js';
import { getConfirmedBalanceSummary, formatCurrency } from './balance.js';

// Current user session
let currentUser = null;
let calendarInstance = null;

// Exportar currentUser para acceso global
window.getCurrentUser = () => currentUser;

// Inicializar cuando el DOM esté listo
document.addEventListener('DOMContentLoaded', () => {
    // Initialize user session
    initUserSession();
    
    const calendar = new Calendar('calendar-body');
    calendar.init();
    calendarInstance = calendar;

    // Sincronizar solo el mes visible desde Supabase y refrescar indicadores
    // DESHABILITADO: La tabla 'events' antigua ya no existe, ahora usamos sistema V2
    // try {
    //     const d = calendar.currentDate;
    //     syncDownMonth(d.getFullYear(), d.getMonth()).then(() => {
    //         calendar.refreshAllEventIndicators();
    //     });
    // } catch (e) { /* ignore */ }
    
    // V2: Ya no es necesario llamar refreshAllEventIndicators aquí
    // porque calendar.render() ya lo hace automáticamente
    
    // Inicializar sistema de notificaciones (async)
    initNotificationSystem().catch(err => console.error('Error initializing notifications:', err));
    
    // Crear panel de notificaciones en el header
    createNotificationPanel();

    // Configurar botones de ayuda y configuración
    const helpBtn = document.getElementById('help-btn');
    if (helpBtn) helpBtn.addEventListener('click', openHelpGuide);
    const settingsBtn = document.getElementById('settings-btn');
    if (settingsBtn) settingsBtn.addEventListener('click', openSettingsPanel);
    const statsBtn = document.getElementById('stats-btn');
    if (statsBtn) statsBtn.addEventListener('click', openStatsDrawer);
    const planningBtn = document.getElementById('planning-btn');
    if (planningBtn) planningBtn.addEventListener('click', openPlanningModal);
    
    // Botón de acceso rápido a mis finanzas
    const quickAccessBtn = document.getElementById('quick-access-btn');
    if (quickAccessBtn) quickAccessBtn.addEventListener('click', openQuickAccessPanel);

    // Acciones rápidas: agregar ingreso/gasto para hoy
    const quickIncome = document.getElementById('quick-add-income');
    const quickExpense = document.getElementById('quick-add-expense');
    const openForToday = (type) => {
        const todayISO = new Date().toISOString().slice(0,10);
        // Usar modal V2 directamente con el tipo seleccionado
        import('./calendar-modals-v2.js').then(async (module) => {
            // Mostrar directamente el modal de crear movimiento con el tipo específico
            const typeMap = { 'ingreso': 'ingreso', 'gasto': 'gasto' };
            await module.showCreateEventDialog(todayISO, () => {
                calendarInstance?.invalidateCache();
                calendarInstance?.refreshAllEventIndicators();
            });
            
            // Auto-seleccionar el tipo después de un momento
            setTimeout(() => {
                const select = document.querySelector('.swal2-select');
                if (select) {
                    select.value = type === 'ingreso' ? 'movement-income' : 'movement-expense';
                    // Trigger change event para proceder automáticamente
                    const confirmBtn = document.querySelector('.swal2-confirm');
                    if (confirmBtn) confirmBtn.click();
                }
            }, 100);
        });
    };
    if (quickIncome) quickIncome.addEventListener('click', () => openForToday('ingreso'));
    if (quickExpense) quickExpense.addEventListener('click', () => openForToday('gasto'));

    // Mostrar guía de uso si es la primera vez de este usuario
    try { maybeShowOnboarding(); } catch (_) {}
    
    // Actualizar notificaciones cada 5 minutos
    setInterval(() => {
        updateNotifications();
    }, 5 * 60 * 1000);

    // Re-sincronizar al navegar entre meses
    // DESHABILITADO: La tabla 'events' antigua ya no existe, ahora usamos sistema V2
    // const reSync = () => {
    //     const d = calendar.currentDate;
    //     syncDownMonth(d.getFullYear(), d.getMonth()).then(() => {
    //         calendar.refreshAllEventIndicators();
    //     });
    // };
    
    // V2: Refrescar indicadores directamente al cambiar de mes
    const reSync = () => {
        calendar.invalidateCache(); // Invalidar caché al cambiar de mes
        calendar.refreshAllEventIndicators();
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
        
        // Inicializar el userId en el sistema de planeación
        if (currentUser.userId) {
            setUserId(currentUser.userId);
        }
        
        // Aplicar tema guardado por usuario (light/dark)
        applySavedTheme();
        
        // Display user name
        const userNameElement = document.getElementById('user-name');
        if (userNameElement) {
            userNameElement.textContent = currentUser.name || currentUser.username;
        }

        // Setup balance indicator
        setupBalanceIndicator();
        updateBalanceIndicator();

        // Setup logout button
        const logoutBtn = document.getElementById('logout-btn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', handleLogout);
        }
        
        // Mostrar toast de bienvenida
        showWelcomeToasts();
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
 * Mostrar toast de bienvenida con resumen financiero
 */
async function showWelcomeToasts() {
    // Crear contenedor de toasts si no existe
    let toastContainer = document.querySelector('.toast-container');
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.className = 'toast-container';
        document.body.appendChild(toastContainer);
    }
    
    // Función helper para crear un toast
    const createToast = (icon, title, message, type = 'info', duration = 4000) => {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <div class="toast-icon">${icon}</div>
            <div class="toast-content">
                <div class="toast-title">${title}</div>
                <div class="toast-message">${message}</div>
            </div>
            <button class="toast-close">&times;</button>
        `;
        
        // Cerrar al hacer clic en X
        toast.querySelector('.toast-close').addEventListener('click', () => {
            toast.classList.add('toast-hide');
            setTimeout(() => toast.remove(), 300);
        });
        
        // Auto-cerrar después del tiempo especificado
        setTimeout(() => {
            if (toast.parentNode) {
                toast.classList.add('toast-hide');
                setTimeout(() => toast.remove(), 300);
            }
        }, duration);
        
        return toast;
    };
    
    try {
        // Toast de bienvenida
        const userName = currentUser?.name || currentUser?.username || 'Usuario';
        const welcomeToast = createToast(
            '👋',
            `¡Hola, ${userName}!`,
            'Bienvenido de vuelta a tu calendario financiero',
            'success',
            3000
        );
        toastContainer.appendChild(welcomeToast);
        
        // Obtener resumen de balance
        const summary = await getConfirmedBalanceSummary();
        const balance = summary?.balance || 0;
        
        // Toast de balance actual (después de 500ms)
        setTimeout(() => {
            const balanceType = balance >= 0 ? 'success' : 'warning';
            const balanceIcon = balance >= 0 ? '💰' : '⚠️';
            const balanceToast = createToast(
                balanceIcon,
                'Balance actual',
                formatCurrency(balance),
                balanceType,
                5000
            );
            toastContainer.appendChild(balanceToast);
        }, 500);
        
        // Obtener alertas pendientes
        const alerts = await getPendingAlerts();
        if (alerts && alerts.length > 0) {
            // Toast de alertas pendientes (después de 1000ms)
            setTimeout(() => {
                const alertToast = createToast(
                    '🔔',
                    'Tienes alertas pendientes',
                    `${alerts.length} evento(s) requieren tu atención`,
                    'info',
                    6000
                );
                alertToast.style.cursor = 'pointer';
                alertToast.addEventListener('click', () => {
                    displayAlerts(alerts);
                    alertToast.classList.add('toast-hide');
                    setTimeout(() => alertToast.remove(), 300);
                });
                toastContainer.appendChild(alertToast);
            }, 1000);
        }
    } catch (err) {
        console.error('Error showing welcome toasts:', err);
    }
}

/**
 * Abrir panel de acceso rápido con patrones, planes y ahorros
 */
async function openQuickAccessPanel() {
    try {
        const { supabase } = await import('./supabase-client.js');
        
        // Obtener datos en paralelo
        const userId = currentUser?.userId;
        if (!userId) {
            console.error('No user ID found');
            return;
        }
        
        // Cargar patrones de ingreso, patrones de gasto, planes y ahorros
        const [incomePatterns, expensePatterns, plans, savings] = await Promise.all([
            supabase.from('income_patterns').select('*').eq('user_id', userId).eq('active', true),
            supabase.from('expense_patterns').select('*').eq('user_id', userId).eq('active', true),
            supabase.from('plans').select('*').eq('user_id', userId).in('status', ['active', 'paused']),
            supabase.from('savings_patterns').select('*').eq('user_id', userId).eq('active', true)
        ]);
        
        const incomes = incomePatterns.data || [];
        const expenses = expensePatterns.data || [];
        const activePlans = plans.data || [];
        const activeSavings = savings.data || [];
        
        // Calcular totales - V2: usar base_amount en lugar de amount
        const totalIncomeMonthly = incomes.reduce((sum, p) => {
            const freq = p.frequency;
            const amount = parseFloat(p.base_amount) || 0;
            let factor = 1;
            if (freq === 'weekly') factor = 4.33;
            else if (freq === 'biweekly') factor = 2.17;
            else if (freq === 'daily') factor = 30;
            else if (freq === 'yearly') factor = 1/12;
            return sum + (amount * factor);
        }, 0);
        
        const totalExpenseMonthly = expenses.reduce((sum, p) => {
            const freq = p.frequency;
            const amount = parseFloat(p.base_amount) || 0;
            let factor = 1;
            if (freq === 'weekly') factor = 4.33;
            else if (freq === 'biweekly') factor = 2.17;
            else if (freq === 'daily') factor = 30;
            else if (freq === 'yearly') factor = 1/12;
            return sum + (amount * factor);
        }, 0);
        
        const totalSavings = activeSavings.reduce((sum, s) => sum + (parseFloat(s.current_balance) || 0), 0);
        const totalPlansAccumulated = activePlans.reduce((sum, p) => sum + (p.current_amount || 0), 0);
        
        // Generar HTML del panel
        const html = `
            <div class="quick-access-panel">
                <!-- Botones de acceso rápido -->
                <div style="display: flex; gap: 12px; margin-bottom: 16px;">
                    <!-- Botón de Planeación Financiera -->
                    <div class="planning-access-banner" style="flex: 1; background: linear-gradient(135deg, #dbeafe 0%, #e0f2fe 100%); border-radius: 12px; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; border: 1px solid #93c5fd;">
                        <div style="display: flex; align-items: center; gap: 12px;">
                            <span style="font-size: 1.5rem;">🛒</span>
                            <div>
                                <div style="font-weight: 600; color: #1e40af;">Planifica Compras</div>
                                <div style="font-size: 0.75rem; color: #3b82f6;">Productos y metas</div>
                            </div>
                        </div>
                        <button id="open-planning-modal-btn" style="background: #3b82f6; color: white; border: none; padding: 8px 12px; border-radius: 8px; cursor: pointer; font-weight: 500;">Abrir</button>
                    </div>
                    
                    <!-- Botón de Análisis Financiero -->
                    <div class="analysis-access-banner" style="flex: 1; background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%); border-radius: 12px; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; border: 1px solid #86efac;">
                        <div style="display: flex; align-items: center; gap: 12px;">
                            <span style="font-size: 1.5rem;">📊</span>
                            <div>
                                <div style="font-weight: 600; color: #166534;">Análisis Inteligente</div>
                                <div style="font-size: 0.75rem; color: #22c55e;">Salud financiera</div>
                            </div>
                        </div>
                        <button id="open-financial-analysis-btn" style="background: #22c55e; color: white; border: none; padding: 8px 12px; border-radius: 8px; cursor: pointer; font-weight: 500;">Ver</button>
                    </div>
                </div>

                <!-- Resumen superior -->
                <div class="quick-summary-cards">
                    <div class="summary-card income-card">
                        <span class="card-icon">📈</span>
                        <span class="card-label">Ingresos/mes</span>
                        <span class="card-value">${formatCurrency(totalIncomeMonthly)}</span>
                    </div>
                    <div class="summary-card expense-card">
                        <span class="card-icon">📉</span>
                        <span class="card-label">Gastos/mes</span>
                        <span class="card-value">${formatCurrency(totalExpenseMonthly)}</span>
                    </div>
                    <div class="summary-card savings-card">
                        <span class="card-icon">🐷</span>
                        <span class="card-label">Ahorros</span>
                        <span class="card-value">${formatCurrency(totalSavings)}</span>
                    </div>
                    <div class="summary-card plans-card">
                        <span class="card-icon">🎯</span>
                        <span class="card-label">En planes</span>
                        <span class="card-value">${formatCurrency(totalPlansAccumulated)}</span>
                    </div>
                </div>
                
                <!-- Tabs -->
                <div class="quick-tabs">
                    <button class="quick-tab active" data-tab="incomes">📈 Ingresos (${incomes.length})</button>
                    <button class="quick-tab" data-tab="expenses">📉 Gastos (${expenses.length})</button>
                    <button class="quick-tab" data-tab="plans">🎯 Planes (${activePlans.length})</button>
                    <button class="quick-tab" data-tab="savings">🐷 Ahorros (${activeSavings.length})</button>
                </div>
                
                <!-- Contenido de tabs -->
                <div class="quick-tab-content">
                    <!-- Ingresos -->
                    <div class="quick-tab-pane active" id="tab-incomes">
                        ${incomes.length === 0 ? '<p class="no-items">No tienes patrones de ingreso activos</p>' :
                            incomes.map(p => `
                                <div class="quick-item income-item" data-type="income" data-id="${p.id}">
                                    <div class="item-info">
                                        <span class="item-name">${p.name}</span>
                                        <span class="item-freq">${translateFrequency(p.frequency)}</span>
                                    </div>
                                    <span class="item-amount positive">${formatCurrency(p.base_amount)}</span>
                                </div>
                            `).join('')
                        }
                    </div>
                    
                    <!-- Gastos -->
                    <div class="quick-tab-pane" id="tab-expenses">
                        ${expenses.length === 0 ? '<p class="no-items">No tienes patrones de gasto activos</p>' :
                            expenses.map(p => `
                                <div class="quick-item expense-item" data-type="expense" data-id="${p.id}">
                                    <div class="item-info">
                                        <span class="item-name">${p.name}${p.category ? ` <small>(${p.category})</small>` : ''}</span>
                                        <span class="item-freq">${translateFrequency(p.frequency)}</span>
                                    </div>
                                    <span class="item-amount negative">${formatCurrency(p.base_amount)}</span>
                                </div>
                            `).join('')
                        }
                    </div>
                    
                    <!-- Planes -->
                    <div class="quick-tab-pane" id="tab-plans">
                        ${activePlans.length === 0 ? '<p class="no-items">No tienes planes activos</p>' :
                            activePlans.map(p => {
                                const progress = p.target_amount > 0 ? Math.min(100, (p.current_amount / p.target_amount) * 100) : 0;
                                return `
                                    <div class="quick-item plan-item" data-type="plan" data-id="${p.id}">
                                        <div class="item-info">
                                            <span class="item-name">${p.name}</span>
                                            <div class="plan-progress-mini">
                                                <div class="progress-bar-mini" style="width: ${progress}%"></div>
                                            </div>
                                        </div>
                                        <div class="plan-amounts">
                                            <span class="item-amount">${formatCurrency(p.current_amount)}</span>
                                            <span class="item-target">/ ${formatCurrency(p.target_amount)}</span>
                                        </div>
                                    </div>
                                `;
                            }).join('')
                        }
                    </div>
                    
                    <!-- Ahorros -->
                    <div class="quick-tab-pane" id="tab-savings">
                        ${activeSavings.length === 0 ? '<p class="no-items">No tienes cuentas de ahorro activas</p>' :
                            activeSavings.map(s => `
                                <div class="quick-item savings-item" data-type="savings" data-id="${s.id}">
                                    <div class="item-info">
                                        <span class="item-name">${s.name}</span>
                                        <span class="item-desc">${s.description || 'Sin descripción'}</span>
                                    </div>
                                    <span class="item-amount savings-amount">${formatCurrency(parseFloat(s.current_balance) || 0)}</span>
                                </div>
                            `).join('')
                        }
                    </div>
                </div>
            </div>
        `;
        
        await Swal.fire({
            title: '📋 Mis Finanzas',
            html: html,
            width: '650px',
            showCloseButton: true,
            showConfirmButton: false,
            customClass: {
                popup: 'quick-access-popup',
                htmlContainer: 'quick-access-container'
            },
            didOpen: () => {
                // Botón para abrir Planeación Financiera
                const openPlanningBtn = document.getElementById('open-planning-modal-btn');
                if (openPlanningBtn) {
                    openPlanningBtn.addEventListener('click', async () => {
                        Swal.close();
                        // Importar y abrir el modal de planeación
                        const planningModals = await import('./planning-modals.js');
                        planningModals.openPlanningModal();
                    });
                }
                
                // Botón para abrir Análisis Financiero Inteligente
                const openAnalysisBtn = document.getElementById('open-financial-analysis-btn');
                if (openAnalysisBtn) {
                    openAnalysisBtn.addEventListener('click', async () => {
                        Swal.close();
                        // Importar y abrir el modal de análisis financiero
                        openFinancialAnalysisModal();
                    });
                }

                // Manejar cambio de tabs
                const tabs = document.querySelectorAll('.quick-tab');
                const panes = document.querySelectorAll('.quick-tab-pane');
                
                tabs.forEach(tab => {
                    tab.addEventListener('click', () => {
                        tabs.forEach(t => t.classList.remove('active'));
                        panes.forEach(p => p.classList.remove('active'));
                        
                        tab.classList.add('active');
                        const targetPane = document.getElementById(`tab-${tab.dataset.tab}`);
                        if (targetPane) targetPane.classList.add('active');
                    });
                });
                
                // Manejar clic en items para ver detalles
                const items = document.querySelectorAll('.quick-item');
                items.forEach(item => {
                    item.addEventListener('click', async () => {
                        const type = item.dataset.type;
                        const id = item.dataset.id;
                        
                        // Cerrar este modal e importar el módulo de modals para mostrar detalles
                        Swal.close();
                        
                        const modalsModule = await import('./calendar-modals-v2.js');
                        
                        if (type === 'income') {
                            // Mostrar detalles del patrón de ingreso
                            await modalsModule.showPatternDetails(id, 'income', () => {});
                        } else if (type === 'expense') {
                            // Mostrar detalles del patrón de gasto
                            await modalsModule.showPatternDetails(id, 'expense', () => {});
                        } else if (type === 'plan') {
                            // Mostrar detalles del plan
                            await modalsModule.showPlanDetails(id, () => {});
                        } else if (type === 'savings') {
                            // Mostrar detalles del ahorro - usar showSavingsManagementDialog por ahora
                            await modalsModule.showSavingsManagementDialog();
                        }
                    });
                });
            }
        });
        
    } catch (err) {
        console.error('Error opening quick access panel:', err);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo cargar el panel de acceso rápido'
        });
    }
}

/**
 * Abre el modal de Análisis Financiero Inteligente
 */
async function openFinancialAnalysisModal() {
    try {
        // Mostrar loading mientras se carga el análisis
        Swal.fire({
            title: '📊 Análisis Financiero',
            html: `
                <div style="padding: 40px; text-align: center;">
                    <div style="font-size: 3rem; margin-bottom: 20px;">📊</div>
                    <h3 style="margin-bottom: 10px;">Analizando tus finanzas...</h3>
                    <p style="color: #64748b;">Esto solo tomará un momento</p>
                </div>
            `,
            width: '900px',
            showConfirmButton: false,
            showCloseButton: true,
            allowOutsideClick: false,
            didOpen: async () => {
                try {
                    // Importar y ejecutar el dashboard
                    const { initFinancialDashboard, showExpenseLinkingModal } = await import('./financial-dashboard.js');
                    
                    // Reemplazar contenido con contenedor del dashboard
                    const container = Swal.getHtmlContainer();
                    container.innerHTML = '<div id="financial-dashboard-modal"></div>';
                    
                    // Inicializar dashboard
                    await initFinancialDashboard('financial-dashboard-modal');
                    
                    // Hacer disponible la función de vinculación globalmente
                    window.showExpenseLinkingModal = showExpenseLinkingModal;
                    
                } catch (error) {
                    console.error('Error loading financial analysis:', error);
                    const container = Swal.getHtmlContainer();
                    container.innerHTML = `
                        <div style="padding: 40px; text-align: center;">
                            <div style="font-size: 3rem; margin-bottom: 20px; color: #ef4444;">⚠️</div>
                            <h3 style="margin-bottom: 10px;">Error al cargar el análisis</h3>
                            <p style="color: #64748b;">${error.message}</p>
                            <p style="color: #94a3b8; font-size: 0.85rem; margin-top: 16px;">
                                Asegúrate de tener patrones de ingreso y gasto registrados.
                            </p>
                        </div>
                    `;
                }
            }
        });
        
    } catch (err) {
        console.error('Error opening financial analysis:', err);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo abrir el análisis financiero'
        });
    }
}

/**
 * Traducir frecuencia al español
 */
function translateFrequency(freq) {
    const map = {
        'daily': 'Diario',
        'weekly': 'Semanal',
        'biweekly': 'Quincenal',
        'monthly': 'Mensual',
        'yearly': 'Anual',
        'once': 'Una vez'
    };
    return map[freq] || freq;
}

/**
 * Setup balance indicator click handler
 */
function setupBalanceIndicator() {
    const balanceIndicator = document.getElementById('balance-indicator');
    if (balanceIndicator) {
        balanceIndicator.addEventListener('click', () => {
            showBalanceSummaryDialog();
        });
    }
}

/**
 * Update balance indicator with current confirmed balance
 */
async function updateBalanceIndicator() {
    const balanceValueEl = document.getElementById('balance-value');
    const balancePill = document.getElementById('balance-indicator');
    
    if (!balanceValueEl || !balancePill) return;
    
    try {
        const summary = await getConfirmedBalanceSummary();
        const rawBalance = summary?.balance || 0;
        
        // Normalizar balance para evitar -$0.00
        const balance = Math.abs(rawBalance) < 0.01 ? 0 : rawBalance;
        
        // Update value
        balanceValueEl.textContent = formatCurrency(balance);
        
        // Update styling based on balance
        balancePill.classList.remove('positive', 'negative');
        if (balance > 0) {
            balancePill.classList.add('positive');
        } else if (balance < 0) {
            balancePill.classList.add('negative');
        }
    } catch (err) {
        console.error('Error updating balance indicator:', err);
        balanceValueEl.textContent = '$0.00';
    }
}

// Export updateBalanceIndicator for use after transactions
export { updateBalanceIndicator };

// Listen for balance update events from anywhere in the app
window.addEventListener('balance-updated', () => {
    updateBalanceIndicator();
});

// Global function to trigger balance update from anywhere
window.refreshBalanceIndicator = updateBalanceIndicator;

/**
 * Get current user ID for database queries
 */
export function getCurrentUserId() {
    return currentUser?.userId || null;
}

/**
 * Muestra la guía de inicio si el usuario nunca la vio
 */
function maybeShowOnboarding() {
    const uid = getCurrentUserId();
    if (!uid) return;
    const key = `onboarding_seen:${uid}`;
    const seen = localStorage.getItem(key);
    if (seen === '1') return;
    openHelpGuide().then(() => {
        localStorage.setItem(key, '1');
    });
}

/**
 * Abre la guía/tutorial de uso (3 pasos rápidos)
 */
async function openHelpGuide() {
    const steps = [
        {
            title: 'Bienvenido 👋',
            html: `Organiza tus <strong>ingresos</strong> y <strong>gastos</strong> en un calendario simple.<br><br>
                   • Clic en un día o usa la barra «➕ Ingreso / ➖ Gasto»<br>
                   • Repetición opcional (semanal/mensual/anual)<br>
                   • Confirma y archiva cuando se cumplan`
        },
        {
            title: 'Tipos de eventos e iconos',
            html: `<div style='text-align:left'>
                    <div style='display:flex;gap:12px;flex-wrap:wrap'>
                      <div><span style='display:inline-block;width:12px;height:12px;background:#2ecc71;border-radius:50%;vertical-align:middle;margin-right:6px'></span>Ingreso</div>
                      <div><span style='display:inline-block;width:12px;height:12px;background:#e74c3c;border-radius:3px;vertical-align:middle;margin-right:6px'></span>Gasto</div>
                      <div><span style='display:inline-block;width:12px;height:12px;border:2px solid #f39c12;vertical-align:middle;margin-right:6px'></span>Préstamo</div>
                      <div><span style='display:inline-block;width:12px;height:12px;background:#9b59b6;border-radius:50%;vertical-align:middle;margin-right:6px'></span>Compensación</div>
                    </div>
                    <div style='margin-top:8px;color:#555'>Pasa el mouse sobre un punto para ver detalles (monto, categoría, frecuencia...).</div>
                   </div>`
        },
        {
            title: 'Préstamos, alertas y configuración',
            html: `Crea préstamos con contrapartes automáticas y configura <strong>alertas</strong> 🔔 antes de vencimientos.<br><br>
                   • Panel ⚙️: tema claro/oscuro, limpiar eventos y cerrar sesión`
        }
    ];
    for (const s of steps) {
        // eslint-disable-next-line no-await-in-loop
        await Swal.fire({ icon: 'info', confirmButtonText: 'Siguiente', ...s });
    }
    await Swal.fire({ icon: 'success', title: '¡Listo!', text: 'Disfruta usando tu calendario 🎉' });
}

/**
 * Abre el panel de configuración
 */
async function openSettingsPanel() {
    const uid = getCurrentUserId();
    const username = currentUser?.username || '—';
    const name = currentUser?.name || '—';
    const email = currentUser?.email || '—';

    const theme = getSavedTheme();

        const html = `
      <div style="text-align:left;display:flex;flex-direction:column;gap:14px;">
        <section>
          <h3 style="margin-bottom:6px;">Usuario</h3>
          <div><strong>Nombre:</strong> ${escapeHtml(name)}</div>
          <div><strong>Usuario:</strong> ${escapeHtml(username)}</div>
          <div><strong>Email:</strong> ${escapeHtml(email)}</div>
          <div><strong>ID:</strong> ${escapeHtml(uid || '—')}</div>
        </section>
        <section>
          <h3 style="margin-bottom:6px;">Apariencia</h3>
          <label style="display:flex;align-items:center;gap:8px;">
            <input id="theme-toggle" type="checkbox" ${theme === 'dark' ? 'checked' : ''} />
            Tema oscuro
          </label>
        </section>
        <section>
          <h3 style="margin-bottom:6px;">Eventos</h3>
          <button id="btn-clear-events" class="danger">🗑️ Limpiar todos los eventos</button>
        </section>
        <section>
          <h3 style="margin-bottom:6px;">Sesión</h3>
          <button id="btn-logout" class="btn-logout">🚪 Cerrar sesión</button>
        </section>
      </div>
    `;

    await Swal.fire({
        title: 'Configuración',
        html,
        showConfirmButton: false,
        showCancelButton: true,
        cancelButtonText: 'Cerrar',
        didOpen: () => {
            const $ = (id) => Swal.getHtmlContainer().querySelector(id);
            const themeToggle = $('#theme-toggle');
            const clearBtn = $('#btn-clear-events');
            const logoutBtn = $('#btn-logout');

            if (themeToggle) {
                themeToggle.addEventListener('change', () => {
                    const newTheme = themeToggle.checked ? 'dark' : 'light';
                    saveTheme(newTheme);
                    applyTheme(newTheme);
                });
            }

            if (clearBtn) {
                clearBtn.addEventListener('click', async () => {
                    const ok = await confirmDanger('¿Eliminar todos los eventos?', 'Se borrarán todos los eventos guardados en este dispositivo.');
                    if (!ok) return;
                    await clearAllEvents(false);
                    await Swal.fire({ icon:'success', title:'Eventos borrados' });
                    try { 
                        calendarInstance?.invalidateCache();
                        calendarInstance?.refreshAllEventIndicators(); 
                    } catch(_) {}
                });
            }

            if (logoutBtn) {
                logoutBtn.addEventListener('click', handleLogout);
            }
        }
    });
}

function escapeHtml(s) { return String(s).replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c])); }

function getSavedTheme() {
    const uid = getCurrentUserId() || 'anon';
    return localStorage.getItem(`theme:${uid}`) || 'light';
}
function saveTheme(theme) {
    const uid = getCurrentUserId() || 'anon';
    localStorage.setItem(`theme:${uid}`, theme);
}
function applyTheme(theme) {
    const body = document.body;
    if (!body) return;
    body.classList.toggle('dark-theme', theme === 'dark');
}
function applySavedTheme() {
    applyTheme(getSavedTheme());
}

async function confirmDanger(title, text) {
    const res = await Swal.fire({
        icon: 'warning',
        title,
        html: text,
        showCancelButton: true,
        confirmButtonText: 'Sí, continuar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#e55353'
    });
    return res.isConfirmed;
}

async function clearAllEvents() {
    // Vaciar localmente solamente
    try { saveEvents({}); } catch (_) {}
}

// ---------- Drawer de estadísticas ----------
function ensureStatsDrawerDom() {
        if (!document.getElementById('stats-drawer')) {
                const overlay = document.createElement('div');
                overlay.id = 'stats-drawer-overlay';
                overlay.addEventListener('click', closeStatsDrawer);
                document.body.appendChild(overlay);

                const drawer = document.createElement('div');
                drawer.id = 'stats-drawer';
                drawer.innerHTML = `
                    <div class="stats-header">
                        <strong>Estadísticas</strong>
                        <button id="stats-close" title="Cerrar">✖</button>
                    </div>
                    <div class="stats-content">
                        <div class="stats-nav">
                            <button data-tab="hoy" class="active">Hoy</button>
                            <button data-tab="semanas">Semanas</button>
                            <button data-tab="mes">Mes</button>
                            <button data-tab="anio">Año</button>
                        </div>
                        <div id="stats-body"></div>
                    </div>`;
                document.body.appendChild(drawer);

                drawer.querySelector('#stats-close')?.addEventListener('click', closeStatsDrawer);
                drawer.querySelectorAll('.stats-nav button').forEach(btn => {
                        btn.addEventListener('click', () => {
                                drawer.querySelectorAll('.stats-nav button').forEach(b => b.classList.remove('active'));
                                btn.classList.add('active');
                                renderStatsTab(btn.getAttribute('data-tab'));
                        });
                });
        }
}

function openStatsDrawer() {
        ensureStatsDrawerDom();
        document.getElementById('stats-drawer-overlay').style.display = 'block';
        document.getElementById('stats-drawer').classList.add('open');
        renderStatsTab('hoy');
}

// Helpers para listar movimientos y manejar clicks
function attachStatCardHandlers() {
    const cards = document.querySelectorAll('#stats-drawer .stat-card');
    cards.forEach(card => {
        card.addEventListener('click', () => {
            const range = card.getAttribute('data-range');
            const scope = card.getAttribute('data-scope');
            const type = card.getAttribute('data-type'); // ingreso | gasto
            const status = card.getAttribute('data-status'); // confirmed | pending
            if (!range) return;
            const [startISO, endISO] = range.split('|');
            if (scope && scope.endsWith('-net')) {
                const events = collectEventsInRange(startISO, endISO);
                return openNetModal(events, `${prettyRange(startISO, endISO)} — Neto`);
            }
            const events = collectEventsInRange(startISO, endISO)
                .filter(ev => (!type || ev.event.type === type) &&
                               (!status || (status === 'confirmed' ? !!ev.event.confirmed : !ev.event.confirmed))
                );
            openMovementsModal(events, `${prettyRange(startISO, endISO)} — ${labelFor(type, status)}`);
        });
    });
}

function attachWeekRowHandlers() {
    document.querySelectorAll('#stats-drawer .stats-row').forEach(row => {
        row.style.cursor = 'pointer';
        row.addEventListener('click', () => {
            const [startISO, endISO] = (row.getAttribute('data-range')||'|').split('|');
            const events = collectEventsInRange(startISO, endISO);
            openNetModal(events, `Semana ${prettyRange(startISO, endISO)}`);
        });
    });
}

function attachAnnualRowHandlers() {
    document.querySelectorAll('#stats-drawer .annual-row').forEach(cell => {
        cell.parentElement.style.cursor = 'pointer';
        cell.parentElement.addEventListener('click', () => {
            const [fromM, toM, size] = (cell.getAttribute('data-group')||'||').split('|').map(Number);
            const year = new Date().getFullYear();
            const start = new Date(year, fromM - 1, 1).toISOString().slice(0,10);
            const end = new Date(year, toM, 0).toISOString().slice(0,10);
            const events = collectEventsInRange(start, end);
            openNetModal(events, `Meses ${fromM}–${toM}`);
        });
    });
}

function attachAnnualTotalRowHandlers() {
    const row = document.querySelector('#stats-drawer .annual-total-row');
    if (!row) return;
    row.style.cursor = 'pointer';
    row.addEventListener('click', () => {
        const year = Number(row.getAttribute('data-full-year')) || (new Date()).getFullYear();
        const start = new Date(year, 0, 1).toISOString().slice(0,10);
        const end = new Date(year, 12, 0).toISOString().slice(0,10);
        const events = collectEventsInRange(start, end);
        openNetModal(events, `Total anual ${year}`);
    });
}
function collectEventsInRange(startISO, endISO) {
    const start = new Date(startISO + 'T00:00:00');
    const end = new Date(endISO + 'T23:59:59');
    const all = [];
    try {
        const data = JSON.parse(localStorage.getItem(`events:${getCurrentUserId()}`) || '{}');
        Object.keys(data).forEach(iso => {
            const d = new Date(iso + 'T00:00:00');
            if (d >= start && d <= end) {
                (data[iso]||[]).forEach((e, idx) => all.push({ dateISO: iso, index: idx, event: e }));
            }
        });
    } catch (_) {}
    // Orden cronológico por fecha y createdAt si existe
    all.sort((a,b) => {
        if (a.dateISO !== b.dateISO) return a.dateISO < b.dateISO ? -1 : 1;
        const ca = a.event.createdAt || '';
        const cb = b.event.createdAt || '';
        return ca < cb ? -1 : (ca > cb ? 1 : 0);
    });
    return all;
}

function labelFor(type, status) {
    const t = type === 'ingreso' ? 'Ingresos' : 'Gastos';
    const s = status === 'confirmed' ? 'confirmados' : 'pendientes';
    return `${t} ${s}`;
}

function prettyRange(startISO, endISO) {
    return startISO === endISO ? startISO : `${startISO} a ${endISO}`;
}

async function openMovementsModal(items, title) {
        const rows = items.map(({dateISO, event}) => {
        const amt = event.confirmed ? (event.confirmedAmount ?? event.amount ?? 0) : (event.amount ?? 0);
        const badge = event.confirmed ? '✅' : '⌛';
        return `<tr>
          <td>${dateISO}</td>
          <td>${escapeHtml(event.title || '')}</td>
          <td>${event.type === 'ingreso' ? 'Ingreso' : (event.type === 'gasto' ? 'Gasto' : 'Evento')}</td>
          <td style="text-align:right">${renderMoney(amt)}</td>
          <td>${badge}</td>
        </tr>`;
    }).join('');

    const total = items.reduce((s, {event}) => s + Number(event.confirmed ? (event.confirmedAmount ?? event.amount ?? 0) : (event.amount ?? 0)), 0);

    await Swal.fire({
        title,
                width: 720,
                html: `<div style="text-align:left; max-height:60vh; overflow:auto;">
          <table class="table-clean" style="margin-bottom:8px">
            <thead><tr><th>Fecha</th><th>Título</th><th>Tipo</th><th>Importe</th><th>Estado</th></tr></thead>
            <tbody>${rows || '<tr><td colspan="5"><em>Sin movimientos</em></td></tr>'}</tbody>
            <tfoot><tr><th colspan="3" style="text-align:right">Total</th><th style="text-align:right">${renderMoney(total)}</th><th></th></tr></tfoot>
          </table>
        </div>`,
        confirmButtonText: 'Cerrar'
    });
}

async function openNetModal(items, title) {
    const incomes = items.filter(x => x.event.type === 'ingreso');
    const expenses = items.filter(x => x.event.type === 'gasto');

    const makeRows = (arr) => arr.map(({dateISO, event}) => {
        const amt = event.confirmed ? (event.confirmedAmount ?? event.amount ?? 0) : (event.amount ?? 0);
        const badge = event.confirmed ? '✅' : '⌛';
        return `<tr>
          <td>${dateISO}</td>
          <td>${escapeHtml(event.title || '')}</td>
          <td>${badge}</td>
          <td style="text-align:right">${renderMoney(amt)}</td>
        </tr>`;
    }).join('');

    const sum = (arr) => arr.reduce((s, {event}) => s + Number(event.confirmed ? (event.confirmedAmount ?? event.amount ?? 0) : (event.amount ?? 0)), 0);
    const incTotal = sum(incomes); const expTotal = sum(expenses);

        await Swal.fire({
        title,
                width: 760,
                html: `<div style="text-align:left; max-height:60vh; overflow:auto;">
                    <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px; min-width:600px;">
                    <div>
            <h3 style="margin:0 0 6px 0;">Ingresos</h3>
            <table class="table-clean">
              <thead><tr><th>Fecha</th><th>Título</th><th>Estado</th><th>Importe</th></tr></thead>
              <tbody>${makeRows(incomes) || '<tr><td colspan="4"><em>Sin ingresos</em></td></tr>'}</tbody>
              <tfoot><tr><th colspan="3" style="text-align:right">Total</th><th style="text-align:right">${renderMoney(incTotal)}</th></tr></tfoot>
            </table>
          </div>
          <div>
            <h3 style="margin:0 0 6px 0;">Gastos</h3>
            <table class="table-clean">
              <thead><tr><th>Fecha</th><th>Título</th><th>Estado</th><th>Importe</th></tr></thead>
              <tbody>${makeRows(expenses) || '<tr><td colspan="4"><em>Sin gastos</em></td></tr>'}</tbody>
              <tfoot><tr><th colspan="3" style="text-align:right">Total</th><th style="text-align:right">${renderMoney(expenses.reduce((s,{event})=>s+Number(event.confirmed?(event.confirmedAmount??event.amount??0):(event.amount??0)),0))}</th></tr></tfoot>
            </table>
          </div>
                    </div>
                </div>
                <div style="margin-top:8px;text-align:right"><strong>Neto:</strong> ${renderMoney(incTotal - expTotal)}</div>`,
        confirmButtonText: 'Cerrar'
    });
}

function closeStatsDrawer() {
        const overlay = document.getElementById('stats-drawer-overlay');
        const drawer = document.getElementById('stats-drawer');
        if (overlay) overlay.style.display = 'none';
        if (drawer) drawer.classList.remove('open');
}

async function renderStatsTab(tab) {
        const body = document.getElementById('stats-body');
        if (!body) return;

        const now = new Date();
        const todayISO = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString().slice(0,10);
        const year = now.getFullYear();
        const month = now.getMonth();

        // Mostrar loading
        body.innerHTML = '<div style="text-align:center;padding:20px;">Cargando...</div>';

        if (tab === 'hoy') {
                const { acc, netConfirmed, netPending } = await computeDailyStats(todayISO);
                    body.innerHTML = `
                    <div class="stat-grid">
                            <div class="stat-card" data-scope="day" data-range="${todayISO}|${todayISO}" data-type="ingreso" data-status="confirmed"><div class="stat-title">Ingresos confirmados</div><div class="stat-value income">${renderMoney(acc.confirmed.income)}</div></div>
                            <div class="stat-card" data-scope="day" data-range="${todayISO}|${todayISO}" data-type="gasto" data-status="confirmed"><div class="stat-title">Gastos confirmados</div><div class="stat-value expense">${renderMoney(acc.confirmed.expense)}</div></div>
                            <div class="stat-card" data-scope="day" data-range="${todayISO}|${todayISO}" data-type="ingreso" data-status="pending"><div class="stat-title">Ingresos pendientes</div><div class="stat-value income">${renderMoney(acc.pending.income)}</div></div>
                            <div class="stat-card" data-scope="day" data-range="${todayISO}|${todayISO}" data-type="gasto" data-status="pending"><div class="stat-title">Gastos pendientes</div><div class="stat-value expense">${renderMoney(acc.pending.expense)}</div></div>
                            <div class="stat-card" data-scope="day-net" data-range="${todayISO}|${todayISO}" style="grid-column: span 2"><div class="stat-title">Neto (confirmado / pendiente)</div><div class="stat-value net">${renderMoney(netConfirmed)} / ${renderMoney(netPending)}</div></div>
                    </div>`;
                    attachStatCardHandlers();
                return;
        }

        if (tab === 'semanas') {
                const weeks = await computeWeeklyStatsForMonth(year, month);
                        let total = { cInc:0, cExp:0, pInc:0, pExp:0 };
                        const rows = weeks.map(w => {
                                total.cInc += w.acc.confirmed.income; total.cExp += w.acc.confirmed.expense;
                                total.pInc += w.acc.pending.income; total.pExp += w.acc.pending.expense;
                        const title = `Semana ${w.week}`;
                                const startISO = w.range[0].toISOString().slice(0,10);
                                const endISO = w.range[1].toISOString().slice(0,10);
                                return `<tr class="stats-row" data-range="${startISO}|${endISO}">
                            <td>${title}</td>
                            <td class="income">${renderMoney(w.acc.confirmed.income)}</td>
                            <td class="expense">${renderMoney(w.acc.confirmed.expense)}</td>
                            <td class="income">${renderMoney(w.acc.pending.income)}</td>
                            <td class="expense">${renderMoney(w.acc.pending.expense)}</td>
                        </tr>`;
                }).join('');
                        const totalRow = `<tr style="font-weight:600">
                                <td>Total mes</td>
                                <td class="income">${renderMoney(total.cInc)}</td>
                                <td class="expense">${renderMoney(total.cExp)}</td>
                                <td class="income">${renderMoney(total.pInc)}</td>
                                <td class="expense">${renderMoney(total.pExp)}</td>
                        </tr>`;
                        body.innerHTML = `
                    <table class="table-clean">
                        <thead><tr><th>Semana</th><th>Ing. conf.</th><th>Gast. conf.</th><th>Ing. pend.</th><th>Gast. pend.</th></tr></thead>
                                <tbody>${rows}${totalRow}</tbody>
                    </table>`;
                        attachWeekRowHandlers();
                return;
        }

        if (tab === 'mes') {
                // Selector de periodo (mes/año) y render dinámico
                const monthVal = `${year}-${String(month+1).padStart(2,'0')}`;
                body.innerHTML = `
                    <div style="margin-bottom:10px; display:flex; align-items:center; gap:8px;">
                        <label for="month-picker">Periodo:</label>
                        <input id="month-picker" type="month" value="${monthVal}" />
                    </div>
                    <div id="monthly-container">Cargando...</div>`;

                const renderMonthly = async () => {
                        const container = body.querySelector('#monthly-container');
                        if (container) container.innerHTML = 'Cargando...';
                        const val = body.querySelector('#month-picker').value || monthVal;
                        const [yy, mm] = val.split('-').map(Number);
                        const selYear = yy; const selMonth = (mm || 1) - 1;
                        const endISO = new Date(selYear, selMonth+1, 0).toISOString().slice(0,10);
                        const isCurrentMonth = (selYear === year) && (selMonth === month);
                        const startISO = isCurrentMonth ? todayISO : new Date(selYear, selMonth, 1).toISOString().slice(0,10);
                        const acc = await computeMonthlyFutureStats(selYear, selMonth, startISO);
                        const netC = acc.confirmed.income - acc.confirmed.expense;
                        const netP = acc.pending.income - acc.pending.expense;
                        if (container) container.innerHTML = `
                            <div class="stat-grid">
                                <div class="stat-card" data-scope="month" data-range="${startISO}|${endISO}" data-type="ingreso" data-status="confirmed"><div class="stat-title">Ingresos confirmados (${isCurrentMonth? 'resto del mes' : 'mes elegido'})</div><div class="stat-value income">${renderMoney(acc.confirmed.income)}</div></div>
                                <div class="stat-card" data-scope="month" data-range="${startISO}|${endISO}" data-type="gasto" data-status="confirmed"><div class="stat-title">Gastos confirmados (${isCurrentMonth? 'resto del mes' : 'mes elegido'})</div><div class="stat-value expense">${renderMoney(acc.confirmed.expense)}</div></div>
                                <div class="stat-card" data-scope="month" data-range="${startISO}|${endISO}" data-type="ingreso" data-status="pending"><div class="stat-title">Ingresos pendientes (${isCurrentMonth? 'resto del mes' : 'mes elegido'})</div><div class="stat-value income">${renderMoney(acc.pending.income)}</div></div>
                                <div class="stat-card" data-scope="month" data-range="${startISO}|${endISO}" data-type="gasto" data-status="pending"><div class="stat-title">Gastos pendientes (${isCurrentMonth? 'resto del mes' : 'mes elegido'})</div><div class="stat-value expense">${renderMoney(acc.pending.expense)}</div></div>
                                <div class="stat-card" data-scope="month-net" data-range="${startISO}|${endISO}" style="grid-column: span 2"><div class="stat-title">Neto (confirmado / pendiente)</div><div class="stat-value net">${renderMoney(netC)} / ${renderMoney(netP)}</div></div>
                            </div>`;
                        attachStatCardHandlers();
                };
                body.querySelector('#month-picker').addEventListener('change', () => renderMonthly());
                renderMonthly();
                return;
        }

        if (tab === 'anio') {
                const html = `
                    <div style="margin-bottom:10px;">
                        <label>Agrupación: 
                            <select id="group-size">
                                <option value="2">Bimestral</option>
                                <option value="3" selected>Trimestral</option>
                                <option value="6">Semestral</option>
                                <option value="12">Anual</option>
                            </select>
                        </label>
                    </div>
                    <div id="annual-container">Cargando...</div>`;
                body.innerHTML = html;
                const renderAnnual = async () => {
                        const container = body.querySelector('#annual-container');
                        if (container) container.innerHTML = 'Cargando...';
                        const val = Number(body.querySelector('#group-size').value);
                        const groups = await computeAnnualStatsGroup(year, val);
                    let total = { cInc:0, cExp:0, pInc:0, pExp:0, netC:0, netP:0 };
                    const rows = groups.map(g => {
                                const netC = g.acc.confirmed.income - g.acc.confirmed.expense;
                                const netP = g.acc.pending.income - g.acc.pending.expense;
                        total.cInc += g.acc.confirmed.income; total.cExp += g.acc.confirmed.expense;
                        total.pInc += g.acc.pending.income; total.pExp += g.acc.pending.expense;
                        total.netC += netC; total.netP += netP;
                                return `<tr>
                            <td class="annual-row" data-group="${g.fromMonth}|${g.toMonth}|${val}">${g.fromMonth}–${g.toMonth}</td>
                                        <td class="income">${renderMoney(g.acc.confirmed.income)}</td>
                                        <td class="expense">${renderMoney(g.acc.confirmed.expense)}</td>
                                        <td class="income">${renderMoney(g.acc.pending.income)}</td>
                                        <td class="expense">${renderMoney(g.acc.pending.expense)}</td>
                                        <td class="net">${renderMoney(netC)} / ${renderMoney(netP)}</td>
                                </tr>`;
                        }).join('');
                    const totalRow = `<tr class="annual-total-row" style="font-weight:600" data-full-year="${year}">
                        <td class="annual-total">Total anual</td>
                        <td class="income">${renderMoney(total.cInc)}</td>
                        <td class="expense">${renderMoney(total.cExp)}</td>
                        <td class="income">${renderMoney(total.pInc)}</td>
                        <td class="expense">${renderMoney(total.pExp)}</td>
                        <td class="net">${renderMoney(total.netC)} / ${renderMoney(total.netP)}</td>
                    </tr>`;
                    if (container) container.innerHTML = `
                            <table class="table-clean">
                                <thead><tr><th>Meses</th><th>Ing. conf.</th><th>Gast. conf.</th><th>Ing. pend.</th><th>Gast. pend.</th><th>Neto (C/P)</th></tr></thead>
                        <tbody>${rows}${totalRow}</tbody>
                            </table>`;
                    attachAnnualRowHandlers();
                    attachAnnualTotalRowHandlers();
                };
                body.querySelector('#group-size').addEventListener('change', () => renderAnnual());
                renderAnnual();
        }
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
async function updateNotifications() {
    const alerts = await getPendingAlerts();
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
async function showNotificationModal() {
    const alerts = await getPendingAlerts();
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
                item.addEventListener('click', async function() {
                    const date = this.dataset.date;
                    const id = this.dataset.id;
                    const type = this.dataset.type;
                    
                    Swal.close();
                    
                    // Abrir modal correspondiente según el tipo
                    const module = await import('./calendar-modals-v2.js');
                    
                    switch(type) {
                        case 'movement':
                            await module.showMovementDetails(id, () => {
                                calendarInstance?.invalidateCache();
                                calendarInstance?.refreshAllEventIndicators();
                            });
                            break;
                        case 'projected':
                            // Para proyecciones, abrir el modal de la fecha
                            await module.showCreateEventDialog(date, () => {
                                calendarInstance?.invalidateCache();
                                calendarInstance?.refreshAllEventIndicators();
                            });
                            break;
                        case 'plan':
                            await module.showPlanDetails(id, () => {
                                calendarInstance?.invalidateCache();
                                calendarInstance?.refreshAllEventIndicators();
                            });
                            break;
                        case 'loan':
                            await module.showLoanDetails(id, () => {
                                calendarInstance?.invalidateCache();
                                calendarInstance?.refreshAllEventIndicators();
                            });
                            break;
                        default:
                            // Fallback: abrir modal de la fecha
                            await module.showCreateEventDialog(date, () => {
                                calendarInstance?.invalidateCache();
                                calendarInstance?.refreshAllEventIndicators();
                            });
                    }
                });
                
                item.addEventListener('mouseenter', function() {
                    const bgColor = this.style.background;
                    // Oscurecer el color de fondo al pasar el mouse
                    this.style.background = bgColor.includes('fee2e2') ? '#fecaca' : '#e8e8e8';
                });
                
                item.addEventListener('mouseleave', function() {
                    // Restaurar color original
                    const atRisk = this.querySelector('[style*="En riesgo"]');
                    this.style.background = atRisk ? '#fee2e2' : '#f9f9f9';
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

