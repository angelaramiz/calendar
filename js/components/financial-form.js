import "./frequency-toggle.js";

const template = document.createElement("template");
template.innerHTML = `
<style>
    :host { display:block; font-family: inherit; color: #222; }
    label { display:block; margin-top:8px; font-weight:600; }
    input, textarea, select { width:100%; padding:8px; border-radius:6px; border:1px solid #ddd; box-sizing:border-box; font-size:0.95rem; }
    .actions{ display:flex; gap:8px; justify-content:flex-end; margin-top:12px; }
    button{ padding:8px 12px; border-radius:6px; border:none; cursor:pointer; }
    button.save{ background:#2d8cf0; color:#fff }
    button.cancel{ background:#ccc }
</style>

<div>
    <label for="title">Título</label>
    <input id="title" type="text" placeholder="Ej: Salario, Alquiler">

    <label for="desc">Descripción</label>
    <textarea id="desc" rows="3" placeholder="Opcional"></textarea>

    <label id="amount-label" for="amount" style="display:none">Monto</label>
    <input id="amount" type="number" placeholder="Monto" style="display:none">

    <label id="category-label" for="category" style="display:none">Categoría</label>
    <select id="category" style="display:none">
        <option value="">Seleccionar categoría</option>
    </select>

    <label id="loan-label" style="display:none;margin-top:8px"><input id="loan-checkbox" type="checkbox" /> <span id="loan-text">Préstamo</span></label>
    
    <div id="loan-advanced" style="display:none;margin-top:12px;padding:12px;background:#f9f9f9;border-radius:8px;border:1px solid #e0e0e0">
        <h4 style="margin:0 0 10px 0;font-size:0.95rem;color:#555">⚙️ Configuración Avanzada del Préstamo</h4>
        
        <label for="loan-expected-return">Retorno/Pago Esperado ($)</label>
        <input id="loan-expected-return" type="number" min="0" step="0.01" placeholder="Monto a recibir/pagar">
        
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:8px">
            <div>
                <label for="loan-interest-value">Interés ($)</label>
                <input id="loan-interest-value" type="number" min="0" step="0.01" placeholder="Ej: 50">
            </div>
            <div>
                <label for="loan-interest-percent">Interés (%)</label>
                <input id="loan-interest-percent" type="number" min="0" step="0.01" placeholder="Ej: 5">
            </div>
        </div>
        
        <label for="loan-payment-plan" style="margin-top:8px">Plan de Pagos</label>
        <select id="loan-payment-plan">
            <option value="single">Pago único</option>
            <option value="weekly">Semanal</option>
            <option value="biweekly">Quincenal</option>
            <option value="monthly">Mensual</option>
            <option value="custom">Personalizado</option>
        </select>
        
        <div id="loan-single-payment" style="display:block;margin-top:8px">
            <label for="loan-recovery-days">Días hasta el pago</label>
            <input id="loan-recovery-days" type="number" min="1" placeholder="Ej: 30">
        </div>
        
        <div id="loan-recurring-payment" style="display:none;margin-top:8px">
            <label for="loan-payment-frequency">Frecuencia de pagos</label>
            <input id="loan-payment-frequency" type="number" min="1" value="1" placeholder="Cada cuántos días/semanas">
            
            <label for="loan-payment-count" style="margin-top:8px">Número de pagos</label>
            <input id="loan-payment-count" type="number" min="1" value="1" placeholder="Total de cuotas">
        </div>
        
        <div id="loan-custom-payment" style="display:none;margin-top:8px">
            <label for="loan-custom-dates">Fechas específicas (separadas por coma)</label>
            <input id="loan-custom-dates" type="text" placeholder="Ej: 2025-12-01, 2025-12-15">
        </div>
        
        <label for="loan-notes" style="margin-top:8px">Notas adicionales</label>
        <textarea id="loan-notes" rows="2" placeholder="Información adicional sobre el préstamo"></textarea>
    </div>

    <div id="frequency-section">
        <label style="margin-top:10px; font-weight:600">Frecuencia</label>
        <frequency-toggle id="freq"></frequency-toggle>
    </div>

    <div class="actions">
        <button class="cancel" id="cancel">Cancelar</button>
        <button class="save" id="save">Guardar</button>
    </div>
</div>
`;

