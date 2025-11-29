/**
 * Pattern Scheduler - Generador de Ocurrencias desde Patrones
 * 
 * Este módulo maneja la lógica de:
 * 1. Generar ocurrencias proyectadas desde income_patterns y expense_patterns
 * 2. Combinarlas con movements confirmados para el calendario
 * 3. Determinar qué mostrar como "pendiente" vs "confirmado"
 */

import { supabase } from './supabase-client.js';

// ============================================================================
// UTILIDADES DE FECHAS
// ============================================================================

/**
 * Obtiene el primer día del mes
 * @param {number} year - Año
 * @param {number} month - Mes (0-11, formato JavaScript nativo)
 */
function startOfMonth(year, month) {
    return new Date(year, month, 1);
}

/**
 * Obtiene el último día del mes
 * @param {number} year - Año
 * @param {number} month - Mes (0-11, formato JavaScript nativo)
 */
function endOfMonth(year, month) {
    return new Date(year, month + 1, 0); // día 0 del siguiente mes = último día del mes
}

/**
 * Limita una fecha a un rango
 */
function clampDate(date, minDate, maxDate) {
    if (date < minDate) return minDate;
    if (date > maxDate) return maxDate;
    return date;
}

/**
 * Agrega días a una fecha
 */
function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

/**
 * Agrega meses manteniendo el día (ajusta si el mes destino tiene menos días)
 */
function addMonthsKeepingDay(date, monthsToAdd) {
    const d = new Date(date);
    const year = d.getFullYear();
    const month = d.getMonth() + monthsToAdd;
    const dayAnchor = d.getDate();
    
    // último día del mes destino
    const lastDay = new Date(year, month + 1, 0).getDate();
    const safeDay = Math.min(dayAnchor, lastDay);
    return new Date(year, month, safeDay);
}

/**
 * Agrega años a una fecha
 */
function addYears(date, yearsToAdd) {
    const d = new Date(date);
    d.setFullYear(d.getFullYear() + yearsToAdd);
    return d;
}

/**
 * Convierte Date a formato ISO (YYYY-MM-DD)
 */
function toISODateString(date) {
    if (!(date instanceof Date) || isNaN(date.getTime())) {
        console.error('toISODateString recibió una fecha inválida:', date);
        throw new Error(`Fecha inválida: ${date}`);
    }
    return date.toISOString().slice(0, 10);
}

/**
 * Convierte string YYYY-MM-DD a Date
 */
function parseISODate(dateStr) {
    return new Date(dateStr + 'T00:00:00');
}

// ============================================================================
// GENERADORES DE OCURRENCIAS POR FRECUENCIA
// ============================================================================

/**
 * Genera ocurrencias diarias
 * Nota: V2 no soporta frecuencia 'daily', pero se mantiene por compatibilidad
 */
function generateDailyOccurrences(pattern, rangeStart, rangeEnd) {
    const { start_date, end_date, interval } = pattern;
    const stepDays = interval || 1;
    
    const patternStart = parseISODate(start_date);
    const patternEnd = end_date ? parseISODate(end_date) : rangeEnd;
    
    const effectiveStart = clampDate(rangeStart, patternStart, patternEnd);
    const effectiveEnd = clampDate(rangeEnd, patternStart, patternEnd);
    
    let current = new Date(patternStart);
    
    // Avanzar hasta el primer >= effectiveStart
    while (current < effectiveStart) {
        current = addDays(current, stepDays);
    }
    
    const dates = [];
    
    while (current <= effectiveEnd) {
        dates.push(toISODateString(current));
        current = addDays(current, stepDays);
    }
    
    return dates;
}

/**
 * Genera ocurrencias semanales
 */
