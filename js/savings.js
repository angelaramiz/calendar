/**
 * savings.js
 * Gestión de patrones de ahorro y transacciones de ahorro
 */

import { supabase } from './supabase-client.js';

// ============================================================================
// SAVINGS PATTERNS CRUD
// ============================================================================

/**
 * Obtiene todos los savings_patterns del usuario
 */
export async function getSavingsPatterns(activeOnly = false) {
    try {
        let query = supabase
            .from('savings_patterns')
            .select('*')
            .order('priority', { ascending: false })
            .order('name', { ascending: true });

        if (activeOnly) {
            query = query.eq('active', true);
        }

        const { data, error } = await query;
        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching savings patterns:', error);
        throw error;
    }
}

/**
 * Obtiene un savings_pattern por ID con sus income sources
 */
export async function getSavingsPatternById(id) {
    try {
        const { data: pattern, error: patternError } = await supabase
            .from('savings_patterns')
            .select('*')
            .eq('id', id)
            .single();

        if (patternError) throw patternError;

        // Obtener income sources
        const { data: sources, error: sourcesError } = await supabase
            .from('savings_pattern_income_sources')
            .select(`
                *,
                income_pattern:income_patterns(*)
            `)
            .eq('savings_pattern_id', id);

        if (sourcesError) throw sourcesError;

        pattern.income_sources = sources || [];
        return pattern;
    } catch (error) {
        console.error('Error fetching savings pattern:', error);
        throw error;
    }
}

/**
 * Obtiene todos los savings_patterns con sus income sources
 */
export async function getSavingsPatternsWithSources(activeOnly = false) {
    try {
        let query = supabase
            .from('savings_patterns')
            .select('*')
            .order('priority', { ascending: false })
            .order('name', { ascending: true });

        if (activeOnly) {
            query = query.eq('active', true);
        }

        const { data: patterns, error: patternsError } = await query;
        if (patternsError) throw patternsError;

        if (!patterns || patterns.length === 0) return [];

        // Obtener todas las income sources
        const patternIds = patterns.map(p => p.id);
        const { data: allSources, error: sourcesError } = await supabase
            .from('savings_pattern_income_sources')
            .select(`
                *,
                income_pattern:income_patterns(*)
            `)
            .in('savings_pattern_id', patternIds);

        if (sourcesError) throw sourcesError;

        // Agrupar sources por savings_pattern_id
        const sourcesByPattern = {};
        for (const source of (allSources || [])) {
            if (!sourcesByPattern[source.savings_pattern_id]) {
                sourcesByPattern[source.savings_pattern_id] = [];
            }
            sourcesByPattern[source.savings_pattern_id].push(source);
        }

        // Asignar sources a cada pattern
        for (const pattern of patterns) {
            pattern.income_sources = sourcesByPattern[pattern.id] || [];
        }

        return patterns;
    } catch (error) {
        console.error('Error fetching savings patterns with sources:', error);
        throw error;
    }
}

/**
 * Crea un nuevo savings_pattern
 */
export async function createSavingsPattern(patternData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos
        validateSavingsPatternData(patternData);

        const pattern = {
            user_id: user.id,
            name: patternData.name,
            description: patternData.description || null,
            allocation_type: patternData.allocation_type,
            allocation_value: patternData.allocation_type !== 'remainder' 
                ? parseFloat(patternData.allocation_value) 
                : null,
            target_amount: patternData.target_amount ? parseFloat(patternData.target_amount) : null,
            current_balance: 0,
            priority: patternData.priority || 5,
            active: patternData.active !== false,
            // Campos de programación
            frequency: patternData.frequency || null,
            interval_value: patternData.interval_value || 1,
            day_of_week: patternData.day_of_week !== undefined ? patternData.day_of_week : null,
            day_of_month: patternData.day_of_month !== undefined ? patternData.day_of_month : null,
            start_date: patternData.start_date || null,
            end_date: patternData.end_date || null
        };

        const { data: createdPattern, error: patternError } = await supabase
            .from('savings_patterns')
            .insert([pattern])
            .select()
            .single();

        if (patternError) throw patternError;

        // Agregar income sources si se proporcionaron
        if (patternData.income_sources && patternData.income_sources.length > 0) {
            await assignIncomeSourcestoSavingsPattern(createdPattern.id, patternData.income_sources);
        }

        return await getSavingsPatternById(createdPattern.id);
    } catch (error) {
        console.error('Error creating savings pattern:', error);
        throw error;
    }
}

