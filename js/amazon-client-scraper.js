/**
 * amazon-client-scraper.js
 * Scraping de Amazon directo desde el navegador del cliente
 * Evita CAPTCHA al usar la IP del usuario en lugar del servidor
 */

export async function scrapeAmazonFromClient(url) {
    try {
        // Fetch la página de Amazon
        const response = await fetch(url, {
            headers: {
                'User-Agent': navigator.userAgent,
                'Accept': 'text/html,application/xhtml+xml',
                'Accept-Language': 'es-MX,es;q=0.9'
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const html = await response.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');

        // Extraer nombre del producto
        let name = '';
        const nameSelectors = [
            '#productTitle',
            '#title',
            'h1.product-title',
            '[data-feature-name="title"]'
        ];
        
        for (const selector of nameSelectors) {
            const element = doc.querySelector(selector);
            if (element) {
                name = element.textContent.trim();
                break;
            }
        }

        // Extraer precio
        let price = 0;
        const priceSelectors = [
            '.a-price .a-offscreen',
            '#priceblock_ourprice',
            '#priceblock_dealprice',
            '.a-price-whole',
            '[data-a-color="price"] .a-offscreen'
        ];

        for (const selector of priceSelectors) {
            const element = doc.querySelector(selector);
            if (element) {
                const priceText = element.textContent.trim();
                const priceMatch = priceText.match(/[\d,]+\.?\d*/);
                if (priceMatch) {
                    price = parseFloat(priceMatch[0].replace(/,/g, ''));
                    break;
                }
            }
        }

        // Extraer imagen
        let image = '';
        const imageSelectors = [
            '#landingImage',
            '#imgBlkFront',
            '#main-image',
            '.a-dynamic-image'
        ];

        for (const selector of imageSelectors) {
            const element = doc.querySelector(selector);
            if (element) {
                image = element.src || element.dataset.src || element.dataset.oldHires || '';
                if (image) break;
            }
        }

        return {
            success: true,
            name,
            price,
            image,
            currency: 'MXN',
            platform: 'amazon'
        };

    } catch (error) {
        console.error('Error scraping Amazon from client:', error);
        
        // Si falla por CORS, intentar con API de respaldo
        if (error.message.includes('CORS') || error.message.includes('blocked')) {
            throw new Error('CORS_BLOCKED');
        }
        
        throw error;
    }
}
