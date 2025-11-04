/**
 * Módulo de Estructura de Datos Optimizada
 * Prepara los datos para migración a base de datos
 * Agrupación: Mensual → Semanal → Eventos
 */

/**
 * Obtiene el número de semana del mes (1-6) según la lógica específica:
 * - Semana 1: Días del mes desde el primer día hasta completar la semana
 * - Semanas 2-5: Semanas completas (7 días)
 * - Semana 6: Últimos días del mes que no caben en semanas anteriores
 * 
 * @param {Date} date - Fecha a evaluar
 * @returns {number} Número de semana (1-6)
 */
export function getWeekOfMonth(date) {
    const year = date.getFullYear();
    const month = date.getMonth();
    const day = date.getDate();
    
    // Primer día del mes
    const firstDay = new Date(year, month, 1);
    const firstDayOfWeek = firstDay.getDay(); // 0=Dom, 6=Sáb
    
    // Calcular en qué semana cae este día
    // Si el mes empieza en Domingo (0), la semana 1 tiene 7 días
    // Si empieza en Sábado (6), la semana 1 tiene solo 1 día
    
    const daysInFirstWeek = firstDayOfWeek === 0 ? 7 : (7 - firstDayOfWeek);
    
    if (day <= daysInFirstWeek) {
        return 1;
    }
    
    // Para días después de la primera semana
    const daysAfterFirstWeek = day - daysInFirstWeek;
    const weekNumber = Math.floor(daysAfterFirstWeek / 7) + 2;
    
    // Máximo 6 semanas por mes
    return Math.min(weekNumber, 6);
}

/**
 * Obtiene el rango de fechas de una semana específica del mes
 * Solo incluye los días que pertenecen al mes actual
 * 
 * @param {number} year - Año
 * @param {number} month - Mes (0-11)
 * @param {number} weekNumber - Número de semana (1-6)
 * @returns {Object} { start: Date, end: Date, days: number }
 */
export function getWeekRangeInMonth(year, month, weekNumber) {
    const firstDay = new Date(year, month, 1);
    const firstDayOfWeek = firstDay.getDay();
    const lastDay = new Date(year, month + 1, 0);
    const totalDaysInMonth = lastDay.getDate();
    
    const daysInFirstWeek = firstDayOfWeek === 0 ? 7 : (7 - firstDayOfWeek);
    
    let startDay, endDay;
    
    if (weekNumber === 1) {
        startDay = 1;
        endDay = daysInFirstWeek;
    } else {
        startDay = daysInFirstWeek + ((weekNumber - 2) * 7) + 1;
        endDay = Math.min(startDay + 6, totalDaysInMonth);
    }
    
    return {
        start: new Date(year, month, startDay),
        end: new Date(year, month, endDay),
        days: endDay - startDay + 1,
        startDay,
        endDay
    };
}

/**
 * Estructura de datos para Usuario
 */
export class UserSchema {
    constructor(data = {}) {
        this.id = data.id || null;                          // UUID generado por DB
        this.email = data.email || null;
        this.name = data.name || null;
        this.createdAt = data.createdAt || new Date().toISOString();
        this.updatedAt = data.updatedAt || new Date().toISOString();
        this.settings = data.settings || {
            notifications: {
                enabled: true,
                browserNotifications: false,
                alerts: {
                    eventReminder: true,
                    loanDue: true,
                    recurringEvent: true
                },
                timing: {
                    daysBefore: 1,
                    showOnStartup: true
                }
            },
            preferences: {
                currency: 'USD',
                dateFormat: 'YYYY-MM-DD',
                firstDayOfWeek: 0, // 0=Domingo
                theme: 'light'
            }
        };
    }
    
    toJSON() {
        return {
            id: this.id,
            email: this.email,
            name: this.name,
            createdAt: this.createdAt,
            updatedAt: this.updatedAt,
            settings: this.settings
        };
    }
}

/**
 * Estructura de datos para Evento individual
 */