function generateWeeklyOccurrences(pattern, rangeStart, rangeEnd) {
    const { start_date, end_date, interval } = pattern;
    const stepDays = 7 * (interval || 1);
    
    const patternStart = parseISODate(start_date);
    const patternEnd = end_date ? parseISODate(end_date) : rangeEnd;
    
    const effectiveStart = clampDate(rangeStart, patternStart, patternEnd);
    const effectiveEnd = clampDate(rangeEnd, patternStart, patternEnd);
    
    let current = new Date(patternStart);
    
    // Avanzar hasta el primer >= effectiveStart
    while (current < effectiveStart) {
        current = addDays(current, stepDays);
    }
    
    const dates = [];
    
    while (current <= effectiveEnd) {
        dates.push(toISODateString(current));
        current = addDays(current, stepDays);
    }
    
    return dates;
}

/**
 * Genera ocurrencias mensuales
 */
function generateMonthlyOccurrences(pattern, rangeStart, rangeEnd) {
    const { start_date, end_date, interval } = pattern;
    
    const patternStart = parseISODate(start_date);
    const patternEnd = end_date ? parseISODate(end_date) : rangeEnd;
    
    const effectiveStart = clampDate(rangeStart, patternStart, patternEnd);
    const effectiveEnd = clampDate(rangeEnd, patternStart, patternEnd);
    
    let current = new Date(patternStart);
    
    // Avanzar de mes en mes hasta estar dentro del rango
    while (current < effectiveStart) {
        current = addMonthsKeepingDay(current, interval || 1);
    }
    
    const dates = [];
    
    while (current <= effectiveEnd) {
        dates.push(toISODateString(current));
        current = addMonthsKeepingDay(current, interval || 1);
    }
    
    return dates;
}

/**
 * Genera ocurrencias anuales
 */
function generateYearlyOccurrences(pattern, rangeStart, rangeEnd) {
    const { start_date, end_date, interval } = pattern;
    
    const patternStart = parseISODate(start_date);
    const patternEnd = end_date ? parseISODate(end_date) : rangeEnd;
    
    const effectiveStart = clampDate(rangeStart, patternStart, patternEnd);
    const effectiveEnd = clampDate(rangeEnd, patternStart, patternEnd);
    
    let current = new Date(patternStart);
    
    // Avanzar de año en año hasta estar dentro del rango
    while (current < effectiveStart) {
        current = addYears(current, interval || 1);
    }
    
    const dates = [];
    
    while (current <= effectiveEnd) {
        dates.push(toISODateString(current));
        current = addYears(current, interval || 1);
    }
    
    return dates;
}

/**
 * Función genérica para generar ocurrencias de un patrón
 */
export function generateOccurrencesForPattern(pattern, rangeStart, rangeEnd) {
    if (!pattern.active) return [];
    
    const frequency = pattern.frequency;
    
    switch (frequency) {
        case 'daily':
            return generateDailyOccurrences(pattern, rangeStart, rangeEnd);
        case 'weekly':
            return generateWeeklyOccurrences(pattern, rangeStart, rangeEnd);
        case 'biweekly':
            // V2: biweekly es cada 2 semanas, equivalente a weekly con interval=2
            return generateWeeklyOccurrences({ ...pattern, interval: 2 }, rangeStart, rangeEnd);
        case 'monthly':
            return generateMonthlyOccurrences(pattern, rangeStart, rangeEnd);
        case 'yearly':
            return generateYearlyOccurrences(pattern, rangeStart, rangeEnd);
        default:
            console.warn(`Frecuencia desconocida: ${frequency}`);
            return [];
    }
}

// ============================================================================
// CONSULTAS A SUPABASE
// ============================================================================

/**
 * Obtiene patrones de ingresos activos para un rango de fechas
 */
async function getIncomePatterns(userId, rangeStart, rangeEnd) {
    if (!userId || userId === 'anon') return [];
    
    const startISO = toISODateString(rangeStart);
    const endISO = toISODateString(rangeEnd);
    
    const { data, error } = await supabase
        .from('income_patterns')
        .select('*')
        .eq('user_id', userId)
        .eq('active', true)
        .lte('start_date', endISO)
        .or(`end_date.is.null,end_date.gte.${startISO}`);
    
    if (error) {
        console.error('Error al obtener income_patterns:', error);
        return [];
    }
    
    return data || [];
}

