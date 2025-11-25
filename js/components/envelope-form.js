/**
 * Envelope Form Component - Formulario para crear/editar apartados de ahorro
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

    const colors = [
      { value: '#6366f1', name: 'Azul' },
      { value: '#8b5cf6', name: 'Morado' },
      { value: '#ec4899', name: 'Rosa' },
      { value: '#f43f5e', name: 'Rojo' },
      { value: '#f97316', name: 'Naranja' },
      { value: '#eab308', name: 'Amarillo' },
      { value: '#22c55e', name: 'Verde' },
      { value: '#14b8a6', name: 'Turquesa' }
    ];

    const icons = ['💰', '🏠', '🚗', '✈️', '🎓', '💡', '🎁', '🏥', '🛒', '💳', '📱', '🎮'];

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
          <label for="envelope-target">Monto Objetivo</label>
          <input 
            type="number" 
            id="envelope-target" 
            value="${envelope.target_amount || ''}" 
            placeholder="0.00" 
            step="0.01"
            min="0"
          />
          <small>Opcional - Define cuánto quieres ahorrar en este apartado</small>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="envelope-color">Color</label>
            <select id="envelope-color">
              ${colors.map(c => `
                <option value="${c.value}" ${envelope.color === c.value ? 'selected' : ''}>
                  ${c.name}
                </option>
              `).join('')}
            </select>
          </div>

          <div class="form-group">
            <label for="envelope-icon">Ícono</label>
            <select id="envelope-icon">
              ${icons.map(icon => `
                <option value="${icon}" ${(envelope.emoji || envelope.icon) === icon ? 'selected' : ''}>
                  ${icon}
                </option>
              `).join('')}
            </select>
          </div>
        </div>

        <div class="envelope-preview">
          <div class="envelope-card" style="border-color: ${envelope.color || '#6366f1'}">
            <div class="envelope-icon">${envelope.emoji || envelope.icon || '💰'}</div>
            <div class="envelope-name">${envelope.name || 'Nombre del apartado'}</div>
            <div class="envelope-balance">$${envelope.current_balance?.toFixed(2) || '0.00'}</div>
          </div>
        </div>

        ${isEdit ? `
          <div class="envelope-progress">
            <div class="progress-info">
              <span>Balance Actual</span>
              <span class="progress-amount">$${envelope.current_balance?.toFixed(2) || '0.00'}${envelope.target_amount ? ` / $${envelope.target_amount.toFixed(2)}` : ''}</span>
            </div>
            ${envelope.target_amount ? `
              <div class="progress-bar">
                <div class="progress-fill" style="width: ${this.calculateProgress(envelope)}%; background: ${envelope.color || '#6366f1'}"></div>
              </div>
              <div class="progress-percentage">${this.calculateProgress(envelope)}%</div>
            ` : ''}
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
    // Attach event listeners after rendering
    this.attachEventListeners();
  }

  setupPreviewUpdates() {
    const nameInput = this.querySelector('#envelope-name');
    const colorInput = this.querySelector('#envelope-color');
    const iconInput = this.querySelector('#envelope-icon');
    const preview = this.querySelector('.envelope-card');
    const previewIcon = preview?.querySelector('.envelope-icon');
    const previewName = preview?.querySelector('.envelope-name');

    nameInput?.addEventListener('input', (e) => {
      if (previewName) previewName.textContent = e.target.value || 'Nombre del apartado';
    });

    colorInput?.addEventListener('change', (e) => {
      if (preview) preview.style.borderColor = e.target.value;
    });

    iconInput?.addEventListener('change', (e) => {
      if (previewIcon) previewIcon.textContent = e.target.value;
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

    if (!name) {
      Swal.fire({
        icon: 'warning',
        title: 'Campo Requerido',
        text: 'Por favor ingresa un nombre para el apartado'
      });
      return false;
    }

    return true;
  }

  getFormData() {
    const isEdit = !!this.envelopeData;
    const targetAmount = parseFloat(this.querySelector('#envelope-target').value);
    
    const formData = {
      name: this.querySelector('#envelope-name').value.trim(),
      type: 'savings', // Tipo por defecto, puede ser savings, sinking_fund o budget
      target_amount: targetAmount > 0 ? targetAmount : null,
      color: this.querySelector('#envelope-color').value,
      icon: this.querySelector('#envelope-icon').value,
      is_active: true
    };

    if (isEdit) {
      formData.id = this.envelopeData.id;
      formData.current_balance = this.envelopeData.current_balance || 0;
    } else {
      formData.current_balance = 0;
    }

    return formData;
  }

  calculateProgress(envelope) {
    if (!envelope || !envelope.target_amount || envelope.target_amount === 0) return 0;
    return Math.min(Math.round((envelope.current_balance / envelope.target_amount) * 100), 100);
  }
}

customElements.define('envelope-form', EnvelopeForm);
export default EnvelopeForm;
