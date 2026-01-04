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
export async function getIncomePatterns(userId = null, activeOnly = false) {
    try {
        // Si no se proporciona userId, obtenerlo del usuario autenticado
        if (!userId) {
            const { data: { user } } = await supabase.auth.getUser();
            if (user) {
                userId = user.id;
            }
        }

        let query = supabase
            .from('income_patterns')
            .select('*')
            .order('name', { ascending: true });

        // Filtrar por usuario si se tiene el userId
        if (userId) {
            query = query.eq('user_id', userId);
        }

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
            day_of_week: patternData.day_of_week !== undefined ? parseInt(patternData.day_of_week) : null,
            day_of_month: patternData.day_of_month !== undefined ? parseInt(patternData.day_of_month) : null,
            start_date: patternData.start_date,
            end_date: patternData.end_date || null,
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
        if (updates.day_of_week !== undefined) pattern.day_of_week = updates.day_of_week !== null ? parseInt(updates.day_of_week) : null;
        if (updates.day_of_month !== undefined) pattern.day_of_month = updates.day_of_month !== null ? parseInt(updates.day_of_month) : null;
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
export async function getExpensePatterns(userId = null, activeOnly = false) {
    try {
        console.log('[PATTERNS] getExpensePatterns START', { userId, activeOnly });
        
        // Si no se proporciona userId, obtenerlo del usuario autenticado
        if (!userId) {
            const { data: { user } } = await supabase.auth.getUser();
            if (user) {
                userId = user.id;
                console.log('[PATTERNS] Got userId from auth:', userId);
            } else {
                console.warn('[PATTERNS] No user authenticated');
            }
        }

        let query = supabase
            .from('expense_patterns')
            .select('*')
            .order('name', { ascending: true });

        // Filtrar por usuario si se tiene el userId
        if (userId) {
            query = query.eq('user_id', userId);
            console.log('[PATTERNS] Added user_id filter:', userId);
        }

        if (activeOnly) {
            query = query.eq('active', true);
            console.log('[PATTERNS] Added active filter');
        }

        console.log('[PATTERNS] Executing query...');
        const { data, error } = await query;
        
        if (error) {
            console.error('[PATTERNS] Supabase error:', error);
            throw error;
        }
        
        console.log('[PATTERNS] getExpensePatterns SUCCESS, found:', data?.length || 0, data);
        return data || [];
    } catch (error) {
        console.error('[PATTERNS] getExpensePatterns ERROR:', error);
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
            day_of_week: patternData.day_of_week !== undefined ? parseInt(patternData.day_of_week) : null,
            day_of_month: patternData.day_of_month !== undefined ? parseInt(patternData.day_of_month) : null,
            start_date: patternData.start_date,
            end_date: patternData.end_date || null,
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
        if (updates.day_of_week !== undefined) pattern.day_of_week = updates.day_of_week !== null ? parseInt(updates.day_of_week) : null;
        if (updates.day_of_month !== undefined) pattern.day_of_month = updates.day_of_month !== null ? parseInt(updates.day_of_month) : null;
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
// EXPENSE PATTERN INCOME SOURCES
// ============================================================================

/**
 * Obtiene un expense_pattern por ID con sus income sources
 */
export async function getExpensePatternWithSources(id) {
    try {
        const { data: pattern, error: patternError } = await supabase
            .from('expense_patterns')
            .select('*')
            .eq('id', id)
            .single();

        if (patternError) throw patternError;

        // Obtener income sources asignadas
        const { data: sources, error: sourcesError } = await supabase
            .from('expense_pattern_income_sources')
            .select(`
                *,
                income_pattern:income_patterns(*)
            `)
            .eq('expense_pattern_id', id);

        if (sourcesError) throw sourcesError;

        pattern.income_sources = sources || [];
        return pattern;
    } catch (error) {
        console.error('Error fetching expense pattern with sources:', error);
        throw error;
    }
}

/**
 * Obtiene todos los expense_patterns con sus income sources
 */
export async function getExpensePatternsWithSources(activeOnly = false) {
    try {
        let query = supabase
            .from('expense_patterns')
            .select('*')
            .order('name', { ascending: true });

        if (activeOnly) {
            query = query.eq('active', true);
        }

        const { data: patterns, error: patternsError } = await query;
        if (patternsError) throw patternsError;

        // Obtener todas las income sources de una vez
        const patternIds = patterns.map(p => p.id);
        const { data: allSources, error: sourcesError } = await supabase
            .from('expense_pattern_income_sources')
            .select(`
                *,
                income_pattern:income_patterns(*)
            `)
            .in('expense_pattern_id', patternIds);

        if (sourcesError) throw sourcesError;

        // Agrupar sources por expense_pattern_id
        const sourcesByPattern = {};
        for (const source of (allSources || [])) {
            if (!sourcesByPattern[source.expense_pattern_id]) {
                sourcesByPattern[source.expense_pattern_id] = [];
            }
            sourcesByPattern[source.expense_pattern_id].push(source);
        }

        // Asignar sources a cada pattern
        for (const pattern of patterns) {
            pattern.income_sources = sourcesByPattern[pattern.id] || [];
        }

        return patterns;
    } catch (error) {
        console.error('Error fetching expense patterns with sources:', error);
        throw error;
    }
}

/**
 * Asigna income sources a un expense_pattern
 */
export async function assignIncomeSourcesToExpensePattern(expensePatternId, sources) {
    try {
        // Validar sources
        validateIncomeSources(sources);

        // Preparar datos
        const incomeSources = sources.map(source => ({
            expense_pattern_id: expensePatternId,
            income_pattern_id: source.income_pattern_id,
            allocation_type: source.allocation_type,
            allocation_value: parseFloat(source.allocation_value),
            notes: source.notes || null
        }));

        const { data, error } = await supabase
            .from('expense_pattern_income_sources')
            .insert(incomeSources)
            .select();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error assigning income sources to expense pattern:', error);
        throw error;
    }
}

/**
 * Actualiza una income source de un expense_pattern
 */
export async function updateExpensePatternIncomeSource(sourceId, updates) {
    try {
        const source = {};

        if (updates.allocation_type !== undefined) source.allocation_type = updates.allocation_type;
        if (updates.allocation_value !== undefined) source.allocation_value = parseFloat(updates.allocation_value);
        if (updates.notes !== undefined) source.notes = updates.notes;

        const { data, error } = await supabase
            .from('expense_pattern_income_sources')
            .update(source)
            .eq('id', sourceId)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating expense pattern income source:', error);
        throw error;
    }
}

/**
 * Elimina una income source de un expense_pattern
 */
export async function removeExpensePatternIncomeSource(sourceId) {
    try {
        const { error } = await supabase
            .from('expense_pattern_income_sources')
            .delete()
            .eq('id', sourceId);

        if (error) throw error;
        return true;
    } catch (error) {
        console.error('Error removing expense pattern income source:', error);
        throw error;
    }
}

/**
 * Reemplaza todas las income sources de un expense_pattern
 */
export async function replaceExpensePatternIncomeSources(expensePatternId, newSources) {
    try {
        // 1. Eliminar sources existentes
        await supabase
            .from('expense_pattern_income_sources')
            .delete()
            .eq('expense_pattern_id', expensePatternId);

        // 2. Agregar nuevas sources
        if (newSources && newSources.length > 0) {
            await assignIncomeSourcesToExpensePattern(expensePatternId, newSources);
        }

        return true;
    } catch (error) {
        console.error('Error replacing expense pattern income sources:', error);
        throw error;
    }
}

/**
 * Obtiene los income sources de un expense_pattern
 */
export async function getExpensePatternIncomeSources(expensePatternId) {
    try {
        const { data, error } = await supabase
            .from('expense_pattern_income_sources')
            .select(`
                *,
                income_pattern:income_patterns(*)
            `)
            .eq('expense_pattern_id', expensePatternId);

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching expense pattern income sources:', error);
        throw error;
    }
}

/**
 * Calcula el monto cubierto mensual de un expense_pattern
 * basándose en las income sources asignadas
 */
export async function calculateExpensePatternCoverage(expensePatternId) {
    try {
        const pattern = await getExpensePatternWithSources(expensePatternId);
        if (!pattern || !pattern.income_sources || pattern.income_sources.length === 0) {
            return {
                expense_amount: parseFloat(pattern?.base_amount || 0),
                covered_amount: 0,
                coverage_percent: 0,
                sources_breakdown: []
            };
        }

        let totalCovered = 0;
        const sourcesBreakdown = [];

        for (const source of pattern.income_sources) {
            const incomePattern = source.income_pattern;
            if (!incomePattern || !incomePattern.active) continue;

            // Convertir frecuencia a monto mensual
            let monthlyOccurrences = 0;
            
            if (incomePattern.frequency === 'daily') {
                monthlyOccurrences = 30 / (incomePattern.interval || 1);
            } else if (incomePattern.frequency === 'weekly') {
                monthlyOccurrences = 4 / (incomePattern.interval || 1);
            } else if (incomePattern.frequency === 'monthly') {
                monthlyOccurrences = 1 / (incomePattern.interval || 1);
            } else if (incomePattern.frequency === 'yearly') {
                monthlyOccurrences = (1 / 12) / (incomePattern.interval || 1);
            }

            // Calcular contribución mensual de este income pattern
            let contribution = 0;
            if (source.allocation_type === 'percent') {
                contribution = parseFloat(incomePattern.base_amount) * parseFloat(source.allocation_value) * monthlyOccurrences;
            } else if (source.allocation_type === 'fixed') {
                contribution = parseFloat(source.allocation_value) * monthlyOccurrences;
            }

            totalCovered += contribution;
            sourcesBreakdown.push({
                income_pattern_id: incomePattern.id,
                income_pattern_name: incomePattern.name,
                allocation_type: source.allocation_type,
                allocation_value: source.allocation_value,
                monthly_contribution: contribution
            });
        }

        // Calcular monto mensual del gasto
        let expenseMonthlyAmount = 0;
        if (pattern.frequency === 'daily') {
            expenseMonthlyAmount = parseFloat(pattern.base_amount) * (30 / (pattern.interval || 1));
        } else if (pattern.frequency === 'weekly') {
            expenseMonthlyAmount = parseFloat(pattern.base_amount) * (4 / (pattern.interval || 1));
        } else if (pattern.frequency === 'monthly') {
            expenseMonthlyAmount = parseFloat(pattern.base_amount) / (pattern.interval || 1);
        } else if (pattern.frequency === 'yearly') {
            expenseMonthlyAmount = parseFloat(pattern.base_amount) / (12 * (pattern.interval || 1));
        }

        const coveragePercent = expenseMonthlyAmount > 0 
            ? Math.min((totalCovered / expenseMonthlyAmount) * 100, 100)
            : 0;

        return {
            expense_amount: expenseMonthlyAmount,
            covered_amount: totalCovered,
            coverage_percent: Math.round(coveragePercent * 100) / 100,
            sources_breakdown: sourcesBreakdown
        };
    } catch (error) {
        console.error('Error calculating expense pattern coverage:', error);
        throw error;
    }
}

// ============================================================================
// VALIDACIÓN
// ============================================================================

/**
 * Valida y normaliza las income sources para expense_patterns
 * Si allocation_type es 'percent' y el valor es > 1, lo convierte automáticamente a decimal
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

        let value = parseFloat(source.allocation_value);
        if (value <= 0) {
            throw new Error('allocation_value debe ser mayor a 0');
        }

        // Auto-convertir porcentaje a decimal si es necesario
        if (source.allocation_type === 'percent' && value > 1) {
            console.warn(`allocation_value (${value}) parece estar en formato porcentaje, convirtiendo a decimal`);
            source.allocation_value = value / 100;
        }
    }
}

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
        // V2: Frecuencias válidas son weekly, biweekly, monthly, yearly
        const validFrequencies = ['weekly', 'biweekly', 'monthly', 'yearly'];
        if (!validFrequencies.includes(data.frequency)) {
            throw new Error(`Frecuencia inválida. Debe ser: ${validFrequencies.join(', ')}`);
        }
    }

    if (data.interval !== undefined && parseInt(data.interval) < 1) {
        throw new Error('El intervalo debe ser mayor a 0');
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