/**
 * Obtiene patrones de gastos activos para un rango de fechas
 */
async function getExpensePatterns(userId, rangeStart, rangeEnd) {
    if (!userId || userId === 'anon') return [];
    
    const startISO = toISODateString(rangeStart);
    const endISO = toISODateString(rangeEnd);
    
    const { data, error } = await supabase
        .from('expense_patterns')
        .select('*')
        .eq('user_id', userId)
        .eq('active', true)
        .lte('start_date', endISO)
        .or(`end_date.is.null,end_date.gte.${startISO}`);
    
    if (error) {
        console.error('Error al obtener expense_patterns:', error);
        return [];
    }
    
    return data || [];
}

/**
 * Obtiene movements confirmados para un rango de fechas
 */
async function getMovements(userId, rangeStart, rangeEnd) {
    if (!userId || userId === 'anon') return [];
    
    const startISO = toISODateString(rangeStart);
    const endISO = toISODateString(rangeEnd);
    
    const { data, error } = await supabase
        .from('movements')
        .select('*')
        .eq('user_id', userId)
        .eq('archived', false)
        .gte('date', startISO)
        .lte('date', endISO);
    
    if (error) {
        console.error('Error al obtener movements:', error);
        return [];
    }
    
    return data || [];
}

/**
 * Obtiene planes que tienen target_date en el rango especificado
 */
async function getPlansWithTargetDate(userId, rangeStart, rangeEnd) {
    if (!userId || userId === 'anon') return [];
    
    const startISO = toISODateString(rangeStart);
    const endISO = toISODateString(rangeEnd);
    
    const { data, error } = await supabase
        .from('plans')
        .select('*')
        .eq('user_id', userId)
        .gte('target_date', startISO)
        .lte('target_date', endISO);
    
    if (error) {
        console.error('Error al obtener plans:', error);
        return [];
    }
    
    return data || [];
}

/**
 * Obtiene savings_patterns con frecuencia programada
 */
async function getScheduledSavingsPatterns(userId, rangeStart, rangeEnd) {
    if (!userId || userId === 'anon') return [];
    
    const startISO = toISODateString(rangeStart);
    const endISO = toISODateString(rangeEnd);
    
    const { data, error } = await supabase
        .from('savings_patterns')
        .select('*')
        .eq('user_id', userId)
        .eq('active', true)
        .not('frequency', 'is', null) // Solo los que tienen frecuencia programada
        .or(`start_date.is.null,start_date.lte.${endISO}`)
        .or(`end_date.is.null,end_date.gte.${startISO}`);
    
    if (error) {
        console.error('Error al obtener scheduled savings_patterns:', error);
        return [];
    }
    
    return data || [];
}

/**
 * Genera ocurrencias para un savings_pattern programado
 */