// Centralized category options
const CATEGORY_OPTIONS = {
    gasto: [
        { v: '', t: 'Seleccionar categoría' },
        { v: 'vivienda', t: 'Vivienda' },
        { v: 'transporte', t: 'Transporte' },
        { v: 'alimentacion', t: 'Alimentación' },
        { v: 'salud', t: 'Salud' },
        { v: 'entretenimiento', t: 'Entretenimiento' },
        { v: 'educacion', t: 'Educación' },
        { v: 'servicios', t: 'Servicios' },
        { v: 'otros', t: 'Otros' }
    ],
    ingreso: [
        { v: '', t: 'Seleccionar categoría' },
        { v: 'salario', t: 'Salario' },
        { v: 'freelance', t: 'Freelance' },
        { v: 'inversiones', t: 'Inversiones' },
        { v: 'reembolso', t: 'Reembolso' },
        { v: 'otros', t: 'Otros' }
    ]
};

class FinancialForm extends HTMLElement {
    constructor() {
        super();
        this._shadow = this.attachShadow({ mode: "open" });
        this._shadow.appendChild(template.content.cloneNode(true));

        this._title = this._shadow.getElementById("title");
        this._desc = this._shadow.getElementById("desc");
        this._amount = this._shadow.getElementById("amount");
        this._amountLabel = this._shadow.getElementById("amount-label");
        this._category = this._shadow.getElementById("category");
        this._categoryLabel = this._shadow.getElementById("category-label");
        this._loanCheckbox = this._shadow.getElementById("loan-checkbox");
        this._loanLabel = this._shadow.getElementById("loan-label");
        this._loanText = this._shadow.getElementById("loan-text");
        this._loanAdvanced = this._shadow.getElementById("loan-advanced");
        this._loanExpectedReturn = this._shadow.getElementById("loan-expected-return");
        this._loanInterestValue = this._shadow.getElementById("loan-interest-value");
        this._loanInterestPercent = this._shadow.getElementById("loan-interest-percent");
        this._loanPaymentPlan = this._shadow.getElementById("loan-payment-plan");
        this._loanRecoveryDays = this._shadow.getElementById("loan-recovery-days");
        this._loanSinglePayment = this._shadow.getElementById("loan-single-payment");
        this._loanRecurringPayment = this._shadow.getElementById("loan-recurring-payment");
        this._loanCustomPayment = this._shadow.getElementById("loan-custom-payment");
        this._loanPaymentFrequency = this._shadow.getElementById("loan-payment-frequency");
        this._loanPaymentCount = this._shadow.getElementById("loan-payment-count");
        this._loanCustomDates = this._shadow.getElementById("loan-custom-dates");
        this._loanNotes = this._shadow.getElementById("loan-notes");
        this._frequencySection = this._shadow.getElementById("frequency-section");
        this._freq = this._shadow.getElementById("freq");
        this._save = this._shadow.getElementById("save");
        this._cancel = this._shadow.getElementById("cancel");

        this._onSave = this._onSave.bind(this);
        this._onCancel = this._onCancel.bind(this);
        this._onLoanToggle = this._onLoanToggle.bind(this);
        this._onInterestChange = this._onInterestChange.bind(this);
        this._onPaymentPlanChange = this._onPaymentPlanChange.bind(this);
    }

    connectedCallback() {
        this._type = this.getAttribute("type") || "gasto";
        // show amount for both types
        if (this._type === "ingreso" || this._type === "gasto") {
            this._amount.style.display = "block";
            this._amountLabel.style.display = "block";
        }

        // configure category/select depending on type
        this._category.style.display = "block";
        this._categoryLabel.style.display = "block";
        this._categoryLabel.textContent = "Categoría";

        if (this._type === "gasto") {
            this._amountLabel.textContent = "Monto del Gasto";
            this._amount.placeholder = "Monto gastado";
        } else if (this._type === "ingreso") {
            this._amountLabel.textContent = "Monto";
            this._amount.placeholder = "Monto esperado";
        }

        // populate select from centralized options depending on type
        const opts = CATEGORY_OPTIONS[this._type] || CATEGORY_OPTIONS.gasto;
        this._category.innerHTML = opts.map(o => `<option value="${o.v}">${o.t}</option>`).join('');

        // configure loan checkbox text and behaviour
        if (this._loanLabel) {
            this._loanLabel.style.display = 'block';
            this._loanText.textContent = this._type === 'gasto' ? 'Préstamo a favor' : 'Préstamo en contra';
        }
        if (this._loanCheckbox) {
            this._loanCheckbox.addEventListener('change', this._onLoanToggle);
        }
        
        // Interest auto-calculation
        if (this._loanInterestValue) {
            this._loanInterestValue.addEventListener('input', () => this._onInterestChange('value'));
        }
        if (this._loanInterestPercent) {
            this._loanInterestPercent.addEventListener('input', () => this._onInterestChange('percent'));
        }
        
        // Payment plan change
        if (this._loanPaymentPlan) {
            this._loanPaymentPlan.addEventListener('change', this._onPaymentPlanChange);
        }

        this._save.addEventListener("click", this._onSave);
        this._cancel.addEventListener("click", this._onCancel);
    }

