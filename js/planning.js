/**
 * Planning Module - Gestión de Metas, Apartados, Gastos Planificados y Eventos Especiales
 * Sincroniza con Supabase V2 (envelopes, plans, expense_patterns)
 * NOTA: goals y planned_expenses migrados a plans y expense_patterns
 */

import { supabase } from './supabase-client.js';

// ==================== LOGGING ====================

const DEBUG = true; // Cambiar a false en producción

function logInfo(module, action, data) {
  if (DEBUG) {
    console.log(`[PLANNING:${module}] ${action}`, data);
  }
}

function logError(module, action, error, context = {}) {
  console.error(`[PLANNING:${module}] ERROR in ${action}:`, {
    error: error.message || error,
    code: error.code,
    details: error.details,
    hint: error.hint,
    context
  });
}

function logWarning(module, action, message, data = {}) {
  console.warn(`[PLANNING:${module}] WARNING in ${action}: ${message}`, data);
}

// ==================== ENVELOPES (APARTADOS) ====================

/**
 * Obtener todos los envelopes del usuario
 */
export async function getEnvelopes(userId) {
  try {
    if (!userId) {
      logWarning('ENVELOPES', 'getEnvelopes', 'userId is required');
      return [];
    }

    logInfo('ENVELOPES', 'getEnvelopes', { userId });

    const { data, error } = await supabase
      .from('envelopes')
      .select('*')
      .eq('user_id', userId)
      .order('created_at', { ascending: false });

    if (error) {
      logError('ENVELOPES', 'getEnvelopes', error, { userId });
      return [];
    }

    logInfo('ENVELOPES', 'getEnvelopes', `Found ${data?.length || 0} envelopes`);
    return data || [];
  } catch (error) {
    logError('ENVELOPES', 'getEnvelopes', error, { userId });
    return [];
  }
}

/**
 * Crear nuevo envelope
 */
export async function createEnvelope(userId, envelopeData) {
  try {
    if (!userId) {
      throw new Error('userId es requerido');
    }

    if (!envelopeData.name || envelopeData.name.trim() === '') {
      throw new Error('El nombre del apartado es requerido');
    }

    if (envelopeData.target_amount && envelopeData.target_amount < 0) {
      throw new Error('El monto objetivo no puede ser negativo');
    }

    logInfo('ENVELOPES', 'createEnvelope', { userId, name: envelopeData.name });

    const insertData = {
      user_id: userId,
      name: envelopeData.name.trim(),
      type: envelopeData.type || 'savings',
      target_amount: envelopeData.target_amount || null,
      current_balance: 0,
      color: envelopeData.color || '#6366f1',
      emoji: envelopeData.icon || envelopeData.emoji || '💰',
      is_active: true
    };

    const { data, error } = await supabase
      .from('envelopes')
      .insert(insertData)
      .select()
      .single();

    if (error) {
      logError('ENVELOPES', 'createEnvelope', error, insertData);
      throw new Error(`Error al crear apartado: ${error.message}`);
    }

    logInfo('ENVELOPES', 'createEnvelope', `Created envelope ${data.id}`);
    return data;
  } catch (error) {
    logError('ENVELOPES', 'createEnvelope', error, { userId, envelopeData });
    throw error;
  }
}

/**
 * Actualizar envelope
 */
export async function updateEnvelope(envelopeId, updates) {
  try {
    if (!envelopeId) {
      throw new Error('envelopeId es requerido');
    }

    if (updates.name !== undefined && updates.name.trim() === '') {
      throw new Error('El nombre no puede estar vacío');
    }

    if (updates.target_amount !== undefined && updates.target_amount < 0) {
      throw new Error('El monto objetivo no puede ser negativo');
    }

    logInfo('ENVELOPES', 'updateEnvelope', { envelopeId, updates });

    // Limpiar campos
    const cleanUpdates = { ...updates };
    if (cleanUpdates.name) cleanUpdates.name = cleanUpdates.name.trim();
    if (cleanUpdates.description) cleanUpdates.description = cleanUpdates.description.trim();

    const { data, error } = await supabase
      .from('envelopes')
      .update(cleanUpdates)
      .eq('id', envelopeId)
      .select()
      .single();

    if (error) {
      logError('ENVELOPES', 'updateEnvelope', error, { envelopeId, updates });
      throw new Error(`Error al actualizar apartado: ${error.message}`);
    }

    logInfo('ENVELOPES', 'updateEnvelope', `Updated envelope ${envelopeId}`);
    return data;
  } catch (error) {
    logError('ENVELOPES', 'updateEnvelope', error, { envelopeId, updates });
    throw error;
  }
}

/**
 * Eliminar envelope (soft delete)
 */
