/**
 * @fileoverview Módulo de acceso a base de datos
 * Proporciona funciones para interactuar con la base de datos PostgreSQL
 * y mantener compatibilidad con localStorage durante la migración
 */

import { 
    EventSchema, 
    LoanSchema, 
    RecurringSeriesSchema, 
    AlertSchema,
    MonthGroupSchema,
    migrateFromLocalStorage,
    convertGroupedToFlat,
    getEventsFromMonth
} from './data-structure.js';

/**
 * Configuración de la base de datos
 */
const DB_CONFIG = {
    // Para desarrollo local con API REST
    apiUrl: 'http://localhost:3000/api',
    
    // Para producción con Supabase/PostgreSQL
    supabaseUrl: null,
    supabaseKey: null,
    
    // Modo híbrido durante migración
    useLocalStorage: true, // true = localStorage, false = API/DB
    enableSync: false // true = sincronizar localStorage con DB
};

/**
 * Cliente de base de datos
 */
class DatabaseClient {
    constructor() {
        this.userId = null;
        this.cache = {
            events: new Map(),
            months: new Map(),
            loans: new Map(),
            series: new Map()
        };
    }

    /**
     * Inicializa la conexión a la base de datos
     * @param {string} userId - ID del usuario
     */
    async init(userId) {
        this.userId = userId;
        
        if (DB_CONFIG.useLocalStorage) {
            console.log('📦 Usando localStorage (modo legacy)');
            return;
        }
        
        try {
            // Intentar conexión a la API/DB
            const response = await fetch(`${DB_CONFIG.apiUrl}/health`);
            if (response.ok) {
                console.log('✅ Conectado a la base de datos');
            }
        } catch (error) {
            console.warn('⚠️ No se pudo conectar a la DB, usando localStorage:', error.message);
            DB_CONFIG.useLocalStorage = true;
        }
    }

    // =========================================================================
    // EVENTOS
    // =========================================================================

    /**
     * Obtiene todos los eventos del usuario
     * @returns {Promise<Object>} Eventos agrupados por fecha
     */
    async getAllEvents() {
        if (DB_CONFIG.useLocalStorage) {
            const data = localStorage.getItem('calendarEvents');
            return data ? JSON.parse(data) : {};
        }
        
        try {
            const response = await fetch(`${DB_CONFIG.apiUrl}/events?userId=${this.userId}`);
            if (!response.ok) throw new Error('Error al cargar eventos');
            
            const events = await response.json();
            // Convertir a formato legacy para compatibilidad
            return this._groupEventsByDate(events);
        } catch (error) {
            console.error('Error cargando eventos:', error);
            return {};
        }
    }

    /**
     * Obtiene eventos de un mes específico (formato agrupado)
     * @param {number} year - Año
     * @param {number} month - Mes (0-11)
     * @returns {Promise<MonthGroupSchema|null>}
     */
    async getMonthSummary(year, month) {
        const cacheKey = `${year}-${month}`;
        
        // Verificar caché
        if (this.cache.months.has(cacheKey)) {
            return this.cache.months.get(cacheKey);
        }
        
        if (DB_CONFIG.useLocalStorage) {
            // En localStorage, construir agrupación desde eventos planos
            const allEvents = await this.getAllEvents();
            const migrated = migrateFromLocalStorage(allEvents, this.userId);
            const monthData = migrated.months[cacheKey];
            
            if (monthData) {
                this.cache.months.set(cacheKey, monthData);
            }
            
            return monthData || null;
        }
        
        try {
            const response = await fetch(
                `${DB_CONFIG.apiUrl}/months/${year}/${month}?userId=${this.userId}`
            );
            
            if (!response.ok) throw new Error('Error al cargar resumen mensual');
            
            const data = await response.json();
            const monthGroup = MonthGroupSchema.fromJSON(data);
            
            // Cachear
            this.cache.months.set(cacheKey, monthGroup);
            
            return monthGroup;
        } catch (error) {
            console.error('Error cargando resumen mensual:', error);
            return null;
        }
    }