/**
 * Actualiza un savings_pattern existente
 */
export async function updateSavingsPattern(id, updates) {
    try {
        const pattern = {};

        if (updates.name !== undefined) pattern.name = updates.name;
        if (updates.description !== undefined) pattern.description = updates.description;
        if (updates.allocation_type !== undefined) {
            pattern.allocation_type = updates.allocation_type;
            if (updates.allocation_type === 'remainder') {
                pattern.allocation_value = null;
            }
        }
        if (updates.allocation_value !== undefined && updates.allocation_type !== 'remainder') {
            pattern.allocation_value = parseFloat(updates.allocation_value);
        }
        if (updates.target_amount !== undefined) {
            pattern.target_amount = updates.target_amount ? parseFloat(updates.target_amount) : null;
        }
        if (updates.priority !== undefined) pattern.priority = updates.priority;
        if (updates.active !== undefined) pattern.active = updates.active;
        
        // Campos de programación
        if (updates.frequency !== undefined) pattern.frequency = updates.frequency;
        if (updates.interval_value !== undefined) pattern.interval_value = updates.interval_value;
        if (updates.day_of_week !== undefined) pattern.day_of_week = updates.day_of_week;
        if (updates.day_of_month !== undefined) pattern.day_of_month = updates.day_of_month;
        if (updates.start_date !== undefined) pattern.start_date = updates.start_date;
        if (updates.end_date !== undefined) pattern.end_date = updates.end_date;

        const { data, error } = await supabase
            .from('savings_patterns')
            .update(pattern)
            .eq('id', id)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating savings pattern:', error);
        throw error;
    }
}

/**
 * Elimina un savings_pattern (soft delete)
 */
export async function deleteSavingsPattern(id, hard = false) {
    try {
        if (hard) {
            const { error } = await supabase
                .from('savings_patterns')
                .delete()
                .eq('id', id);

            if (error) throw error;
        } else {
            await updateSavingsPattern(id, { active: false });
        }
        return true;
    } catch (error) {
        console.error('Error deleting savings pattern:', error);
        throw error;
    }
}

// ============================================================================
// SAVINGS PATTERN INCOME SOURCES
// ============================================================================

/**
 * Asigna income sources a un savings_pattern
 */
export async function assignIncomeSourcestoSavingsPattern(savingsPatternId, incomePatternIds) {
    try {
        const sources = incomePatternIds.map(incomeId => ({
            savings_pattern_id: savingsPatternId,
            income_pattern_id: typeof incomeId === 'object' ? incomeId.income_pattern_id : incomeId
        }));

        const { data, error } = await supabase
            .from('savings_pattern_income_sources')
            .insert(sources)
            .select();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error assigning income sources to savings pattern:', error);
        throw error;
    }
}

/**
 * Reemplaza todas las income sources de un savings_pattern
 */
export async function replaceSavingsPatternIncomeSources(savingsPatternId, incomePatternIds) {
    try {
        // Eliminar sources existentes
        await supabase
            .from('savings_pattern_income_sources')
            .delete()
            .eq('savings_pattern_id', savingsPatternId);

        // Agregar nuevas sources
        if (incomePatternIds && incomePatternIds.length > 0) {
            await assignIncomeSourcestoSavingsPattern(savingsPatternId, incomePatternIds);
        }

        return true;
    } catch (error) {
        console.error('Error replacing savings pattern income sources:', error);
        throw error;
    }
}

// ============================================================================
// SAVINGS TRANSACTIONS
// ============================================================================

/**
 * Obtiene las transacciones de un savings_pattern
 */
export async function getSavingsTransactions(savingsPatternId, limit = 50) {
    try {
        const { data, error } = await supabase
            .from('savings_transactions')
            .select('*')
            .eq('savings_pattern_id', savingsPatternId)
            .order('transaction_date', { ascending: false })
            .order('created_at', { ascending: false })
            .limit(limit);

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching savings transactions:', error);
        throw error;
    }
}

/**
 * Crea un depósito manual en un savings_pattern
 */
