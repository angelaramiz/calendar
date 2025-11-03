/**
 * Módulo del Calendario
 * Gestiona la renderización y navegación del calendario
 */

import { loadEvents } from './events.js';
import { openEventModal } from './modal.js';

export class Calendar {
    constructor(containerId) {
        this.cells = document.querySelectorAll(`#${containerId} td`);
        this.currentMonthElement = document.getElementById('current-month');
        this.currentDate = new Date();
        this.monthNames = [
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        ];
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

        // Actualizar indicadores de eventos
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
     */
    updateCellIndicator(dateISO) {
        const events = loadEvents();
        const cell = document.querySelector(`#calendar-body td[data-date="${dateISO}"]`);
        
        if (!cell) return;

        // Eliminar indicadores previos
        cell.querySelectorAll('.event-indicator').forEach(n => n.remove());

        const eventsList = events[dateISO];
        if (eventsList && eventsList.length) {
            eventsList.forEach(event => {
                const indicator = this.createEventIndicator(event);
                cell.appendChild(indicator);
            });
        }
    }

    /**
     * Crea un indicador visual de evento
     */
    createEventIndicator(event) {
        const span = document.createElement('span');
        span.className = 'event-indicator';
        
        // Agregar clase específica según el tipo
        if (event.type === 'ingreso') {
            span.classList.add('event-income');
        } else if (event.type === 'gasto') {
            span.classList.add('event-expense');
        }
        
        // Si está archivado (historial), agregar clase especial
        if (event.archived) {
            span.classList.add('event-archived');
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
        
        // Información de préstamo
        if (event.loan && !event.loan.isCounterpart) {
            const loanKind = event.loan.kind === 'favor' ? 'Préstamo a favor' : 'Préstamo en contra';
            tooltip += `\n💰 ${loanKind}`;
            if (event.loan.recoveryDays) {
                tooltip += ` (Recupero en ${event.loan.recoveryDays} días)`;
            }
        }
        
        // Si es contraparte
        if (event.loan && event.loan.isCounterpart) {
            tooltip += `\n↩️ Compensación de préstamo`;
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
    refreshAllEventIndicators() {
        // Limpiar todos los indicadores
        document.querySelectorAll('#calendar-body td').forEach(td => {
            td.querySelectorAll('.event-indicator').forEach(n => n.remove());
        });

        // Recrear indicadores desde storage
        const events = loadEvents();
        Object.keys(events).forEach(dateISO => {
            this.updateCellIndicator(dateISO);
        });
    }

    /**
     * Adjunta listeners a las celdas del calendario
     */
    attachCellListeners() {
        this.cells.forEach(cell => {
            cell.addEventListener('click', (e) => {
                const dateISO = e.currentTarget.dataset.date;
                if (!dateISO) return;

                openEventModal(dateISO, (affectedDates) => {
                    // Callback para actualizar UI después de cambios
                    affectedDates.forEach(date => this.updateCellIndicator(date));
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