export async function deleteEnvelope(envelopeId) {
  try {
    if (!envelopeId) {
      throw new Error('envelopeId es requerido');
    }

    logInfo('ENVELOPES', 'deleteEnvelope', { envelopeId });

    const { error } = await supabase
      .from('envelopes')
      .update({ is_active: false })
      .eq('id', envelopeId);

    if (error) {
      logError('ENVELOPES', 'deleteEnvelope', error, { envelopeId });
      throw new Error(`Error al eliminar apartado: ${error.message}`);
    }

    logInfo('ENVELOPES', 'deleteEnvelope', `Deleted envelope ${envelopeId}`);
  } catch (error) {
    logError('ENVELOPES', 'deleteEnvelope', error, { envelopeId });
    throw error;
  }
}

/**
 * Agregar transacción a envelope
 */
export async function addEnvelopeTransaction(userId, transactionData) {
  try {
    if (!userId) {
      throw new Error('userId es requerido');
    }

    if (!transactionData.envelope_id) {
      throw new Error('envelope_id es requerido');
    }

    if (!transactionData.amount || transactionData.amount <= 0) {
      throw new Error('El monto debe ser mayor a 0');
    }

    if (!transactionData.transaction_type) {
      throw new Error('transaction_type es requerido');
    }

    logInfo('ENVELOPES', 'addEnvelopeTransaction', { userId, transactionData });

    // Mapear tipo de transacción al formato de BD
    const kindMap = {
      'deposit': 'fund',
      'withdrawal': 'withdraw'
    };
    
    const kind = kindMap[transactionData.transaction_type] || transactionData.transaction_type;

    if (!['fund', 'withdraw', 'transfer_in', 'transfer_out', 'adjustment'].includes(kind)) {
      throw new Error(`Tipo de transacción inválido: ${kind}`);
    }

    // Verificar que hay suficiente balance para retiros
    if (kind === 'withdraw') {
      const { data: envelope } = await supabase
        .from('envelopes')
        .select('current_balance')
        .eq('id', transactionData.envelope_id)
        .single();

      if (!envelope) {
        throw new Error('Apartado no encontrado');
      }

      if (envelope.current_balance < transactionData.amount) {
        throw new Error(`Balance insuficiente. Disponible: ${envelope.current_balance}`);
      }
    }
    
    const insertData = {
      user_id: userId,
      envelope_id: transactionData.envelope_id,
      amount: transactionData.amount,
      kind: kind,
      date: transactionData.date || new Date().toISOString().split('T')[0],
      notes: transactionData.description?.trim() || null,
      related_event_id: transactionData.related_event_id || null
    };

    const { data, error } = await supabase
      .from('envelope_transactions')
      .insert(insertData)
      .select()
      .single();

    if (error) {
      logError('ENVELOPES', 'addEnvelopeTransaction', error, insertData);
      throw new Error(`Error al agregar transacción: ${error.message}`);
    }

    // Actualizar balance del envelope
    await updateEnvelopeBalance(transactionData.envelope_id);

    logInfo('ENVELOPES', 'addEnvelopeTransaction', `Created transaction ${data.id}`);
    return data;
  } catch (error) {
    logError('ENVELOPES', 'addEnvelopeTransaction', error, { userId, transactionData });
    throw error;
  }
}

/**
 * Actualizar balance de envelope basado en transacciones
 */
async function updateEnvelopeBalance(envelopeId) {
  try {
    logInfo('ENVELOPES', 'updateEnvelopeBalance', { envelopeId });

    const { data: transactions, error: txError } = await supabase
      .from('envelope_transactions')
      .select('amount, kind')
      .eq('envelope_id', envelopeId);

    if (txError) {
      logError('ENVELOPES', 'updateEnvelopeBalance', txError, { envelopeId });
      throw txError;
    }

    const balance = transactions?.reduce((sum, t) => {
      return t.kind === 'fund' || t.kind === 'transfer_in'
        ? sum + parseFloat(t.amount)
        : sum - parseFloat(t.amount);
    }, 0) || 0;

    const { error: updateError } = await supabase
      .from('envelopes')
      .update({ current_balance: balance })
      .eq('id', envelopeId);

    if (updateError) {
      logError('ENVELOPES', 'updateEnvelopeBalance', updateError, { envelopeId });
      throw updateError;
    }

    logInfo('ENVELOPES', 'updateEnvelopeBalance', { envelopeId, newBalance: balance });
  } catch (error) {
    logError('ENVELOPES', 'updateEnvelopeBalance', error, { envelopeId });
    throw error;
  }
}

/**
 * Obtener transacciones de un envelope
 */
export async function getEnvelopeTransactions(envelopeId) {
  try {
    if (!envelopeId) {
      logWarning('ENVELOPES', 'getEnvelopeTransactions', 'envelopeId is required');
      return [];
    }

    logInfo('ENVELOPES', 'getEnvelopeTransactions', { envelopeId });

    const { data, error } = await supabase
      .from('envelope_transactions')
      .select('*')
      .eq('envelope_id', envelopeId)
      .order('date', { ascending: false });

    if (error) {
      logError('ENVELOPES', 'getEnvelopeTransactions', error, { envelopeId });
      return [];
    }

    logInfo('ENVELOPES', 'getEnvelopeTransactions', `Found ${data?.length || 0} transactions`);
    return data || [];
  } catch (error) {
    logError('ENVELOPES', 'getEnvelopeTransactions', error, { envelopeId });
    return [];
  }
}

