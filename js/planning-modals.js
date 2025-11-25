/**
 * Planning Modals - Modales para gestión de planeación financiera
 */

import * as Planning from './planning.js';
import './components/goal-form.js';
import './components/envelope-form.js';
import './components/planned-expense-form.js';

// Swal está disponible globalmente desde sweetalert2@11.js

let currentUserId = null;

export function setUserId(userId) {
  currentUserId = userId;
}

// ==================== MODAL PRINCIPAL DE PLANEACIÓN ====================

export async function openPlanningModal() {
  if (!currentUserId) {
    Swal.fire('Error', 'No hay sesión activa', 'error');
    return;
  }

  const dashboard = await Planning.getPlanningDashboard(currentUserId);

  const result = await Swal.fire({
    title: '📊 Planeación Financiera',
    html: `
      <div class="planning-dashboard">
        <div class="dashboard-tabs">
          <button class="tab-btn active" data-tab="overview">📈 Resumen</button>
          <button class="tab-btn" data-tab="goals">🎯 Metas</button>
          <button class="tab-btn" data-tab="envelopes">💰 Apartados</button>
          <button class="tab-btn" data-tab="expenses">📅 Gastos Planificados</button>
        </div>

        <div class="tab-content active" id="tab-overview">
          ${renderOverviewTab(dashboard)}
        </div>

        <div class="tab-content" id="tab-goals">
          ${renderGoalsTab(dashboard.goals)}
        </div>

        <div class="tab-content" id="tab-envelopes">
          ${renderEnvelopesTab(dashboard.envelopes)}
        </div>

        <div class="tab-content" id="tab-expenses">
          ${renderExpensesTab(dashboard.plannedExpenses)}
        </div>
      </div>
    `,
    width: '900px',
    showConfirmButton: false,
    showCloseButton: true,
    didOpen: () => {
      setupTabSwitching();
      setupActionButtons();
    }
  });
}

function renderOverviewTab(dashboard) {
  return `
    <div class="overview-grid">
      <div class="overview-card">
        <div class="card-icon">🎯</div>
        <div class="card-content">
          <div class="card-label">Metas Activas</div>
          <div class="card-value">${dashboard.goals.total}</div>
          <div class="card-detail">${dashboard.goals.completed} completadas</div>
          <div class="progress-bar-mini">
            <div class="progress-fill" style="width: ${(dashboard.goals.totalCurrent / dashboard.goals.totalTarget * 100) || 0}%"></div>
          </div>
          <div class="card-amount">${Planning.formatCurrency(dashboard.goals.totalCurrent)} / ${Planning.formatCurrency(dashboard.goals.totalTarget)}</div>
        </div>
      </div>

      <div class="overview-card">
        <div class="card-icon">💰</div>
        <div class="card-content">
          <div class="card-label">Apartados</div>
          <div class="card-value">${dashboard.envelopes.total}</div>
          <div class="card-detail">Balance total</div>
          <div class="card-amount">${Planning.formatCurrency(dashboard.envelopes.totalBalance)}</div>
        </div>
      </div>

      <div class="overview-card">
        <div class="card-icon">📅</div>
        <div class="card-content">
          <div class="card-label">Gastos Planificados</div>
          <div class="card-value">${dashboard.plannedExpenses.total}</div>
          <div class="card-detail">${dashboard.plannedExpenses.thisMonth} este mes</div>
          <div class="card-amount">${Planning.formatCurrency(dashboard.plannedExpenses.totalEstimated)}</div>
        </div>
      </div>
    </div>
  `;
}

