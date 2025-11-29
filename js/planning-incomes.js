/**
 * @fileoverview Modal de vinculación de ingresos con metas y gastos planificados
 * Permite asignar eventos de tipo income a planning items
 */

import * as Planning from './planning.js';

/**
 * Modal para asignar ingresos a meta
 */
export async function openAssignIncomesToGoalModal(goalId, currentUserId) {
  try {
    const [goal, availableIncomes, assignedIncomes] = await Promise.all([
      Planning.getGoals(currentUserId).then(goals => goals.find(g => g.id === goalId)),
      Planning.getAvailableIncomes(currentUserId),
      Planning.getGoalAssignedIncomes(goalId)
    ]);

    if (!goal) {
      Swal.fire('Error', 'Meta no encontrada', 'error');
      return;
    }

    const remaining = parseFloat(goal.target_amount) - parseFloat(goal.current_amount || 0);

    await Swal.fire({
      title: `💰 Asignar Ingresos a: ${goal.name || goal.title}`,
      html: `
        <div class="assign-incomes-modal">
          <div class="goal-summary">
            <div class="summary-row">
              <span>Meta:</span>
              <strong>${Planning.formatCurrency(goal.target_amount)}</strong>
            </div>
            <div class="summary-row">
              <span>Ahorrado:</span>
              <strong>${Planning.formatCurrency(goal.current_amount || 0)}</strong>
            </div>
            <div class="summary-row">
              <span>Falta:</span>
              <strong style="color: #f59e0b">${Planning.formatCurrency(remaining)}</strong>
            </div>
          </div>

          <div class="progress-section">
            <div class="progress-bar" style="height: 24px; background: #e5e7eb; border-radius: 12px; overflow: hidden;">
              <div class="progress-fill" style="width: ${Planning.calculateGoalProgress(goal)}%; height: 100%; background: linear-gradient(90deg, #22c55e, #16a34a); transition: width 0.3s ease;"></div>
            </div>
            <div style="text-align: center; margin-top: 8px; font-weight: 600; color: #22c55e;">
              ${Planning.calculateGoalProgress(goal)}%
            </div>
          </div>

          <div class="incomes-sections" style="margin-top: 24px;">
            ${assignedIncomes.length > 0 ? `
              <div class="section">
                <h4 style="margin: 0 0 12px 0; color: #22c55e;">✅ Ingresos Asignados (${assignedIncomes.length})</h4>
                <div class="incomes-list">
                  ${assignedIncomes.map(inc => `
                    <div class="income-item" style="display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 8px; background: #f0fdf4;">
                      <div>
                        <div style="font-weight: 600;">${inc.title}</div>
                        <div style="font-size: 0.875rem; color: #6b7280;">${formatDateSpanish(inc.date)}</div>
                      </div>
                      <div style="display: flex; align-items: center; gap: 12px;">
                        <span style="font-weight: 600; color: #22c55e;">${Planning.formatCurrency(inc.amount)}</span>
                        <button class="btn-unassign-income" data-income-id="${inc.id}" style="padding: 4px 8px; background: #fee2e2; border: 1px solid #fca5a5; border-radius: 6px; cursor: pointer;" title="Desvincular">
                          ❌
                        </button>
                      </div>
                    </div>
                  `).join('')}
                </div>
              </div>
            ` : ''}

            ${availableIncomes.length > 0 ? `
              <div class="section" style="margin-top: 16px;">
                <h4 style="margin: 0 0 12px 0; color: #3b82f6;">💵 Ingresos Disponibles (${availableIncomes.length})</h4>
                <div class="incomes-list">
                  ${availableIncomes.map(inc => `
                    <div class="income-item" style="display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 8px; background: #fff;">
                      <div>
                        <div style="font-weight: 600;">${inc.title}</div>
                        <div style="font-size: 0.875rem; color: #6b7280;">${formatDateSpanish(inc.date)}</div>
                      </div>
                      <div style="display: flex; align-items: center; gap: 12px;">
                        <span style="font-weight: 600;">${Planning.formatCurrency(inc.amount)}</span>
                        <button class="btn-assign-income" data-income-id="${inc.id}" data-amount="${inc.amount}" style="padding: 6px 12px; background: #3b82f6; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 500;">
                          Asignar
                        </button>
                      </div>
                    </div>
                  `).join('')}
                </div>
              </div>
            ` : '<div style="padding: 24px; text-align: center; color: #9ca3af;"><p>No hay ingresos disponibles para asignar</p></div>'}
          </div>
        </div>
      `,
      showConfirmButton: false,
      showCloseButton: true,
      width: '700px',
      didOpen: () => {
        // Asignar ingresos
        document.querySelectorAll('.btn-assign-income').forEach(btn => {
          btn.addEventListener('click', async (e) => {
            const incomeId = e.target.dataset.incomeId;
            const amount = parseFloat(e.target.dataset.amount);
            
            try {
              await Planning.assignIncomeToGoal(incomeId, goalId, amount);
              Swal.fire({
                icon: 'success',
                title: '✅ Ingreso asignado',
                timer: 1500,
                showConfirmButton: false
              });
              openAssignIncomesToGoalModal(goalId, currentUserId);
            } catch (error) {
              Swal.fire('Error', error.message, 'error');
            }
          });
        });

        // Desvincular ingresos
        document.querySelectorAll('.btn-unassign-income').forEach(btn => {
          btn.addEventListener('click', async (e) => {
            const incomeId = e.target.dataset.incomeId;
            
            const confirmResult = await Swal.fire({
              title: '¿Desvincular ingreso?',
              text: 'El ingreso quedará disponible nuevamente',
              icon: 'question',
              showCancelButton: true,
              confirmButtonText: 'Sí, desvincular',
              cancelButtonText: 'Cancelar'
            });

            if (confirmResult.isConfirmed) {
              try {
                await Planning.unassignIncome(incomeId);
                Swal.fire({
                  icon: 'success',
                  title: '✅ Ingreso desvinculado',
                  timer: 1500,
                  showConfirmButton: false
                });
                openAssignIncomesToGoalModal(goalId, currentUserId);
              } catch (error) {
                Swal.fire('Error', error.message, 'error');
              }
            }
          });
        });
      }
    });
  } catch (error) {
    // Error de migración pendiente
    if (error.message && error.message.includes('migración')) {
      Swal.fire({
        icon: 'warning',
        title: '⚠️ Migración de Base de Datos Requerida',
        html: `
          <p style="margin-bottom: 16px;">Esta funcionalidad requiere actualizar la base de datos.</p>
          <p style="margin-bottom: 16px;"><strong>Pasos para habilitar:</strong></p>
          <ol style="text-align: left; padding-left: 20px; margin-bottom: 16px;">
            <li>Abre el SQL Editor de Supabase</li>
            <li>Ejecuta el archivo: <code>docs/migrations/05-income-linking.sql</code></li>
            <li>Recarga esta página</li>
          </ol>
          <p style="font-size: 0.875rem; color: #6b7280;">Contacta al administrador si necesitas ayuda.</p>
        `,
        confirmButtonText: 'Entendido',
        width: '600px'
      });
    } else {
      Swal.fire('Error', error.message, 'error');
    }
  }
}