// ==================== GOALS (METAS) ====================

/**
 * Obtener todas las metas del usuario
 */
export async function getGoals(userId) {
  try {
    if (!userId) {
      logWarning('GOALS', 'getGoals', 'userId is required');
      return [];
    }

    logInfo('GOALS', 'getGoals', { userId });

    const { data, error } = await supabase
      .from('plans')
      .select('*')
      .eq('user_id', userId)
      .order('created_at', { ascending: false });

    if (error) {
      logError('GOALS', 'getGoals', error, { userId });
      return [];
    }

    logInfo('GOALS', 'getGoals', `Found ${data?.length || 0} goals`);
    return data || [];
  } catch (error) {
    logError('GOALS', 'getGoals', error, { userId });
    return [];
  }
}

/**
 * Crear nueva meta
 */
export async function createGoal(userId, goalData) {
  try {
    if (!userId) {
      throw new Error('userId es requerido');
    }

    const title = (goalData.name || goalData.title || '').trim();
    if (!title) {
      throw new Error('El título de la meta es requerido');
    }

    if (!goalData.target_amount || goalData.target_amount <= 0) {
      throw new Error('El monto objetivo debe ser mayor a 0');
    }

    const priority = parseInt(goalData.priority);
    if (isNaN(priority) || priority < 1 || priority > 5) {
      throw new Error('La prioridad debe ser un número entre 1 y 5');
    }

    logInfo('GOALS', 'createGoal', { userId, title });

    const insertData = {
      user_id: userId,
      title: title,
      description: goalData.description?.trim() || null,
      target_amount: parseFloat(goalData.target_amount),
      saved_amount: 0,
      target_date: goalData.target_date || goalData.due_date || null,
      priority: priority,
      status: 'active'
    };

    const { data, error } = await supabase
      .from('plans')
      .insert(insertData)
      .select()
      .single();

    if (error) {
      logError('GOALS', 'createGoal', error, insertData);
      throw new Error(`Error al crear meta: ${error.message}`);
    }

    logInfo('GOALS', 'createGoal', `Created goal ${data.id}`);
    return data;
  } catch (error) {
    logError('GOALS', 'createGoal', error, { userId, goalData });
    throw error;
  }
}

/**
 * Actualizar meta
 */
export async function updateGoal(goalId, updates) {
  try {
    if (!goalId) {
      throw new Error('goalId es requerido');
    }

    logInfo('GOALS', 'updateGoal', { goalId, updates });

    // Validar campos
    const cleanUpdates = { ...updates };
    
    if (cleanUpdates.title !== undefined) {
      cleanUpdates.title = cleanUpdates.title.trim();
      if (!cleanUpdates.title) {
        throw new Error('El título no puede estar vacío');
      }
    }

    if (cleanUpdates.target_amount !== undefined && cleanUpdates.target_amount <= 0) {
      throw new Error('El monto objetivo debe ser mayor a 0');
    }

    if (cleanUpdates.priority !== undefined) {
      const priority = parseInt(cleanUpdates.priority);
      if (isNaN(priority) || priority < 1 || priority > 5) {
        throw new Error('La prioridad debe ser un número entre 1 y 5');
      }
      cleanUpdates.priority = priority;
    }

    if (cleanUpdates.description !== undefined) {
      cleanUpdates.description = cleanUpdates.description.trim() || null;
    }

    const { data, error } = await supabase
      .from('plans')
      .update(cleanUpdates)
      .eq('id', goalId)
      .select()
      .single();

    if (error) {
      logError('GOALS', 'updateGoal', error, { goalId, updates });
      throw new Error(`Error al actualizar meta: ${error.message}`);
    }

    logInfo('GOALS', 'updateGoal', `Updated goal ${goalId}`);
    return data;
  } catch (error) {
    logError('GOALS', 'updateGoal', error, { goalId, updates });
    throw error;
  }
}

/**
 * Eliminar meta (soft delete)
 */
export async function deleteGoal(goalId) {
  try {
    if (!goalId) {
      throw new Error('goalId es requerido');
    }

    logInfo('GOALS', 'deleteGoal', { goalId });

    const { error } = await supabase
      .from('plans')
      .update({ status: 'cancelled' })
      .eq('id', goalId);

    if (error) {
      logError('GOALS', 'deleteGoal', error, { goalId });
      throw new Error(`Error al eliminar meta: ${error.message}`);
    }

    logInfo('GOALS', 'deleteGoal', `Deleted goal ${goalId}`);
  } catch (error) {
    logError('GOALS', 'deleteGoal', error, { goalId });
    throw error;
  }
}