function renderGoalsTab(goalsData) {
  return `
    <div class="tab-header">
      <button class="btn-primary btn-add-goal">➕ Nueva Meta</button>
    </div>
    <div class="goals-list">
      ${goalsData.items.length === 0 ? `
        <div class="empty-state">
          <div class="empty-icon">🎯</div>
          <div class="empty-text">No tienes metas creadas</div>
          <div class="empty-hint">Crea tu primera meta para empezar a ahorrar</div>
        </div>
      ` : goalsData.items.map(goal => `
        <div class="goal-item" data-goal-id="${goal.id}">
          <div class="goal-header">
            <div class="goal-name">${goal.title || goal.name}</div>
            <div class="goal-actions">
              <button class="btn-icon btn-assign-incomes" title="Asignar Ingresos">💰</button>
              <button class="btn-icon btn-fund-goal" title="Agregar fondeo">💵</button>
              <button class="btn-icon btn-edit-goal" title="Editar">✏️</button>
              <button class="btn-icon btn-delete-goal" title="Eliminar">🗑️</button>
            </div>
          </div>
          ${goal.description ? `<div class="goal-description">${goal.description}</div>` : ''}
          <div class="goal-progress">
            <div class="progress-bar">
              <div class="progress-fill" style="width: ${Planning.calculateGoalProgress(goal)}%"></div>
            </div>
            <div class="progress-info">
              <span>${Planning.formatCurrency(goal.saved_amount || 0)} / ${Planning.formatCurrency(goal.target_amount)}</span>
              <span>${Planning.calculateGoalProgress(goal)}%</span>
            </div>
          </div>
          <div class="goal-meta">
            <span class="badge badge-priority-${goal.priority}">Prioridad: ${goal.priority}</span>
            ${goal.due_date ? `<span class="goal-days">${Planning.getDaysUntilTarget(goal.due_date)} días restantes</span>` : ''}
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

function renderEnvelopesTab(envelopesData) {
  return `
    <div class="tab-header">
      <button class="btn-primary btn-add-envelope">➕ Nuevo Apartado</button>
    </div>
    <div class="envelopes-grid">
      ${envelopesData.items.length === 0 ? `
        <div class="empty-state">
          <div class="empty-icon">💰</div>
          <div class="empty-text">No tienes apartados creados</div>
          <div class="empty-hint">Crea apartados para organizar tus ahorros</div>
        </div>
      ` : envelopesData.items.map(env => `
        <div class="envelope-card" style="border-color: ${env.color || '#6366f1'}" data-envelope-id="${env.id}">
          <div class="envelope-header">
            <div class="envelope-icon">${env.emoji || env.icon || '💰'}</div>
            <div class="envelope-actions">
              <button class="btn-icon btn-deposit" title="Depositar">➕</button>
              <button class="btn-icon btn-withdraw" title="Retirar">➖</button>
              <button class="btn-icon btn-edit-envelope" title="Editar">✏️</button>
              <button class="btn-icon btn-delete-envelope" title="Eliminar">🗑️</button>
            </div>
          </div>
          <div class="envelope-name">${env.name || 'Sin nombre'}</div>
          <div class="envelope-balance">${Planning.formatCurrency(env.current_balance || 0)}</div>
          ${env.target_amount ? `
            <div class="envelope-progress">
              <div class="progress-bar-small">
                <div class="progress-fill" style="width: ${Planning.calculateEnvelopeProgress(env)}%; background: ${env.color}"></div>
              </div>
              <div class="progress-text">${Planning.calculateEnvelopeProgress(env)}% de ${Planning.formatCurrency(env.target_amount)}</div>
            </div>
          ` : ''}
        </div>
      `).join('')}
    </div>
  `;
}

function renderExpensesTab(expensesData) {
  return `
    <div class="tab-header">
      <button class="btn-primary btn-add-expense">➕ Nuevo Gasto Planificado</button>
    </div>
    <div class="expenses-list">
      ${expensesData.items.length === 0 ? `
        <div class="empty-state">
          <div class="empty-icon">📅</div>
          <div class="empty-text">No tienes gastos planificados</div>
          <div class="empty-hint">Planea tus gastos futuros para mejor control</div>
        </div>
      ` : expensesData.items.map(exp => `
        <div class="expense-item ${exp.status === 'done' ? 'completed' : ''}" data-expense-id="${exp.id}">
          <div class="expense-header">
            <div class="expense-title">${exp.title}</div>
            <div class="expense-actions">
              ${exp.status !== 'done' ? `
                <button class="btn-icon btn-assign-incomes-expense" title="Asignar Ingresos">💰</button>
                <button class="btn-icon btn-complete-expense" title="Marcar completado">✅</button>
                <button class="btn-icon btn-edit-expense" title="Editar">✏️</button>
              ` : ''}
              <button class="btn-icon btn-delete-expense" title="Eliminar">🗑️</button>
            </div>
          </div>
          ${exp.description ? `<div class="expense-description">${exp.description}</div>` : ''}
          <div class="expense-info">
            <span class="expense-amount">${Planning.formatCurrency(exp.amount)}</span>
            <span class="expense-date">📅 ${formatDateSpanish(exp.planned_date)}</span>
            <span class="badge badge-priority-${getPriorityName(exp.priority)}">
              ${getPriorityLabel(exp.priority)}
            </span>
          </div>
          ${exp.status === 'done' ? `
            <div class="completion-badge">✅ Completado</div>
          ` : ''}
        </div>
      `).join('')}
    </div>
  `;
}

function getPriorityName(priority) {
  // Si es string, retornar tal cual
  if (typeof priority === 'string') return priority;
  // Si es número, mapear a nombre
  const map = { 1: 'low', 2: 'low', 3: 'medium', 4: 'high', 5: 'critical' };
  return map[priority] || 'medium';
}

function getPriorityLabel(priority) {
  // Si es string
  if (typeof priority === 'string') {
    const labels = { low: '🔵 Baja', medium: '🟡 Media', high: '🟠 Alta', critical: '🔴 Crítica' };
    return labels[priority] || '🟡 Media';
  }
  // Si es número
  const labels = { 1: '🔵 Muy Baja', 2: '🔵 Baja', 3: '🟡 Media', 4: '🟠 Alta', 5: '🔴 Crítica' };
  return labels[priority] || '🟡 Media';
}

// ==================== HELPERS ====================

function setupTabSwitching() {
  const tabs = document.querySelectorAll('.tab-btn');
  const contents = document.querySelectorAll('.tab-content');

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetTab = tab.dataset.tab;
      
      tabs.forEach(t => t.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));
      
      tab.classList.add('active');
      document.getElementById(`tab-${targetTab}`)?.classList.add('active');
    });
  });
}

