/**
 * captcha-solver.js
 * Sistema interactivo para resolver CAPTCHAs de Amazon
 * El usuario resuelve el CAPTCHA en un iframe y continúa el scraping
 */

export class CaptchaSolver {
    constructor() {
        this.resolveCallback = null;
        this.rejectCallback = null;
    }

    /**
     * Abre ventana modal con iframe de Amazon para resolver CAPTCHA
     * @param {string} amazonUrl - URL del producto en Amazon
     * @returns {Promise<boolean>} - true si se resolvió, false si se canceló
     */
    async solveCaptcha(amazonUrl) {
        return new Promise((resolve, reject) => {
            this.resolveCallback = resolve;
            this.rejectCallback = reject;
            this.showCaptchaModal(amazonUrl);
        });
    }

    showCaptchaModal(amazonUrl) {
        // Crear overlay y modal
        const modalHTML = `
            <div class="captcha-solver-overlay" id="captcha-solver-overlay">
                <div class="captcha-solver-modal">
                    <div class="captcha-modal-header">
                        <h3>🤖 Verificación requerida</h3>
                        <p>Amazon requiere que verifiques que eres humano</p>
                    </div>
                    
                    <div class="captcha-modal-body">
                        <div class="captcha-instructions">
                            <p><strong>👇 Sigue estos pasos:</strong></p>
                            <ol>
                                <li>Resuelve el CAPTCHA en la ventana de abajo</li>
                                <li>Espera a que cargue la página del producto</li>
                                <li>Haz clic en "Continuar" cuando veas el producto</li>
                            </ol>
                        </div>
                        
                        <div class="captcha-iframe-container">
                            <iframe 
                                id="captcha-iframe" 
                                src="${amazonUrl}"
                                sandbox="allow-same-origin allow-scripts allow-forms"
                                loading="eager">
                            </iframe>
                            <div class="iframe-loading">
                                <div class="spinner"></div>
                                <p>Cargando página de Amazon...</p>
                            </div>
                        </div>
                    </div>
                    
                    <div class="captcha-modal-footer">
                        <button id="captcha-cancel-btn" class="btn-secondary">
                            ❌ Cancelar
                        </button>
                        <button id="captcha-continue-btn" class="btn-primary">
                            ✅ Continuar (CAPTCHA resuelto)
                        </button>
                    </div>
                </div>
            </div>
        `;

        // Agregar al body
        document.body.insertAdjacentHTML('beforeend', modalHTML);

        // Agregar estilos
        this.injectStyles();

        // Event listeners
        const iframe = document.getElementById('captcha-iframe');
        const continueBtn = document.getElementById('captcha-continue-btn');
        const cancelBtn = document.getElementById('captcha-cancel-btn');
        const loadingIndicator = document.querySelector('.iframe-loading');

        // Ocultar loading cuando iframe cargue
        iframe.addEventListener('load', () => {
            loadingIndicator.style.display = 'none';
        });

        // Botón continuar
        continueBtn.addEventListener('click', () => {
            this.closeCaptchaModal();
            if (this.resolveCallback) {
                this.resolveCallback(true);
            }
        });

        // Botón cancelar
        cancelBtn.addEventListener('click', () => {
            this.closeCaptchaModal();
            if (this.rejectCallback) {
                this.rejectCallback(new Error('CAPTCHA_CANCELLED'));
            }
        });

        // Cerrar con ESC
        const handleEsc = (e) => {
            if (e.key === 'Escape') {
                cancelBtn.click();
                document.removeEventListener('keydown', handleEsc);
            }
        };
        document.addEventListener('keydown', handleEsc);
    }

    closeCaptchaModal() {
        const overlay = document.getElementById('captcha-solver-overlay');
        if (overlay) {
            overlay.remove();
        }
    }