export async function createSavingsDeposit(savingsPatternId, amount, notes = null, date = null) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        const transactionDate = date || new Date().toISOString().split('T')[0];
        const depositAmount = parseFloat(amount);

        if (depositAmount <= 0) {
            throw new Error('El monto del depósito debe ser mayor a 0');
        }

        // Obtener el patrón de ahorro
        const { data: pattern, error: patternError } = await supabase
            .from('savings_patterns')
            .select('*')
            .eq('id', savingsPatternId)
            .single();

        if (patternError) throw patternError;

        // Crear movement de tipo 'gasto' (ahorro abstracto)
        const { data: movement, error: movementError } = await supabase
            .from('movements')
            .insert([{
                user_id: user.id,
                title: `Ahorro: ${pattern.name}`,
                description: notes || 'Depósito manual de ahorro',
                type: 'gasto',
                category: 'Ahorro',
                date: transactionDate,
                expected_amount: depositAmount,
                confirmed_amount: depositAmount,
                confirmed: true,
                archived: false
            }])
            .select()
            .single();

        if (movementError) throw movementError;

        // Registrar transacción de ahorro
        const { data: transaction, error: transactionError } = await supabase
            .from('savings_transactions')
            .insert([{
                user_id: user.id,
                savings_pattern_id: savingsPatternId,
                transaction_type: 'deposit',
                amount: depositAmount,
                movement_id: movement.id,
                transaction_date: transactionDate,
                notes: notes
            }])
            .select()
            .single();

        if (transactionError) throw transactionError;

        // Actualizar balance del patrón de ahorro
        const { error: updateError } = await supabase
            .from('savings_patterns')
            .update({ 
                current_balance: parseFloat(pattern.current_balance) + depositAmount 
            })
            .eq('id', savingsPatternId);

        if (updateError) throw updateError;

        return {
            transaction,
            movement,
            new_balance: parseFloat(pattern.current_balance) + depositAmount
        };
    } catch (error) {
        console.error('Error creating savings deposit:', error);
        throw error;
    }
}

/**
 * Crea un retiro de un savings_pattern
 * El retiro suma al balance disponible (crea un ingreso)
 */
export async function createSavingsWithdrawal(savingsPatternId, amount, notes = null, date = null) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        const transactionDate = date || new Date().toISOString().split('T')[0];
        const withdrawalAmount = parseFloat(amount);

        if (withdrawalAmount <= 0) {
            throw new Error('El monto del retiro debe ser mayor a 0');
        }

        // Obtener el patrón de ahorro
        const { data: pattern, error: patternError } = await supabase
            .from('savings_patterns')
            .select('*')
            .eq('id', savingsPatternId)
            .single();

        if (patternError) throw patternError;

        // Verificar que hay suficiente balance
        if (withdrawalAmount > parseFloat(pattern.current_balance)) {
            throw new Error(`Balance insuficiente. Disponible: $${pattern.current_balance}`);
        }

        // Crear movement de tipo 'ingreso' (retiro de ahorro)
        const { data: movement, error: movementError } = await supabase
            .from('movements')
            .insert([{
                user_id: user.id,
                title: `Retiro de ahorro: ${pattern.name}`,
                description: notes || 'Retiro de ahorro',
                type: 'ingreso',
                category: 'Retiro de Ahorro',
                date: transactionDate,
                expected_amount: withdrawalAmount,
                confirmed_amount: withdrawalAmount,
                confirmed: true,
                archived: false
            }])
            .select()
            .single();

        if (movementError) throw movementError;

        // Registrar transacción de retiro
        const { data: transaction, error: transactionError } = await supabase
            .from('savings_transactions')
            .insert([{
                user_id: user.id,
                savings_pattern_id: savingsPatternId,
                transaction_type: 'withdrawal',
                amount: withdrawalAmount,
                movement_id: movement.id,
                transaction_date: transactionDate,
                notes: notes
            }])
            .select()
            .single();

        if (transactionError) throw transactionError;

        // Actualizar balance del patrón de ahorro
        const newBalance = parseFloat(pattern.current_balance) - withdrawalAmount;
        const { error: updateError } = await supabase
            .from('savings_patterns')
            .update({ current_balance: newBalance })
            .eq('id', savingsPatternId);

        if (updateError) throw updateError;

        return {
            transaction,
            movement,
            new_balance: newBalance
        };
    } catch (error) {
        console.error('Error creating savings withdrawal:', error);
        throw error;
    }
}

/**
 * Obtiene el resumen de todos los ahorros del usuario
 */