export class EventSchema {
    constructor(data = {}) {
        this.id = data.id || null;                          // UUID generado por DB
        this.userId = data.userId || null;                  // FK a Usuario
        this.title = data.title || '';
        this.description = data.description || data.desc || '';
        this.type = data.type || 'evento';                  // 'ingreso', 'gasto', 'evento'
        this.amount = data.amount !== undefined ? Number(data.amount) : null;
        this.category = data.category || null;
        this.date = data.date || data.origin || null;       // YYYY-MM-DD
        this.confirmed = data.confirmed || false;
        this.archived = data.archived || false;
        this.confirmedAmount = data.confirmedAmount !== undefined ? Number(data.confirmedAmount) : null;
        
        // Recurrencia
        this.isRecurring = data.frequency ? true : false;
        this.seriesId = data.seriesId || null;              // FK a Serie si es recurrente
        this.occurrenceDate = data.occurrenceDate || null;  // Fecha específica de esta ocurrencia
        this.frequency = data.frequency || null;            // 'semanal', 'mensual', 'anual'
        this.interval = data.interval || 1;
        this.limit = data.limit || 6;
        
        // Préstamo
        this.loanId = data.loan?.loanId || null;            // FK a Préstamo si aplica
        this.isLoanCounterpart = data.loan?.isCounterpart || false;
        
        // Metadata
        this.createdAt = data.createdAt || new Date().toISOString();
        this.updatedAt = data.updatedAt || new Date().toISOString();
    }
    
    toJSON() {
        return {
            id: this.id,
            userId: this.userId,
            title: this.title,
            description: this.description,
            type: this.type,
            amount: this.amount,
            category: this.category,
            date: this.date,
            confirmed: this.confirmed,
            archived: this.archived,
            confirmedAmount: this.confirmedAmount,
            isRecurring: this.isRecurring,
            seriesId: this.seriesId,
            occurrenceDate: this.occurrenceDate,
            frequency: this.frequency,
            interval: this.interval,
            limit: this.limit,
            loanId: this.loanId,
            isLoanCounterpart: this.isLoanCounterpart,
            createdAt: this.createdAt,
            updatedAt: this.updatedAt
        };
    }
}

/**
 * Estructura de datos para Serie Recurrente
 */
export class RecurringSeriesSchema {
    constructor(data = {}) {
        this.id = data.id || data.seriesId || null;         // UUID
        this.userId = data.userId || null;
        this.title = data.title || '';
        this.description = data.description || '';
        this.type = data.type || 'evento';
        this.amount = data.amount !== undefined ? Number(data.amount) : null;
        this.category = data.category || null;
        this.frequency = data.frequency || 'mensual';
        this.interval = data.interval || 1;
        this.startDate = data.startDate || data.origin || null;
        this.endDate = data.endDate || null;                // null = sin fin
        this.occurrenceCount = data.occurrenceCount || data.limit || null;
        this.createdAt = data.createdAt || new Date().toISOString();
        this.updatedAt = data.updatedAt || new Date().toISOString();
    }
    
    toJSON() {
        return {
            id: this.id,
            userId: this.userId,
            title: this.title,
            description: this.description,
            type: this.type,
            amount: this.amount,
            category: this.category,
            frequency: this.frequency,
            interval: this.interval,
            startDate: this.startDate,
            endDate: this.endDate,
            occurrenceCount: this.occurrenceCount,
            createdAt: this.createdAt,
            updatedAt: this.updatedAt
        };
    }
}

/**
 * Estructura de datos para Préstamo
 */
export class LoanSchema {
    constructor(data = {}) {
        this.id = data.id || data.loanId || null;           // UUID
        this.userId = data.userId || null;
        this.eventId = data.eventId || null;                // FK al evento original
        this.kind = data.kind || 'favor';                   // 'favor' o 'contra'
        this.amount = data.amount !== undefined ? Number(data.amount) : null;
        this.expectedReturn = data.expectedReturn !== undefined ? Number(data.expectedReturn) : null;
        this.interestValue = data.interestValue !== undefined ? Number(data.interestValue) : null;
        this.interestPercent = data.interestPercent !== undefined ? Number(data.interestPercent) : null;
        this.paymentPlan = data.paymentPlan || 'single';
        this.recoveryDays = data.recoveryDays || null;
        this.paymentFrequency = data.paymentFrequency || null;
        this.paymentCount = data.paymentCount || null;
        this.customDates = data.customDates || null;        // JSON array
        this.notes = data.notes || null;
        this.status = data.status || 'active';              // 'active', 'completed', 'cancelled'
        this.createdAt = data.createdAt || new Date().toISOString();
        this.updatedAt = data.updatedAt || new Date().toISOString();
    }
    
    toJSON() {
        return {
            id: this.id,
            userId: this.userId,
            eventId: this.eventId,
            kind: this.kind,
            amount: this.amount,
            expectedReturn: this.expectedReturn,
            interestValue: this.interestValue,
            interestPercent: this.interestPercent,
            paymentPlan: this.paymentPlan,
            recoveryDays: this.recoveryDays,
            paymentFrequency: this.paymentFrequency,
            paymentCount: this.paymentCount,
            customDates: this.customDates,
            notes: this.notes,
            status: this.status,
            createdAt: this.createdAt,
            updatedAt: this.updatedAt
        };
    }
}

