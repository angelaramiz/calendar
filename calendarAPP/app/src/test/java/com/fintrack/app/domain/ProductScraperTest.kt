package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductScraperTest {

    @Test
    fun extractSharedUrl_picksFirstUrlAndTrimsPunctuation() {
        assertEquals(
            "https://www.mercadolibre.com.mx/x",
            ProductScraper.extractSharedUrl("Mira esto: https://www.mercadolibre.com.mx/x.")
        )
        assertEquals(
            "https://a.co/d/abc",
            ProductScraper.extractSharedUrl("https://a.co/d/abc (oferta)")
        )
        assertNull(ProductScraper.extractSharedUrl("sin link aquí"))
        assertNull(ProductScraper.extractSharedUrl(null))
    }

    @Test
    fun cleanProductName_removesStoreSuffixes() {
        assertEquals(
            "Audífonos Sony",
            ProductScraper.cleanProductName("Audífonos Sony : Amazon.com.mx: Deportes")
        )
        assertEquals(
            "Laptop HP",
            ProductScraper.cleanProductName("Laptop HP | MercadoLibre")
        )
        assertEquals(
            "Silla gamer",
            ProductScraper.cleanProductName("Silla gamer - $4,599")
        )
    }

    @Test
    fun storeFor_fixesShortStore() {
        assertEquals("Amazon", ProductScraper.storeFor("amazon", "https://www.amazon.com.mx/dp/x", "A"))
        assertEquals(
            "MercadoLibre",
            ProductScraper.storeFor("mercadolibre", "https://articulo.mercadolibre.com.mx/x", "")
        )
        assertEquals("Mi tienda", ProductScraper.storeFor("x", "https://x.com", "Mi tienda"))
    }

    @Test
    fun parseQuickResponse_success() {
        val body = """{"success":true,"data":{"url":"https://articulo.mercadolibre.com.mx/x","platform":"mercadolibre","name":"Bicicleta rodada 29 | MercadoLibre","price":7499.0,"store":"M"}}"""
        val result = ProductScraper.parseQuickResponse("https://articulo.mercadolibre.com.mx/x", body)
        assertTrue(result is ProductScraper.QuickResult.Success)
        val info = (result as ProductScraper.QuickResult.Success).info
        assertEquals("Bicicleta rodada 29", info.name)
        assertEquals(7499.0, info.price, 0.001)
        assertEquals("MercadoLibre", info.store)
    }

    @Test
    fun parseQuickResponse_platformObjectRealBackendShape() {
        // Forma real del backend: platform es objeto y success booleano.
        val body = """{"data":{"currency":"MXN","image":"","name":"Bicicleta rodada 29","platform":{"currency":"MXN","patterns":["mercadolibre"],"store":"MercadoLibre"},"price":7499.0,"store":"M","url":"https://articulo.mercadolibre.com.mx/x"},"success":true}"""
        val result = ProductScraper.parseQuickResponse("https://articulo.mercadolibre.com.mx/x", body)
        assertTrue(result is ProductScraper.QuickResult.Success)
        val info = (result as ProductScraper.QuickResult.Success).info
        assertEquals("Bicicleta rodada 29", info.name)
        assertEquals(7499.0, info.price, 0.001)
        assertEquals("MercadoLibre", info.store)
    }

    @Test
    fun parseQuickResponse_captchaAsksManual() {
        val body = """{"success":false,"error":"CAPTCHA_DETECTADO","message":"Amazon requiere verificación"}"""
        val result = ProductScraper.parseQuickResponse("https://www.amazon.com.mx/dp/x", body)
        assertTrue(result is ProductScraper.QuickResult.NeedsManual)
    }

    @Test
    fun parseQuickResponse_blockedPageAsksManual() {
        val body = """{"success":true,"data":{"url":"https://x","platform":"amazon","name":"Sign in","price":0.0,"store":"A"}}"""
        val result = ProductScraper.parseQuickResponse("https://x", body)
        assertTrue(result is ProductScraper.QuickResult.NeedsManual)
    }

    @Test
    fun parseQuickResponse_errorAndGarbage() {
        val error = ProductScraper.parseQuickResponse(
            "https://x",
            """{"success":false,"error":"ERROR_SCRAPING","message":"boom"}"""
        )
        assertTrue(error is ProductScraper.QuickResult.Error)
        val garbage = ProductScraper.parseQuickResponse("https://x", "no-json")
        assertTrue(garbage is ProductScraper.QuickResult.Error)
    }
}