function generateSavingsOccurrences(pattern, rangeStart, rangeEnd) {
    const occurrences = [];
    
    if (!pattern.frequency) return occurrences;
    
    const startDate = pattern.start_date ? new Date(pattern.start_date) : rangeStart;
    const endDate = pattern.end_date ? new Date(pattern.end_date) : rangeEnd;
    
    // Clampar al rango del mes
    const effectiveStart = clampDate(startDate, rangeStart, rangeEnd);
    const effectiveEnd = clampDate(endDate, rangeStart, rangeEnd);
    
    let current = new Date(effectiveStart);
    
    // Ajustar al día correcto según frecuencia
    if (pattern.frequency === 'weekly' || pattern.frequency === 'biweekly') {
        // Ajustar al día de la semana
        if (pattern.day_of_week !== null && pattern.day_of_week !== undefined) {
            const dayDiff = pattern.day_of_week - current.getDay();
            if (dayDiff < 0) {
                current.setDate(current.getDate() + dayDiff + 7);
            } else {
                current.setDate(current.getDate() + dayDiff);
            }
        }
    } else if (pattern.frequency === 'monthly') {
        // Ajustar al día del mes
        if (pattern.day_of_month !== null && pattern.day_of_month !== undefined) {
            current.setDate(Math.min(pattern.day_of_month, new Date(current.getFullYear(), current.getMonth() + 1, 0).getDate()));
        }
    }
    
    const interval = pattern.interval_value || 1;
    let iterCount = 0;
    const maxIterations = 100;
    
    while (current <= effectiveEnd && iterCount < maxIterations) {
        iterCount++;
        
        if (current >= effectiveStart && current <= effectiveEnd) {
            const dateStr = toISODateString(current);
            
            // Calcular el monto basado en el tipo de asignación
            let amount = 0;
            if (pattern.allocation_type === 'fixed') {
                amount = parseFloat(pattern.allocation_value) || 0;
            } else if (pattern.allocation_type === 'percent') {
                // Para porcentaje, necesitaríamos el ingreso vinculado
                // Por ahora mostramos como "pendiente de calcular"
                amount = null; 
            }
            
            occurrences.push({
                date: dateStr,
                savings_pattern_id: pattern.id,
                name: pattern.name,
                description: pattern.description || 'Ahorro programado',
                expected_amount: amount,
                allocation_type: pattern.allocation_type,
                allocation_value: pattern.allocation_value,
                current_balance: parseFloat(pattern.current_balance) || 0,
                target_amount: pattern.target_amount ? parseFloat(pattern.target_amount) : null,
                type: 'savings', // Para identificar en el calendario
                is_projected: true
            });
        }
        
        // Avanzar al siguiente
        switch (pattern.frequency) {
            case 'weekly':
                current = addDays(current, 7 * interval);
                break;
            case 'biweekly':
                current = addDays(current, 14 * interval);
                break;
            case 'monthly':
                current.setMonth(current.getMonth() + interval);
                if (pattern.day_of_month) {
                    current.setDate(Math.min(pattern.day_of_month, new Date(current.getFullYear(), current.getMonth() + 1, 0).getDate()));
                }
                break;
            case 'yearly':
                current.setFullYear(current.getFullYear() + interval);
                break;
        }
    }
    
    return occurrences;
}

// ============================================================================
// FUNCIÓN PRINCIPAL: OBTENER DATOS DEL CALENDARIO
// ============================================================================

/**
 * Obtiene datos completos del calendario para un mes
 * 
 * Combina:
 * - Ocurrencias proyectadas desde patrones
 * - Movements confirmados (separados por tipo: regular, loan, plan)
 * - Plan targets (fechas objetivo de metas)
 * - Savings patterns programados
 * 
 * Retorna objeto con estructura:
 * {
 *   "2024-11-15": {
 *     date: "2024-11-15",
 *     confirmed_movements: [...],      // todos los movements
 *     loan_movements: [...],            // subset con loan_id
 *     plan_movements: [...],            // subset con plan_id
 *     plan_targets: [...],              // metas con target_date en esta fecha
 *     projected_incomes: [...],         // ocurrencias proyectadas de income_patterns
 *     projected_expenses: [...],        // ocurrencias proyectadas de expense_patterns
 *     projected_savings: [...]          // ocurrencias proyectadas de savings_patterns
 *   },
 *   ...
 * }
 */
