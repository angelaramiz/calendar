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
 * Crear nuevo envelope (V2)
 * Campos V2: name, description, category, budget_amount, current_amount, period_type, active
 */
export async function createEnvelope(userId, envelopeData) {
  try {
    if (!userId) {
      throw new Error('userId es requerido');
    }

    if (!envelopeData.name || envelopeData.name.trim() === '') {
      throw new Error('El nombre del apartado es requerido');
    }

    if (!envelopeData.budget_amount || envelopeData.budget_amount <= 0) {
      throw new Error('El monto presupuestado debe ser mayor a 0');
    }

    logInfo('ENVELOPES', 'createEnvelope', { userId, name: envelopeData.name });

    const insertData = {
      user_id: userId,
      name: envelopeData.name.trim(),
      description: envelopeData.description?.trim() || null,
      category: envelopeData.category || null,
      budget_amount: parseFloat(envelopeData.budget_amount),
      current_amount: parseFloat(envelopeData.current_amount) || 0,
      period_type: envelopeData.period_type || 'monthly',
      active: true
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
 * Actualizar envelope (V2)
 */
export async function updateEnvelope(envelopeId, updates) {
  try {
    if (!envelopeId) {
      throw new Error('envelopeId es requerido');
    }

    if (updates.name !== undefined && updates.name.trim() === '') {
      throw new Error('El nombre no puede estar vacío');
    }

    if (updates.budget_amount !== undefined && updates.budget_amount <= 0) {
      throw new Error('El monto presupuestado debe ser mayor a 0');
    }

    logInfo('ENVELOPES', 'updateEnvelope', { envelopeId, updates });

    // Limpiar campos - mapear solo campos V2 válidos
    const cleanUpdates = {};
    if (updates.name !== undefined) cleanUpdates.name = updates.name.trim();
    if (updates.description !== undefined) cleanUpdates.description = updates.description?.trim() || null;
    if (updates.category !== undefined) cleanUpdates.category = updates.category;
    if (updates.budget_amount !== undefined) cleanUpdates.budget_amount = parseFloat(updates.budget_amount);
    if (updates.current_amount !== undefined) cleanUpdates.current_amount = parseFloat(updates.current_amount);
    if (updates.period_type !== undefined) cleanUpdates.period_type = updates.period_type;
    if (updates.active !== undefined) cleanUpdates.active = updates.active;

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
      .update({ active: false })
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
 * Agregar transacción a envelope (V2)
 * En V2 no hay tabla envelope_transactions, se actualiza current_amount directamente
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

    // Obtener el envelope actual
    const { data: envelope, error: fetchError } = await supabase
      .from('envelopes')
      .select('current_amount')
      .eq('id', transactionData.envelope_id)
      .single();

    if (fetchError || !envelope) {
      throw new Error('Apartado no encontrado');
    }

    const currentAmount = parseFloat(envelope.current_amount) || 0;
    const amount = parseFloat(transactionData.amount);
    let newAmount;

    // Calcular nuevo monto según tipo de transacción
    if (transactionData.transaction_type === 'deposit' || transactionData.transaction_type === 'fund') {
      newAmount = currentAmount + amount;
    } else if (transactionData.transaction_type === 'withdrawal' || transactionData.transaction_type === 'withdraw') {
      if (currentAmount < amount) {
        throw new Error(`Balance insuficiente. Disponible: ${currentAmount}`);
      }
      newAmount = currentAmount - amount;
    } else {
      throw new Error(`Tipo de transacción inválido: ${transactionData.transaction_type}`);
    }

    // Actualizar current_amount del envelope
    const { data, error } = await supabase
      .from('envelopes')
      .update({ current_amount: newAmount })
      .eq('id', transactionData.envelope_id)
      .select()
      .single();

    if (error) {
      logError('ENVELOPES', 'addEnvelopeTransaction', error, transactionData);
      throw new Error(`Error al actualizar apartado: ${error.message}`);
    }

    logInfo('ENVELOPES', 'addEnvelopeTransaction', `Updated envelope ${transactionData.envelope_id} to ${newAmount}`);
    return data;
  } catch (error) {
    logError('ENVELOPES', 'addEnvelopeTransaction', error, { userId, transactionData });
    throw error;
  }
}

/**
 * Obtener transacciones de un envelope
 * V2: No hay tabla envelope_transactions, retorna array vacío
 * Las transacciones se reflejan directamente en current_amount
 */
export async function getEnvelopeTransactions(envelopeId) {
  logWarning('ENVELOPES', 'getEnvelopeTransactions', 'V2: No hay historial de transacciones, use current_amount directamente');
  return [];
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
      name: title,
      description: goalData.description?.trim() || null,
      target_amount: parseFloat(goalData.target_amount),
      current_amount: 0,
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

    // V2: No existe goal_funding, actualizar current_amount directamente
    // Primero obtener el current_amount actual
    const { data: plan, error: planError } = await supabase
      .from('plans')
      .select('current_amount')
      .eq('id', fundingData.goal_id)
      .single();
    
    if (planError) {
      logError('GOALS', 'addGoalFunding', planError, { goalId: fundingData.goal_id });
      throw new Error(`Error al obtener meta: ${planError.message}`);
    }

    const newAmount = parseFloat(plan.current_amount || 0) + parseFloat(fundingData.amount);

    const { data, error } = await supabase
      .from('plans')
      .update({ current_amount: newAmount })
      .eq('id', fundingData.goal_id)
      .select()
      .single();

    if (error) {
      logError('GOALS', 'addGoalFunding', error, { goalId: fundingData.goal_id, newAmount });
      throw new Error(`Error al agregar fondeo: ${error.message}`);
    }

    logInfo('GOALS', 'addGoalFunding', `Updated plan ${data.id} to ${newAmount}`);
    return data;
  } catch (error) {
    logError('GOALS', 'addGoalFunding', error, { userId, fundingData });
    throw error;
  }
}

/**
 * Actualizar progreso de meta 
 * V2: current_amount ya se actualiza directamente, esta función es un placeholder
 */
async function updateGoalProgress(goalId) {
  // En V2, el current_amount se actualiza directamente en addGoalFunding
  // Esta función se mantiene por compatibilidad
  logInfo('GOALS', 'updateGoalProgress', { goalId, note: 'V2: current_amount se actualiza directamente' });
}

/**
 * Obtener fondeos de una meta
 * V2: No existe goal_funding, retornar array vacío
 */
export async function getGoalFundings(goalId) {
  console.warn('getGoalFundings está deshabilitado - no existe goal_funding en V2');
  return [];
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
    const activeEnvelopes = envelopes.filter(e => e.active);
    const pendingExpenses = plannedExpenses.filter(e => e.status === 'planned' || e.status === 'scheduled');

    const dashboard = {
      goals: {
        total: activeGoals.length,
        completed: activeGoals.filter(g => (g.current_amount || 0) >= g.target_amount).length,
        totalTarget: activeGoals.reduce((sum, g) => sum + (parseFloat(g.target_amount) || 0), 0),
        totalCurrent: activeGoals.reduce((sum, g) => sum + (parseFloat(g.current_amount) || 0), 0),
        items: activeGoals
      },
      envelopes: {
        total: activeEnvelopes.length,
        totalBalance: activeEnvelopes.reduce((sum, e) => sum + (parseFloat(e.current_amount) || 0), 0),
        totalBudget: activeEnvelopes.reduce((sum, e) => sum + (parseFloat(e.budget_amount) || 0), 0),
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
        totalBudget: 0,
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
 * @deprecated V2: No disponible, movements no tiene planned_expense_id
 */
export async function linkEventToPlannedExpense(eventId, plannedExpenseId) {
  logWarning('EXPENSES', 'linkEventToPlannedExpense', 'DEPRECATED: V2 no soporta esta funcionalidad');
  // En V2, solo completamos el gasto planificado sin vincular
  await completePlannedExpense(plannedExpenseId);
}

/**
 * Calcular progreso de meta (porcentaje)
 */
export function calculateGoalProgress(goal) {
  if (!goal || !goal.target_amount) return 0;
  const currentAmount = goal.current_amount || 0;
  const progress = (currentAmount / goal.target_amount) * 100;
  return Math.min(Math.round(progress), 100);
}

/**
 * Calcular progreso de envelope (porcentaje) - V2
 * Compara current_amount vs budget_amount
 */
export function calculateEnvelopeProgress(envelope) {
  if (!envelope || !envelope.budget_amount) return 0;
  const progress = (envelope.current_amount / envelope.budget_amount) * 100;
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
// NOTA V2: Estas funciones están deprecadas. En V2, movements no tiene campos
// goal_id, envelope_id, planned_expense_id. Use los sistemas de fondeo directo
// como addGoalFunding() o addEnvelopeTransaction() en su lugar.

/**
 * Obtener ingresos disponibles
 * @deprecated V2: Use movements directamente con getMovements()
 */
export async function getAvailableIncomes(userId, options = {}) {
  logWarning('INCOMES', 'getAvailableIncomes', 'DEPRECATED: V2 no soporta vinculación de ingresos. Use movements.');
  return [];
}

/**
 * Asignar ingreso a meta
 * @deprecated V2: Use addGoalFunding() directamente
 */
export async function assignIncomeToGoal(incomeEventId, goalId, amount = null) {
  logWarning('INCOMES', 'assignIncomeToGoal', 'DEPRECATED: V2 no soporta vinculación. Use addGoalFunding().');
  throw new Error('V2: Use addGoalFunding() para fondear metas directamente');
}

/**
 * Asignar ingreso a gasto planificado
 * @deprecated V2: No disponible
 */
export async function assignIncomeToPlannedExpense(incomeEventId, expenseId, amount = null) {
  logWarning('INCOMES', 'assignIncomeToPlannedExpense', 'DEPRECATED: V2 no soporta esta funcionalidad.');
  throw new Error('V2: Funcionalidad no disponible');
}

/**
 * Asignar ingreso a apartado (envelope)
 * @deprecated V2: Use addEnvelopeTransaction() directamente
 */
export async function assignIncomeToEnvelope(incomeEventId, envelopeId, amount = null) {
  logWarning('INCOMES', 'assignIncomeToEnvelope', 'DEPRECATED: V2 no soporta vinculación. Use addEnvelopeTransaction().');
  throw new Error('V2: Use addEnvelopeTransaction() para fondear apartados directamente');
}

/**
 * Obtener ingresos asignados a una meta
 * @deprecated V2: No disponible
 */
export async function getGoalAssignedIncomes(goalId) {
  logWarning('INCOMES', 'getGoalAssignedIncomes', 'DEPRECATED: V2 no soporta vinculación de ingresos.');
  return [];
}

/**
 * Desvincular ingreso
 * @deprecated V2: No disponible
 */
export async function unassignIncome(incomeEventId) {
  logWarning('INCOMES', 'unassignIncome', 'DEPRECATED: V2 no soporta vinculación de ingresos.');
  return true;
}

/**
 * Obtener ingresos asignados a un gasto planificado
 * @deprecated V2: No disponible
 */
export async function getExpenseAssignedIncomes(expenseId) {
  logWarning('INCOMES', 'getExpenseAssignedIncomes', 'DEPRECATED: V2 no soporta vinculación de ingresos.');
  return [];
}
