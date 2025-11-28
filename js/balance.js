/**
 * balance.js
 * Gestión del balance de movimientos confirmados y cálculos de disponibilidad
 */

import { supabase } from './supabase-client.js';

// ============================================================================
// BALANCE DE MOVIMIENTOS CONFIRMADOS
// ============================================================================

/**
 * Obtiene el resumen del balance de movimientos confirmados
 */
export async function getConfirmedBalanceSummary() {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        const { data, error } = await supabase
            .from('confirmed_balance_summary')
            .select('*')
            .eq('user_id', user.id)
            .single();

        if (error && error.code !== 'PGRST116') throw error; // PGRST116 = no rows

        return data || {
            total_income: 0,
            total_expenses: 0,
            balance: 0,
            income_count: 0,
            expense_count: 0
        };
    } catch (error) {
        console.error('Error fetching confirmed balance summary:', error);
        throw error;
    }
}

/**
 * Obtiene el balance confirmado por mes
 */
export async function getMonthlyConfirmedBalance(year = null, limit = 12) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        let query = supabase
            .from('monthly_confirmed_balance')
            .select('*')
            .eq('user_id', user.id)
            .order('month', { ascending: false })
            .limit(limit);

        if (year) {
            query = query.eq('year', year);
        }

        const { data, error } = await query;
        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching monthly confirmed balance:', error);
        throw error;
    }
}

/**
 * Obtiene el balance confirmado para un rango de fechas específico
 */
export async function getConfirmedBalanceForDateRange(startDate, endDate) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        const { data, error } = await supabase
            .from('movements')
            .select('type, confirmed_amount')
            .eq('user_id', user.id)
            .eq('confirmed', true)
            .eq('archived', false)
            .gte('date', startDate)
            .lte('date', endDate);

        if (error) throw error;

        const result = {
            total_income: 0,
            total_expenses: 0,
            balance: 0,
            income_count: 0,
            expense_count: 0
        };

        (data || []).forEach(m => {
            if (m.type === 'ingreso') {
                result.total_income += parseFloat(m.confirmed_amount);
                result.income_count++;
            } else if (m.type === 'gasto') {
                result.total_expenses += parseFloat(m.confirmed_amount);
                result.expense_count++;
            }
        });

        result.balance = result.total_income - result.total_expenses;
        return result;
    } catch (error) {
        console.error('Error fetching confirmed balance for date range:', error);
        throw error;
    }
}

// ============================================================================
// ASIGNACIONES DE INGRESOS
// ============================================================================

/**
 * Obtiene las asignaciones de todos los patrones de ingreso
 */
export async function getIncomePatternAllocations() {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        const { data, error } = await supabase
            .from('income_pattern_allocations')
            .select('*')
            .eq('user_id', user.id)
            .eq('active', true)
            .order('income_name');

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching income pattern allocations:', error);
        throw error;
    }
}

/**
 * Obtiene las asignaciones de un patrón de ingreso específico
 */
export async function getIncomePatternAllocation(incomePatternId) {
    try {
        const { data, error } = await supabase
            .from('income_pattern_allocations')
            .select('*')
            .eq('income_pattern_id', incomePatternId)
            .single();

        if (error && error.code !== 'PGRST116') throw error;
        return data;
    } catch (error) {
        console.error('Error fetching income pattern allocation:', error);
        throw error;
    }
}

/**
 * Calcula el porcentaje disponible de un ingreso considerando todas las asignaciones
 */
export async function calculateAvailablePercentage(incomePatternId) {
    try {
        const allocation = await getIncomePatternAllocation(incomePatternId);
        
        if (!allocation) {
            return {
                percent_available: 1,
                amount_available: 0,
                total_percent_allocated: 0,
                total_fixed_allocated: 0,
                breakdown: {
                    to_expenses: { percent: 0, fixed: 0 },
                    to_plans: { percent: 0, fixed: 0 }
                }
            };
        }

        return {
            percent_available: parseFloat(allocation.percent_available) || 0,
            amount_available: parseFloat(allocation.amount_available) || 0,
            total_percent_allocated: parseFloat(allocation.total_percent_allocated) || 0,
            total_fixed_allocated: parseFloat(allocation.total_fixed_allocated) || 0,
            base_amount: parseFloat(allocation.base_amount) || 0,
            breakdown: {
                to_expenses: {
                    percent: parseFloat(allocation.percent_allocated_to_expenses) || 0,
                    fixed: parseFloat(allocation.fixed_allocated_to_expenses) || 0
                },
                to_plans: {
                    percent: parseFloat(allocation.percent_allocated_to_plans) || 0,
                    fixed: parseFloat(allocation.fixed_allocated_to_plans) || 0
                }
            }
        };
    } catch (error) {
        console.error('Error calculating available percentage:', error);
        throw error;
    }
}

