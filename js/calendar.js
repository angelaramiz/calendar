/**
 * Módulo del Calendario
 * Gestiona la renderización y navegación del calendario (V2)
 */

import { showConfirmProjectedDialog, showMovementDetails, showLoanDetails, showPlanDetails, showCreateEventDialog } from './calendar-modals-v2.js';
import { getCalendarDataForMonth } from './pattern-scheduler.js';

export class Calendar {
    constructor(containerId) {
        this.cells = document.querySelectorAll(`#${containerId} td`);
        this.currentMonthElement = document.getElementById('current-month');
        this.currentDate = new Date();
        this.monthNames = [
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        ];
        
        // Caché para datos del calendario (evita múltiples llamadas a la API)
        this._calendarDataCache = null;
        this._calendarDataCacheKey = null;
    }

    /**
     * Inicializa el calendario y los event listeners
     */
    init() {
        this.render();
        this.attachCellListeners();
        this.attachNavigationListeners();
    }

    /**
     * Renderiza el calendario para el mes actual
     */
    render() {
        const year = this.currentDate.getFullYear();
        const month = this.currentDate.getMonth();

        // Actualizar título
        this.currentMonthElement.textContent = `${this.monthNames[month]} ${year}`;

        // Calcular días
        const firstDay = new Date(year, month, 1);
        const lastDay = new Date(year, month + 1, 0);
        const firstDayIndex = firstDay.getDay();
        const lastDate = lastDay.getDate();
        const prevLastDay = new Date(year, month, 0).getDate();

        // Limpiar celdas
        this.clearCells();

        let dayCounter = 1;
        let prevMonthDay = prevLastDay - firstDayIndex + 1;

        // Llenar celdas
        for (let i = 0; i < 42; i++) {
            const cell = this.cells[i];
            let cellDate = null;

            if (i < firstDayIndex) {
                // Días del mes anterior
                cell.textContent = prevMonthDay;
                cell.classList.add('other-month');
                cellDate = new Date(year, month - 1, prevMonthDay);
                prevMonthDay++;
            } else if (dayCounter <= lastDate) {
                // Días del mes actual
                cell.textContent = dayCounter;
                cellDate = new Date(year, month, dayCounter);

                // Marcar día actual
                if (this.isToday(cellDate)) {
                    cell.classList.add('today');
                }

                dayCounter++;
            } else {
                // Días del próximo mes
                const nextDayNumber = dayCounter - lastDate;
                cell.textContent = nextDayNumber;
                cell.classList.add('other-month');
                cellDate = new Date(year, month + 1, nextDayNumber);
                dayCounter++;
            }

            // Asignar data-date
            if (cellDate) {
                cell.dataset.date = cellDate.toISOString().slice(0, 10);
            }

            cell.style.cursor = 'pointer';
        }

        // Invalidar caché y actualizar indicadores de eventos
        this.invalidateCache();
        this.refreshAllEventIndicators();
    }

    /**
     * Limpia todas las celdas
     */
    clearCells() {
        this.cells.forEach(cell => {
            cell.innerHTML = '';
            cell.classList.remove('today', 'other-month');
            cell.removeAttribute('data-date');
        });
    }

    /**
     * Verifica si una fecha es hoy
     */
    isToday(date) {
        const today = new Date();
        return date.getDate() === today.getDate() &&
               date.getFullYear() === today.getFullYear() &&
               date.getMonth() === today.getMonth();
    }

    /**
     * Actualiza el indicador de eventos para una celda específica
     * Usado para actualizaciones individuales (después de editar/crear/eliminar)
     */
    async updateCellIndicator(dateISO) {
        const cell = document.querySelector(`#calendar-body td[data-date="${dateISO}"]`);
        
        if (!cell) return;

        // Limpiar indicadores de esta celda
        cell.querySelectorAll('.event-indicator, .ghost-indicator, .loan-indicator, .plan-indicator, .target-indicator, .solid-indicator').forEach(n => n.remove());

        // Invalidar caché para obtener datos frescos
        this.invalidateCache();
        
        // Obtener datos V2 del mes
        const sessionData = localStorage.getItem('calendar_session');
        const userId = sessionData ? JSON.parse(sessionData).userId : 'anon';
        const year = this.currentDate.getFullYear();
        const month = this.currentDate.getMonth();
        const calendarData = await getCalendarDataForMonth(userId, year, month);
        
        // Renderizar usando el nuevo método
        this.renderCellFromData(cell, dateISO, calendarData);
    }