/**
 * Estructura de datos para Alerta
 */
export class AlertSchema {
    constructor(data = {}) {
        this.id = data.id || null;                          // UUID
        this.userId = data.userId || null;
        this.eventId = data.eventId || null;                // FK al evento
        this.message = data.message || '';
        this.triggerDaysBefore = data.triggerDaysBefore !== undefined ? data.triggerDaysBefore : 1;
        this.priority = data.priority || 'medium';          // 'low', 'medium', 'high', 'critical'
        this.browserNotification = data.browserNotification || false;
        this.isRead = data.isRead || false;
        this.readAt = data.readAt || null;
        this.createdAt = data.createdAt || new Date().toISOString();
    }
    
    toJSON() {
        return {
            id: this.id,
            userId: this.userId,
            eventId: this.eventId,
            message: this.message,
            triggerDaysBefore: this.triggerDaysBefore,
            priority: this.priority,
            browserNotification: this.browserNotification,
            isRead: this.isRead,
            readAt: this.readAt,
            createdAt: this.createdAt
        };
    }
}

/**
 * Estructura de agrupación semanal de eventos
 */
export class WeekGroupSchema {
    constructor(year, month, weekNumber) {
        this.year = year;
        this.month = month;                                 // 0-11
        this.weekNumber = weekNumber;                       // 1-6
        const range = getWeekRangeInMonth(year, month, weekNumber);
        this.startDay = range.startDay;
        this.endDay = range.endDay;
        this.days = range.days;
        this.events = [];                                   // Array de EventSchema
        this.eventCount = 0;
        this.totalIncome = 0;
        this.totalExpense = 0;
    }
    
    addEvent(event) {
        this.events.push(event);
        this.eventCount++;
        
        if (event.type === 'ingreso' && event.amount) {
            this.totalIncome += event.amount;
        } else if (event.type === 'gasto' && event.amount) {
            this.totalExpense += event.amount;
        }
    }
    
    toJSON() {
        return {
            year: this.year,
            month: this.month,
            weekNumber: this.weekNumber,
            startDay: this.startDay,
            endDay: this.endDay,
            days: this.days,
            eventCount: this.eventCount,
            totalIncome: this.totalIncome,
            totalExpense: this.totalExpense,
            events: this.events.map(e => e.toJSON ? e.toJSON() : e)
        };
    }
}

/**
 * Estructura de agrupación mensual de eventos
 */
export class MonthGroupSchema {
    constructor(year, month, userId = null) {
        this.userId = userId;
        this.year = year;
        this.month = month;                                 // 0-11
        this.weeks = {};                                    // { 1: WeekGroupSchema, 2: ..., 6: ... }
        this.eventCount = 0;
        this.totalIncome = 0;
        this.totalExpense = 0;
        this.netBalance = 0;
        
        // Inicializar las 6 semanas posibles
        for (let w = 1; w <= 6; w++) {
            this.weeks[w] = new WeekGroupSchema(year, month, w);
        }
    }
    
    addEvent(event) {
        const eventDate = new Date(event.date + 'T00:00:00');
        const weekNumber = getWeekOfMonth(eventDate);
        
        if (this.weeks[weekNumber]) {
            this.weeks[weekNumber].addEvent(event);
            this.eventCount++;
            
            if (event.type === 'ingreso' && event.amount) {
                this.totalIncome += event.amount;
            } else if (event.type === 'gasto' && event.amount) {
                this.totalExpense += event.amount;
            }
        }
    }
    
    calculateBalance() {
        this.netBalance = this.totalIncome - this.totalExpense;
        return this.netBalance;
    }
    
    toJSON() {
        return {
            userId: this.userId,
            year: this.year,
            month: this.month,
            eventCount: this.eventCount,
            totalIncome: this.totalIncome,
            totalExpense: this.totalExpense,
            netBalance: this.netBalance,
            weeks: Object.keys(this.weeks).reduce((acc, key) => {
                acc[key] = this.weeks[key].toJSON();
                return acc;
            }, {})
        };
    }
    
    /**
     * Compacta el JSON eliminando semanas vacías
     */
    toCompactJSON() {
        const json = this.toJSON();
        
        // Filtrar semanas sin eventos
        const compactWeeks = {};
        Object.keys(json.weeks).forEach(key => {
            if (json.weeks[key].eventCount > 0) {
                compactWeeks[key] = json.weeks[key];
            }
        });
        
        json.weeks = compactWeeks;
        return json;
    }
}

/**
 * Migra datos del formato antiguo (localStorage) al nuevo formato agrupado
 * 
 * @param {Object} oldEvents - Eventos en formato { "YYYY-MM-DD": [...] }
 * @param {string} userId - ID del usuario
 * @returns {Object} { months: {}, events: [], loans: [], series: [] }
 */
