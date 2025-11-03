/**
 * Módulo de Gestión de Eventos
 * Maneja localStorage y operaciones CRUD de eventos
 */

/**
 * Carga eventos desde localStorage
 * @returns {Object} Objeto con fechas como keys y arrays de eventos como values
 */
export function loadEvents() {
    try {
        return JSON.parse(localStorage.getItem('events') || '{}');
    } catch (e) {
        console.error('Error al cargar eventos:', e);
        return {};
    }
}

/**
 * Guarda eventos en localStorage
 * @param {Object} eventsObj - Objeto de eventos a guardar
 */
export function saveEvents(eventsObj) {
    try {
        localStorage.setItem('events', JSON.stringify(eventsObj));
    } catch (e) {
        console.error('Error al guardar eventos:', e);
    }
}

/**
 * Añade un evento único a una fecha específica
 * @param {string} dateISO - Fecha en formato 'YYYY-MM-DD'
 * @param {Object} eventData - Datos del evento
 */
export function addEvent(dateISO, eventData) {
    const events = loadEvents();
    if (!events[dateISO]) {
        events[dateISO] = [];
    }
    events[dateISO].push({
        ...eventData,
        createdAt: new Date().toISOString()
    });
    saveEvents(events);
}

// Helper: add days to ISO date string
function addDaysToISO(dateISO, days) {
    const d = new Date(dateISO + 'T00:00:00');
    d.setDate(d.getDate() + Number(days));
    return d.toISOString().slice(0,10);
}

/**
 * Crea evento(s) contraparte para un loan (préstamo) identificado por loanId.
 * Busca el evento original (loanId) y crea contrapartes según el plan de pagos:
 * - single: un evento en recoveryDays
 * - weekly/biweekly/monthly: múltiples eventos según frecuencia y cantidad
 * - custom: eventos en fechas específicas
 * @param {string} loanId
 * @returns {string[]|null} fechas ISO de los eventos contraparte creados o null
 */
export function createLoanCounterpartByLoanId(loanId) {
    const events = loadEvents();
    const createdDates = [];
    
    // find original event (not counterpart)
    for (const dateISO of Object.keys(events)) {
        const arr = events[dateISO];
        for (let i = 0; i < arr.length; i++) {
            const ev = arr[i];
            if (ev.loan && ev.loan.loanId === loanId && !ev.loan.isCounterpart) {
                const loan = ev.loan;
                const baseAmount = loan.expectedReturn !== undefined && loan.expectedReturn !== null 
                    ? loan.expectedReturn 
                    : (ev.amount !== undefined ? ev.amount : 0);
                
                // Determinar fechas de pago según el plan
                const paymentDates = [];
                
                if (loan.paymentPlan === 'single' && loan.recoveryDays) {
                    // Pago único
                    paymentDates.push({
                        date: addDaysToISO(dateISO, loan.recoveryDays),
                        amount: baseAmount,
                        installment: 1,
                        total: 1
                    });
                } else if (loan.paymentPlan === 'custom' && loan.customDates && loan.customDates.length) {
                    // Fechas personalizadas
                    const amountPerPayment = baseAmount / loan.customDates.length;
                    loan.customDates.forEach((date, idx) => {
                        paymentDates.push({
                            date: date,
                            amount: amountPerPayment,
                            installment: idx + 1,
                            total: loan.customDates.length
                        });
                    });
                } else if (['weekly', 'biweekly', 'monthly'].includes(loan.paymentPlan)) {
                    // Pagos recurrentes
                    const frequency = loan.paymentFrequency || 1;
                    const count = loan.paymentCount || 1;
                    const amountPerPayment = baseAmount / count;
                    
                    let daysIncrement;
                    if (loan.paymentPlan === 'weekly') {
                        daysIncrement = 7 * frequency;
                    } else if (loan.paymentPlan === 'biweekly') {
                        daysIncrement = 14 * frequency;
                    } else if (loan.paymentPlan === 'monthly') {
                        daysIncrement = 30 * frequency; // aproximado
                    }
                    
                    for (let j = 0; j < count; j++) {
                        paymentDates.push({
                            date: addDaysToISO(dateISO, daysIncrement * (j + 1)),
                            amount: amountPerPayment,
                            installment: j + 1,
                            total: count
                        });
                    }
                }
                
                // Crear eventos contraparte
                paymentDates.forEach(payment => {
                    const counterpart = {
                        title: payment.total > 1 
                            ? `Pago ${payment.installment}/${payment.total}: ${ev.title}`
                            : `Compensación: ${ev.title}`,
                        desc: loan.notes 
                            ? `${loan.notes}\n(Contrapartida de préstamo)` 
                            : `Contrapartida de préstamo (${ev.title})`,
                        amount: payment.amount,
                        type: ev.type === 'gasto' ? 'ingreso' : 'gasto',
                        category: ev.category || '',
                        frequency: '',
                        interval: 1,
                        limit: 1,
                        origin: payment.date,
                        loan: {
                            ...loan,
                            isCounterpart: true,
                            installment: payment.installment,
                            totalInstallments: payment.total
                        }
                    };
                    
                    if (!events[payment.date]) events[payment.date] = [];
                    events[payment.date].push({ 
                        ...counterpart, 
                        createdAt: new Date().toISOString() 
                    });
                    createdDates.push(payment.date);
                });
                
                saveEvents(events);
                return createdDates;
            }
        }
    }
    return null;
}

