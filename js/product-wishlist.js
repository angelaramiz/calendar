/**
 * product-wishlist.js
 * Módulo para gestión de productos en línea (wishlist) con web scraping
 * Sistema de planificación financiera para compras online
 */

import { supabase } from './supabase-client.js';
import { captchaSolver } from './captcha-solver.js';

// ==================== CONFIGURACIÓN ====================

// URL del API de scraping - Configura esto en config.js o usa variable de entorno
// En desarrollo: http://localhost:5000
// En producción (Fly.io): https://calendar-backend-ed6u5g.fly.dev
const SCRAPER_API_URL = window.SCRAPER_API_URL || 
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
        ? 'http://localhost:5000'
        : 'https://calendar-backend-ed6u5g.fly.dev');

const DEBUG = true;

function logInfo(action, data) {
    if (DEBUG) console.log(`[PRODUCT-WISHLIST] ${action}`, data);
}

function logError(action, error, context = {}) {
    console.error(`[PRODUCT-WISHLIST] ERROR in ${action}:`, { error: error.message || error, context });
}

// ==================== SCRAPING ====================

/**
 * Mostrar toast de progreso para carga de imagen
 */
function showImageLoadingToast(productName) {
    const toastId = `image-toast-${Date.now()}`;
    const toast = document.createElement('div');
    toast.id = toastId;
    toast.className = 'image-loading-toast';
    toast.innerHTML = `
        <div class="toast-icon">🖼️</div>
        <div class="toast-content">
            <div class="toast-title">Cargando imagen</div>
            <div class="toast-subtitle">${productName.substring(0, 40)}${productName.length > 40 ? '...' : ''}</div>
            <div class="toast-progress">
                <div class="toast-progress-bar"></div>
            </div>
        </div>
    `;
    
    document.body.appendChild(toast);
    
    // Animar entrada
    setTimeout(() => toast.classList.add('show'), 10);
    
    return toastId;
}

/**
 * Actualizar toast a completado
 */
function completeImageToast(toastId, success = true) {
    const toast = document.getElementById(toastId);
    if (!toast) return;
    
    const icon = toast.querySelector('.toast-icon');
    const title = toast.querySelector('.toast-title');
    const progress = toast.querySelector('.toast-progress');
    
    if (success) {
        icon.textContent = '✅';
        title.textContent = 'Imagen cargada';
        progress.style.display = 'none';
    } else {
        icon.textContent = '⚠️';
        title.textContent = 'Sin imagen';
        progress.style.display = 'none';
    }
    
    // Remover después de 2 segundos
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 2000);
}

/**
 * Obtener solo la imagen de un producto (llamada en segundo plano)
 * @param {string} url - URL del producto
 * @param {string} productName - Nombre del producto para el toast
 * @returns {Promise<string>} - URL de la imagen
 */
async function fetchProductImage(url, productName = 'Producto') {
    const toastId = showImageLoadingToast(productName);
    
    try {
        const response = await fetch(`${SCRAPER_API_URL}/api/scrape/image`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        });

        const result = await response.json();
        
        if (result.success && result.image) {
            completeImageToast(toastId, true);
            return result.image;
        }
        completeImageToast(toastId, false);
        return '';
    } catch (error) {
        console.warn('[fetchProductImage] Error:', error);
        completeImageToast(toastId, false);
        return '';
    }
}

/**
 * Hacer scraping de un producto por URL (progresivo: datos rápido, imagen después)
 * @param {string} url - URL del producto
 * @returns {Promise<Object>} - Datos del producto
 */
