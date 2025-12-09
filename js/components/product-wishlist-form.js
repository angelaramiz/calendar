/**
 * product-wishlist-form.js
 * Web Component para el formulario de productos en línea
 * Incluye scraping automático y selección de plan financiero
 */

import * as ProductWishlist from '../product-wishlist.js';

class ProductWishlistForm extends HTMLElement {
    constructor() {
        super();
        this.productData = null;
        this.selectedIncome = null;
        this.planOptions = [];
        this.selectedPlan = null;
        this.isEditing = false;
        this.productId = null;
    }

    connectedCallback() {
        try {
            this.render();
            this.attachEvents();
        } catch (error) {
            console.error('[ProductWishlistForm] Error in connectedCallback:', error);
        }
    }

    static get observedAttributes() {
        return ['product-id'];
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (name === 'product-id' && newValue) {
            this.productId = newValue;
            this.isEditing = true;
            this.loadExistingProduct();
        }
    }

    async loadExistingProduct() {
        try {
            const product = await ProductWishlist.getProductById(this.productId);
            if (product) {
                this.productData = {
                    name: product.product_name,
                    price: product.product_price,
                    image: product.product_image_url,
                    store: product.product_store,
                    url: product.product_url
                };
                this.selectedPlan = {
                    type: product.plan_type,
                    monthlyContribution: product.contribution_value,
                    contributionType: product.contribution_type
                };
                this.render();
                this.showStep('step-review');
            }
        } catch (error) {
            console.error('Error loading product:', error);
        }
    }

    render() {
        this.innerHTML = `
            <div class="product-wishlist-form">
                <!-- Step 1: URL Input -->
                <div class="form-step active" id="step-url">
                    <div class="step-header">
                        <span class="step-number">1</span>
                        <h3>🔗 Pega el enlace del producto</h3>
                    </div>
                    <div class="step-content">
                        <div class="form-group">
                            <label for="product-url">URL del producto</label>
                            <div class="url-input-wrapper">
                                <input type="url" id="product-url" 
                                    placeholder="https://www.amazon.com.mx/dp/..." 
                                    autocomplete="off" />
                                <button type="button" id="btn-scrape" class="btn-primary">
                                    <span class="btn-text">🔍 Buscar</span>
                                    <span class="btn-loader" style="display:none;">⏳</span>
                                </button>
                            </div>
                            <small class="help-text">Pega la URL completa del producto (funciona mejor con Amazon)</small>
                        </div>
                        <div id="scrape-error" class="error-message" style="display:none;"></div>
                        
                        <div class="manual-entry-divider">
                            <span>o también puedes</span>
                        </div>
                        
                        <button type="button" id="btn-manual-entry" class="btn-secondary btn-full">
                            ✏️ Agregar producto manualmente
                        </button>
                    </div>
                    <div class="supported-stores">
                        <span>Tiendas soportadas:</span>
                        <div class="store-badges">
                            <span class="store-badge">📦 Amazon ✓</span>
                            <span class="store-badge">🛒 Mercado Libre</span>
                            <span class="store-badge">🌐 Otras</span>
                        </div>
                    </div>
                </div>

                <!-- Step 2: Product Preview -->
                <div class="form-step" id="step-preview">
                    <div class="step-header">
                        <span class="step-number">2</span>
                        <h3>📦 Confirma el producto</h3>
                    </div>
                    <div class="step-content">
                        <div class="product-preview-card" id="product-preview">
                            <!-- Se llena dinámicamente -->
                        </div>
                        <div class="form-group" style="margin-top: 16px;">
                            <label for="product-notes">Notas adicionales (opcional)</label>
                            <textarea id="product-notes" rows="2" placeholder="Ej: Es el modelo que quiero, color azul..."></textarea>
                        </div>
                    </div>
                    <div class="step-actions">
                        <button type="button" id="btn-back-url" class="btn-secondary">← Cambiar URL</button>
                        <button type="button" id="btn-next-income" class="btn-primary">Continuar →</button>
                    </div>
                </div>

                <!-- Step 3: Select Income Source -->
                <div class="form-step" id="step-income">
                    <div class="step-header">
                        <span class="step-number">3</span>
                        <h3>💰 Selecciona el ingreso</h3>
                    </div>
                    <div class="step-content">
                        <p class="step-description">¿De qué ingreso dependerá esta planificación?</p>
                        <div class="income-list" id="income-list">
                            <!-- Se llena dinámicamente -->
                        </div>
                        <div id="no-incomes-message" class="empty-state" style="display:none;">
                            <div class="empty-icon">💸</div>
                            <div class="empty-text">No tienes ingresos configurados</div>
                            <div class="empty-hint">Primero debes crear un patrón de ingreso para poder planificar</div>
                        </div>
                    </div>
                    <div class="step-actions">
                        <button type="button" id="btn-back-preview" class="btn-secondary">← Atrás</button>
                        <button type="button" id="btn-analyze" class="btn-primary" disabled>Analizar opciones →</button>
                    </div>
                </div>

                <!-- Step 4: Plan Options -->
                <div class="form-step" id="step-plan">
                    <div class="step-header">
                        <span class="step-number">4</span>
                        <h3>📊 Elige tu plan</h3>
                    </div>
                    <div class="step-content">
                        <div class="analysis-summary" id="analysis-summary">
                            <!-- Se llena dinámicamente -->
                        </div>
                        <div class="plan-options" id="plan-options">
                            <!-- Se llena dinámicamente -->
                        </div>
                        <div class="custom-plan-section" style="margin-top: 16px;">
                            <button type="button" id="btn-custom-plan" class="btn-link">
                                ⚙️ Configurar manualmente
                            </button>
                            <div id="custom-plan-form" style="display:none;">
                                <div class="form-row">
                                    <div class="form-group">
                                        <label for="custom-contribution-type">Tipo de aportación</label>
                                        <select id="custom-contribution-type">
                                            <option value="fixed">Monto fijo</option>
                                            <option value="percent">Porcentaje del ingreso</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label for="custom-contribution-value">Valor</label>
                                        <input type="number" id="custom-contribution-value" min="1" step="0.01" />
                                    </div>
                                </div>
                                <button type="button" id="btn-apply-custom" class="btn-primary btn-small">
                                    Aplicar configuración
                                </button>
                            </div>
                        </div>
                    </div>
                    <div class="step-actions">
                        <button type="button" id="btn-back-income" class="btn-secondary">← Atrás</button>
                        <button type="button" id="btn-next-review" class="btn-primary" disabled>Revisar y confirmar →</button>
                    </div>
                </div>

                <!-- Step 5: Review & Confirm -->
                <div class="form-step" id="step-review">
                    <div class="step-header">
                        <span class="step-number">5</span>
                        <h3>✅ Resumen final</h3>
                    </div>
                    <div class="step-content">
                        <div class="review-card" id="review-summary">
                            <!-- Se llena dinámicamente -->
                        </div>
                    </div>
                    <div class="step-actions">
                        <button type="button" id="btn-back-plan" class="btn-secondary">← Modificar</button>
                        <button type="button" id="btn-create-product" class="btn-success">
                            <span class="btn-text">🎉 ${this.isEditing ? 'Guardar cambios' : 'Crear planificación'}</span>
                            <span class="btn-loader" style="display:none;">⏳</span>
                        </button>
                    </div>
                </div>
            </div>
        `;

        this.addStyles();
    }