function setupActionButtons() {
  // Goals
  document.querySelector('.btn-add-goal')?.addEventListener('click', () => openGoalFormModal());
  document.querySelectorAll('.btn-edit-goal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const goalId = e.target.closest('.goal-item').dataset.goalId;
      openGoalFormModal(goalId);
    });
  });
  document.querySelectorAll('.btn-assign-incomes').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      const goalId = e.target.closest('.goal-item').dataset.goalId;
      const { openAssignIncomesToGoalModal } = await import('./planning-incomes.js');
      const currentUser = window.getCurrentUser?.();
      const userId = currentUser?.userId;
      if (userId) {
        openAssignIncomesToGoalModal(goalId, userId);
      } else {
        Swal.fire('Error', 'No se pudo identificar el usuario', 'error');
      }
    });
  });
  document.querySelectorAll('.btn-fund-goal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const goalId = e.target.closest('.goal-item').dataset.goalId;
      openGoalFundingModal(goalId);
    });
  });
  document.querySelectorAll('.btn-delete-goal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const item = e.target.closest('.goal-item');
      const goalId = item.dataset.goalId;
      const goalName = item.querySelector('.goal-name').textContent;
      deleteItemModal('goal', goalId, goalName);
    });
  });

  // Envelopes
  document.querySelector('.btn-add-envelope')?.addEventListener('click', () => openEnvelopeFormModal());
  document.querySelectorAll('.btn-edit-envelope').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const envId = e.target.closest('.envelope-card').dataset.envelopeId;
      openEnvelopeFormModal(envId);
    });
  });
  document.querySelectorAll('.btn-deposit').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const envId = e.target.closest('.envelope-card').dataset.envelopeId;
      openEnvelopeTransactionModal(envId, 'deposit');
    });
  });
  document.querySelectorAll('.btn-withdraw').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const envId = e.target.closest('.envelope-card').dataset.envelopeId;
      openEnvelopeTransactionModal(envId, 'withdrawal');
    });
  });
  document.querySelectorAll('.btn-delete-envelope').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const item = e.target.closest('.envelope-card');
      const envId = item.dataset.envelopeId;
      const envName = item.querySelector('.envelope-name').textContent;
      deleteItemModal('envelope', envId, envName);
    });
  });

  // Planned Expenses
  document.querySelector('.btn-add-expense')?.addEventListener('click', () => openPlannedExpenseFormModal());
  document.querySelectorAll('.btn-edit-expense').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const expId = e.target.closest('.expense-item').dataset.expenseId;
      openPlannedExpenseFormModal(expId);
    });
  });
  document.querySelectorAll('.btn-assign-incomes-expense').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      const expId = e.target.closest('.expense-item').dataset.expenseId;
      const { openAssignIncomesToExpenseModal } = await import('./planning-incomes.js');
      const currentUser = window.getCurrentUser?.();
      const userId = currentUser?.userId;
      if (userId) {
        openAssignIncomesToExpenseModal(expId, userId);
      } else {
        Swal.fire('Error', 'No se pudo identificar el usuario', 'error');
      }
    });
  });
  document.querySelectorAll('.btn-complete-expense').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const expId = e.target.closest('.expense-item').dataset.expenseId;
      completeExpenseModal(expId);
    });
  });
  document.querySelectorAll('.btn-delete-expense').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const item = e.target.closest('.expense-item');
      const expId = item.dataset.expenseId;
      const expTitle = item.querySelector('.expense-title').textContent;
      deleteItemModal('expense', expId, expTitle);
    });
  });
}