export async function scrapeProduct(url, retryWithCaptcha = true) {
    try {
        logInfo('scrapeProduct', { url });

        // 🚀 FASE 1: Obtener datos básicos (nombre + precio) RÁPIDO
        const response = await fetch(`${SCRAPER_API_URL}/api/scrape/quick`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        });

        const result = await response.json();

        if (!result.success) {
            throw new Error(result.error || 'Error al obtener información del producto');
        }

        const product = result.data;
        
        // Limpiar nombre si tiene sufijo de tienda
        if (product.name) {
            // Remover sufijos como ": Amazon.com.mx: Deportes y Aire Libre"
            product.name = product.name
                .replace(/\s*:\s*Amazon\.com\.mx.*$/i, '')
                .replace(/\s*:\s*Amazon\.com.*$/i, '')
                .replace(/\s*\|\s*MercadoLibre.*$/i, '')
                .replace(/\s*-\s*\$\s*[\d,.]+\s*$/i, '')
                .trim();
        }
        
        // Corregir tienda si es solo una letra
        if (product.store && product.store.length <= 2) {
            if (product.platform === 'amazon' || product.url?.toLowerCase().includes('amazon') || product.url?.toLowerCase().includes('a.co')) {
                product.store = 'Amazon';
            } else if (product.platform === 'mercadolibre' || product.url?.toLowerCase().includes('mercadolibre')) {
                product.store = 'MercadoLibre';
            }
        }
        
        // Verificar si el scraping fue exitoso (tiene datos reales)
        const genericNames = [
            'mercado libre', 
            'preferencias de cookies', 
            'producto de mercadolibre',
            'producto de amazon',
            'amazon.com.mx',
            'sign in'
        ];
        
        // Solo es genérico si el nombre COMPLETO coincide o está vacío
        const nameIsGeneric = !product.name || 
                             product.name.length < 10 ||
                             genericNames.some(g => product.name.toLowerCase() === g.toLowerCase());
        
        // Considerar válido si tiene nombre real (aunque no tenga precio)
        const isValidScrape = !nameIsGeneric;
        
        if (!isValidScrape || product.price <= 0) {
            // Scraping parcial o falló, marcar para edición
            product.needsManualInput = true;
            if (!isValidScrape) product.scrapingFailed = true;
            logInfo('scrapeProduct', { message: 'Scraping parcial, requiere datos manuales', product });
        }

        // 🖼️ FASE 2: Obtener imagen en segundo plano (NO bloquea)
        product.imageLoading = true; // Flag para mostrar loading
        fetchProductImage(url, product.name || 'Producto').then(imageUrl => {
            product.image = imageUrl;
            product.imageLoading = false;
            // Disparar evento personalizado para actualizar UI si es necesario
            window.dispatchEvent(new CustomEvent('product-image-loaded', { 
                detail: { url, imageUrl } 
            }));
        }).catch(err => {
            console.warn('No se pudo cargar la imagen:', err);
            product.imageLoading = false;
        });

        logInfo('scrapeProduct', { product });
        return product;
    } catch (error) {
        logError('scrapeProduct', error, { url });
        
        // 🤖 Si es error de CAPTCHA y podemos reintentar, abrir solver
        if (error.message?.includes('CAPTCHA_DETECTADO') && retryWithCaptcha) {
            try {
                logInfo('scrapeProduct', 'CAPTCHA detectado, abriendo solver...');
                
                // Abrir modal para que usuario resuelva CAPTCHA y extraer datos
                const productData = await captchaSolver.solveCaptcha(url);
                
                // Retornar datos extraídos directamente del popup
                logInfo('scrapeProduct', 'Datos extraídos del popup:', productData);
                
                // Agregar URL si no está presente
                if (!productData.url) {
                    productData.url = url;
                }
                
                // 🖼️ Obtener imagen en segundo plano si no se extrajo
                if (!productData.image && productData.name) {
                    productData.imageLoading = true;
                    fetchProductImage(url, productData.name).then(imageUrl => {
                        productData.image = imageUrl;
                        productData.imageLoading = false;
                        window.dispatchEvent(new CustomEvent('product-image-loaded', { 
                            detail: { url, imageUrl } 
                        }));
                    }).catch(err => {
                        console.warn('No se pudo cargar la imagen:', err);
                        productData.imageLoading = false;
                    });
                }
                
                return productData;
                
            } catch (captchaError) {
                if (captchaError.message === 'CAPTCHA_CANCELLED') {
                    logInfo('scrapeProduct', 'Usuario canceló resolución de CAPTCHA');
                } else if (captchaError.message === 'POPUP_CLOSED') {
                    logInfo('scrapeProduct', 'Usuario cerró el popup sin confirmar');
                }
                // Si falla, continuar con entrada manual
            }
        }
        
        // Devolver objeto parcial que indica fallo
        return {
            url,
            platform: detectPlatformFromUrl(url),
            name: '',
            price: 0,
            image: '',
            currency: 'MXN',
            store: detectStoreFromUrl(url),
            needsManualInput: true,
            scrapingFailed: true,
            error: error.error || error.message
        };
    }
}

/**
 * Detectar plataforma desde URL (fallback local)
 */