    addStyles() {
        if (!document.getElementById('product-wishlist-form-styles')) {
            const style = document.createElement('style');
            style.id = 'product-wishlist-form-styles';
            style.textContent = `
                .product-wishlist-form {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                }

                .form-step {
                    display: none;
                    animation: fadeIn 0.3s ease;
                }

                .form-step.active {
                    display: block;
                }

                @keyframes fadeIn {
                    from { opacity: 0; transform: translateY(10px); }
                    to { opacity: 1; transform: translateY(0); }
                }

                .step-header {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    margin-bottom: 20px;
                    padding-bottom: 12px;
                    border-bottom: 2px solid #e5e7eb;
                }

                .step-number {
                    width: 32px;
                    height: 32px;
                    background: linear-gradient(135deg, #3b82f6, #1d4ed8);
                    color: white;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-weight: bold;
                }

                .step-header h3 {
                    margin: 0;
                    font-size: 1.25rem;
                    color: #1f2937;
                }

                .step-description {
                    color: #6b7280;
                    margin-bottom: 16px;
                }

                .url-input-wrapper {
                    display: flex;
                    gap: 8px;
                }

                .url-input-wrapper input {
                    flex: 1;
                    padding: 12px 16px;
                    border: 2px solid #e5e7eb;
                    border-radius: 8px;
                    font-size: 1rem;
                    transition: border-color 0.2s;
                }

                .url-input-wrapper input:focus {
                    outline: none;
                    border-color: #3b82f6;
                }

                .help-text {
                    display: block;
                    margin-top: 8px;
                    color: #9ca3af;
                    font-size: 0.875rem;
                }

                .error-message {
                    background: #fee2e2;
                    color: #dc2626;
                    padding: 12px;
                    border-radius: 8px;
                    margin-top: 12px;
                }

                .supported-stores {
                    margin-top: 24px;
                    padding-top: 16px;
                    border-top: 1px solid #e5e7eb;
                }

                .supported-stores > span {
                    display: block;
                    font-size: 0.875rem;
                    color: #6b7280;
                    margin-bottom: 8px;
                }

                .store-badges {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 8px;
                }

                .store-badge {
                    background: #f3f4f6;
                    padding: 6px 12px;
                    border-radius: 20px;
                    font-size: 0.875rem;
                }

                /* Product Preview Card */
                .product-preview-card {
                    display: flex;
                    gap: 20px;
                    padding: 20px;
                    background: #f9fafb;
                    border-radius: 12px;
                    border: 1px solid #e5e7eb;
                }

                .product-preview-card .product-image {
                    width: 120px;
                    height: 120px;
                    object-fit: contain;
                    background: white;
                    border-radius: 8px;
                    border: 1px solid #e5e7eb;
                }

                .product-preview-card .product-info {
                    flex: 1;
                }

                .product-preview-card .product-name {
                    font-size: 1.1rem;
                    font-weight: 600;
                    color: #1f2937;
                    margin-bottom: 8px;
                    line-height: 1.4;
                }

                .product-preview-card .product-store {
                    display: inline-flex;
                    align-items: center;
                    gap: 4px;
                    background: #dbeafe;
                    color: #1d4ed8;
                    padding: 4px 10px;
                    border-radius: 12px;
                    font-size: 0.875rem;
                    margin-bottom: 12px;
                }

                .product-preview-card .product-price {
                    font-size: 1.5rem;
                    font-weight: bold;
                    color: #059669;
                }

                /* Income List */
                .income-list {
                    display: flex;
                    flex-direction: column;
                    gap: 12px;
                }

                .income-item {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 16px;
                    background: white;
                    border: 2px solid #e5e7eb;
                    border-radius: 12px;
                    cursor: pointer;
                    transition: all 0.2s;
                }

                .income-item:hover {
                    border-color: #3b82f6;
                    background: #f0f9ff;
                }

                .income-item.selected {
                    border-color: #3b82f6;
                    background: #dbeafe;
                }

                .income-item .income-info {
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                }

                .income-item .income-name {
                    font-weight: 600;
                    color: #1f2937;
                }

                .income-item .income-frequency {
                    font-size: 0.875rem;
                    color: #6b7280;
                }

                .income-item .income-amount {
                    font-weight: bold;
                    color: #059669;
                    font-size: 1.1rem;
                }

                /* Plan Options */
                .analysis-summary {
                    background: #f0fdf4;
                    padding: 16px;
                    border-radius: 12px;
                    margin-bottom: 20px;
                    border: 1px solid #bbf7d0;
                }

                .analysis-summary .analysis-row {
                    display: flex;
                    justify-content: space-between;
                    padding: 6px 0;
                    border-bottom: 1px solid #d1fae5;
                }

                .analysis-summary .analysis-row:last-child {
                    border-bottom: none;
                    padding-top: 12px;
                    margin-top: 6px;
                    border-top: 2px solid #22c55e;
                    font-weight: bold;
                }

                .plan-options {
                    display: flex;
                    flex-direction: column;
                    gap: 12px;
                }

                .plan-option {
                    padding: 20px;
                    background: white;
                    border: 2px solid #e5e7eb;
                    border-radius: 12px;
                    cursor: pointer;
                    transition: all 0.2s;
                    position: relative;
                }

                .plan-option:hover {
                    border-color: #3b82f6;
                    transform: translateY(-2px);
                    box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                }

                .plan-option.selected {
                    border-color: #3b82f6;
                    background: #eff6ff;
                }

                .plan-option.recommended::after {
                    content: '⭐ Recomendado';
                    position: absolute;
                    top: -10px;
                    right: 12px;
                    background: #fbbf24;
                    color: #78350f;
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 0.75rem;
                    font-weight: 600;
                }

                .plan-option .plan-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-start;
                    margin-bottom: 12px;
                }

                .plan-option .plan-name {
                    font-size: 1.1rem;
                    font-weight: 600;
                    color: #1f2937;
                }

                .plan-option .plan-badge {
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 0.75rem;
                    font-weight: 600;
                }

                .plan-option .plan-badge.short { background: #fee2e2; color: #dc2626; }
                .plan-option .plan-badge.medium { background: #fef3c7; color: #d97706; }
                .plan-option .plan-badge.long { background: #d1fae5; color: #059669; }

                .plan-option .plan-description {
                    color: #6b7280;
                    margin-bottom: 12px;
                }

                .plan-option .plan-details {
                    display: flex;
                    gap: 16px;
                    flex-wrap: wrap;
                }

                .plan-option .plan-detail {
                    display: flex;
                    flex-direction: column;
                }

                .plan-option .plan-detail-label {
                    font-size: 0.75rem;
                    color: #9ca3af;
                    text-transform: uppercase;
                }

                .plan-option .plan-detail-value {
                    font-weight: 600;
                    color: #1f2937;
                }

                /* Review Card */
                .review-card {
                    background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
                    border-radius: 16px;
                    padding: 24px;
                    border: 1px solid #bae6fd;
                }

                .review-card .review-product {
                    display: flex;
                    gap: 16px;
                    padding-bottom: 20px;
                    border-bottom: 1px solid #bae6fd;
                    margin-bottom: 20px;
                }

                .review-card .review-product-image {
                    width: 80px;
                    height: 80px;
                    object-fit: contain;
                    background: white;
                    border-radius: 8px;
                }

                .review-card .review-section {
                    margin-bottom: 16px;
                }

                .review-card .review-section-title {
                    font-size: 0.875rem;
                    color: #0369a1;
                    text-transform: uppercase;
                    margin-bottom: 8px;
                    font-weight: 600;
                }

                .review-card .review-row {
                    display: flex;
                    justify-content: space-between;
                    padding: 8px 0;
                }

                .review-card .review-row span:first-child {
                    color: #6b7280;
                }

                .review-card .review-row span:last-child {
                    font-weight: 600;
                    color: #1f2937;
                }

                .review-card .review-highlight {
                    background: #22c55e;
                    color: white;
                    padding: 16px;
                    border-radius: 12px;
                    text-align: center;
                    margin-top: 20px;
                }

                .review-card .review-highlight .date {
                    font-size: 1.25rem;
                    font-weight: bold;
                }

                /* Step Actions */
                .step-actions {
                    display: flex;
                    justify-content: space-between;
                    margin-top: 24px;
                    padding-top: 20px;
                    border-top: 1px solid #e5e7eb;
                }

                /* Buttons */
                .btn-primary, .btn-secondary, .btn-success {
                    padding: 12px 24px;
                    border-radius: 8px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.2s;
                    border: none;
                    font-size: 1rem;
                }

                .btn-primary {
                    background: linear-gradient(135deg, #3b82f6, #1d4ed8);
                    color: white;
                }

                .btn-primary:hover:not(:disabled) {
                    transform: translateY(-2px);
                    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
                }

                .btn-primary:disabled {
                    background: #9ca3af;
                    cursor: not-allowed;
                }

                .btn-secondary {
                    background: white;
                    color: #4b5563;
                    border: 2px solid #e5e7eb;
                }

                .btn-secondary:hover {
                    background: #f3f4f6;
                }

                .btn-success {
                    background: linear-gradient(135deg, #22c55e, #16a34a);
                    color: white;
                }

                .btn-success:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 4px 12px rgba(34, 197, 94, 0.4);
                }

                .btn-link {
                    background: none;
                    border: none;
                    color: #3b82f6;
                    cursor: pointer;
                    font-size: 0.875rem;
                    padding: 8px 0;
                }

                .btn-link:hover {
                    text-decoration: underline;
                }

                .btn-small {
                    padding: 8px 16px;
                    font-size: 0.875rem;
                }

                /* Form elements */
                .form-group {
                    margin-bottom: 16px;
                }

                .form-group label {
                    display: block;
                    margin-bottom: 8px;
                    font-weight: 500;
                    color: #374151;
                }

                .form-group input,
                .form-group select,
                .form-group textarea {
                    width: 100%;
                    padding: 10px 14px;
                    border: 2px solid #e5e7eb;
                    border-radius: 8px;
                    font-size: 1rem;
                    transition: border-color 0.2s;
                    box-sizing: border-box;
                }

                .form-group input:focus,
                .form-group select:focus,
                .form-group textarea:focus {
                    outline: none;
                    border-color: #3b82f6;
                }

                .form-row {
                    display: flex;
                    gap: 16px;
                }

                .form-row .form-group {
                    flex: 1;
                }

                /* Manual Entry Divider */
                .manual-entry-divider {
                    display: flex;
                    align-items: center;
                    margin: 20px 0;
                    gap: 16px;
                }

                .manual-entry-divider::before,
                .manual-entry-divider::after {
                    content: '';
                    flex: 1;
                    height: 1px;
                    background: #e5e7eb;
                }

                .manual-entry-divider span {
                    color: #9ca3af;
                    font-size: 0.875rem;
                    white-space: nowrap;
                }

                .btn-full {
                    width: 100%;
                    justify-content: center;
                }

                /* Manual Input Notice and Form */
                .manual-input-notice {
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    background: linear-gradient(135deg, #fef3c7, #fde68a);
                    padding: 16px;
                    border-radius: 12px;
                    margin-bottom: 20px;
                    border: 1px solid #f59e0b;
                }

                .manual-input-notice.manual-mode {
                    background: linear-gradient(135deg, #dbeafe, #bfdbfe);
                    border-color: #3b82f6;
                }

                .manual-input-notice.manual-mode .notice-text strong {
                    color: #1e40af;
                }

                .manual-input-notice.manual-mode .notice-text p {
                    color: #1d4ed8;
                }

                .manual-input-notice .notice-icon {
                    font-size: 1.5rem;
                }

                .manual-input-notice .notice-text strong {
                    display: block;
                    color: #92400e;
                    margin-bottom: 4px;
                }

                .manual-input-notice .notice-text p {
                    margin: 0;
                    color: #a16207;
                    font-size: 0.875rem;
                }

                .manual-input-form {
                    background: #f9fafb;
                    padding: 20px;
                    border-radius: 12px;
                    border: 1px solid #e5e7eb;
                }

                .manual-input-form .form-group {
                    margin-bottom: 16px;
                }

                .manual-input-form .form-group:last-child {
                    margin-bottom: 0;
                }

                .manual-input-form .form-row {
                    display: flex;
                    gap: 16px;
                }

                .manual-input-form .form-row .form-group {
                    flex: 1;
                    margin-bottom: 0;
                }

                .manual-input-form label {
                    display: block;
                    margin-bottom: 6px;
                    font-weight: 500;
                    color: #374151;
                    font-size: 0.875rem;
                }

                .manual-input-form input {
                    width: 100%;
                    padding: 10px 14px;
                    border: 2px solid #d1d5db;
                    border-radius: 8px;
                    font-size: 1rem;
                    transition: border-color 0.2s;
                    box-sizing: border-box;
                }

                .manual-input-form input:focus {
                    outline: none;
                    border-color: #3b82f6;
                    background: white;
                }

                .manual-input-form input::placeholder {
                    color: #9ca3af;
                }

                /* Empty state */
                .empty-state {
                    text-align: center;
                    padding: 40px 20px;
                }

                .empty-state .empty-icon {
                    font-size: 3rem;
                    margin-bottom: 12px;
                }

                .empty-state .empty-text {
                    font-weight: 600;
                    color: #4b5563;
                    margin-bottom: 8px;
                }

                .empty-state .empty-hint {
                    color: #9ca3af;
                    font-size: 0.875rem;
                }

                /* Custom Plan Form */
                #custom-plan-form {
                    background: #f9fafb;
                    padding: 16px;
                    border-radius: 12px;
                    margin-top: 12px;
                    border: 1px solid #e5e7eb;
                }
            `;
            document.head.appendChild(style);
        }
    }