/**
 * Elimina cualquier evento contraparte asociado a loanId
 * @param {string} loanId
 */
export function removeLoanCounterpartByLoanId(loanId) {
    const events = loadEvents();
    let changed = false;
    Object.keys(events).forEach(dateISO => {
        const arr = events[dateISO];
        const newArr = arr.filter(ev => !(ev.loan && ev.loan.loanId === loanId && ev.loan.isCounterpart));
        if (newArr.length !== arr.length) {
            changed = true;
            if (newArr.length) events[dateISO] = newArr; else delete events[dateISO];
        }
    });
    if (changed) saveEvents(events);
}

/**
 * Añade eventos recurrentes en múltiples fechas
 * @param {string[]} dates - Array de fechas ISO
 * @param {Object} eventData - Datos del evento
 */
export function addRecurringEvents(dates, eventData) {
    const events = loadEvents();
    // ensure we have a seriesId to group recurring occurrences
    const seriesId = eventData.seriesId || (`series-${Date.now().toString(36)}-${Math.floor(Math.random()*10000)}`);
    dates.forEach(date => {
        if (!events[date]) {
            events[date] = [];
        }
        events[date].push({
            ...eventData,
            occurrenceDate: date,
            seriesId,
            createdAt: new Date().toISOString()
        });
    });
    saveEvents(events);
}

/**
 * Actualiza todas las ocurrencias futuras de una serie a partir de una fecha dada
 * @param {string} dateISO - Fecha de la ocurrencia base (YYYY-MM-DD)
 * @param {number} index - Índice de la ocurrencia en esa fecha
 * @param {Object} newEventData - Nuevos datos a aplicar
 * @returns {string[]} Array de fechas actualizadas
 */
export function updateFutureOccurrences(dateISO, index, newEventData) {
    const events = loadEvents();
    const baseArr = events[dateISO] || [];
    const base = baseArr[index];
    if (!base) return [];

    const seriesId = base.seriesId;
    const matchBy = {
        seriesId: seriesId || null,
        origin: base.origin || null,
        frequency: base.frequency || null,
        title: base.title || null
    };

    const updatedDates = [];

    // iterate over dates and update events whose occurrenceDate >= dateISO
    Object.keys(events).sort().forEach(d => {
        if (d < dateISO) return; // only future and current
        const arr = events[d];
        arr.forEach((ev, i) => {
            let shouldUpdate = false;
            if (matchBy.seriesId && ev.seriesId && ev.seriesId === matchBy.seriesId) shouldUpdate = true;
            else if (!matchBy.seriesId) {
                // fallback matching by origin+frequency+title
                if (ev.origin && ev.origin === matchBy.origin && ev.frequency === matchBy.frequency && ev.title === matchBy.title) {
                    shouldUpdate = true;
                }
            }

            if (shouldUpdate) {
                // Preserve critical metadata
                events[d][i] = {
                    ...ev,
                    ...newEventData,
                    // Preserve these fields explicitly
                    seriesId: ev.seriesId,
                    occurrenceDate: ev.occurrenceDate || d,
                    createdAt: ev.createdAt,
                    updatedAt: new Date().toISOString()
                };
                if (!updatedDates.includes(d)) {
                    updatedDates.push(d);
                }
            }
        });
    });

    saveEvents(events);
    return updatedDates;
}

/**
 * Elimina un evento específico de una fecha
 * @param {string} dateISO - Fecha en formato 'YYYY-MM-DD'
 * @param {number} index - Índice del evento a eliminar
 */
export function deleteEvent(dateISO, index) {
    const events = loadEvents();
    const arr = events[dateISO] || [];
    
    if (index >= 0 && index < arr.length) {
        arr.splice(index, 1);
        if (arr.length) {
            events[dateISO] = arr;
        } else {
            delete events[dateISO];
        }
        saveEvents(events);
    }
}

/**
 * Actualiza un evento existente en una fecha específica
 * @param {string} dateISO - Fecha en formato 'YYYY-MM-DD'
 * @param {number} index - Índice del evento a actualizar
 * @param {Object} newEventData - Nuevos datos del evento (reemplaza campos)
 */
export function updateEvent(dateISO, index, newEventData) {
    const events = loadEvents();
    const arr = events[dateISO] || [];
    if (index >= 0 && index < arr.length) {
        // preservamos createdAt si existía
        const existing = arr[index] || {};
        arr[index] = {
            ...existing,
            ...newEventData,
            updatedAt: new Date().toISOString()
        };
        events[dateISO] = arr;
        saveEvents(events);
    }
}

/**
 * Obtiene eventos de una fecha específica
 * @param {string} dateISO - Fecha en formato 'YYYY-MM-DD'
 * @returns {Array} Array de eventos
 */
export function getEventsForDate(dateISO) {
    const events = loadEvents();
    return events[dateISO] || [];
}

/**
 * Escapa HTML para prevenir XSS
 * @param {string} str - String a escapar
 * @returns {string} String escapado
 */
export function escapeHTML(str) {
    return String(str || '').replace(/[&<>"']/g, function(m) {
        return {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        }[m];
    });
}

/**
 * Capitaliza la primera letra de un string
 * @param {string} str - String a capitalizar
 * @returns {string} String capitalizado
 */
export function capitalize(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1);
}