export async function getCalendarDataForMonth(userId, year, month) {
    if (!userId || userId === 'anon') {
        return {};
    }
    
    const monthStart = startOfMonth(year, month);
    const monthEnd = endOfMonth(year, month);
    
    try {
        // 1) Obtener patrones activos, movements, planes y ahorros programados
        const [incomePatterns, expensePatterns, movements, plans, savingsPatterns] = await Promise.all([
            getIncomePatterns(userId, monthStart, monthEnd),
            getExpensePatterns(userId, monthStart, monthEnd),
            getMovements(userId, monthStart, monthEnd),
            getPlansWithTargetDate(userId, monthStart, monthEnd),
            getScheduledSavingsPatterns(userId, monthStart, monthEnd)
        ]);
        
        // 2) Crear índice de movements por fecha y patrón
        const movementsByDate = {};
        const loanMovementsByDate = {};
        const planMovementsByDate = {};
        const movementsByPattern = {}; // key: `income_${pattern_id}_${date}` o `expense_${pattern_id}_${date}`
        
        for (const m of movements) {
            const d = m.date;
            
            // Por fecha (todos)
            if (!movementsByDate[d]) movementsByDate[d] = [];
            movementsByDate[d].push(m);
            
            // Separar loan_movements
            if (m.loan_id) {
                if (!loanMovementsByDate[d]) loanMovementsByDate[d] = [];
                loanMovementsByDate[d].push(m);
            }
            
            // Separar plan_movements
            if (m.plan_id) {
                if (!planMovementsByDate[d]) planMovementsByDate[d] = [];
                planMovementsByDate[d].push(m);
            }
            
            // Por patrón + fecha
            if (m.income_pattern_id) {
                const key = `income_${m.income_pattern_id}_${d}`;
                movementsByPattern[key] = m;
            }
            if (m.expense_pattern_id) {
                const key = `expense_${m.expense_pattern_id}_${d}`;
                movementsByPattern[key] = m;
            }
        }
        
        // 3) Crear índice de plan_targets por fecha
        const planTargetsByDate = {};
        for (const plan of plans) {
            // V2: usar target_date directamente
            const targetDate = plan.target_date;
            if (targetDate) {
                if (!planTargetsByDate[targetDate]) planTargetsByDate[targetDate] = [];
                planTargetsByDate[targetDate].push({
                    plan_id: plan.id,
                    name: plan.name,
                    target_amount: plan.target_amount,
                    target_date: plan.target_date,
                    priority: plan.priority,
                    status: plan.status
                });
            }
        }
        
        // 4) Generar ocurrencias proyectadas de patrones
        const projectedIncomes = [];
        const projectedExpenses = [];
        const projectedSavings = [];
        
        // Ingresos proyectados
        for (const p of incomePatterns) {
            const dates = generateOccurrencesForPattern(p, monthStart, monthEnd);
            
            for (const d of dates) {
                const key = `income_${p.id}_${d}`;
                const hasConfirmedMovement = !!movementsByPattern[key];
                
                projectedIncomes.push({
                    date: d,
                    kind: 'projected',
                    pattern_type: 'income',
                    pattern_id: p.id,
                    name: p.name,
                    description: p.description,
                    category: p.category,
                    expected_amount: p.base_amount,
                    has_confirmed_movement: hasConfirmedMovement,
                    confirmed_movement: movementsByPattern[key] || null
                });
            }
        }
        
        // Gastos proyectados
        for (const p of expensePatterns) {
            const dates = generateOccurrencesForPattern(p, monthStart, monthEnd);
            
            for (const d of dates) {
                const key = `expense_${p.id}_${d}`;
                const hasConfirmedMovement = !!movementsByPattern[key];
                
                projectedExpenses.push({
                    date: d,
                    kind: 'projected',
                    pattern_type: 'expense',
                    pattern_id: p.id,
                    name: p.name,
                    description: p.description,
                    category: p.category,
                    expected_amount: p.base_amount,
                    has_confirmed_movement: hasConfirmedMovement,
                    confirmed_movement: movementsByPattern[key] || null
                });
            }
        }
        
        // Ahorros proyectados (programados)
        for (const sp of savingsPatterns) {
            const occurrences = generateSavingsOccurrences(sp, monthStart, monthEnd);
            for (const occ of occurrences) {
                // Verificar si ya hay un movement de ahorro para este patrón en esta fecha
                const existingMovement = (movementsByDate[occ.date] || []).find(
                    m => m.category === 'Ahorro' && m.title?.includes(sp.name)
                );
                occ.has_confirmed_movement = !!existingMovement;
                occ.confirmed_movement = existingMovement || null;
                projectedSavings.push(occ);
            }
        }
        
        // 5) Preparar estructura final por día
        const days = {};
        
        for (let d = new Date(monthStart); d <= monthEnd; d = addDays(d, 1)) {
            const key = toISODateString(d);
            days[key] = {
                date: key,
                confirmed_movements: movementsByDate[key] || [],
                loan_movements: loanMovementsByDate[key] || [],
                plan_movements: planMovementsByDate[key] || [],
                plan_targets: planTargetsByDate[key] || [],
                projected_incomes: projectedIncomes.filter(e => e.date === key),
                projected_expenses: projectedExpenses.filter(e => e.date === key),
                projected_savings: projectedSavings.filter(e => e.date === key)
            };
        }
        
        return days;
        
    } catch (error) {
        console.error('Error en getCalendarDataForMonth:', error);
        return {};
    }
}