function detectPlatformFromUrl(url) {
    const urlLower = url.toLowerCase();
    if (urlLower.includes('mercadolibre') || urlLower.includes('mercadolivre')) return 'mercadolibre';
    if (urlLower.includes('amazon')) return 'amazon';
    if (urlLower.includes('ebay')) return 'ebay';
    if (urlLower.includes('aliexpress')) return 'aliexpress';
    return 'generic';
}

/**
 * Detectar tienda desde URL (fallback local)
 */
function detectStoreFromUrl(url) {
    try {
        const urlObj = new URL(url);
        const host = urlObj.hostname.replace('www.', '');
        if (host.includes('mercadolibre')) return 'MercadoLibre';
        if (host.includes('amazon')) return 'Amazon';
        return host.split('.')[0].charAt(0).toUpperCase() + host.split('.')[0].slice(1);
    } catch (e) {
        return 'Tienda Online';
    }
}

/**
 * Verificar precio actual de un producto
 * @param {string} url - URL del producto
 * @param {number} currentPrice - Precio actual guardado
 * @returns {Promise<Object>} - Información de cambio de precio
 */
export async function checkProductPrice(url, currentPrice) {
    try {
        const response = await fetch(`${SCRAPER_API_URL}/api/scrape/price-check`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, currentPrice })
        });

        const result = await response.json();
        return result.success ? result.data : null;
    } catch (error) {
        logError('checkProductPrice', error, { url });
        return null;
    }
}

// ==================== CRUD PRODUCTOS ====================

/**
 * Obtener todos los productos del usuario
 * @param {string} userId - ID del usuario
 * @param {Object} filters - Filtros opcionales
 * @returns {Promise<Array>} - Lista de productos
 */
export async function getProductWishlist(userId, filters = {}) {
    try {
        if (!userId) {
            logError('getProductWishlist', 'userId is required');
            return [];
        }

        logInfo('getProductWishlist', { userId, filters });

        let query = supabase
            .from('product_wishlist')
            .select(`
                *,
                product_wishlist_income_sources (
                    id,
                    income_pattern_id,
                    allocation_type,
                    allocation_value,
                    active,
                    income_patterns (
                        id,
                        name,
                        base_amount,
                        frequency
                    )
                )
            `)
            .eq('user_id', userId)
            .order('priority', { ascending: false })
            .order('created_at', { ascending: false });

        if (filters.status) {
            query = query.eq('status', filters.status);
        }
        if (filters.plan_type) {
            query = query.eq('plan_type', filters.plan_type);
        }

        const { data, error } = await query;

        if (error) {
            logError('getProductWishlist', error, { userId });
            return [];
        }

        // Enriquecer con datos calculados
        return (data || []).map(product => ({
            ...product,
            progress_percent: product.target_amount > 0
                ? Math.min(100, Math.round((product.current_amount / product.target_amount) * 100))
                : 0,
            remaining_amount: Math.max(0, product.target_amount - product.current_amount),
            days_remaining: product.estimated_completion_date
                ? Math.ceil((new Date(product.estimated_completion_date) - new Date()) / (1000 * 60 * 60 * 24))
                : null
        }));
    } catch (error) {
        logError('getProductWishlist', error, { userId });
        return [];
    }
}

/**
 * Obtener un producto por ID
 * @param {string} productId - ID del producto
 * @returns {Promise<Object|null>} - Producto o null
 */
export async function getProductById(productId) {
    try {
        const { data, error } = await supabase
            .from('product_wishlist')
            .select(`
                *,
                product_wishlist_income_sources (
                    id,
                    income_pattern_id,
                    allocation_type,
                    allocation_value,
                    active,
                    income_patterns (
                        id,
                        name,
                        base_amount,
                        frequency
                    )
                ),
                product_wishlist_contributions (
                    id,
                    amount,
                    contribution_date,
                    notes,
                    created_at
                )
            `)
            .eq('id', productId)
            .single();

        if (error) {
            logError('getProductById', error, { productId });
            return null;
        }

        return {
            ...data,
            progress_percent: data.target_amount > 0
                ? Math.min(100, Math.round((data.current_amount / data.target_amount) * 100))
                : 0,
            remaining_amount: Math.max(0, data.target_amount - data.current_amount)
        };
    } catch (error) {
        logError('getProductById', error, { productId });
        return null;
    }
}

/**
 * Crear un nuevo producto en la wishlist
 * @param {string} userId - ID del usuario
 * @param {Object} productData - Datos del producto
 * @returns {Promise<Object>} - Producto creado
 */