/**
 * Agregar fondeo a meta
 */
export async function addGoalFunding(userId, fundingData) {
  try {
    if (!userId) {
      throw new Error('userId es requerido');
    }

    if (!fundingData.goal_id) {
      throw new Error('goal_id es requerido');
    }

    if (!fundingData.amount || fundingData.amount <= 0) {
      throw new Error('El monto debe ser mayor a 0');
    }

    logInfo('GOALS', 'addGoalFunding', { userId, goalId: fundingData.goal_id, amount: fundingData.amount });

    const insertData = {
      user_id: userId,
      goal_id: fundingData.goal_id,
      amount: parseFloat(fundingData.amount),
      date: fundingData.funding_date || new Date().toISOString().split('T')[0],
      source: fundingData.source || 'manual',
      notes: fundingData.notes?.trim() || null,
      envelope_id: fundingData.envelope_id || null,
      related_event_id: fundingData.related_event_id || null
    };

    const { data, error } = await supabase
      .from('goal_funding')
      .insert(insertData)
      .select()
      .single();

    if (error) {
      logError('GOALS', 'addGoalFunding', error, insertData);
      throw new Error(`Error al agregar fondeo: ${error.message}`);
    }

    // Actualizar saved_amount de la meta
    await updateGoalProgress(fundingData.goal_id);

    logInfo('GOALS', 'addGoalFunding', `Created funding ${data.id}`);
    return data;
  } catch (error) {
    logError('GOALS', 'addGoalFunding', error, { userId, fundingData });
    throw error;
  }
}

/**
 * Actualizar progreso de meta basado en fondeos
 */
async function updateGoalProgress(goalId) {
  try {
    logInfo('GOALS', 'updateGoalProgress', { goalId });

    const { data: fundings, error: fundError } = await supabase
      .from('goal_funding')
      .select('amount')
      .eq('goal_id', goalId);

    if (fundError) {
      logError('GOALS', 'updateGoalProgress', fundError, { goalId });
      throw fundError;
    }

    const savedAmount = fundings?.reduce((sum, f) => sum + parseFloat(f.amount), 0) || 0;

    const { error: updateError } = await supabase
      .from('plans')
      .update({ saved_amount: savedAmount })
      .eq('id', goalId);

    if (updateError) {
      logError('GOALS', 'updateGoalProgress', updateError, { goalId });
      throw updateError;
    }

    logInfo('GOALS', 'updateGoalProgress', { goalId, savedAmount });
  } catch (error) {
    logError('GOALS', 'updateGoalProgress', error, { goalId });
    throw error;
  }
}

/**
 * Obtener fondeos de una meta
 */
export async function getGoalFundings(goalId) {
  try {
    if (!goalId) {
      logWarning('GOALS', 'getGoalFundings', 'goalId is required');
      return [];
    }

    logInfo('GOALS', 'getGoalFundings', { goalId });

    const { data, error } = await supabase
      .from('goal_funding')
      .select('*')
      .eq('goal_id', goalId)
      .order('date', { ascending: false });

    if (error) {
      logError('GOALS', 'getGoalFundings', error, { goalId });
      return [];
    }

    logInfo('GOALS', 'getGoalFundings', `Found ${data?.length || 0} fundings`);
    return data || [];
  } catch (error) {
    logError('GOALS', 'getGoalFundings', error, { goalId });
    return [];
  }
}

// ==================== PLANNED EXPENSES (GASTOS PLANIFICADOS) ====================

/**
 * Obtener gastos planificados del usuario
 * DESHABILITADO: planned_expenses no existe en V2
 * En V2, usar expense_patterns para gastos recurrentes
 */
export async function getPlannedExpenses(userId) {
  console.warn('getPlannedExpenses está deshabilitado - no existe planned_expenses en V2');
  return [];
  
  // try {
  //   if (!userId) {
  //     logWarning('EXPENSES', 'getPlannedExpenses', 'userId is required');
  //     return [];
  //   }

  //   logInfo('EXPENSES', 'getPlannedExpenses', { userId });

  //   const { data, error } = await supabase
  //     .from('planned_expenses')
  //     .select('*')
  //     .eq('user_id', userId)
  //     .order('planned_date', { ascending: true });

  //   if (error) {
  //     logError('EXPENSES', 'getPlannedExpenses', error, { userId });
  //     return [];
  //   }

  //   logInfo('EXPENSES', 'getPlannedExpenses', `Found ${data?.length || 0} expenses`);
  //   return data || [];
  // } catch (error) {
  //   logError('EXPENSES', 'getPlannedExpenses', error, { userId });
  //   return [];
  // }
}

/**
 * Crear gasto planificado
 */