function getCategoryLabel(category) {
  const labels = {
    general: 'General',
    travel: 'Viajes',
    education: 'Educación',
    emergency: 'Emergencia',
    purchase: 'Compra',
    savings: 'Ahorro',
    debt: 'Deuda',
    other: 'Otro'
  };
  return labels[category] || category;
}

function formatDateSpanish(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleDateString('es-MX', { 
    year: 'numeric', 
    month: 'long', 
    day: 'numeric' 
  });
}

// ==================== FORM MODALS ====================

async function openGoalFormModal(goalId = null) {
  let goal = null;
  
  if (goalId) {
    const goals = await Planning.getGoals(currentUserId);
    goal = goals.find(g => g.id === goalId);
  }

  const container = document.createElement('div');
  const goalForm = document.createElement('goal-form');
  
  if (goal) {
    goalForm.setGoal(goal);
  }

  container.appendChild(goalForm);

  // Registrar listeners ANTES de abrir la modal
  goalForm.addEventListener('save', async (e) => {
    try {
      if (goalId) {
        await Planning.updateGoal(goalId, e.detail);
        Swal.fire('✅ Meta actualizada', '', 'success');
      } else {
        await Planning.createGoal(currentUserId, e.detail);
        Swal.fire('✅ Meta creada', '', 'success');
      }
      Swal.close();
      openPlanningModal(); // Reload
    } catch (error) {
      Swal.fire('Error', error.message, 'error');
    }
  });

  goalForm.addEventListener('cancel', () => {
    Swal.close();
  });

  // Abrir la modal DESPUÉS de registrar listeners
  Swal.fire({
    html: container,
    showConfirmButton: false,
    showCloseButton: true,
    width: '600px',
    customClass: {
      popup: 'planning-modal'
    }
  });
}

async function openEnvelopeFormModal(envelopeId = null) {
  let envelope = null;
  
  if (envelopeId) {
    const envelopes = await Planning.getEnvelopes(currentUserId);
    envelope = envelopes.find(e => e.id === envelopeId);
  }

  const container = document.createElement('div');
  const envelopeForm = document.createElement('envelope-form');
  
  if (envelope) {
    envelopeForm.setEnvelope(envelope);
  }

  container.appendChild(envelopeForm);

  // Abre la modal SIN esperar, y conecta eventos inmediatamente
  Swal.fire({
    html: container,
    showConfirmButton: false,
    showCloseButton: true,
    width: '600px',
    customClass: {
      popup: 'planning-modal'
    }
  });

  envelopeForm.addEventListener('save', async (e) => {
    try {
      if (envelopeId) {
        await Planning.updateEnvelope(envelopeId, e.detail);
        Swal.fire('✅ Apartado actualizado', '', 'success');
      } else {
        await Planning.createEnvelope(currentUserId, e.detail);
        Swal.fire('✅ Apartado creado', '', 'success');
      }
      Swal.close();
      openPlanningModal(); // Reload
    } catch (error) {
      Swal.fire('Error', error.message, 'error');
    }
  });

  envelopeForm.addEventListener('cancel', () => {
    Swal.close();
  });
}

