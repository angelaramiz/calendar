/**
 * patterns.js
 * Gestión de patrones de ingresos y gastos recurrentes (V2)
 */

import { supabase } from './supabase-client.js';

// ============================================================================
// INCOME PATTERNS
// ============================================================================

/**
 * Obtiene todos los income_patterns del usuario
 */
export async function getIncomePatterns(activeOnly = false) {
    try {
        let query = supabase
            .from('income_patterns')
            .select('*')
            .order('name', { ascending: true });

        if (activeOnly) {
            query = query.eq('active', true);
        }

        const { data, error } = await query;
        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching income patterns:', error);
        throw error;
    }
}

/**
 * Obtiene un income_pattern por ID
 */
export async function getIncomePatternById(id) {
    try {
        const { data, error } = await supabase
            .from('income_patterns')
            .select('*')
            .eq('id', id)
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error fetching income pattern:', error);
        throw error;
    }
}

/**
 * Crea un nuevo income_pattern
 */
export async function createIncomePattern(patternData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos
        validatePatternData(patternData);

        const pattern = {
            user_id: user.id,
            name: patternData.name,
            description: patternData.description || null,
            category: patternData.category || null,
            base_amount: parseFloat(patternData.base_amount),
            frequency: patternData.frequency,
            interval: parseInt(patternData.interval) || 1,
            start_date: patternData.start_date,
            end_date: patternData.end_date || null,
            occurrence_limit: patternData.occurrence_limit ? parseInt(patternData.occurrence_limit) : null,
            active: patternData.active !== false
        };

        const { data, error } = await supabase
            .from('income_patterns')
            .insert([pattern])
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error creating income pattern:', error);
        throw error;
    }
}

/**
 * Actualiza un income_pattern existente
 */
export async function updateIncomePattern(id, updates) {
    try {
        // Validar datos si se actualizan campos críticos
        if (updates.base_amount || updates.frequency || updates.interval) {
            validatePatternData(updates, true);
        }

        const pattern = {};
        if (updates.name !== undefined) pattern.name = updates.name;
        if (updates.description !== undefined) pattern.description = updates.description;
        if (updates.category !== undefined) pattern.category = updates.category;
        if (updates.base_amount !== undefined) pattern.base_amount = parseFloat(updates.base_amount);
        if (updates.frequency !== undefined) pattern.frequency = updates.frequency;
        if (updates.interval !== undefined) pattern.interval = parseInt(updates.interval);
        if (updates.start_date !== undefined) pattern.start_date = updates.start_date;
        if (updates.end_date !== undefined) pattern.end_date = updates.end_date;
        if (updates.occurrence_limit !== undefined) {
            pattern.occurrence_limit = updates.occurrence_limit ? parseInt(updates.occurrence_limit) : null;
        }
        if (updates.active !== undefined) pattern.active = updates.active;

        const { data, error } = await supabase
            .from('income_patterns')
            .update(pattern)
            .eq('id', id)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating income pattern:', error);
        throw error;
    }
}

/**
 * Elimina un income_pattern (soft delete: marca como inactive)
 */
export async function deleteIncomePattern(id, hard = false) {
    try {
        if (hard) {
            // Hard delete: eliminar permanentemente
            const { error } = await supabase
                .from('income_patterns')
                .delete()
                .eq('id', id);

            if (error) throw error;
        } else {
            // Soft delete: marcar como inactivo
            await updateIncomePattern(id, { active: false });
        }
        return true;
    } catch (error) {
        console.error('Error deleting income pattern:', error);
        throw error;
    }
}

// ============================================================================
// EXPENSE PATTERNS
// ============================================================================

/**
 * Obtiene todos los expense_patterns del usuario
 */
export async function getExpensePatterns(activeOnly = false) {
    try {
        let query = supabase
            .from('expense_patterns')
            .select('*')
            .order('name', { ascending: true });

        if (activeOnly) {
            query = query.eq('active', true);
        }

        const { data, error } = await query;
        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching expense patterns:', error);
        throw error;
    }
}

/**
 * Obtiene un expense_pattern por ID
 */
export async function getExpensePatternById(id) {
    try {
        const { data, error } = await supabase
            .from('expense_patterns')
            .select('*')
            .eq('id', id)
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error fetching expense pattern:', error);
        throw error;
    }
}

/**
 * Crea un nuevo expense_pattern
 */