export async function createPlannedExpense(userId, expenseData) {
  try {
    if (!userId) {
      throw new Error('userId es requerido');
    }

    const title = (expenseData.title || '').trim();
    if (!title) {
      throw new Error('El título del gasto es requerido');
    }

    const amount = parseFloat(expenseData.estimated_amount || expenseData.amount);
    if (!amount || amount <= 0) {
      throw new Error('El monto debe ser mayor a 0');
    }

    const plannedDate = expenseData.due_date || expenseData.planned_date;
    if (!plannedDate) {
      throw new Error('La fecha planificada es requerida');
    }

    // Priority puede venir como string (low, medium, high, critical) o número (1-5)
    let priorityNum = 3; // default medium
    if (typeof expenseData.priority === 'number') {
      priorityNum = expenseData.priority;
    } else if (typeof expenseData.priority === 'string') {
      const priorityMap = { low: 2, medium: 3, high: 4, critical: 5 };
      priorityNum = priorityMap[expenseData.priority] || 3;
    }

    logInfo('EXPENSES', 'createPlannedExpense', { userId, title });

    const insertData = {
      user_id: userId,
      title: title,
      description: expenseData.description?.trim() || null,
      amount: amount,
      planned_date: plannedDate,
      category: expenseData.category?.trim() || null,
      priority: priorityNum,
      status: expenseData.status || 'planned',
      envelope_id: expenseData.envelope_id || null, // Solo envelope_id según schema
      frequency: expenseData.frequency || 'once',
      auto_create_event: expenseData.auto_create_event !== false
    };

    const { data, error } = await supabase
      .from('expense_patterns')
      .insert(insertData)
      .select()
      .single();
    // NOTA: Convertido a expense_patterns (V2)

    if (error) {
      logError('EXPENSES', 'createPlannedExpense', error, insertData);
      throw new Error(`Error al crear gasto planificado: ${error.message}`);
    }

    logInfo('EXPENSES', 'createPlannedExpense', `Created expense ${data.id}`);
    return data;
  } catch (error) {
    logError('EXPENSES', 'createPlannedExpense', error, { userId, expenseData });
    throw error;
  }
}

/**
 * Actualizar gasto planificado
 */
export async function updatePlannedExpense(expenseId, updates) {
  try {
    if (!expenseId) {
      throw new Error('expenseId es requerido');
    }

    logInfo('EXPENSES', 'updatePlannedExpense', { expenseId, updates });

    // Validar campos
    const cleanUpdates = { ...updates };

    if (cleanUpdates.title !== undefined) {
      cleanUpdates.title = (cleanUpdates.title || '').trim();
      if (!cleanUpdates.title) {
        throw new Error('El título no puede estar vacío');
      }
    }

    if (cleanUpdates.amount !== undefined && cleanUpdates.amount <= 0) {
      throw new Error('El monto debe ser mayor a 0');
    }

    if (cleanUpdates.priority !== undefined) {
      // Priority puede venir como string (low, medium, high, critical) o número (1-5)
      let priorityNum;
      if (typeof cleanUpdates.priority === 'number') {
        priorityNum = cleanUpdates.priority;
      } else if (typeof cleanUpdates.priority === 'string') {
        const priorityMap = { low: 2, medium: 3, high: 4, critical: 5 };
        priorityNum = priorityMap[cleanUpdates.priority] || 3;
      } else {
        priorityNum = parseInt(cleanUpdates.priority);
      }
      
      if (isNaN(priorityNum) || priorityNum < 1 || priorityNum > 5) {
        throw new Error('La prioridad debe ser un número entre 1 y 5');
      }
      cleanUpdates.priority = priorityNum;
    }

    if (cleanUpdates.description !== undefined) {
      cleanUpdates.description = cleanUpdates.description ? cleanUpdates.description.trim() : null;
    }

    const { data, error } = await supabase
      .from('expense_patterns')
      .update(cleanUpdates)
      .eq('id', expenseId)
      .select()
      .single();
    // NOTA: Convertido a expense_patterns (V2)

    if (error) {
      logError('EXPENSES', 'updatePlannedExpense', error, { expenseId, updates });
      throw new Error(`Error al actualizar gasto planificado: ${error.message}`);
    }

    logInfo('EXPENSES', 'updatePlannedExpense', `Updated expense ${expenseId}`);
    return data;
  } catch (error) {
    logError('EXPENSES', 'updatePlannedExpense', error, { expenseId, updates });
    throw error;
  }
}

/**
 * Marcar gasto planificado como completado
 */
export async function completePlannedExpense(expenseId, actualAmount = null) {
  try {
    if (!expenseId) {
      throw new Error('expenseId es requerido');
    }

    logInfo('EXPENSES', 'completePlannedExpense', { expenseId, actualAmount });

    const updates = {
      status: 'done'
    };

    const result = await updatePlannedExpense(expenseId, updates);
    
    logInfo('EXPENSES', 'completePlannedExpense', `Completed expense ${expenseId}`);
    return result;
  } catch (error) {
    logError('EXPENSES', 'completePlannedExpense', error, { expenseId, actualAmount });
    throw error;
  }
}

/**
 * Eliminar gasto planificado
 */