    /**
     * Crea un indicador sólido para movements confirmados (solid circle)
     */
    createSolidIndicator(movement) {
        const span = document.createElement('span');
        span.className = 'event-indicator solid-indicator';
        
        // Color según tipo
        if (movement.type === 'ingreso') {
            span.classList.add('event-income');
            span.style.background = '#10b981'; // green
        } else if (movement.type === 'gasto') {
            span.classList.add('event-expense');
            span.style.background = '#ef4444'; // red
        } else {
            span.style.background = '#6b7280'; // gray para ajustes
        }

        // Tooltip
        let tooltip = `${movement.title}\n`;
        tooltip += `💵 $${movement.confirmed_amount}`;
        if (movement.expected_amount && movement.expected_amount !== movement.confirmed_amount) {
            tooltip += ` (Esperado: $${movement.expected_amount})`;
        }
        if (movement.category) tooltip += `\n🏷️ ${movement.category}`;
        if (movement.description) tooltip += `\n📝 ${movement.description}`;
        
        span.title = tooltip;
        span.dataset.movementId = movement.id;
        span.dataset.type = 'movement';
        
        // Sin click handler - el click en la celda abrirá el modal principal
        
        return span;
    }

    /**
     * Crea un indicador de loan movement (gold glow)
     */
    createLoanIndicator(movement) {
        const span = document.createElement('span');
        span.className = 'event-indicator loan-indicator';
        span.style.background = '#fbbf24'; // amber/gold
        span.style.boxShadow = '0 0 8px rgba(251, 191, 36, 0.6)';

        let tooltip = `💰 Préstamo\n${movement.title}\n`;
        tooltip += `💵 $${movement.confirmed_amount}`;
        if (movement.description) tooltip += `\n📝 ${movement.description}`;
        
        span.title = tooltip;
        span.dataset.movementId = movement.id;
        span.dataset.loanId = movement.loan_id;
        span.dataset.type = 'loan-movement';
        
        // Sin click handler - el click en la celda abrirá el modal principal
        
        return span;
    }

    /**
     * Crea un indicador de plan movement (blue glow)
     */
    createPlanMovementIndicator(movement) {
        const span = document.createElement('span');
        span.className = 'event-indicator plan-indicator';
        span.style.background = '#3b82f6'; // blue
        span.style.boxShadow = '0 0 8px rgba(59, 130, 246, 0.6)';

        let tooltip = `🎯 Ahorro para plan\n${movement.title}\n`;
        tooltip += `💵 $${movement.confirmed_amount}`;
        if (movement.description) tooltip += `\n📝 ${movement.description}`;
        
        span.title = tooltip;
        span.dataset.movementId = movement.id;
        span.dataset.planId = movement.plan_id;
        span.dataset.type = 'plan-movement';
        
        // Sin click handler - el click en la celda abrirá el modal principal
        
        return span;
    }