export async function createProductWishlist(userId, productData) {
    try {
        if (!userId) throw new Error('userId es requerido');
        if (!productData.product_name) throw new Error('product_name es requerido');
        if (!productData.product_price || productData.product_price <= 0) {
            throw new Error('product_price debe ser mayor a 0');
        }
        
        // Permitir URL vacía para entrada manual
        const productUrl = productData.product_url || 'manual://entry';

        logInfo('createProductWishlist', { userId, productName: productData.product_name });

        const insertData = {
            user_id: userId,
            product_url: productUrl,
            product_name: productData.product_name,
            product_image_url: productData.product_image_url || null,
            product_price: parseFloat(productData.product_price),
            product_store: productData.product_store || null,
            target_amount: parseFloat(productData.target_amount || productData.product_price),
            current_amount: parseFloat(productData.current_amount) || 0,
            plan_type: productData.plan_type || 'medium',
            contribution_type: productData.contribution_type || 'fixed',
            contribution_value: parseFloat(productData.contribution_value) || 0,
            priority: productData.priority || 5,
            start_date: productData.start_date || new Date().toISOString().split('T')[0],
            estimated_completion_date: productData.estimated_completion_date || null,
            original_estimated_date: productData.estimated_completion_date || null,
            notes: productData.notes || null,
            status: 'active'
        };

        const { data, error } = await supabase
            .from('product_wishlist')
            .insert(insertData)
            .select()
            .single();

        if (error) {
            logError('createProductWishlist', error, insertData);
            throw new Error(`Error al crear producto: ${error.message}`);
        }

        logInfo('createProductWishlist', `Created product ${data.id}`);
        return data;
    } catch (error) {
        logError('createProductWishlist', error, { userId, productData });
        throw error;
    }
}

/**
 * Actualizar un producto
 * @param {string} productId - ID del producto
 * @param {Object} updates - Datos a actualizar
 * @returns {Promise<Object>} - Producto actualizado
 */
export async function updateProductWishlist(productId, updates) {
    try {
        if (!productId) throw new Error('productId es requerido');

        logInfo('updateProductWishlist', { productId, updates });

        // Limpiar campos
        const cleanUpdates = {};
        const allowedFields = [
            'product_name', 'product_image_url', 'product_price', 'target_amount',
            'current_amount', 'plan_type', 'contribution_type', 'contribution_value',
            'priority', 'estimated_completion_date', 'status', 'notes'
        ];

        for (const field of allowedFields) {
            if (updates[field] !== undefined) {
                cleanUpdates[field] = updates[field];
            }
        }

        const { data, error } = await supabase
            .from('product_wishlist')
            .update(cleanUpdates)
            .eq('id', productId)
            .select()
            .single();

        if (error) {
            logError('updateProductWishlist', error, { productId, updates });
            throw new Error(`Error al actualizar producto: ${error.message}`);
        }

        return data;
    } catch (error) {
        logError('updateProductWishlist', error, { productId });
        throw error;
    }
}

/**
 * Eliminar un producto (soft delete)
 * @param {string} productId - ID del producto
 */
export async function deleteProductWishlist(productId) {
    try {
        if (!productId) throw new Error('productId es requerido');

        logInfo('deleteProductWishlist', { productId });

        const { error } = await supabase
            .from('product_wishlist')
            .update({ status: 'cancelled' })
            .eq('id', productId);

        if (error) {
            logError('deleteProductWishlist', error, { productId });
            throw new Error(`Error al eliminar producto: ${error.message}`);
        }
    } catch (error) {
        logError('deleteProductWishlist', error, { productId });
        throw error;
    }
}

// ==================== CONTRIBUCIONES ====================

/**
 * Agregar contribución a un producto
 * @param {string} productId - ID del producto
 * @param {Object} contributionData - Datos de la contribución
 * @returns {Promise<Object>} - Contribución creada
 */
export async function addContribution(productId, contributionData) {
    try {
        if (!productId) throw new Error('productId es requerido');
        if (!contributionData.amount || contributionData.amount <= 0) {
            throw new Error('El monto debe ser mayor a 0');
        }

        logInfo('addContribution', { productId, amount: contributionData.amount });

        const insertData = {
            product_wishlist_id: productId,
            amount: parseFloat(contributionData.amount),
            income_pattern_id: contributionData.income_pattern_id || null,
            movement_id: contributionData.movement_id || null,
            notes: contributionData.notes || null,
            contribution_date: contributionData.date || new Date().toISOString().split('T')[0]
        };

        const { data, error } = await supabase
            .from('product_wishlist_contributions')
            .insert(insertData)
            .select()
            .single();

        if (error) {
            logError('addContribution', error, insertData);
            throw new Error(`Error al agregar contribución: ${error.message}`);
        }

        return data;
    } catch (error) {
        logError('addContribution', error, { productId, contributionData });
        throw error;
    }
}