export async function deletePlannedExpense(expenseId) {
  try {
    if (!expenseId) {
      throw new Error('expenseId es requerido');
    }

    logInfo('EXPENSES', 'deletePlannedExpense', { expenseId });

    const { error } = await supabase
      .from('expense_patterns')
      .update({ status: 'cancelled' })
      .eq('id', expenseId);
    // NOTA: Convertido a expense_patterns (V2)

    if (error) {
      logError('EXPENSES', 'deletePlannedExpense', error, { expenseId });
      throw new Error(`Error al eliminar gasto planificado: ${error.message}`);
    }

    logInfo('EXPENSES', 'deletePlannedExpense', `Deleted expense ${expenseId}`);
  } catch (error) {
    logError('EXPENSES', 'deletePlannedExpense', error, { expenseId });
    throw error;
  }
}

// ==================== HELPER FUNCTIONS ====================

/**
 * Obtener resumen de planeación para dashboard
 */
export async function getPlanningDashboard(userId) {
  const startTime = performance.now();
  
  try {
    if (!userId) {
      throw new Error('userId es requerido para el dashboard');
    }

    logInfo('DASHBOARD', 'getPlanningDashboard', { userId });

    const [goals, envelopes, plannedExpenses] = await Promise.all([
      getGoals(userId),
      getEnvelopes(userId),
      getPlannedExpenses(userId)
    ]);

    const activeGoals = goals.filter(g => g.status === 'active');
    const activeEnvelopes = envelopes.filter(e => e.is_active);
    const pendingExpenses = plannedExpenses.filter(e => e.status === 'planned' || e.status === 'scheduled');

    const dashboard = {
      goals: {
        total: activeGoals.length,
        completed: activeGoals.filter(g => (g.saved_amount || 0) >= g.target_amount).length,
        totalTarget: activeGoals.reduce((sum, g) => sum + (parseFloat(g.target_amount) || 0), 0),
        totalCurrent: activeGoals.reduce((sum, g) => sum + (parseFloat(g.saved_amount) || 0), 0),
        items: activeGoals
      },
      envelopes: {
        total: activeEnvelopes.length,
        totalBalance: activeEnvelopes.reduce((sum, e) => sum + (parseFloat(e.current_balance) || 0), 0),
        totalTarget: activeEnvelopes.reduce((sum, e) => sum + (parseFloat(e.target_amount) || 0), 0),
        items: activeEnvelopes
      },
      plannedExpenses: {
        total: pendingExpenses.length,
        totalEstimated: pendingExpenses.reduce((sum, e) => sum + (parseFloat(e.amount) || 0), 0),
        thisMonth: pendingExpenses.filter(e => {
          if (!e.planned_date) return false;
          const expDate = new Date(e.planned_date);
          const now = new Date();
          return expDate.getMonth() === now.getMonth() && expDate.getFullYear() === now.getFullYear();
        }).length,
        items: pendingExpenses
      }
    };

    const endTime = performance.now();
    logInfo('DASHBOARD', 'getPlanningDashboard', `Loaded in ${(endTime - startTime).toFixed(2)}ms`);

    return dashboard;
  } catch (error) {
    logError('DASHBOARD', 'getPlanningDashboard', error, { userId });
    
    // Retornar estructura vacía en caso de error
    return {
      goals: {
        total: 0,
        completed: 0,
        totalTarget: 0,
        totalCurrent: 0,
        items: []
      },
      envelopes: {
        total: 0,
        totalBalance: 0,
        totalTarget: 0,
        items: []
      },
      plannedExpenses: {
        total: 0,
        totalEstimated: 0,
        thisMonth: 0,
        items: []
      }
    };
  }
}

/**
 * Vincular evento real con gasto planificado
 */
export async function linkEventToPlannedExpense(eventId, plannedExpenseId) {
  // Actualizar evento para incluir referencia
  const { error: eventError } = await supabase
    .from('events')
    .update({ planned_expense_id: plannedExpenseId })
    .eq('id', eventId);

  if (eventError) {
    console.error('Error vinculando evento:', eventError);
    throw eventError;
  }

  // Obtener monto del evento
  const { data: event } = await supabase
    .from('events')
    .select('amount')
    .eq('id', eventId)
    .single();

  // Marcar gasto planificado como completado con monto real
  await completePlannedExpense(plannedExpenseId, event?.amount);
}

/**
 * Calcular progreso de meta (porcentaje)
 */
export function calculateGoalProgress(goal) {
  if (!goal || !goal.target_amount) return 0;
  const savedAmount = goal.saved_amount || 0;
  const progress = (savedAmount / goal.target_amount) * 100;
  return Math.min(Math.round(progress), 100);
}

/**
 * Calcular progreso de envelope (porcentaje)
 */
export function calculateEnvelopeProgress(envelope) {
  if (!envelope || !envelope.target_amount) return 0;
  const progress = (envelope.current_balance / envelope.target_amount) * 100;
  return Math.min(Math.round(progress), 100);
}

