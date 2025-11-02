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

/**
 * Añade eventos recurrentes en múltiples fechas
 * @param {string[]} dates - Array de fechas ISO
 * @param {Object} eventData - Datos del evento
 */
export function addRecurringEvents(dates, eventData) {
    const events = loadEvents();
    dates.forEach(date => {
        if (!events[date]) {
            events[date] = [];
        }
        events[date].push({
            ...eventData,
            occurrenceDate: date,
            createdAt: new Date().toISOString()
        });
    });
    saveEvents(events);
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