/**
 * Obtener historial de contribuciones
 * @param {string} productId - ID del producto
 * @returns {Promise<Array>} - Lista de contribuciones
 */
export async function getContributions(productId) {
    try {
        const { data, error } = await supabase
            .from('product_wishlist_contributions')
            .select('*')
            .eq('product_wishlist_id', productId)
            .order('contribution_date', { ascending: false });

        if (error) {
            logError('getContributions', error, { productId });
            return [];
        }

        return data || [];
    } catch (error) {
        logError('getContributions', error, { productId });
        return [];
    }
}

// ==================== FUENTES DE INGRESO ====================

/**
 * Asignar ingreso a un producto
 * @param {string} productId - ID del producto
 * @param {string} incomePatternId - ID del patrón de ingreso
 * @param {Object} allocation - Configuración de asignación
 * @returns {Promise<Object>} - Asignación creada
 */
export async function assignIncomeToProduct(productId, incomePatternId, allocation) {
    try {
        if (!productId) throw new Error('productId es requerido');
        if (!incomePatternId) throw new Error('incomePatternId es requerido');

        logInfo('assignIncomeToProduct', { productId, incomePatternId });

        const insertData = {
            product_wishlist_id: productId,
            income_pattern_id: incomePatternId,
            allocation_type: allocation.type || 'percent',
            allocation_value: parseFloat(allocation.value),
            active: true,
            notes: allocation.notes || null
        };

        const { data, error } = await supabase
            .from('product_wishlist_income_sources')
            .insert(insertData)
            .select()
            .single();

        if (error) {
            logError('assignIncomeToProduct', error, insertData);
            throw new Error(`Error al asignar ingreso: ${error.message}`);
        }

        return data;
    } catch (error) {
        logError('assignIncomeToProduct', error, { productId, incomePatternId });
        throw error;
    }
}

/**
 * Remover asignación de ingreso
 * @param {string} assignmentId - ID de la asignación
 */
export async function removeIncomeAssignment(assignmentId) {
    try {
        const { error } = await supabase
            .from('product_wishlist_income_sources')
            .delete()
            .eq('id', assignmentId);

        if (error) {
            logError('removeIncomeAssignment', error, { assignmentId });
            throw new Error(`Error al remover asignación: ${error.message}`);
        }
    } catch (error) {
        logError('removeIncomeAssignment', error, { assignmentId });
        throw error;
    }
}

// ==================== ANÁLISIS FINANCIERO ====================

/**
 * Analizar opciones de planificación para un producto
 * @param {number} targetAmount - Monto objetivo
 * @param {Array} incomePatterns - Patrones de ingreso del usuario
 * @param {Array} expensePatterns - Patrones de gasto
 * @param {Array} existingPlans - Planes existentes
 * @returns {Promise<Object>} - Análisis y opciones
 */
export async function analyzeProductPlan(targetAmount, incomePatterns, expensePatterns = [], existingPlans = []) {
    try {
        logInfo('analyzeProductPlan', { targetAmount, incomes: incomePatterns.length });

        // Usar cálculo local (más rápido y sin dependencias externas)
        return calculatePlanOptionsLocal(targetAmount, incomePatterns, expensePatterns, existingPlans);
    } catch (error) {
        logError('analyzeProductPlan', error);
        throw error;
    }
}

/**
 * Cálculo local de opciones de planificación
 */
