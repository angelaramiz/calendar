/**
 * stats.js
 * Módulo de estadísticas V2
 * Usa movements desde Supabase en lugar de localStorage
 */

import { getMovements } from './movements.js';

function formatMoney(n) { return (Number(n || 0)).toFixed(2); }

function emptyAcc() {
    return { confirmed: { income: 0, expense: 0 }, pending: { income: 0, expense: 0 } };
}

function sumMovement(movement, acc) {
    if (!movement) return acc;
    const isIncome = movement.type === 'ingreso';
    const isConfirmed = !!movement.confirmed;
    const val = isConfirmed 
        ? (movement.confirmed_amount ?? movement.expected_amount ?? 0) 
        : (movement.expected_amount ?? 0);
    
    if (isConfirmed) {
        if (isIncome) acc.confirmed.income += Number(val);
        else acc.confirmed.expense += Number(val);
    } else {
        if (isIncome) acc.pending.income += Number(val);
        else acc.pending.expense += Number(val);
    }
    return acc;
}

/**
 * Obtiene estadísticas del día
 * @param {string} todayISO - Fecha en formato YYYY-MM-DD
 * @returns {Promise<{acc: Object, netConfirmed: number, netPending: number}>}
 */
export async function computeDailyStats(todayISO) {
    try {
        const movements = await getMovements({
            startDate: todayISO,
            endDate: todayISO,
            archived: false
        });

        const acc = emptyAcc();
        movements.forEach(m => sumMovement(m, acc));
        
        const netConfirmed = acc.confirmed.income - acc.confirmed.expense;
        const netPending = acc.pending.income - acc.pending.expense;
        
        return { acc, netConfirmed, netPending };
    } catch (error) {
        console.error('Error computing daily stats:', error);
        return { acc: emptyAcc(), netConfirmed: 0, netPending: 0 };
    }
}

/**
 * Obtiene estadísticas semanales del mes
 * @param {number} year - Año
 * @param {number} monthIndex - Mes (0-11)
 * @returns {Promise<Array>}
 */
export async function computeWeeklyStatsForMonth(year, monthIndex) {
    try {
        const firstDay = new Date(year, monthIndex, 1);
        const lastDay = new Date(year, monthIndex + 1, 0);
        const startISO = firstDay.toISOString().slice(0, 10);
        const endISO = lastDay.toISOString().slice(0, 10);

        const movements = await getMovements({
            startDate: startISO,
            endDate: endISO,
            archived: false
        });

        // Agrupar por semana
        const result = [];
        for (let w = 1; w <= 6; w++) {
            const startDayNum = (w - 1) * 7 + 1;
            const endDayNum = Math.min(w * 7, lastDay.getDate());
            
            // Verificar si esta semana tiene días en el mes
            const testDate = new Date(year, monthIndex, startDayNum);
            if (testDate.getMonth() !== monthIndex || startDayNum > lastDay.getDate()) continue;
            
            const weekStart = new Date(year, monthIndex, startDayNum);
            const weekEnd = new Date(year, monthIndex, endDayNum);
            const weekStartISO = weekStart.toISOString().slice(0, 10);
            const weekEndISO = weekEnd.toISOString().slice(0, 10);
            
            const acc = emptyAcc();
            movements
                .filter(m => m.date >= weekStartISO && m.date <= weekEndISO)
                .forEach(m => sumMovement(m, acc));
            
            result.push({
                week: w,
                acc,
                range: [weekStart, weekEnd]
            });
        }
        
        return result;
    } catch (error) {
        console.error('Error computing weekly stats:', error);
        return [];
    }
}

/**
 * Obtiene estadísticas del mes (desde una fecha hacia adelante)
 * @param {number} year - Año
 * @param {number} monthIndex - Mes (0-11)
 * @param {string} fromDateISO - Fecha desde la cual calcular
 * @returns {Promise<Object>}
 */
export async function computeMonthlyFutureStats(year, monthIndex, fromDateISO) {
    try {
        const lastDay = new Date(year, monthIndex + 1, 0);
        const endISO = lastDay.toISOString().slice(0, 10);

        const movements = await getMovements({
            startDate: fromDateISO,
            endDate: endISO,
            archived: false
        });

        const acc = emptyAcc();
        movements.forEach(m => sumMovement(m, acc));
        
        return acc;
    } catch (error) {
        console.error('Error computing monthly stats:', error);
        return emptyAcc();
    }
}

/**
 * Obtiene estadísticas anuales agrupadas
 * @param {number} year - Año
 * @param {number} groupSize - Tamaño del grupo (2=bimestral, 3=trimestral, 6=semestral, 12=anual)
 * @returns {Promise<Array>}
 */
export async function computeAnnualStatsGroup(year, groupSize) {
    try {
        const startISO = `${year}-01-01`;
        const endISO = `${year}-12-31`;

        const movements = await getMovements({
            startDate: startISO,
            endDate: endISO,
            archived: false
        });

        const groups = [];
        for (let m = 0; m < 12; m += groupSize) {
            const fromMonth = m;
            const toMonth = Math.min(m + groupSize - 1, 11);
            
            const groupStartISO = new Date(year, fromMonth, 1).toISOString().slice(0, 10);
            const groupEndISO = new Date(year, toMonth + 1, 0).toISOString().slice(0, 10);
            
            const acc = emptyAcc();
            movements
                .filter(mov => mov.date >= groupStartISO && mov.date <= groupEndISO)
                .forEach(mov => sumMovement(mov, acc));
            
            groups.push({
                fromMonth: fromMonth + 1,
                toMonth: toMonth + 1,
                acc
            });
        }
        
        return groups;
    } catch (error) {
        console.error('Error computing annual stats:', error);
        return [];
    }
}

export function renderMoney(n) { return `$${formatMoney(n)}`; }