    /**
     * Crea un indicador de plan target (flag marker)
     */
    createPlanTargetIndicator(plan) {
        const span = document.createElement('span');
        span.className = 'target-indicator';
        span.textContent = '🏁';
        span.style.fontSize = '16px';
        span.style.position = 'absolute';
        span.style.bottom = '2px';
        span.style.left = '2px';
        span.style.cursor = 'pointer';
        span.style.animation = 'pulse 2s infinite';

        // V2: usar name en lugar de title, current_amount en lugar de saved_amount
        let tooltip = `🎯 Meta: ${plan.name || plan.title}\n`;
        tooltip += `💰 Objetivo: $${plan.target_amount}`;
        
        // Mostrar progreso calculado desde current_amount
        if (plan.current_amount !== undefined) {
            const progressPercent = plan.target_amount > 0 
                ? Math.round((plan.current_amount / plan.target_amount) * 100) 
                : 0;
            tooltip += `\n💵 Ahorrado: $${plan.current_amount}`;
            tooltip += `\n📊 Progreso: ${progressPercent}%`;
        }
        
        if (plan.description) tooltip += `\n📝 ${plan.description}`;
        
        span.title = tooltip;
        span.dataset.planId = plan.id;
        span.dataset.type = 'plan-target';
        
        // Sin click handler - el click en la celda abrirá el modal principal
        
        return span;
    }

    /**
     * Crea un indicador fantasma para projected events (dashed circle)
     */
    createGhostIndicator(projection, type) {
        const span = document.createElement('span');
        span.className = 'ghost-indicator';
        
        // Color según tipo
        if (type === 'income') {
            span.style.borderColor = '#10b981'; // green
        } else {
            span.style.borderColor = '#ef4444'; // red
        }
        
        span.style.border = '2px dashed';
        span.style.background = 'transparent';
        span.style.opacity = '0.5';

        let tooltip = `⚪ Proyectado\n${projection.name}\n`;
        tooltip += `💵 $${projection.expected_amount}`;
        if (projection.category) tooltip += `\n🏷️ ${projection.category}`;
        if (projection.description) tooltip += `\n📝 ${projection.description}`;
        
        span.title = tooltip;
        span.dataset.projectionDate = projection.date;
        span.dataset.patternId = projection.pattern_id;
        span.dataset.patternType = projection.pattern_type;
        span.dataset.type = 'projected';
        
        // Sin click handler - el click en la celda abrirá el modal principal
        
        return span;
    }

    /**
     * Crea un indicador visual de evento (LEGACY - para compatibilidad)
     */
    createEventIndicator(event) {
        const span = document.createElement('span');
        span.className = 'event-indicator';
        
        // Eventos de planeación tienen estilo especial
        if (event.isPlanningEvent) {
            span.classList.add('event-planning');
            // Color azul claro para metas, azul oscuro para gastos planificados
            if (event.planningType === 'goal') {
                span.style.background = '#dbeafe';
                span.style.borderColor = '#3b82f6';
            } else if (event.planningType === 'planned_expense') {
                span.style.background = '#bfdbfe';
                span.style.borderColor = '#2563eb';
            }
        } else {
            // Agregar clase específica según el tipo de evento normal
            if (event.type === 'ingreso') {
                span.classList.add('event-income');
            } else if (event.type === 'gasto') {
                span.classList.add('event-expense');
            }
            
            // Si está archivado (historial), agregar clase especial
            if (event.archived) {
                span.classList.add('event-archived');
            }
        }
        
        // Si es un préstamo, agregar indicador visual
        if (event.loan && !event.loan.isCounterpart) {
            span.classList.add('event-loan');
            // Añadir pequeño badge de préstamo
            const loanBadge = document.createElement('span');
            loanBadge.className = 'loan-badge';
            loanBadge.textContent = '💰';
            loanBadge.style.fontSize = '8px';
            loanBadge.style.position = 'absolute';
            loanBadge.style.top = '-2px';
            loanBadge.style.right = '-2px';
            span.appendChild(loanBadge);
        }
        
        // Si es contraparte de préstamo, indicador diferente
        if (event.loan && event.loan.isCounterpart) {
            span.classList.add('event-counterpart');
            span.textContent = '↩';
            span.style.fontSize = '10px';
            span.style.lineHeight = '10px';
            span.style.textAlign = 'center';
        }
        
        // Construir tooltip enriquecido
        let tooltip = `${event.title}`;
        
        // Para eventos de planeación, agregar tipo
        if (event.isPlanningEvent) {
            const typeLabel = event.planningType === 'goal' ? '🎯 Meta' : '📅 Gasto Planificado';
            tooltip = `${typeLabel}\n${tooltip}`;
        }
        
        if (event.desc) {
            tooltip += `\n📝 ${event.desc}`;
        }
        
        // Monto esperado vs confirmado
        if (event.confirmed && event.confirmedAmount !== undefined && event.confirmedAmount !== null) {
            tooltip += `\n✅ Confirmado: $${event.confirmedAmount}`;
            if (event.amount !== undefined && event.amount !== event.confirmedAmount) {
                tooltip += ` (Esperado: $${event.amount})`;
            }
        } else if (event.amount !== undefined && event.amount !== null) {
            tooltip += `\n💵 Monto: $${event.amount}`;
        }
        
        if (event.category) {
            tooltip += `\n🏷️ ${event.category}`;
        }
        
        // Información de préstamo (V2)
        if (event.loan) {
            const loanType = event.loan.type === 'given' ? 'Préstamo dado' : 'Préstamo recibido';
            tooltip += `\n💰 ${loanType}`;
            if (event.loan.counterparty) {
                tooltip += ` a ${event.loan.counterparty}`;
            }
            if (event.loan.due_date) {
                tooltip += ` (Vence: ${event.loan.due_date})`;
            }
        }
        
        // Estado de historial
        if (event.archived) {
            tooltip += `\n📦 Historial (Confirmado)`;
        }
        
        // Frecuencia
        if (event.frequency) {
            const interval = event.interval && event.interval > 1 
                ? ` cada ${event.interval}` 
                : ' cada 1';
            const unit = this.getFrequencyUnit(event.frequency, event.interval);
            tooltip += `\n🔁 Repite: ${interval}${unit} (límite: ${event.limit || 6})`;
        }
        
        span.title = tooltip;
        return span;
    }