/**
 * Sugiere un porcentaje de asignación basado en lo disponible
 * @param {string} incomePatternId - ID del patrón de ingreso
 * @param {number} desiredAmount - Monto deseado a asignar (opcional)
 * @returns {Object} Sugerencia de asignación
 */
export async function suggestAllocation(incomePatternId, desiredAmount = null) {
    try {
        const availability = await calculateAvailablePercentage(incomePatternId);
        
        if (availability.percent_available <= 0 && availability.amount_available <= 0) {
            return {
                can_allocate: false,
                suggested_type: null,
                suggested_value: 0,
                message: 'Este ingreso ya está completamente asignado',
                availability
            };
        }

        // Si se especificó un monto deseado
        if (desiredAmount !== null && desiredAmount > 0) {
            const baseAmount = availability.base_amount;
            
            // Verificar si el monto deseado cabe como monto fijo
            if (desiredAmount <= availability.amount_available) {
                return {
                    can_allocate: true,
                    suggested_type: 'fixed',
                    suggested_value: desiredAmount,
                    message: `Se puede asignar $${desiredAmount.toFixed(2)} como monto fijo`,
                    availability
                };
            }

            // Verificar si cabe como porcentaje
            const percentNeeded = desiredAmount / baseAmount;
            if (percentNeeded <= availability.percent_available) {
                return {
                    can_allocate: true,
                    suggested_type: 'percent',
                    suggested_value: percentNeeded,
                    suggested_value_display: (percentNeeded * 100).toFixed(1),
                    message: `Se puede asignar ${(percentNeeded * 100).toFixed(1)}% del ingreso`,
                    availability
                };
            }

            // No se puede asignar el monto completo
            return {
                can_allocate: true,
                partial: true,
                suggested_type: 'percent',
                suggested_value: availability.percent_available,
                suggested_value_display: (availability.percent_available * 100).toFixed(1),
                max_amount: availability.amount_available,
                message: `Solo se puede asignar hasta ${(availability.percent_available * 100).toFixed(1)}% ($${availability.amount_available.toFixed(2)})`,
                availability
            };
        }

        // Sin monto específico, sugerir el 100% disponible
        return {
            can_allocate: true,
            suggested_type: 'percent',
            suggested_value: availability.percent_available,
            suggested_value_display: (availability.percent_available * 100).toFixed(1),
            message: `Disponible: ${(availability.percent_available * 100).toFixed(1)}% ($${availability.amount_available.toFixed(2)})`,
            availability
        };
    } catch (error) {
        console.error('Error suggesting allocation:', error);
        throw error;
    }
}

// ============================================================================
// CÁLCULO DE FECHA ÓPTIMA PARA PLANES
// ============================================================================

/**
 * Calcula la fecha óptima para alcanzar un objetivo considerando prioridades
 * @param {number} targetAmount - Monto objetivo
 * @param {Array} incomeSources - Fuentes de ingreso asignadas
 * @param {number} priority - Prioridad del plan (1-5)
 */