/**
 * Obtener días hasta fecha objetivo
 */
export function getDaysUntilTarget(targetDate) {
  const now = new Date();
  const target = new Date(targetDate);
  const diffTime = target - now;
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  return diffDays;
}

/**
 * Formato de moneda
 */
export function formatCurrency(amount) {
  return new Intl.NumberFormat('es-MX', {
    style: 'currency',
    currency: 'MXN'
  }).format(amount);
}

// ==================== VINCULACIÓN CON INGRESOS ====================

/**
 * Obtener ingresos disponibles (eventos tipo income sin asignar)
 */
export async function getAvailableIncomes(userId, options = {}) {
  try {
    if (!userId) {
      logWarning('INCOMES', 'getAvailableIncomes', 'userId is required');
      return [];
    }

    logInfo('INCOMES', 'getAvailableIncomes', { userId });

    const { startDate, endDate, includeAssigned = false } = options;

    let query = supabase
      .from('events')
      .select('*')
      .eq('user_id', userId)
      .eq('type', 'ingreso')
      .order('date', { ascending: false });

    if (startDate) {
      query = query.gte('date', startDate);
    }

    if (endDate) {
      query = query.lte('date', endDate);
    }

    const { data, error } = await query;

    if (error) {
      logError('INCOMES', 'getAvailableIncomes', error, { userId });
      return [];
    }

    // Filtrar ingresos sin asignar si se requiere
    let incomes = data || [];
    if (!includeAssigned) {
      incomes = incomes.filter(income => !income.goal_id && !income.planned_expense_id && !income.envelope_id);
    }

    logInfo('INCOMES', 'getAvailableIncomes', `Found ${incomes.length} available incomes`);
    return incomes;
  } catch (error) {
    logError('INCOMES', 'getAvailableIncomes', error, { userId });
    return [];
  }
}

/**
 * Asignar ingreso a meta
 */
export async function assignIncomeToGoal(incomeEventId, goalId, amount = null) {
  try {
    if (!incomeEventId || !goalId) {
      throw new Error('incomeEventId y goalId son requeridos');
    }

    logInfo('INCOMES', 'assignIncomeToGoal', { incomeEventId, goalId, amount });

    // Obtener el evento de ingreso
    const { data: income, error: incomeError } = await supabase
      .from('events')
      .select('*')
      .eq('id', incomeEventId)
      .single();

    if (incomeError) {
      throw new Error(`Error al obtener ingreso: ${incomeError.message}`);
    }

    if (income.type !== 'ingreso') {
      throw new Error('El evento debe ser de tipo ingreso');
    }

    // Usar el monto del ingreso o el monto especificado
    const amountToAssign = amount || income.amount;

    // Actualizar el evento para vincularlo con la meta
    const { error: updateError } = await supabase
      .from('events')
      .update({ goal_id: goalId })
      .eq('id', incomeEventId);

    if (updateError) {
      throw new Error(`Error al vincular ingreso: ${updateError.message}`);
    }

    // Agregar fondeo a la meta
    await addGoalFunding(goalId, {
      date: income.date,
      amount: amountToAssign,
      source: 'income',
      related_event_id: incomeEventId,
      notes: `Fondeo desde ingreso: ${income.title}`
    });

    logInfo('INCOMES', 'assignIncomeToGoal', `Assigned income ${incomeEventId} to goal ${goalId}`);
    return true;
  } catch (error) {
    logError('INCOMES', 'assignIncomeToGoal', error, { incomeEventId, goalId });
    throw error;
  }
}

/**
 * Asignar ingreso a gasto planificado
 */
export async function assignIncomeToPlannedExpense(incomeEventId, expenseId, amount = null) {
  try {
    if (!incomeEventId || !expenseId) {
      throw new Error('incomeEventId y expenseId son requeridos');
    }

    logInfo('INCOMES', 'assignIncomeToPlannedExpense', { incomeEventId, expenseId, amount });

    // Obtener el evento de ingreso
    const { data: income, error: incomeError } = await supabase
      .from('events')
      .select('*')
      .eq('id', incomeEventId)
      .single();

    if (incomeError) {
      throw new Error(`Error al obtener ingreso: ${incomeError.message}`);
    }

    if (income.type !== 'ingreso') {
      throw new Error('El evento debe ser de tipo ingreso');
    }

    // Actualizar el evento para vincularlo con el gasto planificado
    const { error: updateError } = await supabase
      .from('events')
      .update({ planned_expense_id: expenseId })
      .eq('id', incomeEventId);

    if (updateError) {
      throw new Error(`Error al vincular ingreso: ${updateError.message}`);
    }

    logInfo('INCOMES', 'assignIncomeToPlannedExpense', `Assigned income ${incomeEventId} to expense ${expenseId}`);
    return true;
  } catch (error) {
    logError('INCOMES', 'assignIncomeToPlannedExpense', error, { incomeEventId, expenseId });
    throw error;
  }
}

