/**
 * plans-v2.js
 * Gestión de planes/metas con asignación de fuentes de ingreso (V2)
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
            .from('plans_with_progress')
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
        return data || [];
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
            .from('plans_with_progress')
            .select('*')
            .eq('id', id)
            .single();

        if (planError) throw planError;

        // Obtener income sources asignadas
        const { data: sources, error: sourcesError } = await supabase
            .from('plan_income_sources')
            .select(`
                *,
                income_pattern:income_patterns(*)
            `)
            .eq('plan_id', id);

        if (sourcesError) throw sourcesError;

        plan.income_sources = sources || [];
        return plan;
    } catch (error) {
        console.error('Error fetching plan:', error);
        throw error;
    }
}

/**
 * Crea un nuevo plan con sus income sources opcionales
 */
export async function createPlan(planData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos
        validatePlanData(planData);

        // 1. Crear el plan
        const plan = {
            user_id: user.id,
            title: planData.title,
            description: planData.description || null,
            category: planData.category || null,
            target_amount: parseFloat(planData.target_amount),
            requested_target_date: planData.requested_target_date || null,
            priority: planData.priority || 3,
            status: planData.status || 'planned',
            auto_create_reminder: planData.auto_create_reminder || false,
            envelope_id: planData.envelope_id || null
        };

        const { data: createdPlan, error: planError } = await supabase
            .from('plans')
            .insert([plan])
            .select()
            .single();

        if (planError) throw planError;

        // 2. Agregar income sources si se proporcionaron
        if (planData.income_sources && planData.income_sources.length > 0) {
            await assignIncomeSources(createdPlan.id, planData.income_sources);
        }

        // 3. Calcular fecha sugerida si hay income sources
        if (planData.income_sources && planData.income_sources.length > 0) {
            const suggestedDate = await calculateSuggestedTargetDate(createdPlan.id);
            if (suggestedDate) {
                await updatePlan(createdPlan.id, { suggested_target_date: suggestedDate });
            }
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

        if (updates.title !== undefined) plan.title = updates.title;
        if (updates.description !== undefined) plan.description = updates.description;
        if (updates.category !== undefined) plan.category = updates.category;
        if (updates.target_amount !== undefined) plan.target_amount = parseFloat(updates.target_amount);
        if (updates.requested_target_date !== undefined) plan.requested_target_date = updates.requested_target_date;
        if (updates.suggested_target_date !== undefined) plan.suggested_target_date = updates.suggested_target_date;
        if (updates.priority !== undefined) plan.priority = updates.priority;
        if (updates.status !== undefined) plan.status = updates.status;
        if (updates.auto_create_reminder !== undefined) plan.auto_create_reminder = updates.auto_create_reminder;
        if (updates.envelope_id !== undefined) plan.envelope_id = updates.envelope_id;

        const { data, error } = await supabase
            .from('plans')
            .update(plan)
            .eq('id', id)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating plan:', error);
        throw error;
    }
}

/**
 * Elimina un plan y sus income sources
 */