    /**
     * Obtiene la unidad de frecuencia formateada
     */
    getFrequencyUnit(frequency, interval) {
        const isPlural = interval > 1;
        const units = {
            'semanal': isPlural ? ' semanas' : ' semana',
            'mensual': isPlural ? ' meses' : ' mes',
            'anual': isPlural ? ' años' : ' año'
        };
        return units[frequency] || '';
    }

    /**
     * Actualiza todos los indicadores de eventos
     */
    async refreshAllEventIndicators() {
        // Evitar llamadas concurrentes
        if (this._isRefreshing) {
            console.log('⏩ Skipping duplicate refreshAllEventIndicators call');
            return;
        }
        this._isRefreshing = true;
        
        try {
            // Limpiar TODOS los tipos de indicadores antes de renderizar
            document.querySelectorAll('#calendar-body td').forEach(td => {
                td.querySelectorAll('.event-indicator, .ghost-indicator, .loan-indicator, .plan-indicator, .target-indicator, .solid-indicator').forEach(n => n.remove());
            });

            // Obtener datos del calendario UNA SOLA VEZ para todo el mes
            const sessionData = localStorage.getItem('calendar_session');
            const userId = sessionData ? JSON.parse(sessionData).userId : 'anon';
            const year = this.currentDate.getFullYear();
            const month = this.currentDate.getMonth();
            const cacheKey = `${userId}_${year}_${month}`;
            
            // Invalidar caché si cambió el mes o el usuario
            if (this._calendarDataCacheKey !== cacheKey) {
                this._calendarDataCache = null;
            }
            
            // Obtener datos (con caché)
            if (!this._calendarDataCache) {
                this._calendarDataCache = await getCalendarDataForMonth(userId, year, month);
                this._calendarDataCacheKey = cacheKey;
            }
            
            const calendarData = this._calendarDataCache;

            // Obtener todas las fechas visibles en el calendario
            const cellsWithDates = Array.from(document.querySelectorAll('#calendar-body td[data-date]'));
            
            // Marcar como batch update para evitar limpieza redundante en updateCellIndicator
            this._isBatchUpdate = true;
            
            // Actualizar indicadores para cada celda usando los datos cacheados
            for (const cell of cellsWithDates) {
                const dateISO = cell.dataset.date;
                this.renderCellFromData(cell, dateISO, calendarData);
            }
            
            // Resetear flag
            this._isBatchUpdate = false;
        } finally {
            this._isRefreshing = false;
        }
    }
    
