/**
 * Envelope Form Component - Formulario para crear/editar apartados (V2)
 * Campos V2: name, description, category, budget_amount, current_amount, period_type, active
 */

class EnvelopeForm extends HTMLElement {
  constructor() {
    super();
    this.envelopeData = null;
  }

  connectedCallback() {
    this.render();
  }

  setEnvelope(envelope) {
    this.envelopeData = envelope;
    if (this.isConnected) {
      this.render();
    }
  }

  render() {
    const isEdit = !!this.envelopeData;
    const envelope = this.envelopeData || {};

    const periodTypes = [
      { value: 'weekly', name: 'Semanal' },
      { value: 'biweekly', name: 'Quincenal' },
      { value: 'monthly', name: 'Mensual' },
      { value: 'yearly', name: 'Anual' }
    ];

    const categories = [
      'Hogar', 'Transporte', 'Alimentación', 'Entretenimiento', 
      'Salud', 'Educación', 'Viajes', 'Ahorro', 'Emergencias', 'Otros'
    ];

    this.innerHTML = `
      <div class="envelope-form">
        <h3>${isEdit ? '✏️ Editar Apartado' : '💰 Nuevo Apartado'}</h3>
        
        <div class="form-group">
          <label for="envelope-name">Nombre del Apartado *</label>
          <input 
            type="text" 
            id="envelope-name" 
            value="${envelope.name || ''}" 
            placeholder="Ej: Renta, Fondo de emergencia"
            required
          />
        </div>

        <div class="form-group">
          <label for="envelope-description">Descripción</label>
          <textarea 
            id="envelope-description" 
            placeholder="Descripción opcional del apartado"
            rows="2"
          >${envelope.description || ''}</textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="envelope-budget">Monto Presupuestado *</label>
            <input 
              type="number" 
              id="envelope-budget" 
              value="${envelope.budget_amount || ''}" 
              placeholder="0.00" 
              step="0.01"
              min="0.01"
              required
            />
          </div>

          <div class="form-group">
            <label for="envelope-period">Período</label>
            <select id="envelope-period">
              ${periodTypes.map(p => `
                <option value="${p.value}" ${envelope.period_type === p.value ? 'selected' : ''}>
                  ${p.name}
                </option>
              `).join('')}
            </select>
          </div>
        </div>

        <div class="form-group">
          <label for="envelope-category">Categoría</label>
          <select id="envelope-category">
            <option value="">Sin categoría</option>
            ${categories.map(c => `
              <option value="${c}" ${envelope.category === c ? 'selected' : ''}>
                ${c}
              </option>
            `).join('')}
          </select>
        </div>

        <div class="envelope-preview">
          <div class="envelope-card">
            <div class="envelope-name">${envelope.name || 'Nombre del apartado'}</div>
            <div class="envelope-balance">$${(envelope.current_amount || 0).toFixed(2)}</div>
          </div>
        </div>

        ${isEdit ? `
          <div class="envelope-progress">
            <div class="progress-info">
              <span>Balance Actual</span>
              <span class="progress-amount">$${(envelope.current_amount || 0).toFixed(2)} / $${(envelope.budget_amount || 0).toFixed(2)}</span>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" style="width: ${this.calculateProgress(envelope)}%"></div>
            </div>
            <div class="progress-percentage">${this.calculateProgress(envelope)}%</div>
          </div>
        ` : ''}

        <div class="form-actions">
          <button type="button" class="btn-secondary btn-cancel-envelope">Cancelar</button>
          <button type="button" class="btn-primary btn-save-envelope">
            ${isEdit ? 'Guardar Cambios' : 'Crear Apartado'}
          </button>
        </div>
      </div>
    `;

    this.setupPreviewUpdates();
    this.attachEventListeners();
  }

  setupPreviewUpdates() {
    const nameInput = this.querySelector('#envelope-name');
    const preview = this.querySelector('.envelope-card');
    const previewName = preview?.querySelector('.envelope-name');

    nameInput?.addEventListener('input', (e) => {
      if (previewName) previewName.textContent = e.target.value || 'Nombre del apartado';
    });
  }

  attachEventListeners() {
    const cancelBtn = this.querySelector('.btn-cancel-envelope');
    const saveBtn = this.querySelector('.btn-save-envelope');

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
    const name = this.querySelector('#envelope-name').value.trim();
    const budget = parseFloat(this.querySelector('#envelope-budget').value);

    if (!name) {
      Swal.fire({
        icon: 'warning',
        title: 'Campo Requerido',
        text: 'Por favor ingresa un nombre para el apartado'
      });
      return false;
    }

    if (!budget || budget <= 0) {
      Swal.fire({
        icon: 'warning',
        title: 'Campo Requerido',
        text: 'Por favor ingresa un monto presupuestado mayor a 0'
      });
      return false;
    }

    return true;
  }

  getFormData() {
    const isEdit = !!this.envelopeData;
    const budgetAmount = parseFloat(this.querySelector('#envelope-budget').value);
    
    const formData = {
      name: this.querySelector('#envelope-name').value.trim(),
      description: this.querySelector('#envelope-description').value.trim() || null,
      category: this.querySelector('#envelope-category').value || null,
      budget_amount: budgetAmount,
      period_type: this.querySelector('#envelope-period').value || 'monthly',
      active: true
    };

    if (isEdit) {
      formData.id = this.envelopeData.id;
      formData.current_amount = this.envelopeData.current_amount || 0;
    } else {
      formData.current_amount = 0;
    }

    return formData;
  }

  calculateProgress(envelope) {
    if (!envelope || !envelope.budget_amount || envelope.budget_amount === 0) return 0;
    return Math.min(Math.round((envelope.current_amount / envelope.budget_amount) * 100), 100);
  }
}

customElements.define('envelope-form', EnvelopeForm);
export default EnvelopeForm;
