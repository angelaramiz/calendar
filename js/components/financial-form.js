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
    <input id="loan-time" type="number" min="0" placeholder="Recupero en días (opcional)" style="display:none;margin-top:6px;padding:8px;border-radius:6px;border:1px solid #ddd;" />

    <label style="margin-top:10px; font-weight:600">Frecuencia</label>
    <frequency-toggle id="freq"></frequency-toggle>

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
    this._loanTime = this._shadow.getElementById("loan-time");
        this._freq = this._shadow.getElementById("freq");
        this._save = this._shadow.getElementById("save");
        this._cancel = this._shadow.getElementById("cancel");

        this._onSave = this._onSave.bind(this);
        this._onCancel = this._onCancel.bind(this);
        this._onLoanToggle = this._onLoanToggle.bind(this);
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

        this._save.addEventListener("click", this._onSave);
        this._cancel.addEventListener("click", this._onCancel);
    }

    disconnectedCallback() {
        this._save.removeEventListener("click", this._onSave);
        this._cancel.removeEventListener("click", this._onCancel);
        if (this._loanCheckbox) this._loanCheckbox.removeEventListener('change', this._onLoanToggle);
    }

    _onCancel() {
        this.dispatchEvent(
            new CustomEvent("cancel", { bubbles: true, composed: true })
        );
    }

    _onLoanToggle(e) {
        if (this._loanTime) {
            this._loanTime.style.display = e.target.checked ? 'block' : 'none';
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
            detail.loan = {
                kind: this._type === 'gasto' ? 'favor' : 'contra',
                recoveryDays: this._loanTime && this._loanTime.value ? Number(this._loanTime.value) : null
            };
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
                    if (this._loanTime) {
                        this._loanTime.style.display = 'block';
                        this._loanTime.value = data.loan.recoveryDays || '';
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
