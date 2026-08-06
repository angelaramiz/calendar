/**
 * product-price-monitor.js
 * Sistema de monitoreo automático de precios y disponibilidad de productos
 * Ejecuta máximo una vez al día por producto
 */

import { supabase } from './supabase-client.js';
import { scrapeProduct } from './product-wishlist.js';

const MONITOR_STORAGE_KEY = 'product_monitor_last_run';
const MONITOR_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24 horas

/**
 * Verificar si ya se ejecutó el monitoreo hoy
 */
function canRunMonitor() {
    const lastRun = localStorage.getItem(MONITOR_STORAGE_KEY);
    if (!lastRun) return true;
    
    const lastRunTime = new Date(lastRun).getTime();
    const now = Date.now();
    
    return (now - lastRunTime) >= MONITOR_INTERVAL_MS;
}

/**
 * Marcar que el monitoreo se ejecutó
 */
function markMonitorRun() {
    localStorage.setItem(MONITOR_STORAGE_KEY, new Date().toISOString());
}

/**
 * Obtener todos los productos activos del usuario
 */
async function getActiveProducts(userId) {
    const { data, error } = await supabase
        .from('product_wishlist')
        .select('*')
        .eq('user_id', userId)
        .eq('status', 'planning')
        .order('created_at', { ascending: false });
    
    if (error) {
        console.error('[ProductMonitor] Error fetching products:', error);
        return [];
    }
    
    return data || [];
}

/**
 * Actualizar precio del producto en la base de datos
 */
async function updateProductPrice(productId, newPrice, availability = 'available') {
    const { error } = await supabase
        .from('product_wishlist')
        .update({
            price: newPrice,
            availability,
            last_price_check: new Date().toISOString(),
            updated_at: new Date().toISOString()
        })
        .eq('id', productId);
    
    if (error) {
        console.error('[ProductMonitor] Error updating price:', error);
        return false;
    }
    
    return true;
}

/**
 * Recalcular fechas objetivo basado en nuevo precio
 */
function recalculateTargetDate(product, newPrice) {
    const monthlyIncome = product.monthly_income || 0;
    if (monthlyIncome <= 0) return null;
    
    const currentSaved = product.current_amount || 0;
    const remaining = newPrice - currentSaved;
    
    if (remaining <= 0) {
        // Ya puede comprarlo!
        return {
            canBuyNow: true,
            monthsNeeded: 0,
            targetDate: new Date().toISOString()
        };
    }
    
    const monthsNeeded = Math.ceil(remaining / monthlyIncome);
    const targetDate = new Date();
    targetDate.setMonth(targetDate.getMonth() + monthsNeeded);
    
    return {
        canBuyNow: false,
        monthsNeeded,
        targetDate: targetDate.toISOString()
    };
}

/**
 * Mostrar notificación de cambio de precio
 */
function showPriceChangeNotification(product, oldPrice, newPrice, availability) {
    const priceChange = newPrice - oldPrice;
    const percentChange = ((priceChange / oldPrice) * 100).toFixed(1);
    
    let icon, title, message, type;
    
    if (availability === 'unavailable') {
        icon = '🚫';
        title = 'Producto no disponible';
        message = `${product.name} ya no está disponible en la tienda.`;
        type = 'warning';
    } else if (priceChange > 0) {
        icon = '📈';
        title = 'Precio aumentó';
        message = `${product.name} subió $${Math.abs(priceChange).toFixed(2)} (+${percentChange}%)`;
        type = 'warning';
    } else if (priceChange < 0) {
        icon = '🎉';
        title = '¡Precio bajó!';
        message = `${product.name} bajó $${Math.abs(priceChange).toFixed(2)} (-${Math.abs(percentChange)}%)`;
        type = 'success';
    } else {
        // Sin cambios
        return;
    }
    
    // Mostrar notificación usando SweetAlert
    Swal.fire({
        icon: type,
        title: `${icon} ${title}`,
        html: `
            <p style="margin-bottom: 15px;">${message}</p>
            <div style="display: flex; justify-content: space-between; padding: 12px; background: #f3f4f6; border-radius: 8px;">
                <div>
                    <div style="font-size: 12px; color: #6b7280;">Precio anterior</div>
                    <div style="font-size: 18px; font-weight: 600; color: #ef4444;">$${oldPrice.toLocaleString('es-MX')}</div>
                </div>
                <div>
                    <div style="font-size: 12px; color: #6b7280;">Precio nuevo</div>
                    <div style="font-size: 18px; font-weight: 600; color: #10b981;">$${newPrice.toLocaleString('es-MX')}</div>
                </div>
            </div>
        `,
        confirmButtonText: 'Entendido',
        showCancelButton: availability === 'unavailable',
        cancelButtonText: 'Ver opciones'
    }).then((result) => {
        if (availability === 'unavailable' && result.dismiss === Swal.DismissReason.cancel) {
            showUnavailableOptions(product);
        }
    });
}

/**
 * Mostrar opciones cuando producto no está disponible
 */