function calculatePlanOptionsLocal(targetAmount, incomePatterns, expensePatterns, existingPlans) {
    // Calcular ingreso mensual
    const calculateMonthlyAmount = (pattern) => {
        const base = parseFloat(pattern.base_amount);
        switch (pattern.frequency) {
            case 'weekly': return base * 4.33;
            case 'biweekly': return base * 2;
            case 'monthly': return base;
            case 'yearly': return base / 12;
            default: return base;
        }
    };

    const totalMonthlyIncome = incomePatterns.reduce((sum, p) => sum + calculateMonthlyAmount(p), 0);
    const totalMonthlyExpenses = expensePatterns.reduce((sum, p) => sum + calculateMonthlyAmount(p), 0);

    // Calcular compromisos existentes
    const existingCommitments = existingPlans.reduce((sum, plan) => {
        if (plan.contribution_type === 'percent') {
            return sum + (totalMonthlyIncome * parseFloat(plan.contribution_value));
        }
        return sum + parseFloat(plan.contribution_value || 0);
    }, 0);

    const availableIncome = Math.max(0, totalMonthlyIncome - totalMonthlyExpenses - existingCommitments);

    const options = [];

    // Siempre generar las 3 opciones (corto, mediano, largo plazo)
    
    // 🚀 Corto plazo (50%+ del ingreso disponible - 1 a 2 meses máximo)
    const shortTermPercent = 0.50;
    const shortTermAmount = availableIncome * shortTermPercent;
    if (shortTermAmount > 0) {
        let description, estimatedMonths, estimatedWeeks, monthlyContribution, percentOfIncome;
        
        // Si el producto cuesta menos del 50% del ingreso mensual, calcular en semanas
        if (targetAmount < shortTermAmount) {
            // Producto muy barato - calcular en semanas (mínimo 1 semana)
            const weeklyBudget = shortTermAmount / 4.33;
            const weeksNeeded = Math.max(1, Math.ceil(targetAmount / weeklyBudget));
            const actualWeeklyAmount = targetAmount / weeksNeeded;
            
            monthlyContribution = actualWeeklyAmount * 4.33;
            estimatedMonths = 1; // Siempre menos de 1 mes
            estimatedWeeks = weeksNeeded;
            percentOfIncome = Math.round((monthlyContribution / availableIncome) * 100);
            description = weeksNeeded === 1 
                ? `Meta alcanzable en 1 semana`
                : `Meta alcanzable en ${weeksNeeded} semanas`;
        } else {
            // Producto normal - calcular en meses
            const monthsNeeded = Math.ceil(targetAmount / shortTermAmount);
            monthlyContribution = shortTermAmount;
            estimatedMonths = monthsNeeded;
            estimatedWeeks = Math.ceil(monthsNeeded * 4.33);
            percentOfIncome = Math.round(shortTermPercent * 100);
            description = monthsNeeded === 1 
                ? `Meta alcanzable en 1 mes`
                : `Meta alcanzable en ${monthsNeeded} meses`;
        }
        
        options.push({
            type: 'short',
            name: '🚀 Corto Plazo',
            description: description,
            monthlyContribution: Math.round(monthlyContribution * 100) / 100,
            contributionValue: Math.round(monthlyContribution * 100) / 100,
            contributionType: 'fixed',
            percentOfIncome: percentOfIncome,
            estimatedMonths: estimatedMonths,
            estimatedWeeks: estimatedWeeks,
            priority: 'high',
            recommended: estimatedMonths <= 2
        });
    }

    // ⚖️ Mediano plazo (30% del ingreso disponible - 2 a 4 meses)
    const mediumTermPercent = 0.30;
    const mediumTermAmount = availableIncome * mediumTermPercent;
    if (mediumTermAmount > 0) {
        const mediumTermMonths = Math.ceil(targetAmount / mediumTermAmount);
        options.push({
            type: 'medium',
            name: '⚖️ Mediano Plazo',
            description: `Meta alcanzable en ~${mediumTermMonths} mes${mediumTermMonths > 1 ? 'es' : ''}`,
            monthlyContribution: Math.round(mediumTermAmount * 100) / 100,
            contributionValue: Math.round(mediumTermAmount * 100) / 100,
            contributionType: 'fixed',
            percentOfIncome: Math.round(mediumTermPercent * 100),
            estimatedMonths: mediumTermMonths,
            estimatedWeeks: Math.ceil(mediumTermMonths * 4.33),
            priority: 'medium',
            recommended: mediumTermMonths >= 2 && mediumTermMonths <= 4
        });
    }

    // 🐢 Largo plazo (15% del ingreso disponible - hasta 6 meses)
    const longTermPercent = 0.15;
    const longTermAmount = availableIncome * longTermPercent;
    if (longTermAmount > 0) {
        const longTermMonths = Math.ceil(targetAmount / longTermAmount);
        options.push({
            type: 'long',
            name: '🐢 Largo Plazo',
            description: `Meta alcanzable en ~${longTermMonths} mes${longTermMonths > 1 ? 'es' : ''}`,
            monthlyContribution: Math.round(longTermAmount * 100) / 100,
            contributionValue: Math.round(longTermAmount * 100) / 100,
            contributionType: 'fixed',
            percentOfIncome: Math.round(longTermPercent * 100),
            estimatedMonths: longTermMonths,
            estimatedWeeks: Math.ceil(longTermMonths * 4.33),
            priority: 'low',
            recommended: longTermMonths > 4 && longTermMonths <= 6
        });
    }

    // Si no hay opciones, crear una personalizada
    if (options.length === 0 && availableIncome > 0) {
        const customMonths = Math.ceil(targetAmount / (availableIncome * 0.20));
        options.push({
            type: customMonths <= 2 ? 'short' : customMonths <= 5 ? 'medium' : 'long',
            name: '📊 Plan Personalizado',
            description: `Meta alcanzable en ~${customMonths} meses`,
            monthlyContribution: Math.round((targetAmount / customMonths) * 100) / 100,
            percentOfIncome: Math.round(((targetAmount / customMonths) / availableIncome) * 100),
            estimatedMonths: customMonths,
            estimatedWeeks: Math.ceil(customMonths * 4.33),
            priority: 'medium',
            recommended: true
        });
    }

    return {
        analysis: {
            totalMonthlyIncome: Math.round(totalMonthlyIncome * 100) / 100,
            totalMonthlyExpenses: Math.round(totalMonthlyExpenses * 100) / 100,
            existingCommitments: Math.round(existingCommitments * 100) / 100,
            availableIncome: Math.round(availableIncome * 100) / 100,
            targetAmount,
            canProceed: availableIncome > 0
        },
        options
    };
}

