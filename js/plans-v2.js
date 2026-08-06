/**
 * plans-v2.js
 * Gestión de planes/metas con asignación de fuentes de ingreso (V2)
 * Compatible con esquema V2: plans tiene name, target_amount, current_amount, target_date, status
 */

import { supabase } from './supabase-client.js';

// ============================================================================
// PLANS CRUD
// ============================================================================

/**
 * Obtiene todos los plans del usuario
 */
export async function getPlans(filters = {}) {
    try {
        let query = supabase
            .from('plans')
            .select('*')
            .order('priority', { ascending: false })
            .order('created_at', { ascending: false });

        if (filters.status) {
            query = query.eq('status', filters.status);
        }
        if (filters.category) {
            query = query.eq('category', filters.category);
        }

        const { data, error } = await query;
        if (error) throw error;
        
        // Calcular progreso para cada plan
        return (data || []).map(plan => ({
            ...plan,
            progress_percent: plan.target_amount > 0 
                ? Math.min(100, (plan.current_amount / plan.target_amount) * 100)
                : 0,
            remaining_amount: Math.max(0, plan.target_amount - plan.current_amount)
        }));
    } catch (error) {
        console.error('Error fetching plans:', error);
        throw error;
    }
}

/**
 * Obtiene un plan por ID con sus income sources
 */
export async function getPlanById(id) {
    try {
        const { data: plan, error: planError } = await supabase
            .from('plans')
            .select('*')
            .eq('id', id)
            .single();

        if (planError) throw planError;
        if (!plan) return null;

        // Obtener income sources
        const { data: sources, error: sourcesError } = await supabase
            .from('plan_income_sources')
            .select(`
                *,
                income_patterns (id, name, base_amount, frequency)
            `)
            .eq('plan_id', id);

        if (sourcesError) {
            console.warn('Error loading income sources:', sourcesError);
        }

        // Calcular progreso
        const progress_percent = plan.target_amount > 0 
            ? Math.min(100, (plan.current_amount / plan.target_amount) * 100)
            : 0;

        return {
            ...plan,
            income_sources: sources || [],
            progress_percent,
            remaining_amount: Math.max(0, plan.target_amount - plan.current_amount)
        };
    } catch (error) {
        console.error('Error fetching plan by ID:', error);
        throw error;
    }
}

/**
 * Crea un nuevo plan
 */
export async function createPlan(planData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos
        validatePlanData(planData);

        // Crear el plan con campos V2
        const plan = {
            user_id: user.id,
            name: planData.name || planData.title, // Soportar ambos nombres
            description: planData.description || null,
            category: planData.category || null,
            target_amount: parseFloat(planData.target_amount),
            current_amount: parseFloat(planData.current_amount) || 0,
            start_date: planData.start_date || null,
            target_date: planData.target_date || planData.requested_target_date || null,
            priority: planData.priority || 5,
            status: planData.status || 'active'
        };

        const { data: createdPlan, error: planError } = await supabase
            .from('plans')
            .insert([plan])
            .select()
            .single();

        if (planError) throw planError;

        // Agregar income sources si se proporcionaron
        if (planData.income_sources && planData.income_sources.length > 0) {
            await assignIncomeSources(createdPlan.id, planData.income_sources);
        }

        return await getPlanById(createdPlan.id);
    } catch (error) {
        console.error('Error creating plan:', error);
        throw error;
    }
}

/**
 * Actualiza un plan existente
 */
export async function updatePlan(id, updates) {
    try {
        const plan = {};

        // Mapear campos (soportar nombres V1 y V2)
        if (updates.name !== undefined) plan.name = updates.name;
        if (updates.title !== undefined) plan.name = updates.title; // Alias
        if (updates.description !== undefined) plan.description = updates.description;
        if (updates.category !== undefined) plan.category = updates.category;
        if (updates.target_amount !== undefined) plan.target_amount = parseFloat(updates.target_amount);
        if (updates.current_amount !== undefined) plan.current_amount = parseFloat(updates.current_amount);
        if (updates.start_date !== undefined) plan.start_date = updates.start_date;
        if (updates.target_date !== undefined) plan.target_date = updates.target_date;
        if (updates.requested_target_date !== undefined) plan.target_date = updates.requested_target_date; // Alias
        if (updates.priority !== undefined) plan.priority = updates.priority;
        if (updates.status !== undefined) plan.status = updates.status;

        const { data, error } = await supabase
            .from('plans')
            .update(plan)
            .eq('id', id)
            .select()
            .single();

        if (error) throw error;

        // Actualizar income sources si se proporcionaron
        if (updates.income_sources !== undefined) {
            await supabase.from('plan_income_sources').delete().eq('plan_id', id);
            if (updates.income_sources.length > 0) {
                await assignIncomeSources(id, updates.income_sources);
            }
        }

        return await getPlanById(id);
    } catch (error) {
        console.error('Error updating plan:', error);
        throw error;
    }
}

