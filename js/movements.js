/**
 * movements.js
 * Gestión de movimientos confirmados (V2)
 */

import { supabase } from './supabase-client.js';

// ============================================================================
// MOVEMENTS CRUD
// ============================================================================

/**
 * Obtiene todos los movements del usuario con filtros opcionales
 */
export async function getMovements(filters = {}) {
    try {
        let query = supabase
            .from('movements_with_patterns')
            .select('*')
            .order('date', { ascending: false });

        // Aplicar filtros
        if (filters.startDate) {
            query = query.gte('date', filters.startDate);
        }
        if (filters.endDate) {
            query = query.lte('date', filters.endDate);
        }
        if (filters.type) {
            query = query.eq('type', filters.type);
        }
        if (filters.category) {
            query = query.eq('category', filters.category);
        }
        if (filters.archived !== undefined) {
            query = query.eq('archived', filters.archived);
        }

        const { data, error } = await query;
        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching movements:', error);
        throw error;
    }
}

/**
 * Obtiene un movement por ID
 */
export async function getMovementById(id) {
    try {
        const { data, error } = await supabase
            .from('movements_with_patterns')
            .select('*')
            .eq('id', id)
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error fetching movement:', error);
        throw error;
    }
}

/**
 * Crea un nuevo movement (manual o desde patrón)
 */
export async function createMovement(movementData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos
        validateMovementData(movementData);

        const movement = {
            user_id: user.id,
            title: movementData.title,
            description: movementData.description || null,
            type: movementData.type,
            category: movementData.category || null,
            date: movementData.date,
            expected_amount: movementData.expected_amount ? parseFloat(movementData.expected_amount) : null,
            confirmed_amount: parseFloat(movementData.confirmed_amount),
            income_pattern_id: movementData.income_pattern_id || null,
            expense_pattern_id: movementData.expense_pattern_id || null,
            loan_id: movementData.loan_id || null,
            plan_id: movementData.plan_id || null,
            envelope_id: movementData.envelope_id || null,
            is_loan_counterpart: movementData.is_loan_counterpart || false,
            confirmed: movementData.confirmed !== false,
            archived: movementData.archived || false
        };

        const { data, error } = await supabase
            .from('movements')
            .insert([movement])
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error creating movement:', error);
        throw error;
    }
}

/**
 * Confirma una ocurrencia proyectada de un patrón
 * Convierte un projected event en un movement confirmado
 */
export async function confirmPatternOccurrence(occurrence, adjustedAmount = null) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Determinar origen (income o expense pattern)
        const isIncome = occurrence.pattern_type === 'income';
        
        // Los datos proyectados usan 'name' y 'expected_amount', no 'title' y 'amount'
        const title = occurrence.title || occurrence.name;
        const expectedAmount = occurrence.amount || occurrence.expected_amount;
        
        const movement = {
            user_id: user.id,
            title: title,
            description: occurrence.description || null,
            type: isIncome ? 'ingreso' : 'gasto',
            category: occurrence.category || null,
            date: occurrence.date,
            expected_amount: parseFloat(expectedAmount),
            confirmed_amount: adjustedAmount !== null ? parseFloat(adjustedAmount) : parseFloat(expectedAmount),
            income_pattern_id: isIncome ? occurrence.pattern_id : null,
            expense_pattern_id: !isIncome ? occurrence.pattern_id : null,
            confirmed: true,
            archived: false
        };

        const { data, error } = await supabase
            .from('movements')
            .insert([movement])
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error confirming pattern occurrence:', error);
        throw error;
    }
}

/**
 * Actualiza un movement existente
 */
export async function updateMovement(id, updates) {
    try {
        const movement = {};
        
        if (updates.title !== undefined) movement.title = updates.title;
        if (updates.description !== undefined) movement.description = updates.description;
        if (updates.category !== undefined) movement.category = updates.category;
        if (updates.date !== undefined) movement.date = updates.date;
        if (updates.expected_amount !== undefined) {
            movement.expected_amount = updates.expected_amount ? parseFloat(updates.expected_amount) : null;
        }
        if (updates.confirmed_amount !== undefined) {
            movement.confirmed_amount = parseFloat(updates.confirmed_amount);
        }
        if (updates.confirmed !== undefined) movement.confirmed = updates.confirmed;
        if (updates.archived !== undefined) movement.archived = updates.archived;
        if (updates.envelope_id !== undefined) movement.envelope_id = updates.envelope_id;

        const { data, error } = await supabase
            .from('movements')
            .update(movement)
            .eq('id', id)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating movement:', error);
        throw error;
    }
}

/**
 * Elimina un movement (soft delete: marca como archived)
 */
export async function deleteMovement(id, hard = false) {
    try {
        if (hard) {
            // Hard delete: eliminar permanentemente
            const { error } = await supabase
                .from('movements')
                .delete()
                .eq('id', id);

            if (error) throw error;
        } else {
            // Soft delete: marcar como archivado
            await updateMovement(id, { archived: true });
        }
        return true;
    } catch (error) {
        console.error('Error deleting movement:', error);
        throw error;
    }
}