async function openPlannedExpenseFormModal(expenseId = null) {
  let expense = null;
  
  if (expenseId) {
    const expenses = await Planning.getPlannedExpenses(currentUserId);
    expense = expenses.find(e => e.id === expenseId);
  }

  // Load goals and envelopes for linking
  const [goals, envelopes] = await Promise.all([
    Planning.getGoals(currentUserId),
    Planning.getEnvelopes(currentUserId)
  ]);

  const container = document.createElement('div');
  const expenseForm = document.createElement('planned-expense-form');
  
  expenseForm.setGoals(goals.filter(g => g.is_active));
  expenseForm.setEnvelopes(envelopes.filter(e => e.is_active));
  
  if (expense) {
    expenseForm.setExpense(expense);
  }

  container.appendChild(expenseForm);

  // Abre la modal SIN esperar, y conecta eventos inmediatamente
  Swal.fire({
    html: container,
    showConfirmButton: false,
    showCloseButton: true,
    width: '650px',
    customClass: {
      popup: 'planning-modal'
    }
  });

  expenseForm.addEventListener('save', async (e) => {
    try {
      if (expenseId) {
        await Planning.updatePlannedExpense(expenseId, e.detail);
        Swal.fire('✅ Gasto actualizado', '', 'success');
      } else {
        await Planning.createPlannedExpense(currentUserId, e.detail);
        Swal.fire('✅ Gasto planificado creado', '', 'success');
      }
      Swal.close();
      openPlanningModal(); // Reload
    } catch (error) {
      Swal.fire('Error', error.message, 'error');
    }
  });

  expenseForm.addEventListener('cancel', () => {
    Swal.close();
  });
}

async function openEnvelopeTransactionModal(envelopeId, type) {
  const envelope = (await Planning.getEnvelopes(currentUserId)).find(e => e.id === envelopeId);
  
  if (!envelope) return;

  const result = await Swal.fire({
    title: type === 'deposit' ? '💵 Depositar en Apartado' : '💸 Retirar de Apartado',
    html: `
      <div style="text-align: left; margin-bottom: 16px;">
        <div style="font-size: 14px; color: #6b7280; margin-bottom: 8px;">
          ${envelope.icon} ${envelope.name}
        </div>
        <div style="font-size: 18px; font-weight: bold; color: #3b82f6;">
          Balance actual: ${Planning.formatCurrency(envelope.current_balance)}
        </div>
      </div>
      <input id="transaction-amount" type="number" class="swal2-input" placeholder="Monto" step="0.01" min="0.01" style="width: 90%;">
      <textarea id="transaction-description" class="swal2-textarea" placeholder="Descripción (opcional)" style="width: 90%;"></textarea>
    `,
    showCancelButton: true,
    confirmButtonText: type === 'deposit' ? 'Depositar' : 'Retirar',
    cancelButtonText: 'Cancelar',
    preConfirm: () => {
      const amount = parseFloat(document.getElementById('transaction-amount').value);
      const description = document.getElementById('transaction-description').value.trim();

      if (!amount || amount <= 0) {
        Swal.showValidationMessage('Ingresa un monto válido');
        return false;
      }

      if (type === 'withdrawal' && amount > envelope.current_balance) {
        Swal.showValidationMessage('No hay suficiente balance');
        return false;
      }

      return { amount, description };
    }
  });

  if (result.isConfirmed) {
    try {
      await Planning.addEnvelopeTransaction(currentUserId, {
        envelope_id: envelopeId,
        amount: result.value.amount,
        transaction_type: type,
        description: result.value.description
      });
      Swal.fire('✅ Transacción registrada', '', 'success');
      openPlanningModal(); // Reload
    } catch (error) {
      Swal.fire('Error', error.message, 'error');
    }
  }
}