/**
 * Calcular fecha estimada de completación
 * @param {number} targetAmount - Monto objetivo
 * @param {number} currentAmount - Monto actual
 * @param {number} contributionValue - Valor de contribución
 * @param {string} contributionType - Tipo: 'percent' o 'fixed'
 * @param {number} incomeAmount - Monto del ingreso base
 * @param {string} frequency - Frecuencia del ingreso
 * @returns {Date|null} - Fecha estimada
 */
export function calculateEstimatedDate(targetAmount, currentAmount, contributionValue, contributionType, incomeAmount, frequency) {
    const remaining = targetAmount - (currentAmount || 0);
    if (remaining <= 0) return new Date();

    let perPeriodContribution;
    if (contributionType === 'percent') {
        perPeriodContribution = incomeAmount * contributionValue;
    } else {
        perPeriodContribution = contributionValue;
    }

    if (perPeriodContribution <= 0) return null;

    const periodsNeeded = Math.ceil(remaining / perPeriodContribution);

    let daysPerPeriod;
    switch (frequency) {
        case 'weekly': daysPerPeriod = 7; break;
        case 'biweekly': daysPerPeriod = 14; break;
        case 'monthly': daysPerPeriod = 30; break;
        case 'yearly': daysPerPeriod = 365; break;
        default: daysPerPeriod = 30;
    }

    const totalDays = periodsNeeded * daysPerPeriod;
    const estimatedDate = new Date();
    estimatedDate.setDate(estimatedDate.getDate() + totalDays);

    return estimatedDate;
}

// ==================== SEGUIMIENTO E INACTIVIDAD ====================

/**
 * Verificar productos inactivos y recalcular fechas
 * @param {string} userId - ID del usuario
 * @returns {Promise<Array>} - Productos que fueron actualizados
 */
export async function checkInactiveProducts(userId) {
    try {
        const products = await getProductWishlist(userId, { status: 'active' });
        const updatedProducts = [];

        // Configuración de inactividad por tipo de plan
        const inactivityThresholds = {
            short: 7,   // 7 días
            medium: 14, // 14 días
            long: 30    // 30 días
        };

        const today = new Date();

        for (const product of products) {
            const lastActivity = product.last_contribution_date
                ? new Date(product.last_contribution_date)
                : new Date(product.start_date);

            const daysSinceActivity = Math.floor((today - lastActivity) / (1000 * 60 * 60 * 24));
            const threshold = inactivityThresholds[product.plan_type] || 14;

            if (daysSinceActivity > threshold && product.product_wishlist_income_sources?.length > 0) {
                // Obtener datos del ingreso principal
                const mainSource = product.product_wishlist_income_sources.find(s => s.active);
                if (mainSource && mainSource.income_patterns) {
                    const newDate = calculateEstimatedDate(
                        product.target_amount,
                        product.current_amount,
                        mainSource.allocation_value,
                        mainSource.allocation_type,
                        mainSource.income_patterns.base_amount,
                        mainSource.income_patterns.frequency
                    );

                    if (newDate && newDate.toISOString().split('T')[0] !== product.estimated_completion_date) {
                        // Actualizar fecha
                        await updateProductWishlist(product.id, {
                            estimated_completion_date: newDate.toISOString().split('T')[0]
                        });

                        updatedProducts.push({
                            product,
                            oldDate: product.estimated_completion_date,
                            newDate: newDate.toISOString().split('T')[0],
                            daysDelayed: Math.ceil((newDate - new Date(product.estimated_completion_date)) / (1000 * 60 * 60 * 24))
                        });
                    }
                }
            }
        }

        return updatedProducts;
    } catch (error) {
        logError('checkInactiveProducts', error, { userId });
        return [];
    }
}