    disconnectedCallback() {
        this._save.removeEventListener("click", this._onSave);
        this._cancel.removeEventListener("click", this._onCancel);
        if (this._loanCheckbox) this._loanCheckbox.removeEventListener('change', this._onLoanToggle);
        if (this._loanInterestValue) this._loanInterestValue.removeEventListener('input', this._onInterestChange);
        if (this._loanInterestPercent) this._loanInterestPercent.removeEventListener('input', this._onInterestChange);
        if (this._loanPaymentPlan) this._loanPaymentPlan.removeEventListener('change', this._onPaymentPlanChange);
    }

    _onCancel() {
        this.dispatchEvent(
            new CustomEvent("cancel", { bubbles: true, composed: true })
        );
    }

    _onLoanToggle(e) {
        const isChecked = e.target.checked;
        if (this._loanAdvanced) {
            this._loanAdvanced.style.display = isChecked ? 'block' : 'none';
        }
        // Ocultar frecuencia normal cuando el préstamo está activado
        if (this._frequencySection) {
            this._frequencySection.style.display = isChecked ? 'none' : 'block';
        }
    }
    
    _onInterestChange(source) {
        const amount = parseFloat(this._amount.value) || 0;
        if (amount <= 0) return;
        
        if (source === 'value') {
            // Calcular porcentaje desde valor
            const interestValue = parseFloat(this._loanInterestValue.value) || 0;
            const percent = (interestValue / amount) * 100;
            this._loanInterestPercent.value = percent > 0 ? percent.toFixed(2) : '';
            
            // Actualizar retorno esperado
            const expectedReturn = amount + interestValue;
            this._loanExpectedReturn.value = expectedReturn.toFixed(2);
        } else if (source === 'percent') {
            // Calcular valor desde porcentaje
            const interestPercent = parseFloat(this._loanInterestPercent.value) || 0;
            const interestValue = (amount * interestPercent) / 100;
            this._loanInterestValue.value = interestValue > 0 ? interestValue.toFixed(2) : '';
            
            // Actualizar retorno esperado
            const expectedReturn = amount + interestValue;
            this._loanExpectedReturn.value = expectedReturn.toFixed(2);
        }
    }
    
    _onPaymentPlanChange(e) {
        const plan = e.target.value;
        
        // Ocultar todos los planes
        if (this._loanSinglePayment) this._loanSinglePayment.style.display = 'none';
        if (this._loanRecurringPayment) this._loanRecurringPayment.style.display = 'none';
        if (this._loanCustomPayment) this._loanCustomPayment.style.display = 'none';
        
        // Mostrar el plan seleccionado
        if (plan === 'single') {
            this._loanSinglePayment.style.display = 'block';
        } else if (plan === 'custom') {
            this._loanCustomPayment.style.display = 'block';
        } else {
            // weekly, biweekly, monthly
            this._loanRecurringPayment.style.display = 'block';
        }
    }

