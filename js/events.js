/**
 * Módulo de Gestión de Eventos
 * Maneja localStorage por usuario y sincronización con Supabase (best-effort)
 */
import { supabase } from './supabase-client.js';

// Activar sincronización con Supabase (manteniendo localStorage como fuente inmediata)
const ENABLE_SUPABASE_SYNC = true;

// Obtiene el ID de usuario actual desde la sesión
function getCurrentUserId() {
    try {
        const session = JSON.parse(localStorage.getItem('calendar_session') || 'null');
        return session && session.userId ? session.userId : 'anon';
    } catch {
        return 'anon';
    }
}

// Obtiene la clave de almacenamiento específica por usuario
function getEventsStorageKey() {
    const userId = getCurrentUserId();
    return `events:${userId}`;
}

/**
 * Carga eventos desde localStorage (aislados por usuario)
 * @returns {Object} Objeto con fechas como keys y arrays de eventos como values
 */
export function loadEvents() {
    try {
        const key = getEventsStorageKey();
        return JSON.parse(localStorage.getItem(key) || '{}');
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
        const key = getEventsStorageKey();
        localStorage.setItem(key, JSON.stringify(eventsObj));
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

    // Sincronizar con Supabase en segundo plano
    if (ENABLE_SUPABASE_SYNC) {
        syncDatesToSupabase([dateISO]).catch(() => {});
    }
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
                if (ENABLE_SUPABASE_SYNC && createdDates.length) {
                    syncDatesToSupabase(createdDates).catch(() => {});
                }
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
    if (changed && ENABLE_SUPABASE_SYNC) {
        // sincronizar todas las fechas afectadas
        const affected = Object.keys(events);
        syncDatesToSupabase(affected).catch(() => {});
    }
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
    if (ENABLE_SUPABASE_SYNC) {
        syncDatesToSupabase(dates).catch(() => {});
    }
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
    if (ENABLE_SUPABASE_SYNC) {
        syncDatesToSupabase(updatedDates.length ? updatedDates : [dateISO]).catch(() => {});
    }
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
        if (ENABLE_SUPABASE_SYNC) {
            syncDatesToSupabase([dateISO]).catch(() => {});
        }
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
        if (ENABLE_SUPABASE_SYNC) {
            syncDatesToSupabase([dateISO]).catch(() => {});
        }
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

// ============================================================
// Sincronización con Supabase
// ============================================================

function mapEventToRow(userId, dateISO, ev) {
    return {
        user_id: userId,
        date: dateISO,
        title: ev.title || '',
        description: ev.desc || null,
        type: ev.type || 'evento',
        amount: ev.amount !== undefined && ev.amount !== null ? Number(ev.amount) : null,
        category: ev.category || null,
        confirmed: !!ev.confirmed,
        archived: !!ev.archived,
        confirmed_amount: ev.confirmedAmount !== undefined && ev.confirmedAmount !== null ? Number(ev.confirmedAmount) : null,
        is_recurring: !!(ev.frequency),
        occurrence_date: ev.occurrenceDate || dateISO,
        frequency: ev.frequency || null,
        interval: ev.interval || null,
        limit_count: ev.limit || null,
        loan_id: null,
        is_loan_counterpart: ev.loan && ev.loan.isCounterpart ? true : false
    };
}

async function syncDatesToSupabase(dates) {
    const sessionRaw = localStorage.getItem('calendar_session');
    if (!sessionRaw) return;
    const session = JSON.parse(sessionRaw);
    const userId = session.userId;
    if (!userId) return;

    const events = loadEvents();

    // Borrar filas existentes de esas fechas para el usuario y reinsertar estado actual
    try {
        // Delete
        const { error: delError } = await supabase
            .from('events')
            .delete()
            .eq('user_id', userId)
            .in('date', dates);
        if (delError) {
            console.warn('No se pudo eliminar para resincronizar:', delError.message);
        }


        // Insertar evento por evento para poder asociar préstamos
        for (const dateISO of dates) {
            const list = events[dateISO] || [];
            for (const ev of list) {
                const row = mapEventToRow(userId, dateISO, ev);
                const { data: insertedEvent, error: insErr } = await supabase
                    .from('events')
                    .insert([row])
                    .select('*')
                    .single();
                if (insErr) {
                    console.warn('No se pudo insertar evento:', insErr.message);
                    continue;
                }

                // Si el evento tiene datos de préstamo y NO es contraparte, crear préstamo
                if (ev.loan && !ev.loan.isCounterpart) {
                    const loanRow = mapLoanToRow(userId, insertedEvent.id, ev);
                    const { data: insertedLoan, error: loanErr } = await supabase
                        .from('loans')
                        .insert([loanRow])
                        .select('*')
                        .single();
                    if (loanErr) {
                        console.warn('No se pudo insertar préstamo:', loanErr.message);
                    } else {
                        // Opcional: referenciar loan_id en el evento
                        await supabase.from('events').update({ loan_id: insertedLoan.id }).eq('id', insertedEvent.id);
                    }
                }
            }
        }
    } catch (e) {
        console.warn('Error sincronizando con Supabase:', e);
    }
}

function mapLoanToRow(userId, eventId, ev) {
    const loan = ev.loan || {};
    const principal = ev.amount !== undefined && ev.amount !== null ? Number(ev.amount) : 0;
    return {
        user_id: userId,
        event_id: eventId,
        kind: loan.kind || (ev.type === 'gasto' ? 'favor' : 'contra'),
        amount: principal,
        expected_return: loan.expectedReturn !== undefined && loan.expectedReturn !== null ? Number(loan.expectedReturn) : null,
        interest_value: loan.interestValue !== undefined && loan.interestValue !== null ? Number(loan.interestValue) : null,
        interest_percent: loan.interestPercent !== undefined && loan.interestPercent !== null ? Number(loan.interestPercent) : null,
        payment_plan: loan.paymentPlan || 'single',
        recovery_days: loan.recoveryDays || null,
        payment_frequency: loan.paymentFrequency || null,
        payment_count: loan.paymentCount || null,
        custom_dates: loan.customDates && loan.customDates.length ? loan.customDates : null,
        notes: loan.notes || null,
        status: 'active'
    };
}

export async function syncDownAllEventsForUser() {
    const sessionRaw = localStorage.getItem('calendar_session');
    if (!sessionRaw) return;
    const session = JSON.parse(sessionRaw);
    const userId = session.userId;
    if (!userId) return;

    try {
        const { data, error } = await supabase
            .from('events')
            .select('*')
            .eq('user_id', userId);
        if (error) {
            console.warn('No se pudo leer eventos desde Supabase:', error.message);
            return;
        }

        const grouped = {};
        (data || []).forEach(row => {
            const dateISO = row.date;
            if (!grouped[dateISO]) grouped[dateISO] = [];
            grouped[dateISO].push({
                title: row.title,
                desc: row.description || '',
                type: row.type,
                amount: row.amount !== null && row.amount !== undefined ? Number(row.amount) : null,
                category: row.category || '',
                confirmed: !!row.confirmed,
                archived: !!row.archived,
                confirmedAmount: row.confirmed_amount !== null && row.confirmed_amount !== undefined ? Number(row.confirmed_amount) : null,
                frequency: row.frequency || '',
                interval: row.interval || 1,
                limit: row.limit_count || 0,
                occurrenceDate: row.occurrence_date || row.date,
                createdAt: row.created_at,
                updatedAt: row.updated_at,
                // marcar préstamo para indicador en UI
                loan: (row.loan_id || row.is_loan_counterpart) ? {
                    isCounterpart: !!row.is_loan_counterpart,
                    loanId: row.loan_id || null
                } : undefined
            });
        });

        // Reemplazar almacenamiento local del usuario
        const key = getEventsStorageKey();
        localStorage.setItem(key, JSON.stringify(grouped));
    } catch (e) {
        console.warn('Error en syncDownAllEventsForUser:', e);
    }
}

export async function syncDownMonth(year, month) {
    const sessionRaw = localStorage.getItem('calendar_session');
    if (!sessionRaw) return;
    const session = JSON.parse(sessionRaw);
    const userId = session.userId;
    if (!userId) return;

    // calcular primer y último día del mes
    const start = new Date(year, month, 1);
    const end = new Date(year, month + 1, 0);
    const startISO = start.toISOString().slice(0, 10);
    const endISO = end.toISOString().slice(0, 10);

    try {
        const { data, error } = await supabase
            .from('events')
            .select('*')
            .eq('user_id', userId)
            .gte('date', startISO)
            .lte('date', endISO);
        if (error) {
            console.warn('No se pudo leer eventos del mes desde Supabase:', error.message);
            return;
        }

        // cargar eventos actuales y limpiar solo las fechas del mes
        const key = getEventsStorageKey();
        const current = JSON.parse(localStorage.getItem(key) || '{}');
        // eliminar claves del mes
        const monthPrefix = `${year}-${String(month + 1).padStart(2, '0')}-`;
        Object.keys(current).forEach(k => {
            if (k.startsWith(monthPrefix)) delete current[k];
        });

        // agregar los del servidor
        (data || []).forEach(row => {
            const dateISO = row.date;
            if (!current[dateISO]) current[dateISO] = [];
            current[dateISO].push({
                title: row.title,
                desc: row.description || '',
                type: row.type,
                amount: row.amount !== null && row.amount !== undefined ? Number(row.amount) : null,
                category: row.category || '',
                confirmed: !!row.confirmed,
                archived: !!row.archived,
                confirmedAmount: row.confirmed_amount !== null && row.confirmed_amount !== undefined ? Number(row.confirmed_amount) : null,
                frequency: row.frequency || '',
                interval: row.interval || 1,
                limit: row.limit_count || 0,
                occurrenceDate: row.occurrence_date || row.date,
                createdAt: row.created_at,
                updatedAt: row.updated_at,
                // marcar préstamo para indicador en UI
                loan: (row.loan_id || row.is_loan_counterpart) ? {
                    isCounterpart: !!row.is_loan_counterpart,
                    loanId: row.loan_id || null
                } : undefined
            });
        });

        localStorage.setItem(key, JSON.stringify(current));
    } catch (e) {
        console.warn('Error en syncDownMonth:', e);
    }
}
