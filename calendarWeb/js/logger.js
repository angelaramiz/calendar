/**
 * Logger centralizado para calendarWeb.
 *
 * En produccion (DEBUG = false) solo sale console.error: la consola del
 * usuario no se llena de logs y no se exponen datos (montos, correos).
 * En desarrollo cambia DEBUG a true para ver todo igual que antes.
 *
 * Uso: import { logger } from './logger.js';
 *      logger.debug(...) | logger.info(...) | logger.warn(...) | logger.error(...)
 */
export const DEBUG = false;

function emit(method, args) {
    console[method](...args);
}

export const logger = {
    debug: (...args) => { if (DEBUG) emit('log', args); },
    info: (...args) => { if (DEBUG) emit('log', args); },
    warn: (...args) => { if (DEBUG) emit('warn', args); },
    error: (...args) => emit('error', args),
};