    attachEvents() {
        // Step 1: URL Scraping
        const btnScrape = this.querySelector('#btn-scrape');
        const urlInput = this.querySelector('#product-url');

        btnScrape?.addEventListener('click', () => this.handleScrape());
        urlInput?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.handleScrape();
        });
        
        // Botón de entrada manual
        this.querySelector('#btn-manual-entry')?.addEventListener('click', () => this.startManualEntry());

        // Navigation buttons
        this.querySelector('#btn-back-url')?.addEventListener('click', () => this.showStep('step-url'));
        this.querySelector('#btn-next-income')?.addEventListener('click', () => this.validateAndContinue());
        this.querySelector('#btn-back-preview')?.addEventListener('click', () => this.showStep('step-preview'));
        this.querySelector('#btn-analyze')?.addEventListener('click', () => this.analyzeOptions());
        this.querySelector('#btn-back-income')?.addEventListener('click', () => this.showStep('step-income'));
        this.querySelector('#btn-next-review')?.addEventListener('click', () => this.showReview());
        this.querySelector('#btn-back-plan')?.addEventListener('click', () => this.showStep('step-plan'));
        this.querySelector('#btn-create-product')?.addEventListener('click', () => this.createProduct());

        // Custom plan
        this.querySelector('#btn-custom-plan')?.addEventListener('click', () => {
            const form = this.querySelector('#custom-plan-form');
            form.style.display = form.style.display === 'none' ? 'block' : 'none';
        });

        this.querySelector('#btn-apply-custom')?.addEventListener('click', () => this.applyCustomPlan());
    }
    
    // Iniciar entrada manual sin URL
    startManualEntry() {
        this.productData = {
            url: '',
            platform: 'manual',
            name: '',
            price: 0,
            image: '',
            currency: 'MXN',
            store: '',
            needsManualInput: true,
            isFullManual: true
        };
        this.renderProductPreview();
        this.showStep('step-preview');
        
        // Enfocar el primer campo
        setTimeout(() => {
            this.querySelector('#manual-product-name')?.focus();
        }, 100);
    }

    showStep(stepId) {
        this.querySelectorAll('.form-step').forEach(step => step.classList.remove('active'));
        this.querySelector(`#${stepId}`)?.classList.add('active');
    }

    validateAndContinue() {
        // Si fue entrada manual, validar que tenga nombre y precio
        if (this.productData?.needsManualInput) {
            const name = this.productData.name?.trim();
            const price = this.productData.price;
            
            if (!name) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Nombre requerido',
                    text: 'Por favor ingresa el nombre del producto',
                    confirmButtonText: 'Entendido'
                });
                this.querySelector('#manual-product-name')?.focus();
                return;
            }
            
            if (!price || price <= 0) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Precio requerido',
                    text: 'Por favor ingresa el precio del producto',
                    confirmButtonText: 'Entendido'
                });
                this.querySelector('#manual-product-price')?.focus();
                return;
            }
        }
        
        // Continuar al siguiente paso
        this.loadIncomes();
    }

    async handleScrape() {
        const urlInput = this.querySelector('#product-url');
        const btnScrape = this.querySelector('#btn-scrape');
        const errorDiv = this.querySelector('#scrape-error');
        const url = urlInput?.value?.trim();

        if (!url) {
            errorDiv.textContent = 'Por favor, ingresa la URL del producto';
            errorDiv.style.display = 'block';
            return;
        }

        try {
            // Mostrar loading animado
            btnScrape.querySelector('.btn-text').style.display = 'none';
            btnScrape.querySelector('.btn-loader').style.display = 'inline';
            btnScrape.disabled = true;
            errorDiv.style.display = 'none';

            // Crear modal de carga creativo
            this.showScrapingLoadingModal();

            const data = await ProductWishlist.scrapeProduct(url);
            this.productData = data;

            // Cerrar modal de carga
            this.closeScrapingLoadingModal();

            // Mostrar preview (con campos editables si scraping falló)
            this.renderProductPreview();
            this.showStep('step-preview');
            
            // Si el scraping falló, mostrar mensaje y enfocar el primer campo editable
            if (data.needsManualInput) {
                setTimeout(() => {
                    const nameInput = this.querySelector('#manual-product-name');
                    if (nameInput) nameInput.focus();
                }, 100);
            }
        } catch (error) {
            this.closeScrapingLoadingModal();
            errorDiv.textContent = error.message || 'No se pudo obtener información del producto';
            errorDiv.style.display = 'block';
        } finally {
            btnScrape.querySelector('.btn-text').style.display = 'inline';
            btnScrape.querySelector('.btn-loader').style.display = 'none';
            btnScrape.disabled = false;
        }
    }

    showScrapingLoadingModal() {
        const messages = [
            { icon: '🔍', text: 'Buscando producto...', duration: 2000 },
            { icon: '🛒', text: 'Obteniendo información de la tienda...', duration: 3000 },
            { icon: '💰', text: 'Analizando precios...', duration: 3000 },
            { icon: '📸', text: 'Capturando imagen del producto...', duration: 3000 },
            { icon: '✨', text: 'Finalizando...', duration: 2000 }
        ];

        const modalHtml = `
            <div class="scraping-modal-overlay" id="scraping-loader">
                <div class="scraping-modal">
                    <div class="scraping-animation">
                        <div class="loading-spinner">
                            <div class="spinner-ring"></div>
                            <div class="spinner-ring"></div>
                            <div class="spinner-ring"></div>
                            <span class="spinner-icon">🛍️</span>
                        </div>
                    </div>
                    <div class="scraping-message">
                        <span class="message-icon">🔍</span>
                        <span class="message-text">Conectando con la tienda...</span>
                    </div>
                    <div class="scraping-progress">
                        <div class="progress-bar">
                            <div class="progress-fill"></div>
                        </div>
                    </div>
                    <p class="scraping-tip">💡 Esto puede tardar hasta 30 segundos</p>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHtml);
        
        // Animar los mensajes
        let currentIndex = 0;
        const messageIcon = document.querySelector('.message-icon');
        const messageText = document.querySelector('.message-text');
        const progressFill = document.querySelector('.progress-fill');
        
        const updateMessage = () => {
            if (currentIndex < messages.length) {
                const msg = messages[currentIndex];
                messageIcon.textContent = msg.icon;
                messageText.textContent = msg.text;
                
                // Actualizar barra de progreso
                const progress = ((currentIndex + 1) / messages.length) * 100;
                progressFill.style.width = `${progress}%`;
                
                currentIndex++;
                this.scrapingMessageTimer = setTimeout(updateMessage, msg.duration);
            }
        };
        
        updateMessage();
    }

    closeScrapingLoadingModal() {
        if (this.scrapingMessageTimer) {
            clearTimeout(this.scrapingMessageTimer);
        }
        const modal = document.getElementById('scraping-loader');
        if (modal) {
            modal.classList.add('fade-out');
            setTimeout(() => modal.remove(), 300);
        }
    }

    renderProductPreview() {
        const preview = this.querySelector('#product-preview');
        if (!preview || !this.productData) return;

        const storeIcon = ProductWishlist.getStoreIcon(this.productData.store);
        const needsManual = this.productData.needsManualInput;
        const isFullManual = this.productData.isFullManual;

        if (needsManual) {
            // Determinar el mensaje según si es entrada manual completa o scraping fallido
            const noticeHtml = isFullManual ? `
                <div class="manual-input-notice manual-mode">
                    <span class="notice-icon">✏️</span>
                    <div class="notice-text">
                        <strong>Modo manual</strong>
                        <p>Ingresa los datos del producto que deseas:</p>
                    </div>
                </div>
            ` : `
                <div class="manual-input-notice">
                    <span class="notice-icon">⚠️</span>
                    <div class="notice-text">
                        <strong>No pudimos obtener todos los datos</strong>
                        <p>Completa o corrige la información del producto:</p>
                    </div>
                </div>
            `;
            
            // Modo de entrada manual
            preview.innerHTML = `
                ${noticeHtml}
                <div class="manual-input-form">
                    <div class="form-group">
                        <label for="manual-product-name">Nombre del producto *</label>
                        <input type="text" id="manual-product-name" 
                            placeholder="Ej: iPhone 15 Pro Max 256GB"
                            value="${this.productData.name || ''}" />
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label for="manual-product-price">Precio *</label>
                            <input type="number" id="manual-product-price" 
                                placeholder="0.00"
                                min="1"
                                step="0.01"
                                value="${this.productData.price || ''}" />
                        </div>
                        <div class="form-group">
                            <label for="manual-product-store">Tienda</label>
                            <input type="text" id="manual-product-store" 
                                placeholder="MercadoLibre"
                                value="${this.productData.store || ''}" />
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="manual-product-image">URL de imagen (opcional)</label>
                        <input type="url" id="manual-product-image" 
                            placeholder="https://..."
                            value="${this.productData.image || ''}" />
                    </div>
                </div>
            `;
            
            // Agregar listeners para actualizar productData en tiempo real
            this.setupManualInputListeners();
        } else {
            // Modo normal - el scraping funcionó
            preview.innerHTML = `
                <img class="product-image" 
                    src="${this.productData.image || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                    alt="${this.productData.name}"
                    onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                <div class="product-info">
                    <div class="product-name">${this.productData.name}</div>
                    <div class="product-store">${storeIcon} ${this.productData.store || 'Tienda en línea'}</div>
                    <div class="product-price">${ProductWishlist.formatCurrency(this.productData.price)}</div>
                </div>
            `;
        }
    }
    
    setupManualInputListeners() {
        const nameInput = this.querySelector('#manual-product-name');
        const priceInput = this.querySelector('#manual-product-price');
        const storeInput = this.querySelector('#manual-product-store');
        const imageInput = this.querySelector('#manual-product-image');
        
        const updateData = () => {
            if (nameInput) this.productData.name = nameInput.value.trim();
            if (priceInput) this.productData.price = parseFloat(priceInput.value) || 0;
            if (storeInput) this.productData.store = storeInput.value.trim() || 'Tienda Online';
            if (imageInput) this.productData.image = imageInput.value.trim();
        };
        
        nameInput?.addEventListener('input', updateData);
        priceInput?.addEventListener('input', updateData);
        storeInput?.addEventListener('input', updateData);
        imageInput?.addEventListener('input', updateData);
    }

    async loadIncomes() {
        const incomeList = this.querySelector('#income-list');
        const noIncomesMsg = this.querySelector('#no-incomes-message');
        const btnAnalyze = this.querySelector('#btn-analyze');

        // Obtener userId del contexto global
        const currentUser = window.getCurrentUser?.();
        const userId = currentUser?.userId;

        if (!userId) {
            Swal.fire('Error', 'No se pudo identificar el usuario', 'error');
            return;
        }

        try {
            // Importar el módulo de planning para obtener ingresos
            const { getIncomePatterns } = await import('../patterns.js');
            const incomes = await getIncomePatterns(userId);

            if (!incomes || incomes.length === 0) {
                incomeList.innerHTML = '';
                noIncomesMsg.style.display = 'block';
                btnAnalyze.disabled = true;
            } else {
                noIncomesMsg.style.display = 'none';
                incomeList.innerHTML = incomes.map(income => `
                    <div class="income-item" data-income-id="${income.id}" data-amount="${income.base_amount}" data-frequency="${income.frequency}">
                        <div class="income-info">
                            <div class="income-name">${income.name}</div>
                            <div class="income-frequency">${this.getFrequencyLabel(income.frequency)}</div>
                        </div>
                        <div class="income-amount">${ProductWishlist.formatCurrency(income.base_amount)}</div>
                    </div>
                `).join('');

                // Event listeners para selección
                incomeList.querySelectorAll('.income-item').forEach(item => {
                    item.addEventListener('click', () => {
                        incomeList.querySelectorAll('.income-item').forEach(i => i.classList.remove('selected'));
                        item.classList.add('selected');
                        this.selectedIncome = {
                            id: item.dataset.incomeId,
                            amount: parseFloat(item.dataset.amount),
                            frequency: item.dataset.frequency
                        };
                        btnAnalyze.disabled = false;
                    });
                });
            }
        } catch (error) {
            console.error('Error loading incomes:', error);
            incomeList.innerHTML = '<div class="error-message">Error al cargar ingresos</div>';
        }

        this.showStep('step-income');
    }

    getFrequencyLabel(frequency) {
        const labels = {
            weekly: 'Semanal',
            biweekly: 'Quincenal',
            monthly: 'Mensual',
            yearly: 'Anual'
        };
        return labels[frequency] || frequency;
    }

    async analyzeOptions() {
        if (!this.selectedIncome || !this.productData) return;

        const currentUser = window.getCurrentUser?.();
        const userId = currentUser?.userId;

        try {
            // Obtener gastos y planes existentes para análisis completo
            const { getExpensePatterns } = await import('../patterns.js');
            const { getProductWishlist } = await import('../product-wishlist.js');

            const [expenses, existingProducts] = await Promise.all([
                getExpensePatterns(userId).catch(() => []),
                getProductWishlist(userId, { status: 'active' }).catch(() => [])
            ]);

            const result = await ProductWishlist.analyzeProductPlan(
                this.productData.price,
                [{ base_amount: this.selectedIncome.amount, frequency: this.selectedIncome.frequency }],
                expenses,
                existingProducts.map(p => ({
                    contribution_type: p.contribution_type,
                    contribution_value: p.contribution_value
                }))
            );

            this.planOptions = result.options;
            this.renderAnalysis(result.analysis);
            this.renderPlanOptions(result.options);
            this.showStep('step-plan');
        } catch (error) {
            console.error('Error analyzing options:', error);
            Swal.fire('Error', 'No se pudieron calcular las opciones de planificación', 'error');
        }
    }

    renderAnalysis(analysis) {
        const summary = this.querySelector('#analysis-summary');
        if (!summary) return;

        summary.innerHTML = `
            <div class="analysis-row">
                <span>Ingreso mensual estimado:</span>
                <span>${ProductWishlist.formatCurrency(analysis.totalMonthlyIncome)}</span>
            </div>
            <div class="analysis-row">
                <span>Gastos fijos:</span>
                <span>- ${ProductWishlist.formatCurrency(analysis.totalMonthlyExpenses)}</span>
            </div>
            <div class="analysis-row">
                <span>Otras metas activas:</span>
                <span>- ${ProductWishlist.formatCurrency(analysis.existingCommitments)}</span>
            </div>
            <div class="analysis-row">
                <span>💰 Disponible para este producto:</span>
                <span style="color: #059669;">${ProductWishlist.formatCurrency(analysis.availableIncome)}</span>
            </div>
        `;
    }

    renderPlanOptions(options) {
        const container = this.querySelector('#plan-options');
        const btnNext = this.querySelector('#btn-next-review');
        if (!container) return;

        container.innerHTML = options.map(option => `
            <div class="plan-option ${option.recommended ? 'recommended' : ''}" data-plan-type="${option.type}">
                <div class="plan-header">
                    <div class="plan-name">${option.name}</div>
                    <span class="plan-badge ${option.type}">${option.type === 'short' ? 'Rápido' : option.type === 'medium' ? 'Equilibrado' : 'Tranquilo'}</span>
                </div>
                <div class="plan-description">${option.description}</div>
                <div class="plan-details">
                    <div class="plan-detail">
                        <span class="plan-detail-label">Aportación</span>
                        <span class="plan-detail-value">${ProductWishlist.formatCurrency(option.monthlyContribution)}/mes</span>
                    </div>
                    <div class="plan-detail">
                        <span class="plan-detail-label">% del ingreso</span>
                        <span class="plan-detail-value">${option.percentOfIncome}%</span>
                    </div>
                    <div class="plan-detail">
                        <span class="plan-detail-label">Tiempo estimado</span>
                        <span class="plan-detail-value">${option.estimatedMonths} meses</span>
                    </div>
                </div>
            </div>
        `).join('');

        // Event listeners
        container.querySelectorAll('.plan-option').forEach(option => {
            option.addEventListener('click', () => {
                container.querySelectorAll('.plan-option').forEach(o => o.classList.remove('selected'));
                option.classList.add('selected');

                const planType = option.dataset.planType;
                this.selectedPlan = options.find(o => o.type === planType);
                btnNext.disabled = false;
            });
        });
    }

    applyCustomPlan() {
        const contributionType = this.querySelector('#custom-contribution-type').value;
        const contributionValue = parseFloat(this.querySelector('#custom-contribution-value').value);

        if (!contributionValue || contributionValue <= 0) {
            Swal.fire('Error', 'Ingresa un valor válido', 'error');
            return;
        }

        // Calcular tiempo estimado
        let monthlyContribution;
        if (contributionType === 'percent') {
            const monthlyIncome = this.selectedIncome.amount * (this.selectedIncome.frequency === 'biweekly' ? 2 : 1);
            monthlyContribution = monthlyIncome * (contributionValue / 100);
        } else {
            monthlyContribution = contributionValue;
        }

        const estimatedMonths = Math.ceil(this.productData.price / monthlyContribution);
        let type = 'medium';
        if (estimatedMonths <= 2) type = 'short';
        else if (estimatedMonths > 5) type = 'long';

        this.selectedPlan = {
            type,
            name: '⚙️ Plan Personalizado',
            monthlyContribution,
            percentOfIncome: contributionType === 'percent' ? contributionValue : Math.round((monthlyContribution / this.selectedIncome.amount) * 100),
            estimatedMonths,
            contributionType,
            contributionValue: contributionType === 'percent' ? contributionValue / 100 : contributionValue
        };

        // Habilitar botón siguiente
        this.querySelector('#btn-next-review').disabled = false;

        // Deseleccionar opciones predefinidas y mostrar mensaje
        this.querySelectorAll('.plan-option').forEach(o => o.classList.remove('selected'));
        Swal.fire('✅ Plan personalizado aplicado', `Aportación: ${ProductWishlist.formatCurrency(monthlyContribution)}/mes`, 'success');
    }

    showReview() {
        const review = this.querySelector('#review-summary');
        if (!review || !this.productData || !this.selectedPlan) return;

        const estimatedDate = ProductWishlist.calculateEstimatedDate(
            this.productData.price,
            0,
            this.selectedPlan.contributionValue || this.selectedPlan.monthlyContribution,
            this.selectedPlan.contributionType || 'fixed',
            this.selectedIncome.amount,
            this.selectedIncome.frequency
        );

        const planInfo = ProductWishlist.getPlanTypeInfo(this.selectedPlan.type);

        review.innerHTML = `
            <div class="review-product">
                <img class="review-product-image" 
                    src="${this.productData.image || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}"
                    onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                <div>
                    <div style="font-weight: 600; margin-bottom: 8px;">${this.productData.name}</div>
                    <div style="color: #059669; font-size: 1.25rem; font-weight: bold;">${ProductWishlist.formatCurrency(this.productData.price)}</div>
                </div>
            </div>

            <div class="review-section">
                <div class="review-section-title">Plan seleccionado</div>
                <div class="review-row">
                    <span>Tipo:</span>
                    <span>${planInfo.icon} ${planInfo.label}</span>
                </div>
                <div class="review-row">
                    <span>Aportación:</span>
                    <span>${ProductWishlist.formatCurrency(this.selectedPlan.monthlyContribution)} por ingreso</span>
                </div>
                <div class="review-row">
                    <span>Tiempo estimado:</span>
                    <span>${this.selectedPlan.estimatedMonths} meses</span>
                </div>
            </div>

            <div class="review-section">
                <div class="review-section-title">Ingreso vinculado</div>
                <div class="review-row">
                    <span>Fuente:</span>
                    <span>${this.selectedIncome.amount ? ProductWishlist.formatCurrency(this.selectedIncome.amount) : 'Seleccionado'}</span>
                </div>
            </div>

            <div class="review-highlight">
                <div style="font-size: 0.875rem; opacity: 0.9; margin-bottom: 4px;">📅 Fecha estimada de compra</div>
                <div class="date">${estimatedDate ? estimatedDate.toLocaleDateString('es-MX', { year: 'numeric', month: 'long', day: 'numeric' }) : 'Por calcular'}</div>
            </div>
        `;

        this.showStep('step-review');
    }

    async createProduct() {
        const btn = this.querySelector('#btn-create-product');
        const currentUser = window.getCurrentUser?.();
        const userId = currentUser?.userId;

        if (!userId) {
            Swal.fire('Error', 'No se pudo identificar el usuario', 'error');
            return;
        }

        try {
            btn.querySelector('.btn-text').style.display = 'none';
            btn.querySelector('.btn-loader').style.display = 'inline';
            btn.disabled = true;

            const notes = this.querySelector('#product-notes')?.value?.trim() || null;

            const estimatedDate = ProductWishlist.calculateEstimatedDate(
                this.productData.price,
                0,
                this.selectedPlan.contributionValue || this.selectedPlan.monthlyContribution,
                this.selectedPlan.contributionType || 'fixed',
                this.selectedIncome.amount,
                this.selectedIncome.frequency
            );

            // Crear producto
            const product = await ProductWishlist.createProductWishlist(userId, {
                product_url: this.productData.url,
                product_name: this.productData.name,
                product_image_url: this.productData.image,
                product_price: this.productData.price,
                product_store: this.productData.store,
                target_amount: this.productData.price,
                plan_type: this.selectedPlan.type,
                contribution_type: this.selectedPlan.contributionType || 'fixed',
                contribution_value: this.selectedPlan.contributionValue || this.selectedPlan.monthlyContribution,
                priority: this.selectedPlan.type === 'short' ? 8 : this.selectedPlan.type === 'medium' ? 5 : 3,
                estimated_completion_date: estimatedDate?.toISOString().split('T')[0],
                notes
            });

            // Asignar ingreso
            await ProductWishlist.assignIncomeToProduct(product.id, this.selectedIncome.id, {
                type: this.selectedPlan.contributionType || 'fixed',
                value: this.selectedPlan.contributionType === 'percent' 
                    ? this.selectedPlan.contributionValue 
                    : this.selectedPlan.monthlyContribution
            });

            // Mostrar éxito
            await Swal.fire({
                icon: 'success',
                title: '¡Producto agregado!',
                html: `
                    <div style="text-align: center;">
                        <p><strong>${this.productData.name}</strong> ha sido agregado a tu lista de deseos.</p>
                        <p style="color: #059669; font-size: 1.25rem; font-weight: bold;">
                            Meta: ${ProductWishlist.formatCurrency(this.productData.price)}
                        </p>
                        <p>Fecha estimada: ${estimatedDate?.toLocaleDateString('es-MX', { year: 'numeric', month: 'long', day: 'numeric' })}</p>
                    </div>
                `,
                confirmButtonText: 'Ver mis productos'
            });

            // Emitir evento de éxito
            this.dispatchEvent(new CustomEvent('product-created', {
                detail: { product },
                bubbles: true
            }));

        } catch (error) {
            console.error('Error creating product:', error);
            Swal.fire('Error', error.message || 'No se pudo crear el producto', 'error');
        } finally {
            btn.querySelector('.btn-text').style.display = 'inline';
            btn.querySelector('.btn-loader').style.display = 'none';
            btn.disabled = false;
        }
    }
}

// Registrar el componente solo si no está registrado
if (!customElements.get('product-wishlist-form')) {
    customElements.define('product-wishlist-form', ProductWishlistForm);
}

export default ProductWishlistForm;