    _onSave() {
        const title = this._title.value.trim();
        if (!title) {
            // small inline feedback: focus
            this._title.focus();
            return;
        }
        const detail = {
            title,
            desc: this._desc.value.trim(),
            amount:
                this._type === "ingreso" || this._type === "gasto"
                    ? this._amount.value
                        ? Number(this._amount.value)
                        : null
                    : null,
            ...this._freq.value // frequency, interval, limit
        };

        // Agregar categoría si existe el select (para ingresos y gastos)
        if (this._category) {
            detail.category = this._category.value || '';
        }

        // Agregar loan info si está activado
        if (this._loanCheckbox && this._loanCheckbox.checked) {
            const loanData = {
                kind: this._type === 'gasto' ? 'favor' : 'contra',
                expectedReturn: this._loanExpectedReturn && this._loanExpectedReturn.value ? Number(this._loanExpectedReturn.value) : null,
                interestValue: this._loanInterestValue && this._loanInterestValue.value ? Number(this._loanInterestValue.value) : null,
                interestPercent: this._loanInterestPercent && this._loanInterestPercent.value ? Number(this._loanInterestPercent.value) : null,
                paymentPlan: this._loanPaymentPlan ? this._loanPaymentPlan.value : 'single',
                notes: this._loanNotes && this._loanNotes.value ? this._loanNotes.value.trim() : ''
            };
            
            // Agregar datos específicos según el plan de pago
            if (loanData.paymentPlan === 'single') {
                loanData.recoveryDays = this._loanRecoveryDays && this._loanRecoveryDays.value ? Number(this._loanRecoveryDays.value) : null;
            } else if (loanData.paymentPlan === 'custom') {
                loanData.customDates = this._loanCustomDates && this._loanCustomDates.value 
                    ? this._loanCustomDates.value.split(',').map(d => d.trim()).filter(d => d)
                    : [];
            } else {
                // weekly, biweekly, monthly
                loanData.paymentFrequency = this._loanPaymentFrequency && this._loanPaymentFrequency.value ? Number(this._loanPaymentFrequency.value) : 1;
                loanData.paymentCount = this._loanPaymentCount && this._loanPaymentCount.value ? Number(this._loanPaymentCount.value) : 1;
            }
            
            detail.loan = loanData;
        }

        this.dispatchEvent(
            new CustomEvent("save", { detail, bubbles: true, composed: true })
        );
    }

    // helper to set initial values programmatically
    setInitial(data = {}) {
        if (data.title) this._title.value = data.title;
        if (data.desc) this._desc.value = data.desc;
        if (this._type === "ingreso" || this._type === "gasto") {
            if (data.amount !== undefined && data.amount !== null) {
                this._amount.value = data.amount;
            }
        }
        if ((this._type === "gasto" || this._type === "ingreso") && data.category !== undefined) {
            // if the category isn't one of the options, add it so it's visible/selectable
            const exists = Array.from(this._category.options).some(o => o.value === String(data.category));
            if (!exists && data.category) {
                const opt = document.createElement('option');
                opt.value = data.category;
                opt.textContent = data.category;
                this._category.appendChild(opt);
            }
            this._category.value = data.category || '';
        }
        // load loan initial state
        if (data.loan) {
            try {
                if (this._loanCheckbox) {
                    this._loanCheckbox.checked = true;
                    if (this._loanAdvanced) {
                        this._loanAdvanced.style.display = 'block';
                    }
                    if (this._frequencySection) {
                        this._frequencySection.style.display = 'none';
                    }
                    
                    // Cargar valores avanzados
                    if (this._loanExpectedReturn && data.loan.expectedReturn) {
                        this._loanExpectedReturn.value = data.loan.expectedReturn;
                    }
                    if (this._loanInterestValue && data.loan.interestValue) {
                        this._loanInterestValue.value = data.loan.interestValue;
                    }
                    if (this._loanInterestPercent && data.loan.interestPercent) {
                        this._loanInterestPercent.value = data.loan.interestPercent;
                    }
                    if (this._loanPaymentPlan && data.loan.paymentPlan) {
                        this._loanPaymentPlan.value = data.loan.paymentPlan;
                        this._onPaymentPlanChange({ target: { value: data.loan.paymentPlan } });
                    }
                    if (this._loanRecoveryDays && data.loan.recoveryDays) {
                        this._loanRecoveryDays.value = data.loan.recoveryDays;
                    }
                    if (this._loanPaymentFrequency && data.loan.paymentFrequency) {
                        this._loanPaymentFrequency.value = data.loan.paymentFrequency;
                    }
                    if (this._loanPaymentCount && data.loan.paymentCount) {
                        this._loanPaymentCount.value = data.loan.paymentCount;
                    }
                    if (this._loanCustomDates && data.loan.customDates && data.loan.customDates.length) {
                        this._loanCustomDates.value = data.loan.customDates.join(', ');
                    }
                    if (this._loanNotes && data.loan.notes) {
                        this._loanNotes.value = data.loan.notes;
                    }
                }
            } catch (e) { /* ignore */ }
        }
        if (data.frequency || data.interval || data.limit) {
            this._freq.setValue({
                frequency: data.frequency || '',
                interval: data.interval || 1,
                limit: data.limit || 6
            });
        }
    }
}

if (!customElements.get("financial-form")) {
    customElements.define("financial-form", FinancialForm);
}

export default FinancialForm;