/**
 * Elimina un plan
 */
export async function deletePlan(id) {
    try {
        const { error } = await supabase
            .from('plans')
            .delete()
            .eq('id', id);

        if (error) throw error;
        return true;
    } catch (error) {
        console.error('Error deleting plan:', error);
        throw error;
    }
}

// ============================================================================
// INCOME SOURCES
// ============================================================================

/**
 * Asigna income sources a un plan
 */
export async function assignIncomeSources(planId, sources) {
    try {
        if (!sources || sources.length === 0) return [];

        const inserts = sources.map(source => {
            const allocationType = source.allocation_type || 'fixed';
            let allocationValue = parseFloat(source.allocation_value) || parseFloat(source.amount) || 0;
            
            // Validar que percent esté en formato decimal (0-1)
            if (allocationType === 'percent' && allocationValue > 1) {
                console.warn(`allocation_value (${allocationValue}) parece estar en formato porcentaje, convirtiendo a decimal`);
                allocationValue = allocationValue / 100;
            }
            
            return {
                plan_id: planId,
                income_pattern_id: source.income_pattern_id,
                allocation_type: allocationType,
                allocation_value: allocationValue,
                notes: source.notes || null
            };
        });

        const { data, error } = await supabase
            .from('plan_income_sources')
            .insert(inserts)
            .select();

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error assigning income sources:', error);
        throw error;
    }
}

/**
 * Obtiene income sources de un plan
 */
export async function getPlanIncomeSources(planId) {
    try {
        const { data, error } = await supabase
            .from('plan_income_sources')
            .select(`
                *,
                income_patterns (id, name, base_amount, frequency)
            `)
            .eq('plan_id', planId);

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching plan income sources:', error);
        throw error;
    }
}

/**
 * Elimina una income source de un plan
 */
export async function removeIncomeSource(planId, incomePatternId) {
    try {
        const { error } = await supabase
            .from('plan_income_sources')
            .delete()
            .eq('plan_id', planId)
            .eq('income_pattern_id', incomePatternId);

        if (error) throw error;
        return true;
    } catch (error) {
        console.error('Error removing income source:', error);
        throw error;
    }
}

// ============================================================================
// CONTRIBUCIONES
// ============================================================================

/**
 * Agrega una contribución a un plan
 */
export async function contributeToPlan(planId, amount, description = 'Contribución', date = null) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        const plan = await getPlanById(planId);
        if (!plan) throw new Error('Plan no encontrado');

        // Actualizar current_amount del plan
        const newAmount = (parseFloat(plan.current_amount) || 0) + parseFloat(amount);
        
        const { data, error } = await supabase
            .from('plans')
            .update({ current_amount: newAmount })
            .eq('id', planId)
            .select()
            .single();

        if (error) throw error;

        return await getPlanById(planId);
    } catch (error) {
        console.error('Error contributing to plan:', error);
        throw error;
    }
}

/**
 * Retira dinero de un plan
 */
export async function withdrawFromPlan(planId, amount, description = 'Retiro') {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        const plan = await getPlanById(planId);
        if (!plan) throw new Error('Plan no encontrado');

        const currentAmount = parseFloat(plan.current_amount) || 0;
        if (amount > currentAmount) {
            throw new Error(`Saldo insuficiente. Disponible: $${currentAmount.toFixed(2)}`);
        }

        const newAmount = currentAmount - parseFloat(amount);
        
        const { data, error } = await supabase
            .from('plans')
            .update({ current_amount: newAmount })
            .eq('id', planId)
            .select()
            .single();

        if (error) throw error;

        return await getPlanById(planId);
    } catch (error) {
        console.error('Error withdrawing from plan:', error);
        throw error;
    }
}

/**
 * Recalcula las fechas estimadas de un plan basándose en las fuentes de ingreso asignadas
 * @param {string} planId - ID del plan
 * @param {number} extraContribution - Contribución extra única (ej: ingreso directo)
 * @returns {Object} Plan actualizado con fecha estimada recalculada e info de cambios
 */
