/**
 * Punto de entrada principal de la aplicación
 */

import { Calendar } from './calendar.js';

// Inicializar cuando el DOM esté listo
document.addEventListener('DOMContentLoaded', () => {
    const calendar = new Calendar('calendar-body');
    calendar.init();
});
