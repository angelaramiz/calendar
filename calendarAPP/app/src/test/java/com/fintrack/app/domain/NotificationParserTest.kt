package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {

    private val allowed = setOf("com.mercadopago.wallet")

    @Test
    fun gastoMercadoPago_esAceptado() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Pagaste a Urbani",
            text = "Debitamos $ 40.00 de tu cuenta.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals(40.0, tx.amount, 0.001)
        assertEquals("EXPENSE", tx.type)
        assertEquals("Urbani", tx.merchant)
    }

    @Test
    fun ingresoMercadoPago_esAceptado() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Tu dinero ya está disponible",
            text = "Ingresaste $ 2.00 desde tu cuenta de STP.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals(2.0, tx.amount, 0.001)
        assertEquals("INCOME", tx.type)
    }

    @Test
    fun promocionConMonto_esRechazada() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Aviso",
            text = "Ingresa $1,048 más durante este mes para seguir con el 12% anual en octubre.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Rejected)
        assertEquals("promocion", (result as ParseResult.Rejected).reason)
    }

    @Test
    fun appNoPermitida_esRechazada() {
        val result = NotificationParser.parse(
            packageName = "com.juego.random",
            title = "Oferta",
            text = "Compra ahora con 50% de descuento.",
            allowedPackages = allowed
        )

        assertEquals(ParseResult.Rejected("app_no_permitida"), result)
    }

    @Test
    fun textoSinKeywords_esRechazado() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Hola",
            text = "Tienes mensajes nuevos en tu bandeja.",
            allowedPackages = allowed
        )

        assertEquals(ParseResult.Rejected("sin_keywords"), result)
    }

    @Test
    fun keywordSinMonto_esRechazado() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Aviso",
            text = "Tu pago fue procesado correctamente.",
            allowedPackages = allowed
        )

        assertEquals(ParseResult.Rejected("sin_monto"), result)
    }

    @Test
    fun keywordSinAcento_matcheaIgual() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Cargo",
            text = "Se hizo un debito de $100.00 a tu tarjeta.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        assertEquals("EXPENSE", (result as ParseResult.Accepted).tx.type)
    }

    @Test
    fun ingresoGanaSobreGasto_siHayAmbas() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Reembolso",
            text = "Reembolso de tu compra por $250.00.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        assertEquals("INCOME", (result as ParseResult.Accepted).tx.type)
    }

    @Test
    fun montoConComas_seParseaBien() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Nómina",
            text = "Depósito de nómina por $12,500.00.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        assertEquals(12500.0, (result as ParseResult.Accepted).tx.amount, 0.001)
    }

    @Test
    fun comercioSeExtraeDelTexto_siTituloNoLoTrae() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Compra",
            text = "Compra en Starbucks por $85.00.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals("Starbucks", tx.merchant)
        assertEquals("Comida", tx.category)
    }
}