    /**
     * Guarda un nuevo evento
     * @param {string} dateISO - Fecha en formato YYYY-MM-DD
     * @param {Object} eventData - Datos del evento
     * @returns {Promise<Object>} Evento creado
     */
    async createEvent(dateISO, eventData) {
        if (DB_CONFIG.useLocalStorage) {
            const events = await this.getAllEvents();
            if (!events[dateISO]) events[dateISO] = [];
            
            events[dateISO].push(eventData);
            localStorage.setItem('calendarEvents', JSON.stringify(events));
            
            // Invalidar caché del mes
            const [year, month] = dateISO.split('-');
            this.cache.months.delete(`${year}-${parseInt(month) - 1}`);
            
            return eventData;
        }
        
        try {
            const response = await fetch(`${DB_CONFIG.apiUrl}/events`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: this.userId,
                    date: dateISO,
                    ...eventData
                })
            });
            
            if (!response.ok) throw new Error('Error al crear evento');
            
            const created = await response.json();
            
            // Invalidar caché
            const [year, month] = dateISO.split('-');
            this.cache.months.delete(`${year}-${parseInt(month) - 1}`);
            
            return created;
        } catch (error) {
            console.error('Error creando evento:', error);
            throw error;
        }
    }

    /**
     * Actualiza un evento existente
     * @param {string} dateISO - Fecha del evento
     * @param {number} eventIndex - Índice en el array de la fecha
     * @param {Object} updates - Campos a actualizar
     * @returns {Promise<Object>} Evento actualizado
     */
    async updateEvent(dateISO, eventIndex, updates) {
        if (DB_CONFIG.useLocalStorage) {
            const events = await this.getAllEvents();
            if (!events[dateISO] || !events[dateISO][eventIndex]) {
                throw new Error('Evento no encontrado');
            }
            
            events[dateISO][eventIndex] = {
                ...events[dateISO][eventIndex],
                ...updates
            };
            
            localStorage.setItem('calendarEvents', JSON.stringify(events));
            
            // Invalidar caché
            const [year, month] = dateISO.split('-');
            this.cache.months.delete(`${year}-${parseInt(month) - 1}`);
            
            return events[dateISO][eventIndex];
        }
        
        try {
            const eventId = updates.id || await this._getEventId(dateISO, eventIndex);
            
            const response = await fetch(`${DB_CONFIG.apiUrl}/events/${eventId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(updates)
            });
            
            if (!response.ok) throw new Error('Error al actualizar evento');
            
            const updated = await response.json();
            
            // Invalidar caché
            const [year, month] = dateISO.split('-');
            this.cache.months.delete(`${year}-${parseInt(month) - 1}`);
            
            return updated;
        } catch (error) {
            console.error('Error actualizando evento:', error);
            throw error;
        }
    }

    /**
     * Elimina un evento
     * @param {string} dateISO - Fecha del evento
     * @param {number} eventIndex - Índice en el array
     * @returns {Promise<boolean>} true si se eliminó correctamente
     */
    async deleteEvent(dateISO, eventIndex) {
        if (DB_CONFIG.useLocalStorage) {
            const events = await this.getAllEvents();
            if (!events[dateISO] || !events[dateISO][eventIndex]) {
                throw new Error('Evento no encontrado');
            }
            
            events[dateISO].splice(eventIndex, 1);
            
            if (events[dateISO].length === 0) {
                delete events[dateISO];
            }
            
            localStorage.setItem('calendarEvents', JSON.stringify(events));
            
            // Invalidar caché
            const [year, month] = dateISO.split('-');
            this.cache.months.delete(`${year}-${parseInt(month) - 1}`);
            
            return true;
        }
        
        try {
            const eventId = await this._getEventId(dateISO, eventIndex);
            
            const response = await fetch(`${DB_CONFIG.apiUrl}/events/${eventId}`, {
                method: 'DELETE'
            });
            
            if (!response.ok) throw new Error('Error al eliminar evento');
            
            // Invalidar caché
            const [year, month] = dateISO.split('-');
            this.cache.months.delete(`${year}-${parseInt(month) - 1}`);
            
            return true;
        } catch (error) {
            console.error('Error eliminando evento:', error);
            throw error;
        }
    }

    // =========================================================================
    // PRÉSTAMOS
    // =========================================================================

    /**
     * Crea un préstamo
     * @param {Object} loanData - Datos del préstamo
     * @returns {Promise<Object>} Préstamo creado
     */
    async createLoan(loanData) {
        if (DB_CONFIG.useLocalStorage) {
            // En localStorage, los préstamos están embebidos en los eventos
            return loanData;
        }
        
        try {
            const response = await fetch(`${DB_CONFIG.apiUrl}/loans`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: this.userId,
                    ...loanData
                })
            });
            
            if (!response.ok) throw new Error('Error al crear préstamo');
            
            return await response.json();
        } catch (error) {
            console.error('Error creando préstamo:', error);
            throw error;
        }
    }

    /**
     * Obtiene información de un préstamo por ID
     * @param {string} loanId - ID del préstamo
     * @returns {Promise<Object|null>}
     */
    async getLoan(loanId) {
        if (DB_CONFIG.useLocalStorage) {
            // Buscar en eventos
            const events = await this.getAllEvents();
            for (const dateEvents of Object.values(events)) {
                for (const event of dateEvents) {
                    if (event.loan?.loanId === loanId) {
                        return event.loan;
                    }
                }
            }
            return null;
        }
        
        try {
            const response = await fetch(`${DB_CONFIG.apiUrl}/loans/${loanId}?userId=${this.userId}`);
            if (!response.ok) return null;
            return await response.json();
        } catch (error) {
            console.error('Error obteniendo préstamo:', error);
            return null;
        }
    }

    // =========================================================================
    // SERIES RECURRENTES
    // =========================================================================

    /**
     * Crea una serie recurrente
     * @param {Object} seriesData - Datos de la serie
     * @returns {Promise<Object>} Serie creada
     */
    async createRecurringSeries(seriesData) {
        if (DB_CONFIG.useLocalStorage) {
            // Las series se generan como eventos individuales en localStorage
            return seriesData;
        }
        
        try {
            const response = await fetch(`${DB_CONFIG.apiUrl}/series`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: this.userId,
                    ...seriesData
                })
            });
            
            if (!response.ok) throw new Error('Error al crear serie recurrente');
            
            return await response.json();
        } catch (error) {
            console.error('Error creando serie recurrente:', error);
            throw error;
        }
    }

    // =========================================================================
    // ALERTAS
    // =========================================================================

    /**
     * Obtiene alertas pendientes
     * @returns {Promise<Array>} Array de alertas
     */
    async getPendingAlerts() {
        if (DB_CONFIG.useLocalStorage) {
            // Las alertas se procesan en tiempo real desde eventos
            const { getPendingAlerts } = await import('./notifications.js');
            return getPendingAlerts();
        }
        
        try {
            const response = await fetch(
                `${DB_CONFIG.apiUrl}/alerts/pending?userId=${this.userId}`
            );
            if (!response.ok) return [];
            return await response.json();
        } catch (error) {
            console.error('Error obteniendo alertas:', error);
            return [];
        }
    }

    /**
     * Marca alerta como leída
     * @param {string} alertId - ID de la alerta
     * @returns {Promise<boolean>}
     */
    async markAlertAsRead(alertId) {
        if (DB_CONFIG.useLocalStorage) {
            // Implementar en localStorage si es necesario
            return true;
        }
        
        try {
            const response = await fetch(`${DB_CONFIG.apiUrl}/alerts/${alertId}/read`, {
                method: 'PATCH'
            });
            return response.ok;
        } catch (error) {
            console.error('Error marcando alerta:', error);
            return false;
        }
    }

    // =========================================================================
    // MIGRACIÓN
    // =========================================================================

    /**
     * Migra datos de localStorage a la base de datos
     * @returns {Promise<Object>} Resultado de la migración
     */
    async migrateToDatabase() {
        if (!DB_CONFIG.useLocalStorage) {
            throw new Error('Ya estás usando la base de datos');
        }
        
        console.log('🚀 Iniciando migración de localStorage a base de datos...');
        
        try {
            // 1. Cargar datos de localStorage
            const localEvents = JSON.parse(localStorage.getItem('calendarEvents') || '{}');
            
            // 2. Convertir a estructura agrupada
            const migrated = migrateFromLocalStorage(localEvents, this.userId);
            
            console.log(`📊 Datos migrados:
  - ${migrated.events.length} eventos
  - ${migrated.loans.length} préstamos
  - ${migrated.series.length} series recurrentes
  - ${migrated.alerts.length} alertas
  - ${Object.keys(migrated.months).length} meses`);
            
            // 3. Enviar a la API
            const response = await fetch(`${DB_CONFIG.apiUrl}/migrate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: this.userId,
                    data: migrated
                })
            });
            
            if (!response.ok) {
                throw new Error('Error en la migración: ' + await response.text());
            }
            
            const result = await response.json();
            
            // 4. Backup de localStorage antes de cambiar
            localStorage.setItem('calendarEvents_backup', JSON.stringify(localEvents));
            
            // 5. Cambiar a modo DB
            DB_CONFIG.useLocalStorage = false;
            
            console.log('✅ Migración completada exitosamente');
            
            return {
                success: true,
                ...result,
                migrated
            };
            
        } catch (error) {
            console.error('❌ Error durante la migración:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Restaura desde backup de localStorage
     * @returns {boolean}
     */
    restoreFromBackup() {
        const backup = localStorage.getItem('calendarEvents_backup');
        if (!backup) {
            console.error('No hay backup disponible');
            return false;
        }
        
        localStorage.setItem('calendarEvents', backup);
        DB_CONFIG.useLocalStorage = true;
        
        console.log('✅ Datos restaurados desde backup');
        return true;
    }

    // =========================================================================
    // UTILIDADES PRIVADAS
    // =========================================================================

    /**
     * Agrupa eventos planos por fecha
     * @private
     */
    _groupEventsByDate(events) {
        const grouped = {};
        for (const event of events) {
            if (!grouped[event.date]) {
                grouped[event.date] = [];
            }
            grouped[event.date].push(event);
        }
        return grouped;
    }

    /**
     * Obtiene el ID de un evento desde su posición
     * @private
     */
    async _getEventId(dateISO, eventIndex) {
        // En localStorage no hay IDs reales, usar índice
        return `${dateISO}_${eventIndex}`;
    }

    /**
     * Limpia caché
     */
    clearCache() {
        this.cache.events.clear();
        this.cache.months.clear();
        this.cache.loans.clear();
        this.cache.series.clear();
    }
}

// Singleton
const db = new DatabaseClient();

export { db, DB_CONFIG };