// ==================== DASHBOARD ====================

/**
 * Obtener resumen del dashboard de productos
 * @param {string} userId - ID del usuario
 * @returns {Promise<Object>} - Resumen del dashboard
 */
export async function getProductWishlistDashboard(userId) {
    try {
        const products = await getProductWishlist(userId);

        const active = products.filter(p => p.status === 'active');
        const completed = products.filter(p => p.status === 'completed');
        const paused = products.filter(p => p.status === 'paused');

        const totalTarget = active.reduce((sum, p) => sum + parseFloat(p.target_amount), 0);
        const totalCurrent = active.reduce((sum, p) => sum + parseFloat(p.current_amount), 0);

        // Productos próximos a completar (menos de 30 días)
        const nearCompletion = active.filter(p => p.days_remaining !== null && p.days_remaining <= 30 && p.days_remaining > 0);

        // Productos inactivos (sin contribuciones recientes)
        const today = new Date();
        const inactivityThresholds = { short: 7, medium: 14, long: 30 };
        const inactive = active.filter(p => {
            const lastActivity = p.last_contribution_date
                ? new Date(p.last_contribution_date)
                : new Date(p.start_date);
            const days = Math.floor((today - lastActivity) / (1000 * 60 * 60 * 24));
            return days > (inactivityThresholds[p.plan_type] || 14);
        });

        return {
            summary: {
                total: products.length,
                active: active.length,
                completed: completed.length,
                paused: paused.length,
                totalTarget: Math.round(totalTarget * 100) / 100,
                totalCurrent: Math.round(totalCurrent * 100) / 100,
                overallProgress: totalTarget > 0 ? Math.round((totalCurrent / totalTarget) * 100) : 0
            },
            nearCompletion,
            inactive,
            items: active.slice(0, 5), // Top 5 activos
            completedRecently: completed.slice(0, 3)
        };
    } catch (error) {
        logError('getProductWishlistDashboard', error, { userId });
        return {
            summary: { total: 0, active: 0, completed: 0, paused: 0, totalTarget: 0, totalCurrent: 0, overallProgress: 0 },
            nearCompletion: [],
            inactive: [],
            items: [],
            completedRecently: []
        };
    }
}

// ==================== UTILIDADES ====================

/**
 * Formatear moneda
 * @param {number} amount - Monto
 * @returns {string} - Monto formateado
 */
export function formatCurrency(amount) {
    return new Intl.NumberFormat('es-MX', {
        style: 'currency',
        currency: 'MXN'
    }).format(amount || 0);
}

/**
 * Obtener etiqueta de tipo de plan
 * @param {string} planType - Tipo de plan
 * @returns {Object} - Información del plan
 */
export function getPlanTypeInfo(planType) {
    const types = {
        short: {
            label: 'Corto Plazo',
            icon: '🚀',
            color: '#ef4444',
            description: '1-8 semanas'
        },
        medium: {
            label: 'Mediano Plazo',
            icon: '⚖️',
            color: '#f59e0b',
            description: '2-5 meses'
        },
        long: {
            label: 'Largo Plazo',
            icon: '🐢',
            color: '#22c55e',
            description: '5-9 meses'
        }
    };
    return types[planType] || types.medium;
}

/**
 * Obtener icono de tienda
 * @param {string} store - Nombre de la tienda
 * @returns {string} - Emoji de la tienda
 */
export function getStoreIcon(store) {
    const icons = {
        mercadolibre: '🛒',
        amazon: '📦',
        liverpool: '🏬',
        walmart: '🏪',
        elektra: '⚡',
        coppel: '🏠',
        aliexpress: '🌐',
        generic: '🛍️'
    };
    return icons[store?.toLowerCase()] || '🛍️';
}