export async function recalculatePlanDates(planId, extraContribution = 0) {
    try {
        const plan = await getPlanById(planId);
        if (!plan) throw new Error('Plan no encontrado');
        
        const oldTargetDate = plan.target_date;
        const targetAmount = parseFloat(plan.target_amount);
        const currentAmount = parseFloat(plan.current_amount || 0);
        const remaining = targetAmount - currentAmount;
        
        // Si ya se alcanzó la meta, marcar como completado
        if (remaining <= 0) {
            if (plan.status !== 'completed') {
                await updatePlan(planId, { status: 'completed' });
            }
            return {
                ...plan,
                current_amount: currentAmount,
                status: 'completed',
                dateChanged: false,
                completed: true,
                progress_percent: '100.0'
            };
        }
        
        // Obtener fuentes de ingreso asignadas
        const sources = await getPlanIncomeSources(planId);
        
        // Calcular aporte mensual estimado de fuentes recurrentes
        let monthlyContribution = 0;
        
        if (sources && sources.length > 0) {
            for (const source of sources) {
                if (!source.income_pattern) continue;
                
                const pattern = source.income_pattern;
                const baseAmount = parseFloat(pattern.base_amount) || 0;
                
                // Calcular frecuencia mensual
                let monthlyOccurrences = 1;
                switch (pattern.frequency) {
                    case 'weekly':
                        monthlyOccurrences = 4.33; // Más preciso: 52/12
                        break;
                    case 'biweekly':
                        monthlyOccurrences = 2.17; // 26/12
                        break;
                    case 'monthly':
                        monthlyOccurrences = 1;
                        break;
                    case 'yearly':
                        monthlyOccurrences = 1/12;
                        break;
                }
                
                const monthlyIncome = baseAmount * monthlyOccurrences;
                
                // Calcular contribución según tipo de asignación
                if (source.allocation_type === 'fixed') {
                    monthlyContribution += parseFloat(source.allocation_value) * monthlyOccurrences;
                } else if (source.allocation_type === 'percent') {
                    const percent = parseFloat(source.allocation_value);
                    monthlyContribution += monthlyIncome * percent;
                }
            }
        }
        
        // Calcular nueva fecha estimada
        let newTargetDate = null;
        let monthsNeeded = 0;
        let calculationMethod = 'none';
        
        if (monthlyContribution > 0) {
            // Método 1: Calcular basado en fuentes de ingreso recurrentes
            monthsNeeded = Math.ceil(remaining / monthlyContribution);
            const estimatedDate = new Date();
            estimatedDate.setMonth(estimatedDate.getMonth() + monthsNeeded);
            newTargetDate = estimatedDate.toISOString().slice(0, 10);
            calculationMethod = 'sources';
        } else if (currentAmount > 0) {
            // Método 2: Si no hay fuentes pero ya hay progreso, estimar basado en ritmo actual
            // Calcular cuánto tiempo llevamos ahorrando (desde created_at hasta hoy)
            const createdAt = new Date(plan.created_at);
            const today = new Date();
            const monthsElapsed = Math.max(1, (today - createdAt) / (1000 * 60 * 60 * 24 * 30));
            
            // Ritmo mensual histórico
            const historicalMonthlyRate = currentAmount / monthsElapsed;
            
            if (historicalMonthlyRate > 0) {
                monthsNeeded = Math.ceil(remaining / historicalMonthlyRate);
                const estimatedDate = new Date();
                estimatedDate.setMonth(estimatedDate.getMonth() + monthsNeeded);
                newTargetDate = estimatedDate.toISOString().slice(0, 10);
                monthlyContribution = historicalMonthlyRate;
                calculationMethod = 'historical';
                console.log(`📊 Recálculo histórico: ${currentAmount} en ${monthsElapsed.toFixed(2)} meses = ${historicalMonthlyRate.toFixed(2)}/mes, faltan ${remaining}, estimados ${monthsNeeded} meses más`);
            } else {
                newTargetDate = oldTargetDate;
            }
        } else if (extraContribution > 0) {
            // Método 3: Primera contribución, estimar asumiendo contribuciones similares mensuales
            const assumedMonthlyRate = extraContribution; // Asumir que aportará lo mismo cada mes
            monthsNeeded = Math.ceil(remaining / assumedMonthlyRate);
            const estimatedDate = new Date();
            estimatedDate.setMonth(estimatedDate.getMonth() + monthsNeeded);
            newTargetDate = estimatedDate.toISOString().slice(0, 10);
            monthlyContribution = assumedMonthlyRate;
            calculationMethod = 'assumed';
        } else {
            // Sin datos para calcular, mantener fecha original
            newTargetDate = oldTargetDate;
            console.log('⚠️ Sin datos para recalcular fecha: no hay fuentes, ni historial, ni contribución extra');
        }
        
        // Determinar si la fecha cambió significativamente (más de 7 días de diferencia)
        let dateChanged = false;
        if (newTargetDate && oldTargetDate) {
            const oldDate = new Date(oldTargetDate);
            const newDate = new Date(newTargetDate);
            const diffDays = Math.abs((newDate - oldDate) / (1000 * 60 * 60 * 24));
            dateChanged = diffDays > 7; // Solo notificar si cambia más de una semana
        } else if (newTargetDate && !oldTargetDate) {
            dateChanged = true;
        }
        
        // Actualizar la fecha en la base de datos si cambió
        if (dateChanged && newTargetDate) {
            await updatePlan(planId, { target_date: newTargetDate });
        }
        
        // Obtener plan actualizado
        const updatedPlan = await getPlanById(planId);
        
        return {
            ...updatedPlan,
            old_target_date: oldTargetDate,
            new_target_date: newTargetDate,
            dateChanged: dateChanged,
            monthly_contribution: monthlyContribution,
            months_remaining: monthsNeeded,
            calculation_method: calculationMethod,
            progress_percent: (currentAmount / targetAmount * 100).toFixed(1),
            completed: false
        };
    } catch (error) {
        console.error('Error recalculating plan dates:', error);
        return await getPlanById(planId);
    }
}

