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
     * Abre ventana popup de Amazon para resolver CAPTCHA y extraer datos
     * @param {string} amazonUrl - URL del producto en Amazon
     * @returns {Promise<object>} - Datos del producto extraídos del popup
     */
    async solveCaptcha(amazonUrl) {
        return new Promise((resolve, reject) => {
            this.resolveCallback = resolve;
            this.rejectCallback = reject;
            this.amazonUrl = amazonUrl;
            this.showCaptchaModal(amazonUrl);
        });
    }

    showCaptchaModal(amazonUrl) {
        // Abrir Amazon en ventana popup
        const width = 800;
        const height = 700;
        const left = (window.screen.width - width) / 2;
        const top = (window.screen.height - height) / 2;
        
        const popup = window.open(
            amazonUrl,
            'amazon-captcha',
            `width=${width},height=${height},left=${left},top=${top},toolbar=no,menubar=no,location=yes,status=yes,scrollbars=yes`
        );

        if (!popup) {
            alert('Por favor permite ventanas emergentes para resolver el CAPTCHA');
            if (this.rejectCallback) {
                this.rejectCallback(new Error('POPUP_BLOCKED'));
            }
            return;
        }

        // Listener para recibir datos del popup
        let messageReceived = false;
        const handleMessage = (event) => {
            // Verificar que venga de Amazon
            if (event.origin.includes('amazon.com')) {
                messageReceived = true;
                window.removeEventListener('message', handleMessage);
                
                clearInterval(checkPopupClosed);
                popup.close();
                this.closeCaptchaModal();
                
                if (this.resolveCallback) {
                    this.resolveCallback(event.data);
                }
            }
        };
        window.addEventListener('message', handleMessage);

        // Crear overlay y modal de instrucciones
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
                                <li>Resuelve el CAPTCHA en la <strong>ventana emergente</strong></li>
                                <li>Espera a que cargue la página del producto</li>
                                <li>Haz clic en "Continuar" cuando veas el producto</li>
                            </ol>
                        </div>
                        
                        <div class="popup-status">
                            <div class="status-icon">🔄</div>
                            <p class="status-text">Esperando que resuelvas el CAPTCHA...</p>
                            <p class="status-hint">La ventana de Amazon se abrió en otra pestaña</p>
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
        const continueBtn = document.getElementById('captcha-continue-btn');
        const cancelBtn = document.getElementById('captcha-cancel-btn');

        // Monitorear si popup se cierra
        const checkPopupClosed = setInterval(() => {
            if (popup.closed) {
                clearInterval(checkPopupClosed);
                const overlay = document.getElementById('captcha-solver-overlay');
                if (overlay) {
                    // Popup cerrado sin confirmar
                    this.closeCaptchaModal();
                    if (this.rejectCallback) {
                        this.rejectCallback(new Error('POPUP_CLOSED'));
                    }
                }
            }
        }, 500);

        // Botón continuar - copiar URL del popup y usar para scraping
        continueBtn.addEventListener('click', async () => {
            try {
                continueBtn.disabled = true;
                continueBtn.textContent = '⏳ Extrayendo datos...';
                
                // Intentar obtener la URL actual del popup
                let finalUrl = amazonUrl;
                try {
                    if (popup && !popup.closed && popup.location) {
                        finalUrl = popup.location.href;
                    }
                } catch (e) {
                    // Cross-origin, usar URL original
                    console.log('No se puede acceder a popup URL, usando original');
                }
                
                // Llamar al backend con la URL (ahora con cookies resueltas en el navegador)
                const response = await fetch(`${window.SCRAPER_API_URL || 'https://calendar-backend-ed6u5g.fly.dev'}/api/scrape/quick`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ 
                        url: finalUrl,
                        userResolved: true // Flag para indicar que usuario ya resolvió
                    })
                });
                
                const result = await response.json();
                
                clearInterval(checkPopupClosed);
                window.removeEventListener('message', handleMessage);
                popup.close();
                this.closeCaptchaModal();
                
                if (result.success) {
                    if (this.resolveCallback) {
                        this.resolveCallback({
                            ...result.data,
                            url: amazonUrl,
                            platform: 'amazon',
                            store: 'Amazon'
                        });
                    }
                } else {
                    throw new Error(result.error || 'Error extrayendo datos');
                }
                
            } catch (error) {
                console.error('Error extrayendo datos:', error);
                
                // Si falla todo, pedir entrada manual
                clearInterval(checkPopupClosed);
                window.removeEventListener('message', handleMessage);
                popup.close();
                this.closeCaptchaModal();
                
                if (this.rejectCallback) {
                    this.rejectCallback(new Error('EXTRACTION_FAILED'));
                }
            }
        });

        // Botón cancelar
        cancelBtn.addEventListener('click', () => {
            clearInterval(checkPopupClosed);
            popup.close();
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
                    overflow: auto;
                    display: flex;
                    flex-direction: column;
                    gap: 1.5rem;
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
                    margin: 0.5rem 0;
                }

                .popup-status {
                    flex: 1;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    gap: 1rem;
                    padding: 2rem;
                    background: #f9fafb;
                    border-radius: 8px;
                    min-height: 200px;
                }

                .status-icon {
                    font-size: 4rem;
                    animation: pulse 2s ease-in-out infinite;
                }

                .status-text {
                    font-size: 1.125rem;
                    font-weight: 600;
                    color: #1f2937;
                    margin: 0;
                }

                .status-hint {
                    font-size: 0.95rem;
                    color: #6b7280;
                    margin: 0;
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

                @keyframes pulse {
                    0%, 100% { 
                        opacity: 1;
                        transform: scale(1);
                    }
                    50% { 
                        opacity: 0.7;
                        transform: scale(1.1);
                    }
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
