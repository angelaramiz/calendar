/**
 * product-wishlist-modals.js
 * Modales para gestión de productos en línea (wishlist)
 */

import * as ProductWishlist from './product-wishlist.js';
import './components/product-wishlist-form.js';

let currentUserId = null;

export function setUserId(userId) {
    currentUserId = userId;
}

// ==================== MODAL PRINCIPAL ====================

/**
 * Abrir modal principal de productos deseados
 */
export async function openProductWishlistModal() {
    if (!currentUserId) {
        Swal.fire('Error', 'No hay sesión activa', 'error');
        return;
    }

    const dashboard = await ProductWishlist.getProductWishlistDashboard(currentUserId);

    const result = await Swal.fire({
        title: '🛒 Productos en Línea',
        html: `
            <div class="product-wishlist-dashboard">
                <div class="dashboard-tabs">
                    <button class="tab-btn active" data-tab="overview">📊 Resumen</button>
                    <button class="tab-btn" data-tab="products">📦 Mis Productos</button>
                    <button class="tab-btn" data-tab="completed">✅ Completados</button>
                </div>

                <div class="tab-content active" id="tab-overview">
                    ${renderOverviewTab(dashboard)}
                </div>

                <div class="tab-content" id="tab-products">
                    ${renderProductsTab(dashboard.items)}
                </div>

                <div class="tab-content" id="tab-completed">
                    ${renderCompletedTab(dashboard.completedRecently)}
                </div>
            </div>
        `,
        width: '950px',
        showConfirmButton: false,
        showCloseButton: true,
        customClass: {
            popup: 'product-wishlist-popup'
        },
        didOpen: () => {
            setupTabSwitching();
            setupProductActions();
            checkProductAlerts(dashboard.items);
            addProductWishlistStyles();
        }
    });
}

// ==================== RENDER TABS ====================