export async function getSavingsSummary() {
    try {
        const patterns = await getSavingsPatterns(true);
        
        let totalSaved = 0;
        let totalTarget = 0;
        const patternsSummary = [];

        for (const pattern of patterns) {
            const currentBalance = parseFloat(pattern.current_balance) || 0;
            const targetAmount = parseFloat(pattern.target_amount) || 0;
            
            totalSaved += currentBalance;
            if (targetAmount > 0) {
                totalTarget += targetAmount;
            }

            patternsSummary.push({
                id: pattern.id,
                name: pattern.name,
                current_balance: currentBalance,
                target_amount: targetAmount,
                progress_percent: targetAmount > 0 
                    ? Math.min((currentBalance / targetAmount) * 100, 100) 
                    : null,
                allocation_type: pattern.allocation_type,
                priority: pattern.priority
            });
        }

        return {
            total_saved: totalSaved,
            total_target: totalTarget,
            overall_progress: totalTarget > 0 
                ? Math.min((totalSaved / totalTarget) * 100, 100) 
                : null,
            patterns_count: patterns.length,
            patterns: patternsSummary
        };
    } catch (error) {
        console.error('Error getting savings summary:', error);
        throw error;
    }
}

// ============================================================================
// VALIDACIÓN
// ============================================================================

function validateSavingsPatternData(data) {
    if (!data.name || data.name.trim() === '') {
        throw new Error('El nombre es requerido');
    }

    const validTypes = ['percent', 'fixed', 'remainder'];
    if (!validTypes.includes(data.allocation_type)) {
        throw new Error(`Tipo de asignación inválido. Debe ser: ${validTypes.join(', ')}`);
    }

    if (data.allocation_type === 'percent') {
        const value = parseFloat(data.allocation_value);
        if (isNaN(value) || value <= 0 || value > 1) {
            throw new Error('El porcentaje debe estar entre 0 y 1 (ej: 0.10 para 10%)');
        }
    }

    if (data.allocation_type === 'fixed') {
        const value = parseFloat(data.allocation_value);
        if (isNaN(value) || value <= 0) {
            throw new Error('El monto fijo debe ser mayor a 0');
        }
    }

    if (data.priority !== undefined) {
        const priority = parseInt(data.priority);
        if (isNaN(priority) || priority < 1 || priority > 10) {
            throw new Error('La prioridad debe estar entre 1 y 10');
        }
    }

    if (data.target_amount !== undefined && data.target_amount !== null) {
        const target = parseFloat(data.target_amount);
        if (isNaN(target) || target < 0) {
            throw new Error('La meta de ahorro debe ser mayor o igual a 0');
        }
    }
}

// ============================================================================
// FUNCIONES DE AUTOMATIZACIÓN DE AHORRO
// ============================================================================

/**
 * Obtiene los patrones de ahorro vinculados a un income_pattern específico
 */
export async function getSavingsLinkedToIncome(incomePatternId) {
    try {
        const { data, error } = await supabase
            .from('savings_pattern_income_sources')
            .select(`
                *,
                savings_pattern:savings_patterns(*)
            `)
            .eq('income_pattern_id', incomePatternId);

        if (error) throw error;
        
        // Filtrar solo los patrones de ahorro activos
        return (data || [])
            .filter(s => s.savings_pattern?.active)
            .map(s => ({
                ...s.savings_pattern,
                allocation_from_income: {
                    type: s.allocation_type,
                    value: s.allocation_value
                }
            }));
    } catch (error) {
        console.error('Error fetching savings linked to income:', error);
        throw error;
    }
}

/**
 * Calcula el monto a depositar en ahorro según el tipo de asignación
 */
export function calculateSavingsAmount(savingsPattern, incomeAmount, expensesFromIncome = 0) {
    const allocation = savingsPattern.allocation_from_income || {
        type: savingsPattern.allocation_type,
        value: savingsPattern.allocation_value
    };
    
    const incomeValue = parseFloat(incomeAmount) || 0;
    const expenses = parseFloat(expensesFromIncome) || 0;
    
    switch (allocation.type) {
        case 'percent':
            // Porcentaje del ingreso
            return incomeValue * parseFloat(allocation.value);
        
        case 'fixed':
            // Monto fijo
            return parseFloat(allocation.value);
        
        case 'remainder':
            // El sobrante después de gastos
            const remainder = incomeValue - expenses;
            return Math.max(0, remainder);
        
        default:
            return 0;
    }
}

/**
 * Obtiene los gastos anclados a un income_pattern y sus montos
 */