// ============================================================================
// PROGRESO Y ESTADÍSTICAS
// ============================================================================

/**
 * Obtiene el progreso de un plan
 */
export async function getPlanProgress(planId) {
    try {
        const plan = await getPlanById(planId);
        if (!plan) throw new Error('Plan no encontrado');

        return {
            target_amount: parseFloat(plan.target_amount),
            current_amount: parseFloat(plan.current_amount) || 0,
            remaining_amount: plan.remaining_amount,
            progress_percent: plan.progress_percent,
            is_complete: plan.progress_percent >= 100,
            target_date: plan.target_date
        };
    } catch (error) {
        console.error('Error getting plan progress:', error);
        throw error;
    }
}

/**
 * Obtiene plans con target date en un rango específico
 */
export async function getPlansWithTargetInRange(startDate, endDate) {
    try {
        const { data, error } = await supabase
            .from('plans')
            .select('*')
            .gte('target_date', startDate)
            .lte('target_date', endDate)
            .neq('status', 'cancelled')
            .order('target_date', { ascending: true });

        if (error) throw error;
        
        return (data || []).map(plan => ({
            ...plan,
            progress_percent: plan.target_amount > 0 
                ? Math.min(100, (plan.current_amount / plan.target_amount) * 100)
                : 0
        }));
    } catch (error) {
        console.error('Error fetching plans with target in range:', error);
        throw error;
    }
}

/**
 * Obtiene planes activos
 */
export async function getActivePlans() {
    try {
        return await getPlans({ status: 'active' });
    } catch (error) {
        console.error('Error fetching active plans:', error);
        throw error;
    }
}

/**
 * Marca un plan como completado
 */
export async function completePlan(planId) {
    try {
        return await updatePlan(planId, { 
            status: 'completed'
        });
    } catch (error) {
        console.error('Error completing plan:', error);
        throw error;
    }
}

/**
 * Pausa un plan
 */
export async function pausePlan(planId) {
    try {
        return await updatePlan(planId, { status: 'paused' });
    } catch (error) {
        console.error('Error pausing plan:', error);
        throw error;
    }
}

/**
 * Reactiva un plan pausado
 */
export async function resumePlan(planId) {
    try {
        return await updatePlan(planId, { status: 'active' });
    } catch (error) {
        console.error('Error resuming plan:', error);
        throw error;
    }
}

/**
 * Cancela un plan
 */
export async function cancelPlan(planId) {
    try {
        return await updatePlan(planId, { status: 'cancelled' });
    } catch (error) {
        console.error('Error cancelling plan:', error);
        throw error;
    }
}

// ============================================================================
// SUGERENCIAS PARA CONTRIBUCIONES DESDE INGRESOS
// ============================================================================

/**
 * Obtiene sugerencias de contribución a planes basándose en un ingreso confirmado
 * @param {string} incomePatternId - ID del patrón de ingreso
 * @param {number} confirmedAmount - Monto confirmado del ingreso
 * @returns {Object} Objeto con planes y montos sugeridos
 */