function formatDateSpanish(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleDateString('es-MX', { 
    year: 'numeric', 
    month: 'short', 
    day: 'numeric' 
  });
}

/**
 * Modal para asignar ingresos a gasto planificado
 */
export async function openAssignIncomesToExpenseModal(expenseId, currentUserId) {
  try {
    const [expense, availableIncomes, assignedIncomes] = await Promise.all([
      Planning.getPlannedExpenses(currentUserId).then(expenses => expenses.find(e => e.id === expenseId)),
      Planning.getAvailableIncomes(currentUserId),
      Planning.getExpenseAssignedIncomes(expenseId)
    ]);

    if (!expense) {
      Swal.fire('Error', 'Gasto planificado no encontrado', 'error');
      return;
    }

    const totalAssigned = assignedIncomes.reduce((sum, inc) => sum + parseFloat(inc.amount || 0), 0);
    const remaining = parseFloat(expense.amount) - totalAssigned;
    const progress = Math.min(100, (totalAssigned / parseFloat(expense.amount)) * 100);

    await Swal.fire({
      title: `💳 Asignar Ingresos a: ${expense.title}`,
      html: `
        <div class="assign-incomes-modal">
          <div class="goal-summary">
            <div class="summary-row">
              <span>Monto Total:</span>
              <strong>${Planning.formatCurrency(expense.amount)}</strong>
            </div>
            <div class="summary-row">
              <span>Asignado:</span>
              <strong>${Planning.formatCurrency(totalAssigned)}</strong>
            </div>
            <div class="summary-row">
              <span>Falta:</span>
              <strong style="color: ${remaining > 0 ? '#f59e0b' : '#22c55e'}">${Planning.formatCurrency(Math.max(0, remaining))}</strong>
            </div>
            ${expense.planned_date ? `
              <div class="summary-row">
                <span>Fecha planificada:</span>
                <strong>${formatDateSpanish(expense.planned_date)}</strong>
              </div>
            ` : ''}
          </div>

          <div class="progress-section">
            <div class="progress-bar" style="height: 24px; background: #e5e7eb; border-radius: 12px; overflow: hidden;">
              <div class="progress-fill" style="width: ${progress}%; height: 100%; background: linear-gradient(90deg, #3b82f6, #2563eb); transition: width 0.3s ease;"></div>
            </div>
            <div style="text-align: center; margin-top: 8px; font-weight: 600; color: #3b82f6;">
              ${progress.toFixed(1)}% cubierto
            </div>
          </div>

          <div class="incomes-sections" style="margin-top: 24px;">
            ${assignedIncomes.length > 0 ? `
              <div class="section">
                <h4 style="margin: 0 0 12px 0; color: #3b82f6;">✅ Ingresos Asignados (${assignedIncomes.length})</h4>
                <div class="incomes-list">
                  ${assignedIncomes.map(inc => `
                    <div class="income-item" style="display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 8px; background: #eff6ff;">
                      <div>
                        <div style="font-weight: 600;">${inc.title}</div>
                        <div style="font-size: 0.875rem; color: #6b7280;">${formatDateSpanish(inc.date)}</div>
                      </div>
                      <div style="display: flex; align-items: center; gap: 12px;">
                        <span style="font-weight: 600; color: #3b82f6;">${Planning.formatCurrency(inc.amount)}</span>
                        <button class="btn-unassign-income" data-income-id="${inc.id}" style="padding: 4px 8px; background: #fee2e2; border: 1px solid #fca5a5; border-radius: 6px; cursor: pointer;" title="Desvincular">
                          ❌
                        </button>
                      </div>
                    </div>
                  `).join('')}
                </div>
              </div>
            ` : ''}

            ${availableIncomes.length > 0 ? `
              <div class="section" style="margin-top: 16px;">
                <h4 style="margin: 0 0 12px 0; color: #10b981;">💵 Ingresos Disponibles (${availableIncomes.length})</h4>
                <div class="incomes-list">
                  ${availableIncomes.map(inc => `
                    <div class="income-item" style="display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 8px; background: #fff;">
                      <div>
                        <div style="font-weight: 600;">${inc.title}</div>
                        <div style="font-size: 0.875rem; color: #6b7280;">${formatDateSpanish(inc.date)}</div>
                      </div>
                      <div style="display: flex; align-items: center; gap: 12px;">
                        <span style="font-weight: 600;">${Planning.formatCurrency(inc.amount)}</span>
                        <button class="btn-assign-income" data-income-id="${inc.id}" data-amount="${inc.amount}" style="padding: 6px 12px; background: #10b981; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 500;">
                          Asignar
                        </button>
                      </div>
                    </div>
                  `).join('')}
                </div>
              </div>
            ` : '<div style="padding: 24px; text-align: center; color: #9ca3af;"><p>No hay ingresos disponibles para asignar</p></div>'}
          </div>
        </div>
      `,
      showConfirmButton: false,
      showCloseButton: true,
      width: '700px',
      didOpen: () => {
        // Asignar ingresos
        document.querySelectorAll('.btn-assign-income').forEach(btn => {
          btn.addEventListener('click', async (e) => {
            const incomeId = e.target.dataset.incomeId;
            
            try {
              await Planning.assignIncomeToPlannedExpense(incomeId, expenseId);
              Swal.fire({
                icon: 'success',
                title: '✅ Ingreso asignado',
                timer: 1500,
                showConfirmButton: false
              });
              openAssignIncomesToExpenseModal(expenseId, currentUserId);
            } catch (error) {
              Swal.fire('Error', error.message, 'error');
            }
          });
        });

        // Desvincular ingresos
        document.querySelectorAll('.btn-unassign-income').forEach(btn => {
          btn.addEventListener('click', async (e) => {
            const incomeId = e.target.dataset.incomeId;
            
            const confirmResult = await Swal.fire({
              title: '¿Desvincular ingreso?',
              text: 'El ingreso quedará disponible nuevamente',
              icon: 'question',
              showCancelButton: true,
              confirmButtonText: 'Sí, desvincular',
              cancelButtonText: 'Cancelar'
            });

            if (confirmResult.isConfirmed) {
              try {
                await Planning.unassignIncome(incomeId);
                Swal.fire({
                  icon: 'success',
                  title: '✅ Ingreso desvinculado',
                  timer: 1500,
                  showConfirmButton: false
                });
                openAssignIncomesToExpenseModal(expenseId, currentUserId);
              } catch (error) {
                Swal.fire('Error', error.message, 'error');
              }
            }
          });
        });
      }
    });
  } catch (error) {
    // Error de migración pendiente
    if (error.message && error.message.includes('migración')) {
      Swal.fire({
        icon: 'warning',
        title: '⚠️ Migración de Base de Datos Requerida',
        html: `
          <p style="margin-bottom: 16px;">Esta funcionalidad requiere actualizar la base de datos.</p>
          <p style="margin-bottom: 16px;"><strong>Pasos para habilitar:</strong></p>
          <ol style="text-align: left; padding-left: 20px; margin-bottom: 16px;">
            <li>Abre el SQL Editor de Supabase</li>
            <li>Ejecuta el archivo: <code>docs/migrations/05-income-linking.sql</code></li>
            <li>Recarga esta página</li>
          </ol>
          <p style="font-size: 0.875rem; color: #6b7280;">Contacta al administrador si necesitas ayuda.</p>
        `,
        confirmButtonText: 'Entendido',
        width: '600px'
      });
    } else {
      Swal.fire('Error', error.message, 'error');
    }
  }
}