export async function getExpensesLinkedToIncome(incomePatternId) {
    try {
        const { data, error } = await supabase
            .from('expense_pattern_income_sources')
            .select(`
                *,
                expense_pattern:expense_patterns(*)
            `)
            .eq('income_pattern_id', incomePatternId);

        if (error) throw error;
        
        return (data || [])
            .filter(e => e.expense_pattern?.active)
            .map(e => ({
                ...e.expense_pattern,
                allocation_from_income: {
                    type: e.allocation_type,
                    value: e.allocation_value
                }
            }));
    } catch (error) {
        console.error('Error fetching expenses linked to income:', error);
        throw error;
    }
}

/**
 * Calcula el total de gastos asignados a un ingreso
 */
export function calculateTotalExpensesFromIncome(expenses, incomeAmount) {
    const incomeValue = parseFloat(incomeAmount) || 0;
    let total = 0;
    
    for (const expense of expenses) {
        const allocation = expense.allocation_from_income;
        if (!allocation) continue;
        
        if (allocation.type === 'percent') {
            total += incomeValue * parseFloat(allocation.value);
        } else if (allocation.type === 'fixed') {
            total += parseFloat(allocation.value);
        }
    }
    
    return total;
}

/**
 * Genera sugerencias de ahorro después de confirmar un ingreso
 */
export async function getSavingsSuggestionsForIncome(incomePatternId, confirmedAmount) {
    try {
        const [linkedSavings, linkedExpenses] = await Promise.all([
            getSavingsLinkedToIncome(incomePatternId),
            getExpensesLinkedToIncome(incomePatternId)
        ]);
        
        if (linkedSavings.length === 0) {
            return { hasSuggestions: false, savings: [], totalSuggested: 0 };
        }
        
        const totalExpenses = calculateTotalExpensesFromIncome(linkedExpenses, confirmedAmount);
        const suggestions = [];
        
        for (const savings of linkedSavings) {
            const amount = calculateSavingsAmount(savings, confirmedAmount, totalExpenses);
            if (amount > 0) {
                suggestions.push({
                    savings_pattern_id: savings.id,
                    name: savings.name,
                    suggested_amount: amount,
                    allocation_type: savings.allocation_from_income?.type || savings.allocation_type,
                    current_balance: parseFloat(savings.current_balance) || 0,
                    target_amount: savings.target_amount ? parseFloat(savings.target_amount) : null
                });
            }
        }
        
        return {
            hasSuggestions: suggestions.length > 0,
            savings: suggestions,
            totalSuggested: suggestions.reduce((sum, s) => sum + s.suggested_amount, 0),
            remainingAfterExpenses: confirmedAmount - totalExpenses
        };
    } catch (error) {
        console.error('Error getting savings suggestions:', error);
        throw error;
    }
}

/**
 * Calcula el sobrante después de confirmar un gasto
 * para sugerir apartarlo al ahorro
 */
export async function getRemainderSavingsSuggestion(incomePatternId, expenseAmount, confirmedExpenseAmount) {
    try {
        const linkedSavings = await getSavingsLinkedToIncome(incomePatternId);
        
        // Buscar patrones de ahorro tipo "remainder"
        const remainderSavings = linkedSavings.filter(
            s => (s.allocation_from_income?.type === 'remainder' || s.allocation_type === 'remainder')
        );
        
        if (remainderSavings.length === 0) {
            return { hasSuggestion: false };
        }
        
        // Si el gasto confirmado fue menor al esperado, hay sobrante
        const expectedExpense = parseFloat(expenseAmount) || 0;
        const actualExpense = parseFloat(confirmedExpenseAmount) || 0;
        const surplus = expectedExpense - actualExpense;
        
        if (surplus <= 0) {
            return { hasSuggestion: false, surplus: 0 };
        }
        
        // Distribuir el sobrante entre los ahorros tipo remainder por prioridad
        remainderSavings.sort((a, b) => (b.priority || 1) - (a.priority || 1));
        
        return {
            hasSuggestion: true,
            surplus: surplus,
            savings: remainderSavings.map(s => ({
                savings_pattern_id: s.id,
                name: s.name,
                suggested_amount: surplus, // Cada uno puede recibir el sobrante completo
                current_balance: parseFloat(s.current_balance) || 0
            }))
        };
    } catch (error) {
        console.error('Error getting remainder savings suggestion:', error);
        throw error;
    }
}