async function openGoalFundingModal(goalId) {
  const goal = (await Planning.getGoals(currentUserId)).find(g => g.id === goalId);
  
  if (!goal) return;

  const savedAmount = goal.saved_amount || 0;
  const remaining = goal.target_amount - savedAmount;

  const result = await Swal.fire({
    title: '💵 Agregar Fondeo a Meta',
    html: `
      <div style="text-align: left; margin-bottom: 16px;">
        <div style="font-size: 16px; font-weight: bold; margin-bottom: 8px;">
          ${goal.title || goal.name}
        </div>
        <div style="font-size: 14px; color: #6b7280; margin-bottom: 4px;">
          Progreso: ${Planning.formatCurrency(savedAmount)} / ${Planning.formatCurrency(goal.target_amount)}
        </div>
        <div style="font-size: 14px; color: #3b82f6; font-weight: 600;">
          Falta: ${Planning.formatCurrency(remaining)}
        </div>
      </div>
      <input id="funding-amount" type="number" class="swal2-input" placeholder="Monto a agregar" step="0.01" min="0.01" value="${remaining > 0 ? remaining.toFixed(2) : ''}" style="width: 90%;">
      <textarea id="funding-notes" class="swal2-textarea" placeholder="Notas (opcional)" style="width: 90%;"></textarea>
    `,
    showCancelButton: true,
    confirmButtonText: 'Agregar Fondeo',
    cancelButtonText: 'Cancelar',
    preConfirm: () => {
      const amount = parseFloat(document.getElementById('funding-amount').value);
      const notes = document.getElementById('funding-notes').value.trim();

      if (!amount || amount <= 0) {
        Swal.showValidationMessage('Ingresa un monto válido');
        return false;
      }

      return { amount, notes };
    }
  });

  if (result.isConfirmed) {
    try {
      await Planning.addGoalFunding(currentUserId, {
        goal_id: goalId,
        amount: result.value.amount,
        notes: result.value.notes
      });
      Swal.fire('✅ Fondeo agregado', '', 'success');
      openPlanningModal(); // Reload
    } catch (error) {
      Swal.fire('Error', error.message, 'error');
    }
  }
}

async function completeExpenseModal(expenseId) {
  const expense = (await Planning.getPlannedExpenses(currentUserId)).find(e => e.id === expenseId);
  
  if (!expense) return;

  const result = await Swal.fire({
    title: '✅ Marcar Gasto como Completado',
    html: `
      <div style="text-align: left; margin-bottom: 16px;">
        <div style="font-size: 16px; font-weight: bold; margin-bottom: 8px;">
          ${expense.title}
        </div>
        <div style="font-size: 14px; color: #6b7280;">
          Monto estimado: ${Planning.formatCurrency(expense.estimated_amount)}
        </div>
      </div>
      <input id="actual-amount" type="number" class="swal2-input" placeholder="Monto real (opcional)" step="0.01" min="0" value="${expense.estimated_amount.toFixed(2)}" style="width: 90%;">
    `,
    showCancelButton: true,
    confirmButtonText: 'Marcar Completado',
    cancelButtonText: 'Cancelar',
    preConfirm: () => {
      const actualAmount = parseFloat(document.getElementById('actual-amount').value);
      return actualAmount > 0 ? actualAmount : null;
    }
  });

  if (result.isConfirmed) {
    try {
      await Planning.completePlannedExpense(expenseId, result.value);
      
      // Si tiene envelope asociado, retirar monto
      if (expense.related_envelope_id && result.value) {
        await Planning.addEnvelopeTransaction(currentUserId, {
          envelope_id: expense.related_envelope_id,
          amount: result.value,
          transaction_type: 'withdrawal',
          description: `Gasto completado: ${expense.title}`
        });
      }
      
      Swal.fire('✅ Gasto completado', '', 'success');
      openPlanningModal(); // Reload
    } catch (error) {
      Swal.fire('Error', error.message, 'error');
    }
  }
}

async function deleteItemModal(type, id, name) {
  const result = await Swal.fire({
    title: '¿Estás seguro?',
    text: `Se eliminará: ${name}`,
    icon: 'warning',
    showCancelButton: true,
    confirmButtonText: 'Sí, eliminar',
    cancelButtonText: 'Cancelar',
    confirmButtonColor: '#ef4444'
  });

  if (result.isConfirmed) {
    try {
      if (type === 'goal') {
        await Planning.deleteGoal(id);
      } else if (type === 'envelope') {
        await Planning.deleteEnvelope(id);
      } else if (type === 'expense') {
        await Planning.deletePlannedExpense(id);
      }
      
      Swal.fire('✅ Eliminado', '', 'success');
      openPlanningModal(); // Reload
    } catch (error) {
      Swal.fire('Error', error.message, 'error');
    }
  }
}