export async function getPlanSuggestionsForIncome(incomePatternId, confirmedAmount) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) return { hasSuggestions: false, plans: [] };

        // Obtener planes vinculados a este patrón de ingreso
        const { data: planSources, error } = await supabase
            .from('plan_income_sources')
            .select(`
                plan_id,
                allocation_type,
                allocation_value,
                plans (
                    id,
                    name,
                    target_amount,
                    current_amount,
                    target_date,
                    status,
                    priority
                )
            `)
            .eq('income_pattern_id', incomePatternId);

        if (error) throw error;

        if (!planSources || planSources.length === 0) {
            return { hasSuggestions: false, plans: [] };
        }

        // Calcular sugerencias para cada plan activo
        const suggestions = planSources
            .filter(ps => ps.plans && ps.plans.status === 'active')
            .map(ps => {
                const plan = ps.plans;
                let suggestedAmount = 0;

                if (ps.allocation_type === 'fixed') {
                    suggestedAmount = parseFloat(ps.allocation_value) || 0;
                } else if (ps.allocation_type === 'percent') {
                    const percent = parseFloat(ps.allocation_value) || 0;
                    suggestedAmount = confirmedAmount * percent;
                }

                const currentAmount = parseFloat(plan.current_amount) || 0;
                const targetAmount = parseFloat(plan.target_amount);
                const remaining = targetAmount - currentAmount;

                // No sugerir más de lo que falta
                suggestedAmount = Math.min(suggestedAmount, remaining);

                return {
                    plan_id: plan.id,
                    name: plan.name,
                    target_amount: targetAmount,
                    current_amount: currentAmount,
                    remaining_amount: remaining,
                    target_date: plan.target_date,
                    priority: plan.priority,
                    allocation_type: ps.allocation_type,
                    allocation_value: ps.allocation_value,
                    suggested_amount: Math.max(0, suggestedAmount),
                    progress_percent: targetAmount > 0 ? (currentAmount / targetAmount * 100).toFixed(1) : 0
                };
            })
            .filter(s => s.suggested_amount > 0); // Solo incluir planes con sugerencia > 0

        return {
            hasSuggestions: suggestions.length > 0,
            plans: suggestions.sort((a, b) => b.priority - a.priority) // Ordenar por prioridad
        };
    } catch (error) {
        console.error('Error getting plan suggestions for income:', error);
        return { hasSuggestions: false, plans: [] };
    }
}

// ============================================================================
// VALIDACIÓN
// ============================================================================

/**
 * Valida los datos de un plan
 */
function validatePlanData(data) {
    // Soportar ambos nombres de campo
    const name = data.name || data.title;
    if (!name) {
        throw new Error('Campo requerido: name');
    }

    if (!data.target_amount) {
        throw new Error('Campo requerido: target_amount');
    }

    if (parseFloat(data.target_amount) <= 0) {
        throw new Error('El monto objetivo debe ser mayor a 0');
    }

    if (data.priority !== undefined) {
        const priority = parseInt(data.priority);
        if (priority < 1 || priority > 10) {
            throw new Error('La prioridad debe estar entre 1 y 10');
        }
    }

    if (data.status !== undefined) {
        const validStatuses = ['active', 'completed', 'paused', 'cancelled'];
        if (!validStatuses.includes(data.status)) {
            throw new Error(`Estado inválido. Debe ser: ${validStatuses.join(', ')}`);
        }
    }
}

/**
 * Valida las income sources
 */
function validateIncomeSources(sources) {
    if (!Array.isArray(sources)) {
        throw new Error('Income sources debe ser un array');
    }

    for (const source of sources) {
        if (!source.income_pattern_id) {
            throw new Error('Cada income source debe tener income_pattern_id');
        }
        if (!source.allocation_type || !['percent', 'fixed'].includes(source.allocation_type)) {
            throw new Error('allocation_type debe ser "percent" o "fixed"');
        }
        if (source.allocation_type === 'percent') {
            const value = parseFloat(source.allocation_value);
            if (value <= 0 || value > 1) {
                throw new Error('Para tipo percent, el valor debe estar entre 0 y 1');
            }
        }
    }
}

// ============================================================================
// EXPORTS ADICIONALES PARA COMPATIBILIDAD
// ============================================================================

// Alias para compatibilidad con código que usa nombres V1
export const savePlan = createPlan;
export const fetchPlans = getPlans;
export const fetchPlanById = getPlanById;