function renderOverviewTab(dashboard) {
    const { summary, nearCompletion, inactive } = dashboard;

    return `
        <div class="overview-container">
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-icon">📦</div>
                    <div class="stat-value">${summary.active}</div>
                    <div class="stat-label">Productos Activos</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">✅</div>
                    <div class="stat-value">${summary.completed}</div>
                    <div class="stat-label">Completados</div>
                </div>
                <div class="stat-card highlight">
                    <div class="stat-icon">💰</div>
                    <div class="stat-value">${ProductWishlist.formatCurrency(summary.totalCurrent)}</div>
                    <div class="stat-label">Total Ahorrado</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">🎯</div>
                    <div class="stat-value">${summary.overallProgress}%</div>
                    <div class="stat-label">Progreso Global</div>
                </div>
            </div>

            <div class="action-banner">
                <div class="banner-content">
                    <div class="banner-icon">🛍️</div>
                    <div class="banner-text">
                        <strong>¿Quieres algo nuevo?</strong>
                        <p>Agrega un producto de cualquier tienda en línea y planifica tu compra</p>
                    </div>
                </div>
                <button class="btn-primary btn-add-product">➕ Agregar Producto</button>
            </div>

            ${nearCompletion.length > 0 ? `
                <div class="section-card">
                    <h4>🎉 ¡Casi lo logras!</h4>
                    <div class="product-mini-list">
                        ${nearCompletion.map(p => `
                            <div class="product-mini-item">
                                <img src="${p.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                                    onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                                <div class="product-mini-info">
                                    <div class="product-mini-name">${truncateText(p.product_name, 40)}</div>
                                    <div class="product-mini-progress">
                                        <div class="progress-bar-mini">
                                            <div class="progress-fill" style="width: ${p.progress_percent}%"></div>
                                        </div>
                                        <span>${p.progress_percent}%</span>
                                    </div>
                                </div>
                                <span class="days-badge">${p.days_remaining} días</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            ` : ''}

            ${inactive.length > 0 ? `
                <div class="section-card warning">
                    <h4>⚠️ Productos sin actividad</h4>
                    <p class="section-hint">Estos productos no han recibido aportes recientemente</p>
                    <div class="product-mini-list">
                        ${inactive.slice(0, 3).map(p => `
                            <div class="product-mini-item inactive">
                                <img src="${p.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}"
                                    onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                                <div class="product-mini-info">
                                    <div class="product-mini-name">${truncateText(p.product_name, 40)}</div>
                                    <div class="product-mini-status">Falta: ${ProductWishlist.formatCurrency(p.remaining_amount)}</div>
                                </div>
                                <button class="btn-small btn-contribute" data-product-id="${p.id}">💰 Aportar</button>
                            </div>
                        `).join('')}
                    </div>
                </div>
            ` : ''}
        </div>
    `;
}

function renderProductsTab(products) {
    return `
        <div class="products-header">
            <button class="btn-primary btn-add-product">➕ Nuevo Producto</button>
        </div>
        <div class="products-list">
            ${products.length === 0 ? `
                <div class="empty-state">
                    <div class="empty-icon">🛒</div>
                    <div class="empty-text">No tienes productos en tu lista</div>
                    <div class="empty-hint">Agrega tu primer producto de una tienda en línea</div>
                    <button class="btn-primary btn-add-product" style="margin-top: 16px;">➕ Agregar Producto</button>
                </div>
            ` : products.map(product => renderProductCard(product)).join('')}
        </div>
    `;
}

function renderProductCard(product) {
    const planInfo = ProductWishlist.getPlanTypeInfo(product.plan_type);
    const storeIcon = ProductWishlist.getStoreIcon(product.product_store);

    return `
        <div class="product-card" data-product-id="${product.id}">
            <div class="product-card-image">
                <img src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                    alt="${product.product_name}"
                    onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                <span class="store-badge">${storeIcon}</span>
            </div>
            <div class="product-card-content">
                <div class="product-card-header">
                    <h4 class="product-name">${truncateText(product.product_name, 50)}</h4>
                    <div class="product-actions">
                        <button class="btn-icon btn-contribute" data-product-id="${product.id}" title="Agregar aporte">💰</button>
                        <button class="btn-icon btn-view-product" data-product-id="${product.id}" title="Ver detalles">👁️</button>
                        <button class="btn-icon btn-edit-product" data-product-id="${product.id}" title="Editar">✏️</button>
                        <button class="btn-icon btn-delete-product" data-product-id="${product.id}" title="Eliminar">🗑️</button>
                    </div>
                </div>
                
                <div class="product-progress">
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: ${product.progress_percent}%; background: ${planInfo.color}"></div>
                    </div>
                    <div class="progress-info">
                        <span>${ProductWishlist.formatCurrency(product.current_amount)} / ${ProductWishlist.formatCurrency(product.target_amount)}</span>
                        <span class="progress-percent">${product.progress_percent}%</span>
                    </div>
                </div>

                <div class="product-meta">
                    <span class="meta-badge plan-type" style="background: ${planInfo.color}20; color: ${planInfo.color}">
                        ${planInfo.icon} ${planInfo.label}
                    </span>
                    ${product.estimated_completion_date ? `
                        <span class="meta-badge date">
                            📅 ${formatDateShort(product.estimated_completion_date)}
                        </span>
                    ` : ''}
                    ${product.days_remaining !== null ? `
                        <span class="meta-badge days ${product.days_remaining <= 30 ? 'soon' : ''}">
                            ${product.days_remaining > 0 ? `${product.days_remaining} días` : '¡Hoy!'}
                        </span>
                    ` : ''}
                </div>
            </div>
        </div>
    `;
}

function renderCompletedTab(completed) {
    return `
        <div class="completed-list">
            ${completed.length === 0 ? `
                <div class="empty-state">
                    <div class="empty-icon">🎯</div>
                    <div class="empty-text">Aún no has completado ningún producto</div>
                    <div class="empty-hint">Sigue ahorrando para alcanzar tus metas</div>
                </div>
            ` : completed.map(product => `
                <div class="completed-card">
                    <div class="completed-icon">✅</div>
                    <img src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                        onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                    <div class="completed-info">
                        <div class="completed-name">${truncateText(product.product_name, 40)}</div>
                        <div class="completed-amount">${ProductWishlist.formatCurrency(product.target_amount)}</div>
                        ${product.completed_at ? `
                            <div class="completed-date">Completado el ${formatDateShort(product.completed_at)}</div>
                        ` : ''}
                    </div>
                </div>
            `).join('')}
        </div>
    `;
}

// ==================== MODAL PARA AGREGAR PRODUCTO ====================

export async function openAddProductModal() {
    console.log('[ProductWishlistModals] Opening add product modal...');
    
    await Swal.fire({
        title: '🛒 Agregar Producto en Línea',
        html: '<product-wishlist-form></product-wishlist-form>',
        width: '700px',
        showConfirmButton: false,
        showCloseButton: true,
        allowEnterKey: false, // Prevenir que Enter cierre el modal
        customClass: {
            popup: 'product-form-popup'
        },
        didOpen: () => {
            console.log('[ProductWishlistModals] Modal opened, form element:', document.querySelector('product-wishlist-form'));
            const form = document.querySelector('product-wishlist-form');
            form?.addEventListener('product-created', async () => {
                Swal.close();
                // Reabrir el modal principal para ver el producto agregado
                setTimeout(() => openProductWishlistModal(), 300);
            });
        }
    });
    
    console.log('[ProductWishlistModals] Modal closed');
}

// ==================== MODAL PARA VER DETALLES ====================

export async function openProductDetailModal(productId) {
    const product = await ProductWishlist.getProductById(productId);
    if (!product) {
        Swal.fire('Error', 'Producto no encontrado', 'error');
        return;
    }

    const planInfo = ProductWishlist.getPlanTypeInfo(product.plan_type);
    const storeIcon = ProductWishlist.getStoreIcon(product.product_store);

    await Swal.fire({
        title: '',
        html: `
            <div class="product-detail-modal">
                <div class="product-detail-header">
                    <button class="nav-btn back-btn" data-action="back-to-products">Volver a Productos</button>
                    <img class="product-detail-image" 
                        src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}"
                        onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                    <div class="product-detail-info">
                        <span class="store-tag">${storeIcon} ${product.product_store || 'Tienda en línea'}</span>
                        <h3>${product.product_name}</h3>
                        <div class="price-tag">${ProductWishlist.formatCurrency(product.product_price)}</div>
                        <a href="${product.product_url}" target="_blank" class="product-link">🔗 Ver en tienda</a>
                    </div>
                </div>

                <div class="product-detail-progress">
                    <div class="progress-header">
                        <span>Progreso de ahorro</span>
                        <span class="progress-amount">${ProductWishlist.formatCurrency(product.current_amount)} de ${ProductWishlist.formatCurrency(product.target_amount)}</span>
                    </div>
                    <div class="progress-bar large">
                        <div class="progress-fill" style="width: ${product.progress_percent}%; background: ${planInfo.color}"></div>
                    </div>
                    <div class="progress-footer">
                        <span>Faltan: ${ProductWishlist.formatCurrency(product.remaining_amount)}</span>
                        <span class="progress-percent">${product.progress_percent}%</span>
                    </div>
                </div>

                <div class="product-detail-stats">
                    <div class="stat-item">
                        <span class="stat-label">Plan</span>
                        <span class="stat-value" style="color: ${planInfo.color}">${planInfo.icon} ${planInfo.label}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label">Aportación</span>
                        <span class="stat-value">${ProductWishlist.formatCurrency(product.contribution_value)}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label">Fecha estimada</span>
                        <span class="stat-value">${product.estimated_completion_date ? formatDateShort(product.estimated_completion_date) : 'Por calcular'}</span>
                    </div>
                    ${product.days_remaining !== null ? `
                        <div class="stat-item">
                            <span class="stat-label">Días restantes</span>
                            <span class="stat-value ${product.days_remaining <= 30 ? 'highlight' : ''}">${product.days_remaining}</span>
                        </div>
                    ` : ''}
                </div>

                ${product.product_wishlist_contributions?.length > 0 ? `
                    <div class="contributions-section">
                        <h4>📝 Historial de aportes</h4>
                        <div class="contributions-list">
                            ${product.product_wishlist_contributions.slice(0, 5).map(c => `
                                <div class="contribution-item">
                                    <span class="contribution-date">${formatDateShort(c.contribution_date)}</span>
                                    <span class="contribution-amount">+${ProductWishlist.formatCurrency(c.amount)}</span>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                ` : ''}

                ${product.notes ? `
                    <div class="notes-section">
                        <h4>📝 Notas</h4>
                        <p>${product.notes}</p>
                    </div>
                ` : ''}
            </div>
        `,
        width: '600px',
        showConfirmButton: true,
        confirmButtonText: 'Agregar Aporte',
        showCancelButton: true,
        cancelButtonText: 'Cerrar',
        customClass: {
            popup: 'product-detail-popup'
        },
        didOpen: () => {
            // Botón de navegación
            document.querySelector('.back-btn').addEventListener('click', () => {
                Swal.close();
                setTimeout(() => openProductWishlistModal(), 100);
            });
        }
    }).then(result => {
        if (result.isConfirmed) {
            openContributionModal(productId);
        }
    });
}