function showUnavailableOptions(product) {
    Swal.fire({
        title: '¿Qué deseas hacer?',
        html: `
            <p style="margin-bottom: 20px;">El producto <strong>${product.name}</strong> ya no está disponible.</p>
        `,
        showDenyButton: true,
        showCancelButton: true,
        confirmButtonText: '🔄 Mantener con último precio',
        denyButtonText: '🗑️ Eliminar producto',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#3b82f6',
        denyButtonColor: '#ef4444'
    }).then(async (result) => {
        if (result.isConfirmed) {
            // Mantener producto
            Swal.fire('Producto mantenido', 'Se conservará con el último precio conocido', 'success');
        } else if (result.isDenied) {
            // Eliminar producto
            const { error } = await supabase
                .from('product_wishlist')
                .delete()
                .eq('id', product.id);
            
            if (!error) {
                Swal.fire('Producto eliminado', 'Puedes buscar una alternativa', 'success');
                // Recargar productos
                window.dispatchEvent(new CustomEvent('products-updated'));
            }
        }
    });
}

/**
 * Mostrar notificación de que ya puede comprar
 */
function showCanBuyNowNotification(product, newPrice) {
    const currentSaved = product.current_amount || 0;
    
    Swal.fire({
        icon: 'success',
        title: '🎊 ¡Ya puedes comprarlo!',
        html: `
            <p style="margin-bottom: 15px;"><strong>${product.name}</strong></p>
            <div style="padding: 16px; background: linear-gradient(135deg, #10b981 0%, #059669 100%); border-radius: 12px; color: white; margin-bottom: 15px;">
                <div style="font-size: 14px; opacity: 0.9;">Has ahorrado</div>
                <div style="font-size: 32px; font-weight: 700;">$${currentSaved.toLocaleString('es-MX')}</div>
                <div style="font-size: 14px; opacity: 0.9; margin-top: 8px;">Precio actual: $${newPrice.toLocaleString('es-MX')}</div>
            </div>
            <p style="color: #10b981; font-weight: 500;">¡Tienes suficiente para realizar la compra! 🎉</p>
        `,
        confirmButtonText: 'Ver producto',
        confirmButtonColor: '#10b981'
    });
}

/**
 * Monitorear un producto individual
 */
async function monitorProduct(product) {
    console.log(`[ProductMonitor] Checking: ${product.name}`);
    
    try {
        // Hacer scraping del producto
        const scrapedData = await scrapeProduct(product.url);
        
        if (!scrapedData || scrapedData.needsManualInput) {
            console.warn(`[ProductMonitor] Could not scrape: ${product.name}`);
            return;
        }
        
        const oldPrice = product.price;
        const newPrice = scrapedData.price;
        const availability = scrapedData.error ? 'unavailable' : 'available';
        
        // Verificar si hay cambios
        if (availability === 'unavailable') {
            // Producto no disponible
            await updateProductPrice(product.id, oldPrice, 'unavailable');
            showPriceChangeNotification(product, oldPrice, oldPrice, 'unavailable');
            return;
        }
        
        if (Math.abs(newPrice - oldPrice) < 0.01) {
            // Sin cambios significativos
            await updateProductPrice(product.id, newPrice, 'available');
            return;
        }
        
        // Actualizar precio
        await updateProductPrice(product.id, newPrice, 'available');
        
        // Recalcular fechas
        const calculation = recalculateTargetDate(product, newPrice);
        
        if (calculation?.canBuyNow) {
            // ¡Ya puede comprarlo!
            showCanBuyNowNotification(product, newPrice);
        } else {
            // Mostrar cambio de precio
            showPriceChangeNotification(product, oldPrice, newPrice, 'available');
        }
        
    } catch (error) {
        console.error(`[ProductMonitor] Error monitoring ${product.name}:`, error);
    }
}

/**
 * Ejecutar monitoreo de todos los productos
 */
export async function runPriceMonitor(userId) {
    // Verificar si ya se ejecutó hoy
    if (!canRunMonitor()) {
        console.log('[ProductMonitor] Already ran today, skipping...');
        return;
    }
    
    console.log('[ProductMonitor] Starting daily price check...');
    
    // Obtener productos activos
    const products = await getActiveProducts(userId);
    
    if (products.length === 0) {
        console.log('[ProductMonitor] No products to monitor');
        markMonitorRun();
        return;
    }
    
    console.log(`[ProductMonitor] Monitoring ${products.length} products...`);
    
    // Monitorear cada producto con un pequeño delay para no sobrecargar
    for (let i = 0; i < products.length; i++) {
        await monitorProduct(products[i]);
        
        // Delay de 2 segundos entre productos
        if (i < products.length - 1) {
            await new Promise(resolve => setTimeout(resolve, 2000));
        }
    }
    
    // Marcar que ya se ejecutó
    markMonitorRun();
    
    console.log('[ProductMonitor] Daily check completed');
}

/**
 * Inicializar monitoreo automático al cargar la app
 */
export function initPriceMonitor() {
    // Ejecutar al cargar la página
    const user = supabase.auth.getUser();
    user.then(({ data }) => {
        if (data?.user) {
            // Ejecutar después de 5 segundos para no interferir con la carga inicial
            setTimeout(() => {
                runPriceMonitor(data.user.id);
            }, 5000);
        }
    });
}
