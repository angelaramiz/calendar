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
    fun promoTerminalMini_esRechazada() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "¡Solo por tiempo limitado!",
            text = "Llévate tu Terminal MINI por solo $ 99 y obtén los beneficios.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Rejected)
        assertEquals("promocion", (result as ParseResult.Rejected).reason)
    }

    @Test
    fun promoPaqueteTelcel_esRechazada() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "¡Tu paquete Telcel vence esta noche!",
            text = "Recarga con tu saldo en cuenta y mantente conectado.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Rejected)
        assertEquals("promocion", (result as ParseResult.Rejected).reason)
    }

    @Test
    fun ofertaPorSoloPrecio_esRechazada() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Oferta exclusiva",
            text = "Aprovecha, llévalo por solo $ 1,299 este fin de semana.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Rejected)
        assertEquals("promocion", (result as ParseResult.Rejected).reason)
    }

    @Test
    fun ingresoConMontoEnTitulo_esAceptado() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Recibiste $ 200.00",
            text = "Bautista Gonzalez Maria Remedios te envió dinero y ya está disponible en tu cuenta.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals(200.0, tx.amount, 0.001)
        assertEquals("INCOME", tx.type)
        assertEquals("Bautista Gonzalez Maria Remedios", tx.merchant)
    }

    @Test
    fun dineroEnviado_esGasto() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Enviaste $ 150.00",
            text = "Le enviaste dinero a Juan Perez.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        assertEquals("EXPENSE", (result as ParseResult.Accepted).tx.type)
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

    @Test
    fun pago_rechazado_no_es_movimiento() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Rechazamos tu pago a Paypal steam games",
            text = "Ingresa $752.48 para realizar el pago.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Rejected)
        assertEquals("movimiento_rechazado", (result as ParseResult.Rejected).reason)
    }

    @Test
    fun instruccion_de_fondeo_no_es_ingreso() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Fondos insuficientes",
            text = "Ingresa $500.00 para completar tu compra.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Rejected)
        assertEquals("movimiento_rechazado", (result as ParseResult.Rejected).reason)
    }

    @Test
    fun bancos_nuevos_estan_en_allowlist() {
        val esperados = listOf(
            "com.nu.production",
            "dif.tech.plata",
            "com.didiglobal.passenger",
            "com.citibanamex.banamexmobile",
            "mx.com.bancoazteca.bazdigitalmovil",
            "com.pagopopmobile",
            "com.paypal.android.p2pmobile",
            "com.google.android.apps.walletnfcrel"
        )
        esperados.forEach {
            assertTrue(it, NotificationParser.DEFAULT_PACKAGES.contains(it))
        }
    }

    @Test
    fun notificacion_nu_con_lista_por_defecto_es_aceptada() {
        // Sin pasar allowlist: usa DEFAULT_PACKAGES.
        val result = NotificationParser.parse(
            packageName = "com.nu.production",
            title = "Compra aprobada",
            text = "Compra en Liverpool por $1,250.00 con tu tarjeta Nu."
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals(1250.0, tx.amount, 0.001)
        assertEquals("EXPENSE", tx.type)
        assertEquals("Liverpool", tx.merchant)
    }

    @Test
    fun pago_contactless_de_wallet_es_detectado() {
        // Sin pasar allowlist: usa DEFAULT_PACKAGES.
        val result = NotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Pago realizado",
            text = "Pagaste $350.00 en Starbucks con tu tarjeta terminación 1234."
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals(350.0, tx.amount, 0.001)
        assertEquals("EXPENSE", tx.type)
        assertEquals("Comida", tx.category)
    }

    @Test
    fun app_fuera_de_allowlist_sigue_rechazada() {
        val result = NotificationParser.parse(
            packageName = "com.ejemplo.otro",
            title = "Compra aprobada",
            text = "Compra en Liverpool por $1,250.00."
        )

        assertTrue(result is ParseResult.Rejected)
        assertEquals("app_no_permitida", (result as ParseResult.Rejected).reason)
    }

    @Test
    fun pago_mercadopago_debitamos_cuenta_es_gasto() {
        // Caso real 14/09/2026: "Pagaste a Pay trans urbani / Debitamos $ 40.00 de tu cuenta."
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Pagaste a Pay trans urbani",
            text = "Debitamos $ 40.00 de tu cuenta.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals(40.0, tx.amount, 0.001)
        assertEquals("EXPENSE", tx.type)
        assertEquals("Pay trans urbani", tx.merchant)
    }

    private fun categoriaDe(title: String, text: String, packageName: String = "com.nu.production"): String {
        val result = NotificationParser.parse(
            packageName = packageName,
            title = title,
            text = text,
            allowedPackages = allowed + packageName
        )
        assertTrue("$title | $text", result is ParseResult.Accepted)
        return (result as ParseResult.Accepted).tx.category
    }

    @Test
    fun categorias_por_comercio() {
        assertEquals("Transporte", categoriaDe("Viaje completado", "Pagaste $120.00 por tu viaje en DiDi."))
        assertEquals("Transporte", categoriaDe("Carga de gasolina", "Cargaron $800.00 en Pemex con tu tarjeta."))
        assertEquals("Servicios", categoriaDe("Pago aplicado", "Pagaste tu recibo de luz CFE por $450.00."))
        assertEquals("Salud", categoriaDe("Compra aprobada", "Compra en Farmacia Benavides por $320.00."))
        assertEquals("Compras", categoriaDe("Compra aprobada", "Compra en Amazon por $599.00 con tu tarjeta."))
        assertEquals("Compras", categoriaDe("Pago en tienda", "Pagaste $150.00 en OXXO."))
        assertEquals("Vivienda", categoriaDe("Cargo mensual", "Cargaron $8,000.00 de renta a tu tarjeta."))
        assertEquals("Educación", categoriaDe("Pago aplicado", "Pagaste la colegiatura por $3,500.00."))
        assertEquals("Ocio", categoriaDe("Suscripción", "Cargaron $219.00 de Netflix a tu tarjeta."))
        assertEquals("Comida", categoriaDe("Consumo", "Pagaste $250.00 en Starbucks."))
    }

    @Test
    fun categorias_financieras_y_efectivo() {
        assertEquals(
            "Finanzas",
            categoriaDe("Pago aplicado", "Se aplicó el pago de tu tarjeta por $5,000.00.")
        )
        assertEquals(
            "Compras",
            categoriaDe("Compra aprobada", "Compra en Liverpool por $1,250.00 con tu tarjeta Nu.")
        )
        assertEquals(
            "Efectivo",
            categoriaDe("Retiro exitoso", "Retiraste $2,000.00 en cajero Banamex.")
        )
        assertEquals(
            "Transferencias",
            categoriaDe("Transferencia exitosa", "Transferiste $1,000.00 por SPEI a Juan.")
        )
    }

    @Test
    fun categorias_de_ingreso() {
        assertEquals(
            "Sueldo",
            categoriaDe("Nómina recibida", "Tu nómina de $12,000.00 ya está disponible.")
        )
        assertEquals(
            "Reembolso",
            categoriaDe("Devolución", "Te reembolsamos $300.00 de tu compra.")
        )
        assertEquals(
            "Otros",
            categoriaDe("Recibiste dinero", "Te enviaron $200.00 y ya está disponible.")
        )
    }

    @Test
    fun wallet_transaccion_aprobada_sin_verbo_es_gasto() {
        // Billetera de Google: a veces avisa sin "pagaste/compraste".
        val result = NotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Transacción aprobada",
            text = "Transacción aprobada en OXXO por $250.00."
        )

        assertTrue(result is ParseResult.Accepted)
        val tx = (result as ParseResult.Accepted).tx
        assertEquals("EXPENSE", tx.type)
        assertEquals(250.0, tx.amount, 0.001)
        assertEquals("Compras", tx.category)
    }

    @Test
    fun wallet_monto_prefiere_signo_peso_sobre_terminacion() {
        val result = NotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Compra con tarjeta",
            text = "Compra con tarjeta •1234 por $85.00 en OXXO."
        )

        assertTrue(result is ParseResult.Accepted)
        assertEquals(85.0, (result as ParseResult.Accepted).tx.amount, 0.001)
    }

    @Test
    fun monto_sin_comas_ni_decimales_no_se_trunca() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Depósito",
            text = "Te depositaron 1500 pesos.",
            allowedPackages = allowed
        )

        assertTrue(result is ParseResult.Accepted)
        assertEquals(1500.0, (result as ParseResult.Accepted).tx.amount, 0.001)
    }

    @Test
    fun wallet_en_ingles_se_detecta() {
        val paid = NotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Payment",
            text = "You paid $20.00 at OXXO."
        )
        assertTrue(paid is ParseResult.Accepted)
        assertEquals("EXPENSE", (paid as ParseResult.Accepted).tx.type)

        val received = NotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Money received",
            text = "You received $100.00."
        )
        assertTrue(received is ParseResult.Accepted)
        assertEquals("INCOME", (received as ParseResult.Accepted).tx.type)
    }

    @Test
    fun movimiento_sin_monto_se_rechaza() {
        // "No reconoces este movimiento" sin cantidad no es registrable.
        val result = NotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Aviso de seguridad",
            text = "No reconoces este movimiento, revísalo en tu app."
        )

        assertTrue(result is ParseResult.Rejected)
    }

    @Test
    fun banco_desconocido_con_aviso_de_cargo_se_sugiere() {
        // Paquete fuera de la allowlist pero con texto de movimiento real.
        assertTrue(
            NotificationParser.looksLikeBankActivity(
                packageName = "mx.banco.desconocido",
                title = "Compra aprobada",
                text = "Pagaste $850.50 en Liverpool con tu tarjeta."
            )
        )
    }

    @Test
    fun banco_desconocido_con_promo_no_se_sugiere() {
        assertTrue(
            !NotificationParser.looksLikeBankActivity(
                packageName = "mx.banco.desconocido",
                title = "Promoción",
                text = "Aprovecha 20% de descuento pagando $500 o más."
            )
        )
    }

    @Test
    fun app_comun_sin_movimiento_no_se_sugiere() {
        assertTrue(
            !NotificationParser.looksLikeBankActivity(
                packageName = "com.whatsapp",
                title = "Juan",
                text = "Nos vemos a las 8 en la entrada."
            )
        )
    }

    @Test
    fun mensajeria_con_numero_y_verbo_no_se_sugiere() {
        // "Te envió 3 fotos": verbo genérico + número, pero sin señal bancaria.
        assertTrue(
            !NotificationParser.looksLikeBankActivity(
                packageName = "com.whatsapp",
                title = "María",
                text = "Te envió 3 fotos nuevas del viaje."
            )
        )
    }

    @Test
    fun juego_con_numero_no_se_sugiere() {
        assertTrue(
            !NotificationParser.looksLikeBankActivity(
                packageName = "com.juego.ejemplo",
                title = "Energía lista",
                text = "Tu energía se recargó: 5/5, vuelve a jugar."
            )
        )
    }

    @Test
    fun red_social_con_vistas_no_se_sugiere() {
        assertTrue(
            !NotificationParser.looksLikeBankActivity(
                packageName = "com.youtube",
                title = "Tu video despega",
                text = "Tu video llegó a 1,000 vistas, sigue así."
            )
        )
    }

    @Test
    fun banco_desconocido_con_spei_se_sugiere() {
        assertTrue(
            NotificationParser.looksLikeBankActivity(
                packageName = "mx.banco.desconocido",
                title = "SPEI recibido",
                text = "Recibiste un SPEI de Juan por $1,200.00."
            )
        )
    }

    @Test
    fun banco_desconocido_con_tarjeta_enmascarada_se_sugiere() {
        assertTrue(
            NotificationParser.looksLikeBankActivity(
                packageName = "mx.banco.desconocido",
                title = "Cargo aprobado",
                text = "Cargo a tu tarjeta ****5678 por $320 en OXXO."
            )
        )
    }

    @Test
    fun marca_corta_no_vale_como_substring() {
        // "nu" en "nuevo"/"anual" no es Nu; "anual" además es promo.
        assertTrue(
            !NotificationParser.looksLikeBankActivity(
                packageName = "com.tienda.ejemplo",
                title = "Nuevo beneficio anual",
                text = "Activa tu nuevo beneficio anual con 500 puntos."
            )
        )
    }

    @Test
    fun regla_de_categoria_gana_a_la_heuristica() {
        // Sin regla, "Urbani" no matchea nada y queda en Otros.
        val sinRegla = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Pagaste a Urbani",
            text = "Debitamos $ 40.00 de tu cuenta.",
            allowedPackages = allowed
        )
        assertTrue(sinRegla is ParseResult.Accepted)
        assertEquals("Otros", (sinRegla as ParseResult.Accepted).tx.category)

        val conRegla = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Pagaste a Urbani",
            text = "Debitamos $ 40.00 de tu cuenta.",
            allowedPackages = allowed,
            rules = mapOf("urbani" to "Comida")
        )
        assertTrue(conRegla is ParseResult.Accepted)
        assertEquals("Comida", (conRegla as ParseResult.Accepted).tx.category)
    }

    @Test
    fun regla_de_categoria_es_case_insensitive() {
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Pagaste a Urbani",
            text = "Debitamos $ 40.00 de tu cuenta.",
            allowedPackages = allowed,
            rules = mapOf("URBANI" to "Comida")
        )
        assertTrue(result is ParseResult.Accepted)
        assertEquals("Comida", (result as ParseResult.Accepted).tx.category)
    }

    @Test
    fun regla_no_aplica_a_otro_comercio() {
        // Match exacto por comercio: la regla de Oxxo no toca a Starbucks.
        val result = NotificationParser.parse(
            packageName = "com.mercadopago.wallet",
            title = "Compra",
            text = "Compra en Starbucks por $85.00.",
            allowedPackages = allowed,
            rules = mapOf("oxxo" to "Ocio")
        )
        assertTrue(result is ParseResult.Accepted)
        assertEquals("Comida", (result as ParseResult.Accepted).tx.category)
    }
}