/**
 * Confirma una ocurrencia proyectada → crea Movement
 * 
 * @param {string} patternId - ID del income_pattern o expense_pattern
 * @param {string} patternType - 'income' o 'expense'
 * @param {string} date - Fecha ISO (YYYY-MM-DD)
 * @param {number} actualAmount - Monto real confirmado
 * @param {string} userId - ID del usuario
 */
export async function confirmPatternOccurrence(patternId, patternType, date, actualAmount, userId) {
    if (!userId || userId === 'anon') {
        throw new Error('Usuario no autenticado');
    }
    
    try {
        // Obtener el patrón
        const tableName = patternType === 'income' ? 'income_patterns' : 'expense_patterns';
        const { data: pattern, error: patternError } = await supabase
            .from(tableName)
            .select('*')
            .eq('id', patternId)
            .single();
        
        if (patternError || !pattern) {
            throw new Error(`Patrón no encontrado: ${patternError?.message}`);
        }
        
        // Crear movement
        const movementData = {
            user_id: userId,
            [patternType === 'income' ? 'income_pattern_id' : 'expense_pattern_id']: patternId,
            title: pattern.name,
            description: pattern.description,
            type: patternType === 'income' ? 'ingreso' : 'gasto',
            category: pattern.category,
            date: date,
            expected_amount: pattern.base_amount,
            confirmed_amount: actualAmount,
            difference: actualAmount - pattern.base_amount,
            confirmed: true
        };
        
        const { data: movement, error: movementError } = await supabase
            .from('movements')
            .insert(movementData)
            .select()
            .single();
        
        if (movementError) {
            throw new Error(`Error al crear movement: ${movementError.message}`);
        }
        
        console.log('✅ Ocurrencia confirmada:', movement);
        return movement;
        
    } catch (error) {
        console.error('Error al confirmar ocurrencia:', error);
        throw error;
    }
}

// ============================================================================
// UTILIDADES DE CÁLCULO
// ============================================================================

/**
 * Calcula cuántas veces ocurre un patrón por mes (promedio)
 * Útil para calcular fechas sugeridas de planes
 */
export function getMonthlyOccurrences(frequency, interval = 1) {
    switch (frequency) {
        case 'daily':
            return 30 / interval; // aprox
        case 'weekly':
            return 4 / interval;
        case 'monthly':
            return 1 / interval;
        case 'yearly':
            return (1 / 12) / interval;
        default:
            return 0;
    }
}

/**
 * Calcula el ingreso mensual esperado de un patrón
 */
export function calculateMonthlyIncome(pattern) {
    const occurrences = getMonthlyOccurrences(pattern.frequency, pattern.interval);
    return pattern.base_amount * occurrences;
}
