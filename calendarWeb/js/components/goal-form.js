/**
 * Goal Form Component - Formulario para crear/editar metas financieras
 */

class GoalForm extends HTMLElement {
  constructor() {
    super();
    this.goalData = null;
  }

  connectedCallback() {
    this.render();
  }

  setGoal(goal) {
    this.goalData = goal;
    if (this.isConnected) {
      this.render();
    }
  }

  render() {
    const isEdit = !!this.goalData;
    const goal = this.goalData || {};

    this.innerHTML = `
      <div class="goal-form">
        <h3>${isEdit ? '✏️ Editar Meta' : '🎯 Nueva Meta'}</h3>
        
        <div class="form-group">
          <label for="goal-name">Nombre de la Meta *</label>
          <input 
            type="text" 
            id="goal-name" 
            value="${goal.title || goal.name || ''}" 
            placeholder="Ej: Ahorrar para vacaciones"
            required
          />
        </div>

        <div class="form-group">
          <label for="goal-description">Descripción</label>
          <textarea 
            id="goal-description" 
            rows="3" 
            placeholder="Detalles de tu meta..."
          >${goal.description || ''}</textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="goal-target-amount">Monto Objetivo *</label>
            <input 
              type="number" 
              id="goal-target-amount" 
              value="${goal.target_amount || ''}" 
              placeholder="0.00" 
              step="0.01"
              min="0.01"
              required
            />
          </div>

          <div class="form-group">
            <label for="goal-target-date">Fecha Objetivo</label>
            <input 
              type="date" 
              id="goal-target-date" 
              value="${goal.due_date || goal.target_date || ''}"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="goal-priority">Prioridad (1-5)</label>
            <select id="goal-priority">
              <option value="1" ${goal.priority === 1 ? 'selected' : ''}>1 - Muy Baja</option>
              <option value="2" ${goal.priority === 2 ? 'selected' : ''}>2 - Baja</option>
              <option value="3" ${goal.priority === 3 ? 'selected' : ''}>3 - Media</option>
              <option value="4" ${goal.priority === 4 ? 'selected' : ''}>4 - Alta</option>
              <option value="5" ${goal.priority === 5 ? 'selected' : ''}>5 - Muy Alta</option>
            </select>
          </div>
        </div>

        ${isEdit && goal.current_amount !== undefined ? `
          <div class="goal-progress">
            <div class="progress-info">
              <span>Progreso Actual</span>
              <span class="progress-amount">$${(goal.current_amount || 0).toFixed(2)} / $${(goal.target_amount || 0).toFixed(2)}</span>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" style="width: ${this.calculateProgress(goal)}%"></div>
            </div>
            <div class="progress-percentage">${this.calculateProgress(goal)}%</div>
          </div>
        ` : ''}

        <div class="form-actions">
          <button type="button" class="btn-secondary btn-cancel-goal">Cancelar</button>
          <button type="button" class="btn-primary btn-save-goal">
            ${isEdit ? 'Guardar Cambios' : 'Crear Meta'}
          </button>
        </div>
      </div>
    `;

    // Attach event listeners after rendering
    this.attachEventListeners();
  }

  attachEventListeners() {
    const cancelBtn = this.querySelector('.btn-cancel-goal');
    const saveBtn = this.querySelector('.btn-save-goal');

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
    const name = this.querySelector('#goal-name').value.trim();
    const targetAmount = parseFloat(this.querySelector('#goal-target-amount').value);

    if (!name) {
      Swal.fire({
        icon: 'warning',
        title: 'Campo Requerido',
        text: 'Por favor ingresa un nombre para la meta'
      });
      return false;
    }

    if (!targetAmount || targetAmount <= 0) {
      Swal.fire({
        icon: 'warning',
        title: 'Monto Inválido',
        text: 'Por favor ingresa un monto objetivo válido'
      });
      return false;
    }

    return true;
  }

  getFormData() {
    const isEdit = !!this.goalData;
    
    const formData = {
      title: this.querySelector('#goal-name').value.trim(),
      description: this.querySelector('#goal-description').value.trim() || null,
      target_amount: parseFloat(this.querySelector('#goal-target-amount').value),
      due_date: this.querySelector('#goal-target-date').value || null,
      priority: parseInt(this.querySelector('#goal-priority').value) || 3,
      status: 'active'
    };

    if (isEdit) {
      formData.id = this.goalData.id;
      formData.current_amount = this.goalData.current_amount || 0;
    } else {
      formData.current_amount = 0;
    }

    return formData;
  }

  calculateProgress(goal) {
    if (!goal || !goal.target_amount) return 0;
    return Math.min(Math.round((goal.current_amount / goal.target_amount) * 100), 100);
  }
}

customElements.define('goal-form', GoalForm);
export default GoalForm;