export async function calculateOptimalTargetDate(targetAmount, incomeSources, priority = 3) {
    try {
        if (!incomeSources || incomeSources.length === 0) {
            return null;
        }

        let monthlyContribution = 0;

        for (const source of incomeSources) {
            // Obtener información del patrón de ingreso
            const { data: pattern, error } = await supabase
                .from('income_patterns')
                .select('*')
                .eq('id', source.income_pattern_id)
                .single();

            if (error || !pattern || !pattern.active) continue;

            // Obtener disponibilidad actual
            const availability = await calculateAvailablePercentage(source.income_pattern_id);
            
            // Ajustar asignación si excede lo disponible
            let effectiveAllocation = source.allocation_value;
            if (source.allocation_type === 'percent') {
                effectiveAllocation = Math.min(source.allocation_value, availability.percent_available);
            } else {
                effectiveAllocation = Math.min(source.allocation_value, availability.amount_available);
            }

            // Convertir a contribución mensual
            let monthlyOccurrences = 0;
            if (pattern.frequency === 'daily') {
                monthlyOccurrences = 30 / (pattern.interval || 1);
            } else if (pattern.frequency === 'weekly') {
                monthlyOccurrences = 4.33 / (pattern.interval || 1);
            } else if (pattern.frequency === 'monthly') {
                monthlyOccurrences = 1 / (pattern.interval || 1);
            } else if (pattern.frequency === 'yearly') {
                monthlyOccurrences = (1 / 12) / (pattern.interval || 1);
            }

            let contribution = 0;
            if (source.allocation_type === 'percent') {
                contribution = parseFloat(pattern.base_amount) * effectiveAllocation * monthlyOccurrences;
            } else {
                contribution = effectiveAllocation * monthlyOccurrences;
            }

            monthlyContribution += contribution;
        }

        if (monthlyContribution <= 0) {
            return null;
        }

        // Calcular meses necesarios
        const monthsNeeded = Math.ceil(targetAmount / monthlyContribution);

        // Ajustar por prioridad (mayor prioridad = fecha más cercana posible)
        // Prioridad 5 = sin ajuste, Prioridad 1 = +20% tiempo adicional
        const priorityFactor = 1 + ((5 - priority) * 0.05);
        const adjustedMonths = Math.ceil(monthsNeeded * priorityFactor);

        // Calcular fecha sugerida
        const today = new Date();
        const suggestedDate = new Date(today);
        suggestedDate.setMonth(suggestedDate.getMonth() + adjustedMonths);

        return {
            suggested_date: suggestedDate.toISOString().split('T')[0],
            months_needed: adjustedMonths,
            monthly_contribution: monthlyContribution,
            total_expected: monthlyContribution * adjustedMonths
        };
    } catch (error) {
        console.error('Error calculating optimal target date:', error);
        throw error;
    }
}

// ============================================================================
// RECÁLCULO DE BALANCE POR PRIORIDAD
// ============================================================================

/**
 * Recalcula las asignaciones basándose en prioridades
 * Útil cuando se agrega un nuevo gasto/plan de alta prioridad
 */
export async function recalculateAllocationsByPriority() {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Obtener todos los planes activos ordenados por prioridad
        const { data: plans, error: plansError } = await supabase
            .from('plans')
            .select(`
                *,
                income_sources:plan_income_sources(
                    *,
                    income_pattern:income_patterns(*)
                )
            `)
            .eq('user_id', user.id)
            .in('status', ['planned', 'active'])
            .order('priority', { ascending: false });

        if (plansError) throw plansError;

        // Crear mapa de asignaciones por ingreso
        const incomeAllocations = {};
        const adjustments = [];

        for (const plan of plans || []) {
            for (const source of plan.income_sources || []) {
                const incomeId = source.income_pattern_id;
                
                if (!incomeAllocations[incomeId]) {
                    const availability = await calculateAvailablePercentage(incomeId);
                    incomeAllocations[incomeId] = {
                        base_amount: availability.base_amount,
                        remaining_percent: 1,
                        remaining_amount: availability.base_amount
                    };
                }

                const allocation = incomeAllocations[incomeId];
                let requestedValue = source.allocation_value;
                let actualValue = requestedValue;
                let needsAdjustment = false;

                if (source.allocation_type === 'percent') {
                    if (requestedValue > allocation.remaining_percent) {
                        actualValue = allocation.remaining_percent;
                        needsAdjustment = true;
                    }
                    allocation.remaining_percent -= actualValue;
                    allocation.remaining_amount -= allocation.base_amount * actualValue;
                } else {
                    if (requestedValue > allocation.remaining_amount) {
                        actualValue = allocation.remaining_amount;
                        needsAdjustment = true;
                    }
                    allocation.remaining_amount -= actualValue;
                    allocation.remaining_percent -= actualValue / allocation.base_amount;
                }

                if (needsAdjustment) {
                    adjustments.push({
                        plan_id: plan.id,
                        plan_title: plan.title,
                        source_id: source.id,
                        income_name: source.income_pattern?.name,
                        original_value: requestedValue,
                        adjusted_value: actualValue,
                        allocation_type: source.allocation_type
                    });
                }
            }
        }

        return {
            success: true,
            adjustments,
            income_remaining: incomeAllocations
        };
    } catch (error) {
        console.error('Error recalculating allocations:', error);
        throw error;
    }
}

// ============================================================================
// UTILIDADES DE FORMATO
// ============================================================================

/**
 * Formatea un número como moneda
 */
export function formatCurrency(amount, currency = 'MXN') {
    return new Intl.NumberFormat('es-MX', {
        style: 'currency',
        currency: currency
    }).format(amount);
}

/**
 * Formatea un porcentaje
 */
export function formatPercent(value, decimals = 1) {
    return `${(value * 100).toFixed(decimals)}%`;
}