    /**
     * Renderiza los indicadores de una celda usando datos pre-cargados
     */
    renderCellFromData(cell, dateISO, calendarData) {
        const dayData = calendarData[dateISO];
        if (!dayData) return;

        // 1. Renderizar PROJECTED EVENTS (fantasmas - dashed)
        // Solo mostrar si NO tienen movimiento confirmado
        if (dayData.projected_incomes && dayData.projected_incomes.length > 0) {
            dayData.projected_incomes.forEach(proj => {
                if (!proj.has_confirmed_movement) {
                    const indicator = this.createGhostIndicator(proj, 'income');
                    cell.appendChild(indicator);
                }
            });
        }

        if (dayData.projected_expenses && dayData.projected_expenses.length > 0) {
            dayData.projected_expenses.forEach(proj => {
                if (!proj.has_confirmed_movement) {
                    const indicator = this.createGhostIndicator(proj, 'expense');
                    cell.appendChild(indicator);
                }
            });
        }

        // 2. Renderizar LOAN MOVEMENTS (gold glow)
        if (dayData.loan_movements && dayData.loan_movements.length > 0) {
            dayData.loan_movements.forEach(mov => {
                const indicator = this.createLoanIndicator(mov);
                cell.appendChild(indicator);
            });
        }

        // 3. Renderizar PLAN MOVEMENTS (blue glow)
        if (dayData.plan_movements && dayData.plan_movements.length > 0) {
            dayData.plan_movements.forEach(mov => {
                const indicator = this.createPlanMovementIndicator(mov);
                cell.appendChild(indicator);
            });
        }

        // 4. Renderizar REGULAR MOVEMENTS (solid circles)
        if (dayData.confirmed_movements && dayData.confirmed_movements.length > 0) {
            dayData.confirmed_movements.forEach(mov => {
                // Los loan_movements y plan_movements ya se renderizaron arriba
                // Solo renderizar los movimientos regulares aquí
                if (!mov.loan_id && !mov.plan_id) {
                    const indicator = this.createSolidIndicator(mov);
                    cell.appendChild(indicator);
                }
            });
        }

        // 5. Renderizar PLAN TARGETS (flag marker)
        if (dayData.plan_targets && dayData.plan_targets.length > 0) {
            dayData.plan_targets.forEach(plan => {
                const indicator = this.createPlanTargetIndicator(plan);
                cell.appendChild(indicator);
            });
        }
    }
    
    /**
     * Invalida el caché de datos del calendario (llamar después de crear/editar/eliminar)
     */
    invalidateCache() {
        this._calendarDataCache = null;
        this._calendarDataCacheKey = null;
    }

    /**
     * Adjunta listeners a las celdas del calendario
     */
    attachCellListeners() {
        this.cells.forEach(cell => {
            cell.addEventListener('click', (e) => {
                const dateISO = e.currentTarget.dataset.date;
                if (!dateISO) return;

                // Usar modal V2 para crear eventos
                showCreateEventDialog(dateISO, () => {
                    this.invalidateCache();
                    this.refreshAllEventIndicators();
                });
            });
        });
    }

    /**
     * Adjunta listeners a los botones de navegación
     */
    attachNavigationListeners() {
        const prevBtn = document.getElementById('prev-month');
        const nextBtn = document.getElementById('next-month');

        prevBtn.addEventListener('click', () => {
            this.currentDate.setMonth(this.currentDate.getMonth() - 1);
            this.render();
        });

        nextBtn.addEventListener('click', () => {
            this.currentDate.setMonth(this.currentDate.getMonth() + 1);
            this.render();
        });
    }
}