    injectStyles() {
        if (document.getElementById('captcha-solver-styles')) return;

        const styles = `
            <style id="captcha-solver-styles">
                .captcha-solver-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    background: rgba(0, 0, 0, 0.8);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 99999;
                    backdrop-filter: blur(4px);
                    animation: fadeIn 0.3s ease;
                }

                .captcha-solver-modal {
                    background: white;
                    border-radius: 16px;
                    width: 90%;
                    max-width: 900px;
                    max-height: 90vh;
                    display: flex;
                    flex-direction: column;
                    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
                    animation: slideUp 0.3s ease;
                }

                .captcha-modal-header {
                    padding: 1.5rem;
                    border-bottom: 1px solid #e5e7eb;
                    text-align: center;
                }

                .captcha-modal-header h3 {
                    margin: 0 0 0.5rem 0;
                    font-size: 1.5rem;
                    color: #1f2937;
                }

                .captcha-modal-header p {
                    margin: 0;
                    color: #6b7280;
                    font-size: 0.95rem;
                }

                .captcha-modal-body {
                    flex: 1;
                    padding: 1.5rem;
                    overflow: hidden;
                    display: flex;
                    flex-direction: column;
                    gap: 1rem;
                }

                .captcha-instructions {
                    background: #eff6ff;
                    padding: 1rem;
                    border-radius: 8px;
                    border-left: 4px solid #3b82f6;
                }

                .captcha-instructions p {
                    margin: 0 0 0.5rem 0;
                    color: #1e40af;
                    font-weight: 600;
                }

                .captcha-instructions ol {
                    margin: 0.5rem 0 0 0;
                    padding-left: 1.5rem;
                    color: #1e3a8a;
                }

                .captcha-instructions li {
                    margin: 0.25rem 0;
                }

                .captcha-iframe-container {
                    flex: 1;
                    position: relative;
                    background: #f9fafb;
                    border-radius: 8px;
                    overflow: hidden;
                    min-height: 400px;
                }

                #captcha-iframe {
                    width: 100%;
                    height: 100%;
                    border: none;
                    border-radius: 8px;
                }

                .iframe-loading {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    background: white;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    gap: 1rem;
                }

                .iframe-loading .spinner {
                    width: 48px;
                    height: 48px;
                    border: 4px solid #e5e7eb;
                    border-top-color: #10b981;
                    border-radius: 50%;
                    animation: spin 0.8s linear infinite;
                }

                .iframe-loading p {
                    color: #6b7280;
                    font-size: 0.95rem;
                }

                .captcha-modal-footer {
                    padding: 1.5rem;
                    border-top: 1px solid #e5e7eb;
                    display: flex;
                    gap: 1rem;
                    justify-content: flex-end;
                }

                .captcha-modal-footer button {
                    padding: 0.75rem 1.5rem;
                    border: none;
                    border-radius: 8px;
                    font-size: 1rem;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.2s;
                }

                .btn-secondary {
                    background: #e5e7eb;
                    color: #374151;
                }

                .btn-secondary:hover {
                    background: #d1d5db;
                }

                .btn-primary {
                    background: #10b981;
                    color: white;
                }

                .btn-primary:hover {
                    background: #059669;
                }

                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }

                @keyframes slideUp {
                    from { 
                        opacity: 0;
                        transform: translateY(20px);
                    }
                    to { 
                        opacity: 1;
                        transform: translateY(0);
                    }
                }

                @keyframes spin {
                    to { transform: rotate(360deg); }
                }

                @media (max-width: 768px) {
                    .captcha-solver-modal {
                        width: 95%;
                        max-height: 95vh;
                    }

                    .captcha-modal-body {
                        padding: 1rem;
                    }

                    .captcha-modal-footer {
                        flex-direction: column;
                    }

                    .captcha-modal-footer button {
                        width: 100%;
                    }
                }
            </style>
        `;

        document.head.insertAdjacentHTML('beforeend', styles);
    }
}

// Instancia global
export const captchaSolver = new CaptchaSolver();