export async function deletePlan(id) {
    try {
        // Los plan_income_sources se eliminan automáticamente por CASCADE
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
// PLAN INCOME SOURCES
// ============================================================================

/**
 * Asigna income sources a un plan
 */
export async function assignIncomeSources(planId, sources) {
    try {
        // Validar sources
        validateIncomeSources(sources);

        // Preparar datos
        const incomeSources = sources.map(source => ({
            plan_id: planId,
            income_pattern_id: source.income_pattern_id,
            allocation_type: source.allocation_type,
            allocation_value: parseFloat(source.allocation_value)
        }));

        const { data, error } = await supabase
            .from('plan_income_sources')
            .insert(incomeSources)
            .select();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error assigning income sources:', error);
        throw error;
    }
}

/**
 * Actualiza una income source de un plan
 */
export async function updateIncomeSource(sourceId, updates) {
    try {
        const source = {};

        if (updates.allocation_type !== undefined) source.allocation_type = updates.allocation_type;
        if (updates.allocation_value !== undefined) source.allocation_value = parseFloat(updates.allocation_value);

        const { data, error } = await supabase
            .from('plan_income_sources')
            .update(source)
            .eq('id', sourceId)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating income source:', error);
        throw error;
    }
}

/**
 * Elimina una income source de un plan
 */
export async function removeIncomeSource(sourceId) {
    try {
        const { error } = await supabase
            .from('plan_income_sources')
            .delete()
            .eq('id', sourceId);

        if (error) throw error;
        return true;
    } catch (error) {
        console.error('Error removing income source:', error);
        throw error;
    }
}

/**
 * Reemplaza todas las income sources de un plan
 */
export async function replaceIncomeSources(planId, newSources) {
    try {
        // 1. Eliminar sources existentes
        await supabase
            .from('plan_income_sources')
            .delete()
            .eq('plan_id', planId);

        // 2. Agregar nuevas sources
        if (newSources && newSources.length > 0) {
            await assignIncomeSources(planId, newSources);
        }

        // 3. Recalcular fecha sugerida
        const suggestedDate = await calculateSuggestedTargetDate(planId);
        if (suggestedDate) {
            await updatePlan(planId, { suggested_target_date: suggestedDate });
        }

        return true;
    } catch (error) {
        console.error('Error replacing income sources:', error);
        throw error;
    }
}

// ============================================================================
// CÁLCULO DE FECHA SUGERIDA
// ============================================================================

/**
 * Calcula la fecha sugerida para alcanzar el target_amount
 * basándose en las income sources asignadas
 */
export async function calculateSuggestedTargetDate(planId) {
    try {
        const plan = await getPlanById(planId);
        if (!plan || !plan.income_sources || plan.income_sources.length === 0) {
            return null;
        }

        // Calcular cuánto se ahorra por mes
        let monthlyContribution = 0;

        for (const source of plan.income_sources) {
            const pattern = source.income_pattern;
            if (!pattern || !pattern.active) continue;

            // Convertir frecuencia a contribución mensual
            let monthlyOccurrences = 0;
            
            if (pattern.frequency === 'daily') {
                monthlyOccurrences = 30 / (pattern.interval || 1);
            } else if (pattern.frequency === 'weekly') {
                monthlyOccurrences = 4 / (pattern.interval || 1);
            } else if (pattern.frequency === 'monthly') {
                monthlyOccurrences = 1 / (pattern.interval || 1);
            } else if (pattern.frequency === 'yearly') {
                monthlyOccurrences = (1 / 12) / (pattern.interval || 1);
            }

            // Calcular contribución mensual de este pattern
            let contribution = 0;
            if (source.allocation_type === 'percent') {
                contribution = parseFloat(pattern.base_amount) * parseFloat(source.allocation_value) * monthlyOccurrences;
            } else if (source.allocation_type === 'fixed') {
                contribution = parseFloat(source.allocation_value) * monthlyOccurrences;
            }

            monthlyContribution += contribution;
        }

        if (monthlyContribution <= 0) {
            return null;
        }

        // Calcular cuánto falta ahorrar
        const remaining = parseFloat(plan.target_amount) - (parseFloat(plan.saved_amount) || 0);
        if (remaining <= 0) {
            return new Date().toISOString().split('T')[0]; // Ya alcanzado
        }

        // Calcular meses necesarios
        const monthsNeeded = Math.ceil(remaining / monthlyContribution);

        // Calcular fecha sugerida
        const today = new Date();
        const suggestedDate = new Date(today);
        suggestedDate.setMonth(suggestedDate.getMonth() + monthsNeeded);

        return suggestedDate.toISOString().split('T')[0];
    } catch (error) {
        console.error('Error calculating suggested target date:', error);
        return null;
    }
}

// ============================================================================
// PLAN PROGRESS
// ============================================================================

/**
 * Obtiene el progreso detallado de un plan
 */
export async function getPlanProgress(planId) {
    try {
        const plan = await getPlanById(planId);
        if (!plan) throw new Error('Plan no encontrado');

        return {
            target_amount: parseFloat(plan.target_amount),
            saved_amount: parseFloat(plan.saved_amount) || 0,
            remaining_amount: parseFloat(plan.remaining_amount) || parseFloat(plan.target_amount),
            progress_percent: parseFloat(plan.progress_percent) || 0,
            is_complete: parseFloat(plan.progress_percent) >= 100,
            requested_target_date: plan.requested_target_date,
            suggested_target_date: plan.suggested_target_date
        };
    } catch (error) {
        console.error('Error getting plan progress:', error);
        throw error;
    }
}

/**
 * Obtiene plans con target date en un rango específico
 */
export async function getPlansWithTargetInRange(startDate, endDate, useRequested = true) {
    try {
        const dateField = useRequested ? 'requested_target_date' : 'suggested_target_date';
        
        const { data, error } = await supabase
            .from('plans_with_progress')
            .select('*')
            .gte(dateField, startDate)
            .lte(dateField, endDate)
            .neq('status', 'cancelled')
            .order(dateField, { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching plans with target in range:', error);
        throw error;
    }
}

// ============================================================================
// VALIDACIÓN
// ============================================================================

/**
 * Valida los datos de un plan
 */
function validatePlanData(data) {
    const requiredFields = ['title', 'target_amount'];
    
    for (const field of requiredFields) {
        if (!data[field]) {
            throw new Error(`Campo requerido: ${field}`);
        }
    }

    if (parseFloat(data.target_amount) <= 0) {
        throw new Error('El monto objetivo debe ser mayor a 0');
    }

    if (data.priority !== undefined) {
        const priority = parseInt(data.priority);
        if (priority < 1 || priority > 5) {
            throw new Error('La prioridad debe estar entre 1 y 5');
        }
    }

    if (data.status !== undefined) {
        const validStatuses = ['planned', 'active', 'completed', 'cancelled'];
        if (!validStatuses.includes(data.status)) {
            throw new Error(`Estado inválido. Debe ser: ${validStatuses.join(', ')}`);
        }
    }

    if (data.requested_target_date && data.suggested_target_date) {
        const requested = new Date(data.requested_target_date);
        const suggested = new Date(data.suggested_target_date);
        const today = new Date();
        
        if (requested < today) {
            throw new Error('La fecha solicitada no puede ser en el pasado');
        }
    }
}

/**
 * Valida las income sources
 */
function validateIncomeSources(sources) {
    if (!Array.isArray(sources)) {
        throw new Error('income_sources debe ser un array');
    }

    for (const source of sources) {
        if (!source.income_pattern_id) {
            throw new Error('income_pattern_id es requerido');
        }
        if (!source.allocation_type) {
            throw new Error('allocation_type es requerido');
        }
        if (source.allocation_value === undefined || source.allocation_value === null) {
            throw new Error('allocation_value es requerido');
        }

        const validTypes = ['percent', 'fixed'];
        if (!validTypes.includes(source.allocation_type)) {
            throw new Error(`allocation_type inválido. Debe ser: ${validTypes.join(', ')}`);
        }

        const value = parseFloat(source.allocation_value);
        if (value <= 0) {
            throw new Error('allocation_value debe ser mayor a 0');
        }

        if (source.allocation_type === 'percent' && value > 1) {
            throw new Error('allocation_value para percent debe estar entre 0 y 1 (ejemplo: 0.25 para 25%)');
        }
    }
}

// ============================================================================
// UTILIDADES
// ============================================================================

/**
 * Obtiene estadísticas de plans
 */
export async function getPlanStatistics() {
    try {
        const plans = await getPlans();
        
        const stats = {
            total_plans: plans.length,
            by_status: {
                planned: 0,
                active: 0,
                completed: 0,
                cancelled: 0
            },
            total_target: 0,
            total_saved: 0,
            average_progress: 0
        };

        for (const plan of plans) {
            stats.by_status[plan.status] = (stats.by_status[plan.status] || 0) + 1;
            stats.total_target += parseFloat(plan.target_amount);
            stats.total_saved += parseFloat(plan.saved_amount) || 0;
        }

        const activePlans = plans.filter(p => p.status !== 'cancelled');
        if (activePlans.length > 0) {
            stats.average_progress = activePlans.reduce((sum, p) => {
                return sum + (parseFloat(p.progress_percent) || 0);
            }, 0) / activePlans.length;
        }

        return stats;
    } catch (error) {
        console.error('Error calculating plan statistics:', error);
        throw error;
    }
}

/**
 * Obtiene categorías únicas de plans
 */
export async function getPlanCategories() {
    try {
        const { data, error } = await supabase
            .from('plans')
            .select('category')
            .not('category', 'is', null)
            .order('category');

        if (error) throw error;
        
        const categories = [...new Set(data.map(item => item.category))];
        return categories;
    } catch (error) {
        console.error('Error fetching plan categories:', error);
        return [];
    }
}