export function migrateFromLocalStorage(oldEvents, userId = 'user-local') {
    const months = {};                  // { "YYYY-MM": MonthGroupSchema }
    const events = [];                  // Array de EventSchema
    const loans = [];                   // Array de LoanSchema
    const series = new Map();           // Map de RecurringSeriesSchema
    const alerts = [];                  // Array de AlertSchema
    
    // Procesar cada fecha
    Object.keys(oldEvents).forEach(dateISO => {
        const [year, month, day] = dateISO.split('-').map(Number);
        const monthKey = `${year}-${String(month).padStart(2, '0')}`;
        
        // Crear grupo mensual si no existe
        if (!months[monthKey]) {
            months[monthKey] = new MonthGroupSchema(year, month - 1, userId);
        }
        
        // Procesar eventos del día
        oldEvents[dateISO].forEach(oldEvent => {
            // Crear EventSchema
            const event = new EventSchema({
                ...oldEvent,
                userId,
                date: dateISO
            });
            
            events.push(event);
            months[monthKey].addEvent(event);
            
            // Extraer préstamo si existe
            if (oldEvent.loan && !oldEvent.loan.isCounterpart) {
                const loan = new LoanSchema({
                    ...oldEvent.loan,
                    userId,
                    amount: oldEvent.amount
                });
                loans.push(loan);
            }
            
            // Extraer serie recurrente si existe
            if (oldEvent.seriesId && oldEvent.frequency) {
                if (!series.has(oldEvent.seriesId)) {
                    const seriesData = new RecurringSeriesSchema({
                        id: oldEvent.seriesId,
                        userId,
                        title: oldEvent.title,
                        description: oldEvent.desc,
                        type: oldEvent.type,
                        amount: oldEvent.amount,
                        category: oldEvent.category,
                        frequency: oldEvent.frequency,
                        interval: oldEvent.interval,
                        startDate: oldEvent.origin || dateISO,
                        occurrenceCount: oldEvent.limit
                    });
                    series.set(oldEvent.seriesId, seriesData);
                }
            }
        });
    });
    
    // Calcular balances de todos los meses
    Object.values(months).forEach(month => month.calculateBalance());
    
    return {
        months: months,                     // Para almacenamiento agrupado
        events: events,                     // Para tabla de eventos
        loans: loans,                       // Para tabla de préstamos
        series: Array.from(series.values()), // Para tabla de series
        alerts: alerts                      // Para tabla de alertas
    };
}

/**
 * Obtiene eventos de un mes específico desde el formato agrupado
 * 
 * @param {Object} monthData - Datos del mes en formato MonthGroupSchema
 * @param {number} weekNumber - Número de semana (opcional, null = todo el mes)
 * @returns {Array} Array de eventos
 */
export function getEventsFromMonth(monthData, weekNumber = null) {
    if (!monthData || !monthData.weeks) return [];
    
    if (weekNumber !== null) {
        // Retornar eventos de una semana específica
        const week = monthData.weeks[weekNumber];
        return week ? week.events : [];
    }
    
    // Retornar todos los eventos del mes
    const allEvents = [];
    Object.values(monthData.weeks).forEach(week => {
        allEvents.push(...week.events);
    });
    
    return allEvents;
}

/**
 * Convierte la estructura agrupada de vuelta al formato antiguo
 * Útil para compatibilidad con código existente
 * 
 * @param {Object} months - { "YYYY-MM": MonthGroupSchema }
 * @returns {Object} { "YYYY-MM-DD": [...] }
 */
export function convertGroupedToFlat(months) {
    const flat = {};
    
    Object.values(months).forEach(monthData => {
        const events = getEventsFromMonth(monthData);
        
        events.forEach(event => {
            const date = event.date;
            if (!flat[date]) {
                flat[date] = [];
            }
            flat[date].push(event);
        });
    });
    
    return flat;
}

/**
 * Genera estadísticas de un mes
 */
export function getMonthStats(monthData) {
    if (!monthData) return null;
    
    return {
        year: monthData.year,
        month: monthData.month,
        eventCount: monthData.eventCount,
        totalIncome: monthData.totalIncome,
        totalExpense: monthData.totalExpense,
        netBalance: monthData.netBalance,
        weeklyStats: Object.keys(monthData.weeks).map(key => {
            const week = monthData.weeks[key];
            return {
                weekNumber: week.weekNumber,
                eventCount: week.eventCount,
                totalIncome: week.totalIncome,
                totalExpense: week.totalExpense,
                days: week.days
            };
        })
    };
}
