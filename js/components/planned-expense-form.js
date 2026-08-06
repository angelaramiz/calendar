/**
 * Planned Expense Form Component - Formulario para gastos futuros planificados
 */

class PlannedExpenseForm extends HTMLElement {
  constructor() {
    super();
    this.expenseData = null;
    this.goals = [];
    this.envelopes = [];
  }

  connectedCallback() {
    this.render();
  }

  setExpense(expense) {
    this.expenseData = expense;
    if (this.isConnected) {
      this.render();
    }
  }

  setGoals(goals) {
    this.goals = goals;
  }

  setEnvelopes(envelopes) {
    this.envelopes = envelopes;
  }

  render() {
    const isEdit = !!this.expenseData;
    const expense = this.expenseData || {};
    
    // Convertir priority numérico a string para el select
    let priorityStr = 'medium';
    if (expense.priority) {
      if (typeof expense.priority === 'number') {
        const priorityMap = { 1: 'low', 2: 'low', 3: 'medium', 4: 'high', 5: 'critical' };
        priorityStr = priorityMap[expense.priority] || 'medium';
      } else {
        priorityStr = expense.priority;
      }
    }

    this.innerHTML = `
      <div class="planned-expense-form">
        <h3>${isEdit ? '✏️ Editar Gasto Planificado' : '📅 Nuevo Gasto Planificado'}</h3>
        
        <div class="form-group">
          <label for="expense-title">Título del Gasto *</label>
          <input 
            type="text" 
            id="expense-title" 
            value="${expense.title || ''}" 
            placeholder="Ej: Reparación del auto"
            required
          />
        </div>

        <div class="form-group">
          <label for="expense-description">Descripción</label>
          <textarea 
            id="expense-description" 
            rows="3" 
            placeholder="Detalles del gasto planificado..."
          >${expense.description || ''}</textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="expense-amount">Monto Estimado *</label>
            <input 
              type="number" 
              id="expense-amount" 
              value="${expense.amount || ''}" 
              placeholder="0.00" 
              step="0.01"
              min="0.01"
              required
            />
          </div>

          <div class="form-group">
            <label for="expense-date">Fecha Planificada *</label>
            <input 
              type="date" 
              id="expense-date" 
              value="${expense.planned_date || expense.due_date || ''}"
              required
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="expense-category">Categoría</label>
            <select id="expense-category">
              <option value="general" ${expense.category === 'general' ? 'selected' : ''}>General</option>
              <option value="housing" ${expense.category === 'housing' ? 'selected' : ''}>Vivienda</option>
              <option value="transport" ${expense.category === 'transport' ? 'selected' : ''}>Transporte</option>
              <option value="health" ${expense.category === 'health' ? 'selected' : ''}>Salud</option>
              <option value="education" ${expense.category === 'education' ? 'selected' : ''}>Educación</option>
              <option value="entertainment" ${expense.category === 'entertainment' ? 'selected' : ''}>Entretenimiento</option>
              <option value="utilities" ${expense.category === 'utilities' ? 'selected' : ''}>Servicios</option>
              <option value="maintenance" ${expense.category === 'maintenance' ? 'selected' : ''}>Mantenimiento</option>
              <option value="other" ${expense.category === 'other' ? 'selected' : ''}>Otro</option>
            </select>
          </div>

          <div class="form-group">
            <label for="expense-priority">Prioridad</label>
            <select id="expense-priority">
              <option value="low" ${priorityStr === 'low' ? 'selected' : ''}>🔵 Baja</option>
              <option value="medium" ${priorityStr === 'medium' ? 'selected' : ''}>🟡 Media</option>
              <option value="high" ${priorityStr === 'high' ? 'selected' : ''}>🟠 Alta</option>
              <option value="critical" ${priorityStr === 'critical' ? 'selected' : ''}>🔴 Crítica</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label for="expense-envelope">Asociar a Apartado (Opcional)</label>
          <select id="expense-envelope">
            <option value="">Sin asociar</option>
            ${this.envelopes.map(env => `
              <option value="${env.id}" ${expense.envelope_id === env.id ? 'selected' : ''}>
                ${env.emoji || env.icon || '💰'} ${env.name}
              </option>
            `).join('')}
          </select>
          <small>El gasto se descontará del apartado seleccionado al completarse</small>
        </div>

        ${isEdit && expense.status === 'done' ? `
          <div class="expense-completed">
            <div class="completed-badge">✅ Completado</div>
            <div class="completed-info">
              <div>Fecha: ${expense.planned_date}</div>
              ${expense.amount ? `<div>Monto: $${parseFloat(expense.amount).toFixed(2)}</div>` : ''}
            </div>
          </div>
        ` : ''}

        <div class="form-actions">
          <button type="button" class="btn-secondary btn-cancel-expense">Cancelar</button>
          <button type="button" class="btn-primary btn-save-expense">
            ${isEdit ? 'Guardar Cambios' : 'Crear Gasto Planificado'}
          </button>
        </div>
      </div>
    `;

    // Attach event listeners after rendering
    this.attachEventListeners();
  }

  attachEventListeners() {
    const cancelBtn = this.querySelector('.btn-cancel-expense');
    const saveBtn = this.querySelector('.btn-save-expense');

    if (cancelBtn) {
      cancelBtn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.dispatchEvent(new CustomEvent('cancel', { bubbles: true }));
      });
    }

    if (saveBtn) {
      saveBtn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        if (this.validateForm()) {
          const formData = this.getFormData();
          this.dispatchEvent(new CustomEvent('save', { detail: formData, bubbles: true }));
        }
      });
    }
  }

  validateForm() {
    const title = this.querySelector('#expense-title').value.trim();
    const amount = parseFloat(this.querySelector('#expense-amount').value);
    const date = this.querySelector('#expense-date').value;

    if (!title) {
      Swal.fire({
        icon: 'warning',
        title: 'Campo Requerido',
        text: 'Por favor ingresa un título para el gasto'
      });
      return false;
    }

    if (!amount || amount <= 0) {
      Swal.fire({
        icon: 'warning',
        title: 'Monto Inválido',
        text: 'Por favor ingresa un monto válido mayor a 0'
      });
      return false;
    }

    if (!date) {
      Swal.fire({
        icon: 'warning',
        title: 'Fecha Requerida',
        text: 'Por favor selecciona una fecha para el gasto'
      });
      return false;
    }

    return true;
  }

  getFormData() {
    const isEdit = !!this.expenseData;
    const envelopeId = this.querySelector('#expense-envelope').value;

    const formData = {
      title: this.querySelector('#expense-title').value.trim(),
      description: this.querySelector('#expense-description').value.trim() || null,
      amount: parseFloat(this.querySelector('#expense-amount').value),
      planned_date: this.querySelector('#expense-date').value, // Usar planned_date según schema
      category: this.querySelector('#expense-category').value || 'general',
      priority: this.querySelector('#expense-priority').value || 'medium', // String, será convertido a número en planning.js
      envelope_id: envelopeId || null,
      status: 'planned', // Status según schema: planned, scheduled, committed, done, cancelled
      frequency: 'once',
      auto_create_event: true
    };

    if (isEdit) {
      formData.id = this.expenseData.id;
    }

    return formData;
  }
}

customElements.define('planned-expense-form', PlannedExpenseForm);
export default PlannedExpenseForm;
