/**
 * Módulo de Recurrencia
 * Genera fechas recurrentes según frecuencia, intervalo y límite
 */

/**
 * Genera las fechas recurrentes (incluye la fecha de inicio)
 * @param {string} startISO - Fecha inicial en formato 'YYYY-MM-DD'
 * @param {string} frequency - Tipo: '' | 'semanal' | 'mensual' | 'anual'
 * @param {number} interval - Intervalo entre ocurrencias (>=1)
 * @param {number} limit - Cantidad total de ocurrencias (incluye la inicial)
 * @returns {string[]} Array de fechas ISO
 */
export function generateRecurringDates(startISO, frequency, interval, limit) {
    const result = [];
    
    if (!frequency) {
        result.push(startISO);
        return result;
    }
    
    interval = Math.max(1, parseInt(interval) || 1);
    limit = Math.max(1, parseInt(limit) || 6);

    let current = new Date(startISO + 'T00:00:00');
    result.push(current.toISOString().slice(0, 10));

    for (let i = 1; i < limit; i++) {
        let next = new Date(current.getTime());
        
        switch (frequency) {
            case 'semanal':
                next.setDate(next.getDate() + 7 * interval);
                break;
            case 'mensual':
                next.setMonth(next.getMonth() + interval);
                break;
            case 'anual':
                next.setFullYear(next.getFullYear() + interval);
                break;
            default:
                return result;
        }
        
        result.push(next.toISOString().slice(0, 10));
        current = next;
    }
    
    return result;
}
