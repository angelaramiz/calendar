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
