const template = document.createElement('template');
template.innerHTML = `
<style>
  :host { display:block; font-family: inherit; }
  .header { cursor: pointer; font-weight: 600; user-select: none; color: #333; }
  .header[aria-expanded="true"]::after { content: ' ▲'; }
  .header[aria-expanded="false"]::after { content: ' ▼'; }
  .content { display: none; margin-top: 10px; }
  .content[open] { display: block; }
  select, input { width:100%; padding:8px; border-radius:6px; border:1px solid #ddd; font-size: 0.95rem; box-sizing: border-box; }
  label { display:block; margin-top:8px; font-weight:600; }
</style>

<div>
  <label class="header" part="header" role="button" tabindex="0" aria-expanded="false">Frecuencia de repetición</label>
  <div class="content" part="content">
    <select part="select">
      <option value="">Ninguna</option>
      <option value="semanal">semanal</option>
      <option value="mensual">mensual</option>
      <option value="anual">anual</option>
    </select>
    <br><br>
    <label>Intervalo de ciclo</label>
    <input part="interval" type="number" min="1" value="1">
    <br><br>
    <label>Límite de ciclo</label>
    <input part="limit" type="number" min="1" value="6">
  </div>
</div>
`;

class FrequencyToggle extends HTMLElement {
    constructor() {
        super();
        this._shadow = this.attachShadow({ mode: 'open' });
        this._shadow.appendChild(template.content.cloneNode(true));
        this._header = this._shadow.querySelector('.header');
        this._content = this._shadow.querySelector('.content');
        this._select = this._shadow.querySelector('select');
        this._interval = this._shadow.querySelector('input[part="interval"]');
        this._limit = this._shadow.querySelector('input[part="limit"]');

        this._onHeaderKey = this._onHeaderKey.bind(this);
        this._onHeaderClick = this._onHeaderClick.bind(this);
        this._onSelectChange = this._onSelectChange.bind(this);
    }

    connectedCallback() {
        this._header.addEventListener('click', this._onHeaderClick);
        this._header.addEventListener('keydown', this._onHeaderKey);
        this._select.addEventListener('change', this._onSelectChange);

        // option to set initial values via attributes
        if (this.hasAttribute('frequency')) this._select.value = this.getAttribute('frequency');
        if (this.hasAttribute('interval')) this._interval.value = this.getAttribute('interval');
        if (this.hasAttribute('limit')) this._limit.value = this.getAttribute('limit');

        if (this._select.value) this.open = true;
        else this.open = false;
    }

    disconnectedCallback() {
        this._header.removeEventListener('click', this._onHeaderClick);
        this._header.removeEventListener('keydown', this._onHeaderKey);
        this._select.removeEventListener('change', this._onSelectChange);
    }

    _onHeaderKey(e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            this._toggle();
        }
    }

    _onHeaderClick() { this._toggle(); }

    _onSelectChange() {
        if (this._select.value) this.open = true;
        else this.open = false;
        this._emitChange();
    }

    _toggle() {
        this.open = !this.open;
        this._emitToggle();
    }

    _emitChange() {
        this.dispatchEvent(new CustomEvent('frequency-change', {
            detail: this.value,
            bubbles: true,
            composed: true
        }));
    }

    _emitToggle() {
        this.dispatchEvent(new CustomEvent('toggle', {
            detail: { open: this.open },
            bubbles: true,
            composed: true
        }));
    }

    get open() { return this._content.hasAttribute('open'); }
    set open(val) {
        if (val) {
            this._content.setAttribute('open', '');
            this._header.setAttribute('aria-expanded', 'true');
        } else {
            this._content.removeAttribute('open');
            this._header.setAttribute('aria-expanded', 'false');
        }
    }

    get value() {
        return {
            frequency: this._select.value,
            interval: Number(this._interval.value) || 1,
            limit: Number(this._limit.value) || 6
        };
    }

    setValue({ frequency, interval, limit } = {}) {
        if (frequency !== undefined) this._select.value = frequency;
        if (interval !== undefined) this._interval.value = interval;
        if (limit !== undefined) this._limit.value = limit;
        if (this._select.value) this.open = true;
    }
}

if (!customElements.get('frequency-toggle')) {
    customElements.define('frequency-toggle', FrequencyToggle);
}

export default FrequencyToggle;