/**
 * Asignar ingreso a apartado (envelope)
 */
export async function assignIncomeToEnvelope(incomeEventId, envelopeId, amount = null) {
  try {
    if (!incomeEventId || !envelopeId) {
      throw new Error('incomeEventId y envelopeId son requeridos');
    }

    logInfo('INCOMES', 'assignIncomeToEnvelope', { incomeEventId, envelopeId, amount });

    // Obtener el evento de ingreso
    const { data: income, error: incomeError } = await supabase
      .from('events')
      .select('*')
      .eq('id', incomeEventId)
      .single();

    if (incomeError) {
      throw new Error(`Error al obtener ingreso: ${incomeError.message}`);
    }

    if (income.type !== 'ingreso') {
      throw new Error('El evento debe ser de tipo ingreso');
    }

    // Usar el monto del ingreso o el monto especificado
    const amountToAssign = amount || income.amount;

    // Actualizar el evento para vincularlo con el apartado
    const { error: updateError } = await supabase
      .from('events')
      .update({ envelope_id: envelopeId })
      .eq('id', incomeEventId);

    if (updateError) {
      throw new Error(`Error al vincular ingreso: ${updateError.message}`);
    }

    // Agregar transacción al apartado
    await addEnvelopeTransaction(envelopeId, {
      kind: 'fund',
      amount: amountToAssign,
      date: income.date,
      notes: `Fondeo desde ingreso: ${income.title}`,
      related_event_id: incomeEventId
    });

    logInfo('INCOMES', 'assignIncomeToEnvelope', `Assigned income ${incomeEventId} to envelope ${envelopeId}`);
    return true;
  } catch (error) {
    logError('INCOMES', 'assignIncomeToEnvelope', error, { incomeEventId, envelopeId });
    throw error;
  }
}

/**
 * Obtener ingresos asignados a una meta
 */
export async function getGoalAssignedIncomes(goalId) {
  try {
    if (!goalId) {
      throw new Error('goalId es requerido');
    }

    const { data, error } = await supabase
      .from('events')
      .select('*')
      .eq('goal_id', goalId)
      .eq('type', 'ingreso')
      .order('date', { ascending: false });

    if (error) {
      // Error específico: columna no existe
      if (error.code === '42703') {
        logError('INCOMES', 'getGoalAssignedIncomes', 
          new Error('⚠️ MIGRACIÓN REQUERIDA: Ejecuta docs/migrations/05-income-linking.sql en Supabase'), 
          { goalId, originalError: error });
        throw new Error('Funcionalidad no disponible. Contacta al administrador para ejecutar la migración de base de datos.');
      }
      logError('INCOMES', 'getGoalAssignedIncomes', error, { goalId });
      return [];
    }

    return data || [];
  } catch (error) {
    logError('INCOMES', 'getGoalAssignedIncomes', error, { goalId });
    throw error; // Re-lanzar para que el modal lo maneje
  }
}

/**
 * Desvincular ingreso
 */
export async function unassignIncome(incomeEventId) {
  try {
    if (!incomeEventId) {
      throw new Error('incomeEventId es requerido');
    }

    logInfo('INCOMES', 'unassignIncome', { incomeEventId });

    const { error } = await supabase
      .from('events')
      .update({ 
        goal_id: null, 
        planned_expense_id: null, 
        envelope_id: null 
      })
      .eq('id', incomeEventId);

    if (error) {
      throw new Error(`Error al desvincular ingreso: ${error.message}`);
    }

    logInfo('INCOMES', 'unassignIncome', `Unassigned income ${incomeEventId}`);
    return true;
  } catch (error) {
    logError('INCOMES', 'unassignIncome', error, { incomeEventId });
    throw error;
  }
}

/**
 * Obtener ingresos asignados a un gasto planificado
 */
export async function getExpenseAssignedIncomes(expenseId) {
  try {
    if (!expenseId) {
      throw new Error('expenseId es requerido');
    }

    const { data, error } = await supabase
      .from('events')
      .select('*')
      .eq('planned_expense_id', expenseId)
      .eq('type', 'ingreso')
      .order('date', { ascending: false });

    if (error) {
      // Error específico: columna no existe
      if (error.code === '42703') {
        logError('INCOMES', 'getExpenseAssignedIncomes', 
          new Error('⚠️ MIGRACIÓN REQUERIDA: Ejecuta docs/migrations/05-income-linking.sql en Supabase'), 
          { expenseId, originalError: error });
        throw new Error('Funcionalidad no disponible. Contacta al administrador para ejecutar la migración de base de datos.');
      }
      logError('INCOMES', 'getExpenseAssignedIncomes', error, { expenseId });
      return [];
    }

    return data || [];
  } catch (error) {
    logError('INCOMES', 'getExpenseAssignedIncomes', error, { expenseId });
    throw error; // Re-lanzar para que el modal lo maneje
  }
}