// ==================== MODAL PARA CONTRIBUCIONES ====================

export async function openContributionModal(productId) {
    const product = await ProductWishlist.getProductById(productId);
    if (!product) {
        Swal.fire('Error', 'Producto no encontrado', 'error');
        return;
    }

    const { value: formValues } = await Swal.fire({
        title: 'Agregar Aporte',
        html: `
            <div class="contribution-form">
                <div class="product-mini-header">
                    <button class="nav-btn back-btn" data-action="back-to-details">Volver a Detalles</button>
                    <img src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}"
                        onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                    <div>
                        <div class="product-mini-name">${truncateText(product.product_name, 30)}</div>
                        <div class="product-mini-progress-text">
                            ${ProductWishlist.formatCurrency(product.current_amount)} de ${ProductWishlist.formatCurrency(product.target_amount)}
                        </div>
                    </div>
                </div>

                <div class="form-group">
                    <label for="contribution-amount">Monto a aportar</label>
                    <input type="number" id="contribution-amount" 
                        placeholder="0.00" 
                        min="1" 
                        step="0.01"
                        value="${product.contribution_value || ''}" />
                    <small>Faltan: ${ProductWishlist.formatCurrency(product.remaining_amount)}</small>
                </div>

                <div class="form-group">
                    <label for="contribution-notes">Notas (opcional)</label>
                    <input type="text" id="contribution-notes" placeholder="Ej: Aporte de quincena" />
                </div>

                <div class="quick-amounts">
                    <span>Montos rápidos:</span>
                    <div class="quick-buttons">
                        <button type="button" class="quick-amount-btn" data-amount="${product.contribution_value}">
                            ${ProductWishlist.formatCurrency(product.contribution_value)}
                        </button>
                        <button type="button" class="quick-amount-btn" data-amount="${Math.min(product.remaining_amount, product.contribution_value * 2)}">
                            ${ProductWishlist.formatCurrency(Math.min(product.remaining_amount, product.contribution_value * 2))}
                        </button>
                        <button type="button" class="quick-amount-btn" data-amount="${product.remaining_amount}">
                            Todo (${ProductWishlist.formatCurrency(product.remaining_amount)})
                        </button>
                    </div>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Agregar Aporte',
        cancelButtonText: 'Cancelar',
        preConfirm: () => {
            const amount = parseFloat(document.getElementById('contribution-amount').value);
            const notes = document.getElementById('contribution-notes').value.trim();

            if (!amount || amount <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }

            return { amount, notes };
        },
        didOpen: () => {
            // Botón de navegación
            document.querySelector('.back-btn').addEventListener('click', () => {
                Swal.close();
                setTimeout(() => openProductDetailModal(productId), 100);
            });

            // Quick amount buttons
            document.querySelectorAll('.quick-amount-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    document.getElementById('contribution-amount').value = btn.dataset.amount;
                });
            });
        }
    });

    if (formValues) {
        try {
            await ProductWishlist.addContribution(productId, {
                amount: formValues.amount,
                notes: formValues.notes
            });

            await Swal.fire({
                icon: 'success',
                title: '¡Aporte registrado!',
                text: `Se agregaron ${ProductWishlist.formatCurrency(formValues.amount)} a tu ahorro`,
                timer: 2000,
                showConfirmButton: false
            });

            // Recargar modal principal
            openProductWishlistModal();
        } catch (error) {
            Swal.fire('Error', error.message || 'No se pudo registrar el aporte', 'error');
        }
    }
}

// ==================== MODAL PARA ELIMINAR ====================

export async function openDeleteProductModal(productId) {
    const product = await ProductWishlist.getProductById(productId);
    if (!product) return;

    const result = await Swal.fire({
        title: '¿Eliminar producto?',
        html: `
            <div style="text-align: center;">
                <img src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                    style="width: 80px; height: 80px; object-fit: contain; border-radius: 8px; margin-bottom: 12px;"
                    onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                <p><strong>${product.product_name}</strong></p>
                <p style="color: #6b7280;">Progreso actual: ${ProductWishlist.formatCurrency(product.current_amount)}</p>
            </div>
        `,
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#dc2626',
        confirmButtonText: 'Sí, eliminar',
        cancelButtonText: 'Cancelar'
    });

    if (result.isConfirmed) {
        try {
            await ProductWishlist.deleteProductWishlist(productId);
            Swal.fire('Eliminado', 'El producto ha sido eliminado de tu lista', 'success');
            openProductWishlistModal();
        } catch (error) {
            Swal.fire('Error', error.message || 'No se pudo eliminar', 'error');
        }
    }
}

// ==================== HELPERS ====================

function setupTabSwitching() {
    const tabs = document.querySelectorAll('.product-wishlist-dashboard .tab-btn');
    const contents = document.querySelectorAll('.product-wishlist-dashboard .tab-content');

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const targetTab = tab.dataset.tab;

            tabs.forEach(t => t.classList.remove('active'));
            contents.forEach(c => c.classList.remove('active'));

            tab.classList.add('active');
            document.getElementById(`tab-${targetTab}`)?.classList.add('active');
        });
    });
}

function setupProductActions() {
    // Botones de agregar producto
    document.querySelectorAll('.btn-add-product').forEach(btn => {
        btn.addEventListener('click', () => {
            Swal.close();
            setTimeout(() => openAddProductModal(), 100);
        });
    });

    // Botones de contribuir
    document.querySelectorAll('.btn-contribute').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const productId = btn.dataset.productId;
            Swal.close();
            setTimeout(() => openContributionModal(productId), 100);
        });
    });

    // Botones de ver detalles
    document.querySelectorAll('.btn-view-product').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const productId = btn.dataset.productId;
            Swal.close();
            setTimeout(() => openProductDetailModal(productId), 100);
        });
    });

    // Botones de editar
    document.querySelectorAll('.btn-edit-product').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            // Por ahora redirigir a detalles
            const productId = btn.dataset.productId;
            Swal.close();
            setTimeout(() => openProductDetailModal(productId), 100);
        });
    });

    // Botones de eliminar
    document.querySelectorAll('.btn-delete-product').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const productId = btn.dataset.productId;
            Swal.close();
            setTimeout(() => openDeleteProductModal(productId), 100);
        });
    });

    // Click en cards de producto
    document.querySelectorAll('.product-card').forEach(card => {
        card.addEventListener('click', (e) => {
            if (!e.target.closest('.btn-icon')) {
                const productId = card.dataset.productId;
                Swal.close();
                setTimeout(() => openProductDetailModal(productId), 100);
            }
        });
    });
}

function truncateText(text, maxLength) {
    if (!text) return '';
    return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
}

function formatDateShort(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' });
}

function addProductWishlistStyles() {
    if (document.getElementById('product-wishlist-modal-styles')) return;

    const style = document.createElement('style');
    style.id = 'product-wishlist-modal-styles';
    style.textContent = `
        .product-wishlist-dashboard {
            text-align: left;
        }

        .product-wishlist-dashboard .dashboard-tabs {
            display: flex;
            gap: 8px;
            margin-bottom: 20px;
            border-bottom: 2px solid #e5e7eb;
            padding-bottom: 12px;
        }

        .product-wishlist-dashboard .tab-btn {
            padding: 8px 16px;
            border: none;
            background: #f3f4f6;
            border-radius: 8px;
            cursor: pointer;
            font-weight: 500;
            transition: all 0.2s;
        }

        .product-wishlist-dashboard .tab-btn:hover {
            background: #e5e7eb;
        }

        .product-wishlist-dashboard .tab-btn.active {
            background: #3b82f6;
            color: white;
        }

        .product-wishlist-dashboard .tab-content {
            display: none;
        }

        .product-wishlist-dashboard .tab-content.active {
            display: block;
            animation: fadeIn 0.3s ease;
        }

        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }

        /* Stats Grid */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 12px;
            margin-bottom: 20px;
        }

        .stat-card {
            background: #f9fafb;
            padding: 16px;
            border-radius: 12px;
            text-align: center;
            border: 1px solid #e5e7eb;
        }

        .stat-card.highlight {
            background: linear-gradient(135deg, #dcfce7, #d1fae5);
            border-color: #86efac;
        }

        .stat-card .stat-icon {
            font-size: 1.5rem;
            margin-bottom: 8px;
        }

        .stat-card .stat-value {
            font-size: 1.25rem;
            font-weight: bold;
            color: #1f2937;
        }

        .stat-card .stat-label {
            font-size: 0.75rem;
            color: #6b7280;
            margin-top: 4px;
        }

        /* Action Banner */
        .action-banner {
            display: flex;
            justify-content: space-between;
            align-items: center;
            background: linear-gradient(135deg, #dbeafe, #e0f2fe);
            padding: 20px;
            border-radius: 12px;
            margin-bottom: 20px;
            border: 1px solid #93c5fd;
        }

        .banner-content {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .banner-icon {
            font-size: 2.5rem;
        }

        .banner-text p {
            margin: 4px 0 0;
            color: #6b7280;
        }

        /* Section Cards */
        .section-card {
            background: #f9fafb;
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 16px;
            border: 1px solid #e5e7eb;
        }

        .section-card.warning {
            background: #fffbeb;
            border-color: #fcd34d;
        }

        .section-card h4 {
            margin: 0 0 12px 0;
            color: #1f2937;
        }

        .section-hint {
            color: #6b7280;
            font-size: 0.875rem;
            margin-bottom: 12px;
        }

        /* Product Mini List */
        .product-mini-list {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        .product-mini-item {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 10px;
            background: white;
            border-radius: 8px;
            border: 1px solid #e5e7eb;
        }

        .product-mini-item.inactive {
            opacity: 0.8;
        }

        .product-mini-item img {
            width: 48px;
            height: 48px;
            object-fit: contain;
            border-radius: 6px;
            background: #f3f4f6;
        }

        .product-mini-info {
            flex: 1;
        }

        .product-mini-name {
            font-weight: 500;
            color: #1f2937;
        }

        .product-mini-progress {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-top: 4px;
        }

        .progress-bar-mini {
            flex: 1;
            height: 6px;
            background: #e5e7eb;
            border-radius: 3px;
            overflow: hidden;
        }

        .progress-bar-mini .progress-fill {
            height: 100%;
            background: #22c55e;
            border-radius: 3px;
            transition: width 0.3s;
        }

        .days-badge {
            background: #dbeafe;
            color: #1d4ed8;
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 0.75rem;
            font-weight: 600;
        }

        .btn-small {
            padding: 6px 12px;
            font-size: 0.875rem;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            background: #3b82f6;
            color: white;
        }

        /* Products List */
        .products-header {
            margin-bottom: 16px;
        }

        .products-list {
            display: flex;
            flex-direction: column;
            gap: 12px;
            max-height: 500px;
            overflow-y: auto;
        }

        /* Product Card */
        .product-card {
            display: flex;
            gap: 16px;
            padding: 16px;
            background: white;
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            cursor: pointer;
            transition: all 0.2s;
        }

        .product-card:hover {
            border-color: #3b82f6;
            box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
        }

        .product-card-image {
            position: relative;
            width: 100px;
            min-width: 100px;
        }

        .product-card-image img {
            width: 100%;
            height: 100px;
            object-fit: contain;
            border-radius: 8px;
            background: #f9fafb;
        }

        .product-card-image .store-badge {
            position: absolute;
            bottom: 4px;
            right: 4px;
            background: white;
            padding: 4px;
            border-radius: 4px;
            font-size: 1rem;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }

        .product-card-content {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        .product-card-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
        }

        .product-card-header h4 {
            margin: 0;
            font-size: 1rem;
            color: #1f2937;
            line-height: 1.4;
        }

        .product-actions {
            display: flex;
            gap: 4px;
        }

        .product-actions .btn-icon {
            padding: 6px;
            border: none;
            background: #f3f4f6;
            border-radius: 6px;
            cursor: pointer;
            transition: background 0.2s;
        }

        .product-actions .btn-icon:hover {
            background: #e5e7eb;
        }

        .product-progress {
            display: flex;
            flex-direction: column;
            gap: 6px;
        }

        .progress-bar {
            height: 8px;
            background: #e5e7eb;
            border-radius: 4px;
            overflow: hidden;
        }

        .progress-bar .progress-fill {
            height: 100%;
            border-radius: 4px;
            transition: width 0.3s;
        }

        .progress-info {
            display: flex;
            justify-content: space-between;
            font-size: 0.875rem;
            color: #6b7280;
        }

        .progress-percent {
            font-weight: 600;
            color: #1f2937;
        }

        .product-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }

        .meta-badge {
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 0.75rem;
            font-weight: 500;
        }

        .meta-badge.date {
            background: #f3f4f6;
            color: #4b5563;
        }

        .meta-badge.days {
            background: #dbeafe;
            color: #1d4ed8;
        }

        .meta-badge.days.soon {
            background: #fef3c7;
            color: #d97706;
        }

        /* Empty State */
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

        /* Completed Tab */
        .completed-list {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .completed-card {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 16px;
            background: #f0fdf4;
            border: 1px solid #86efac;
            border-radius: 12px;
        }

        .completed-icon {
            font-size: 1.5rem;
        }

        .completed-card img {
            width: 60px;
            height: 60px;
            object-fit: contain;
            border-radius: 8px;
            background: white;
        }

        .completed-info {
            flex: 1;
        }

        .completed-name {
            font-weight: 600;
            color: #1f2937;
        }

        .completed-amount {
            color: #059669;
            font-weight: bold;
            margin-top: 4px;
        }

        .completed-date {
            font-size: 0.75rem;
            color: #6b7280;
            margin-top: 4px;
        }

        /* Buttons */
        .btn-primary {
            padding: 10px 20px;
            background: linear-gradient(135deg, #3b82f6, #1d4ed8);
            color: white;
            border: none;
            border-radius: 8px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }

        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
        }

        /* Detail Modal */
        .product-detail-modal {
            text-align: left;
        }

        .product-detail-header {
            display: flex;
            gap: 20px;
            margin-bottom: 24px;
        }

        .product-detail-image {
            width: 150px;
            height: 150px;
            object-fit: contain;
            border-radius: 12px;
            background: #f9fafb;
            border: 1px solid #e5e7eb;
        }

        .product-detail-info {
            flex: 1;
        }

        .store-tag {
            display: inline-block;
            background: #dbeafe;
            color: #1d4ed8;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 0.875rem;
            margin-bottom: 8px;
        }

        .product-detail-info h3 {
            margin: 0 0 12px 0;
            font-size: 1.25rem;
            color: #1f2937;
        }

        .price-tag {
            font-size: 1.75rem;
            font-weight: bold;
            color: #059669;
            margin-bottom: 12px;
        }

        .product-link {
            color: #3b82f6;
            text-decoration: none;
            font-size: 0.875rem;
        }

        .product-link:hover {
            text-decoration: underline;
        }

        .product-detail-progress {
            background: #f9fafb;
            padding: 16px;
            border-radius: 12px;
            margin-bottom: 20px;
        }

        .progress-header, .progress-footer {
            display: flex;
            justify-content: space-between;
            margin-bottom: 8px;
        }

        .progress-footer {
            margin-top: 8px;
            margin-bottom: 0;
            font-size: 0.875rem;
            color: #6b7280;
        }

        .progress-bar.large {
            height: 12px;
        }

        .product-detail-stats {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 12px;
            margin-bottom: 20px;
        }

        .stat-item {
            background: #f9fafb;
            padding: 12px;
            border-radius: 8px;
        }

        .stat-item .stat-label {
            font-size: 0.75rem;
            color: #6b7280;
            display: block;
        }

        .stat-item .stat-value {
            font-weight: 600;
            color: #1f2937;
        }

        .stat-item .stat-value.highlight {
            color: #f59e0b;
        }

        .contributions-section, .notes-section {
            background: #f9fafb;
            padding: 16px;
            border-radius: 12px;
            margin-top: 16px;
        }

        .contributions-section h4, .notes-section h4 {
            margin: 0 0 12px 0;
            font-size: 1rem;
        }

        .contributions-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .contribution-item {
            display: flex;
            justify-content: space-between;
            padding: 8px;
            background: white;
            border-radius: 6px;
        }

        .contribution-date {
            color: #6b7280;
            font-size: 0.875rem;
        }

        .contribution-amount {
            color: #059669;
            font-weight: 600;
        }

        /* Contribution Form */
        .contribution-form {
            text-align: left;
        }

        .product-mini-header {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 12px;
            background: #f9fafb;
            border-radius: 8px;
            margin-bottom: 20px;
        }

        .product-mini-header img {
            width: 50px;
            height: 50px;
            object-fit: contain;
            border-radius: 6px;
        }

        .product-mini-progress-text {
            font-size: 0.875rem;
            color: #6b7280;
        }

        .form-group {
            margin-bottom: 16px;
        }

        .form-group label {
            display: block;
            margin-bottom: 8px;
            font-weight: 500;
            color: #374151;
        }

        .form-group input {
            width: 100%;
            padding: 12px;
            border: 2px solid #e5e7eb;
            border-radius: 8px;
            font-size: 1rem;
            box-sizing: border-box;
        }

        .form-group input:focus {
            outline: none;
            border-color: #3b82f6;
        }

        .form-group small {
            display: block;
            margin-top: 6px;
            color: #9ca3af;
            font-size: 0.875rem;
        }

        .quick-amounts {
            background: #f9fafb;
            padding: 12px;
            border-radius: 8px;
        }

        .quick-amounts > span {
            font-size: 0.875rem;
            color: #6b7280;
            display: block;
            margin-bottom: 8px;
        }

        .quick-buttons {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
        }

        .quick-amount-btn {
            padding: 8px 16px;
            background: white;
            border: 1px solid #e5e7eb;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.2s;
        }

        .quick-amount-btn:hover {
            background: #3b82f6;
            color: white;
            border-color: #3b82f6;
        }

        /* Botones de navegación */
        .product-detail-header .nav-btn {
            position: absolute;
            top: 10px;
            right: 10px;
        }

        .product-mini-header .nav-btn {
            position: absolute;
            top: 10px;
            right: 10px;
        }

        .nav-btn {
            background: #f3f4f6;
            border: 1px solid #d1d5db;
            border-radius: 6px;
            padding: 6px 12px;
            cursor: pointer;
            font-size: 0.9rem;
            transition: all 0.2s;
        }

        .nav-btn:hover {
            background: #e5e7eb;
            border-color: #9ca3af;
        }

        .nav-btn.back-btn {
            font-weight: 500;
        }
    `;
    document.head.appendChild(style);
}

/**
 * Revisar productos y mostrar alertas cuando la fecha estimada llegue a 0 días
 */
function checkProductAlerts(products) {
    const today = new Date();
    today.setHours(0, 0, 0, 0); // Solo fecha, sin hora

    products.forEach(async (product) => {
        if (product.days_remaining !== null && product.days_remaining <= 0) {
            const completionDate = new Date(product.estimated_completion_date);
            completionDate.setHours(0, 0, 0, 0);

            if (completionDate <= today) { // Fecha ya llegó o pasó
                if (product.progress_percent >= 100) {
                    // Producto completado, se puede comprar
                    await Swal.fire({
                        title: '🎉 ¡Producto Listo para Comprar!',
                        html: `
                            <div style="text-align: center;">
                                <img src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                                     style="width: 100px; height: 100px; object-fit: cover; border-radius: 8px; margin: 10px auto;" 
                                     onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                                <p><strong>${product.product_name}</strong></p>
                                <p>¡Has alcanzado el 100% del ahorro! Ya puedes comprar este producto.</p>
                                <p style="color: #10b981; font-weight: bold;">Meta completada: ${ProductWishlist.formatCurrency(product.target_amount)}</p>
                            </div>
                        `,
                        icon: 'success',
                        confirmButtonText: '¡Genial!',
                        confirmButtonColor: '#10b981'
                    });
                } else {
                    // Producto no completado, fecha se aplaza
                    const remaining = product.target_amount - product.current_amount;
                    
                    // Verificar si se puede recalcular
                    const activeIncomeSource = product.product_wishlist_income_sources?.find(s => s.active);
                    let canRecalculate = product.contribution_value > 0;
                    
                    // Si no hay contribución en el producto, verificar la asignación
                    if (!canRecalculate && activeIncomeSource) {
                        canRecalculate = activeIncomeSource.allocation_value > 0;
                    }
                    
                    if (canRecalculate) {
                        // Mostrar alerta con indicador de carga
                        const result = await Swal.fire({
                            title: '⚠️ Fecha de Compra Aplazada',
                            html: `
                                <div style="text-align: center;">
                                    <img src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                                         style="width: 100px; height: 100px; object-fit: cover; border-radius: 8px; margin: 10px auto;" 
                                         onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                                    <p><strong>${product.product_name}</strong></p>
                                    <p>No se han realizado suficientes aportes. La fecha estimada de compra se ha aplazado.</p>
                                    <p style="color: #ef4444;">Falta: ${ProductWishlist.formatCurrency(remaining)}</p>
                                    <p style="color: #6b7280; font-size: 0.9em;">Progreso actual: ${product.progress_percent}%</p>
                                    <p style="color: #f59e0b; font-size: 0.9em;">🔄 Recalculando fecha estimada...</p>
                                    <div class="loading-spinner" style="margin: 10px auto; width: 20px; height: 20px; border: 2px solid #f3f3f3; border-top: 2px solid #f59e0b; border-radius: 50%; animation: spin 1s linear infinite;"></div>
                                </div>
                                <style>
                                    @keyframes spin {
                                        0% { transform: rotate(0deg); }
                                        100% { transform: rotate(360deg); }
                                    }
                                </style>
                            `,
                            showConfirmButton: false,
                            allowOutsideClick: false,
                            allowEscapeKey: false
                        });

                        // Recalcular la fecha estimada
                        try {
                            await ProductWishlist.recalculateProductDate(product.id);
                            // Mostrar confirmación y cerrar
                            await Swal.fire({
                                title: '✅ Fecha Recalculada',
                                text: 'La fecha estimada de compra ha sido actualizada.',
                                icon: 'success',
                                timer: 2000,
                                showConfirmButton: false
                            });
                        } catch (error) {
                            console.error('Error recalculating date:', error);
                            await Swal.fire({
                                title: '⚠️ Error',
                                text: 'No se pudo recalcular la fecha. Verifica la configuración del producto.',
                                icon: 'warning',
                                confirmButtonText: 'Entendido'
                            });
                        }
                    } else {
                        // No se puede recalcular
                        await Swal.fire({
                            title: '⚠️ Fecha de Compra Aplazada',
                            html: `
                                <div style="text-align: center;">
                                    <img src="${product.product_image_url || 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'}" 
                                         style="width: 100px; height: 100px; object-fit: cover; border-radius: 8px; margin: 10px auto;" 
                                         onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2280%22>📦</text></svg>'" />
                                    <p><strong>${product.product_name}</strong></p>
                                    <p>No se han realizado suficientes aportes. La fecha estimada de compra se ha aplazado.</p>
                                    <p style="color: #ef4444;">Falta: ${ProductWishlist.formatCurrency(remaining)}</p>
                                    <p style="color: #6b7280; font-size: 0.9em;">Progreso actual: ${product.progress_percent}%</p>
                                    <p style="color: #6b7280; font-size: 0.9em;">La fecha no se puede recalcular automáticamente porque no hay configuración de aportes.</p>
                                </div>
                            `,
                            icon: 'warning',
                            confirmButtonText: 'Entendido',
                            confirmButtonColor: '#f59e0b'
                        });
                    }
                }
            }
        }
    });
}