export async function createExpensePattern(patternData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos
        validatePatternData(patternData);

        const pattern = {
            user_id: user.id,
            name: patternData.name,
            description: patternData.description || null,
            category: patternData.category || null,
            base_amount: parseFloat(patternData.base_amount),
            frequency: patternData.frequency,
            interval: parseInt(patternData.interval) || 1,
            start_date: patternData.start_date,
            end_date: patternData.end_date || null,
            occurrence_limit: patternData.occurrence_limit ? parseInt(patternData.occurrence_limit) : null,
            active: patternData.active !== false
        };

        const { data, error } = await supabase
            .from('expense_patterns')
            .insert([pattern])
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error creating expense pattern:', error);
        throw error;
    }
}

/**
 * Actualiza un expense_pattern existente
 */
export async function updateExpensePattern(id, updates) {
    try {
        // Validar datos si se actualizan campos críticos
        if (updates.base_amount || updates.frequency || updates.interval) {
            validatePatternData(updates, true);
        }

        const pattern = {};
        if (updates.name !== undefined) pattern.name = updates.name;
        if (updates.description !== undefined) pattern.description = updates.description;
        if (updates.category !== undefined) pattern.category = updates.category;
        if (updates.base_amount !== undefined) pattern.base_amount = parseFloat(updates.base_amount);
        if (updates.frequency !== undefined) pattern.frequency = updates.frequency;
        if (updates.interval !== undefined) pattern.interval = parseInt(updates.interval);
        if (updates.start_date !== undefined) pattern.start_date = updates.start_date;
        if (updates.end_date !== undefined) pattern.end_date = updates.end_date;
        if (updates.occurrence_limit !== undefined) {
            pattern.occurrence_limit = updates.occurrence_limit ? parseInt(updates.occurrence_limit) : null;
        }
        if (updates.active !== undefined) pattern.active = updates.active;

        const { data, error } = await supabase
            .from('expense_patterns')
            .update(pattern)
            .eq('id', id)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating expense pattern:', error);
        throw error;
    }
}

/**
 * Elimina un expense_pattern (soft delete: marca como inactive)
 */
export async function deleteExpensePattern(id, hard = false) {
    try {
        if (hard) {
            // Hard delete: eliminar permanentemente
            const { error } = await supabase
                .from('expense_patterns')
                .delete()
                .eq('id', id);

            if (error) throw error;
        } else {
            // Soft delete: marcar como inactivo
            await updateExpensePattern(id, { active: false });
        }
        return true;
    } catch (error) {
        console.error('Error deleting expense pattern:', error);
        throw error;
    }
}

// ============================================================================
// VALIDACIÓN
// ============================================================================

/**
 * Valida los datos de un patrón
 */
function validatePatternData(data, isUpdate = false) {
    const requiredFields = isUpdate ? [] : ['name', 'base_amount', 'frequency', 'start_date'];
    
    for (const field of requiredFields) {
        if (!data[field]) {
            throw new Error(`Campo requerido: ${field}`);
        }
    }

    if (data.base_amount !== undefined && parseFloat(data.base_amount) < 0) {
        throw new Error('El monto base debe ser mayor o igual a 0');
    }

    if (data.frequency !== undefined) {
        const validFrequencies = ['daily', 'weekly', 'monthly', 'yearly'];
        if (!validFrequencies.includes(data.frequency)) {
            throw new Error(`Frecuencia inválida. Debe ser: ${validFrequencies.join(', ')}`);
        }
    }

    if (data.interval !== undefined && parseInt(data.interval) < 1) {
        throw new Error('El intervalo debe ser mayor a 0');
    }

    if (data.occurrence_limit !== undefined && data.occurrence_limit !== null) {
        const limit = parseInt(data.occurrence_limit);
        if (limit < 1) {
            throw new Error('El límite de ocurrencias debe ser mayor a 0');
        }
    }

    if (data.start_date && data.end_date) {
        const start = new Date(data.start_date);
        const end = new Date(data.end_date);
        if (end < start) {
            throw new Error('La fecha de fin no puede ser anterior a la fecha de inicio');
        }
    }
}

// ============================================================================
// UTILIDADES
// ============================================================================

/**
 * Obtiene categorías únicas de income_patterns
 */
export async function getIncomeCategories() {
    try {
        const { data, error } = await supabase
            .from('income_patterns')
            .select('category')
            .not('category', 'is', null)
            .order('category');

        if (error) throw error;
        
        const categories = [...new Set(data.map(item => item.category))];
        return categories;
    } catch (error) {
        console.error('Error fetching income categories:', error);
        return [];
    }
}

/**
 * Obtiene categorías únicas de expense_patterns
 */
export async function getExpenseCategories() {
    try {
        const { data, error } = await supabase
            .from('expense_patterns')
            .select('category')
            .not('category', 'is', null)
            .order('category');

        if (error) throw error;
        
        const categories = [...new Set(data.map(item => item.category))];
        return categories;
    } catch (error) {
        console.error('Error fetching expense categories:', error);
        return [];
    }
}