// ============================================================================
// MOVEMENTS FOR DATE RANGE
// ============================================================================

/**
 * Obtiene movements para un rango de fechas específico
 */
export async function getMovementsForDateRange(startDate, endDate) {
    try {
        const { data, error } = await supabase
            .from('movements_with_patterns')
            .select('*')
            .gte('date', startDate)
            .lte('date', endDate)
            .eq('archived', false)
            .order('date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching movements for date range:', error);
        throw error;
    }
}

/**
 * Obtiene movements para un día específico
 */
export async function getMovementsForDate(date) {
    try {
        const { data, error } = await supabase
            .from('movements_with_patterns')
            .select('*')
            .eq('date', date)
            .eq('archived', false)
            .order('created_at', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching movements for date:', error);
        throw error;
    }
}

// ============================================================================
// MOVEMENTS BY ORIGIN
// ============================================================================

/**
 * Obtiene movements relacionados a un loan
 */
export async function getMovementsByLoan(loanId) {
    try {
        const { data, error } = await supabase
            .from('movements_with_patterns')
            .select('*')
            .eq('loan_id', loanId)
            .order('date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching movements by loan:', error);
        throw error;
    }
}

/**
 * Obtiene movements relacionados a un plan
 */
export async function getMovementsByPlan(planId) {
    try {
        const { data, error } = await supabase
            .from('movements_with_patterns')
            .select('*')
            .eq('plan_id', planId)
            .order('date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching movements by plan:', error);
        throw error;
    }
}

/**
 * Obtiene movements relacionados a un income_pattern
 */
export async function getMovementsByIncomePattern(patternId) {
    try {
        const { data, error } = await supabase
            .from('movements_with_patterns')
            .select('*')
            .eq('income_pattern_id', patternId)
            .order('date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching movements by income pattern:', error);
        throw error;
    }
}

/**
 * Obtiene movements relacionados a un expense_pattern
 */
export async function getMovementsByExpensePattern(patternId) {
    try {
        const { data, error } = await supabase
            .from('movements_with_patterns')
            .select('*')
            .eq('expense_pattern_id', patternId)
            .order('date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching movements by expense pattern:', error);
        throw error;
    }
}

// ============================================================================
// STATISTICS
// ============================================================================

/**
 * Calcula totales de ingresos y gastos para un rango de fechas
 */
export async function getMovementTotals(startDate, endDate) {
    try {
        const movements = await getMovementsForDateRange(startDate, endDate);
        
        const totals = movements.reduce((acc, mov) => {
            if (mov.type === 'ingreso') {
                acc.income += parseFloat(mov.confirmed_amount);
            } else if (mov.type === 'gasto') {
                acc.expense += parseFloat(mov.confirmed_amount);
            }
            return acc;
        }, { income: 0, expense: 0 });

        totals.balance = totals.income - totals.expense;
        return totals;
    } catch (error) {
        console.error('Error calculating movement totals:', error);
        throw error;
    }
}

/**
 * Obtiene totales por categoría
 */
export async function getTotalsByCategory(startDate, endDate, type = null) {
    try {
        let movements = await getMovementsForDateRange(startDate, endDate);
        
        if (type) {
            movements = movements.filter(m => m.type === type);
        }

        const byCategory = movements.reduce((acc, mov) => {
            const cat = mov.category || 'Sin categoría';
            if (!acc[cat]) {
                acc[cat] = { category: cat, total: 0, count: 0 };
            }
            acc[cat].total += parseFloat(mov.confirmed_amount);
            acc[cat].count += 1;
            return acc;
        }, {});

        return Object.values(byCategory).sort((a, b) => b.total - a.total);
    } catch (error) {
        console.error('Error calculating totals by category:', error);
        throw error;
    }
}

// ============================================================================
// VALIDACIÓN
// ============================================================================

/**
 * Valida los datos de un movement
 */
function validateMovementData(data) {
    const requiredFields = ['title', 'type', 'date', 'confirmed_amount'];
    
    for (const field of requiredFields) {
        if (!data[field]) {
            throw new Error(`Campo requerido: ${field}`);
        }
    }

    const validTypes = ['ingreso', 'gasto', 'ajuste'];
    if (!validTypes.includes(data.type)) {
        throw new Error(`Tipo inválido. Debe ser: ${validTypes.join(', ')}`);
    }

    if (parseFloat(data.confirmed_amount) < 0) {
        throw new Error('El monto confirmado debe ser mayor o igual a 0');
    }

    if (data.expected_amount !== undefined && data.expected_amount !== null) {
        if (parseFloat(data.expected_amount) < 0) {
            throw new Error('El monto esperado debe ser mayor o igual a 0');
        }
    }

    // Validar que solo haya un origen
    const origins = [
        data.income_pattern_id,
        data.expense_pattern_id,
        data.loan_id,
        data.plan_id
    ].filter(Boolean);

    if (origins.length > 1) {
        throw new Error('Un movement solo puede tener un origen (pattern, loan o plan)');
    }
}
