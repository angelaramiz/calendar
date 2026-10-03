package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketOcrTest {

    @Test
    fun total_gana_a_subtotal() {
        val texto = """
            OXXO CENTRO
            SABRITAS 45.00
            COCA 30.00
            SUBTOTAL $100.00
            IVA $16.00
            TOTAL $116.00
            GRACIAS POR SU COMPRA
        """.trimIndent()
        val r = TicketOcr.parse(texto)
        assertEquals(116.0, r.amount ?: -1.0, 0.0)
        assertEquals("OXXO CENTRO", r.merchant)
    }

    @Test
    fun varios_montos_sin_total_prefiere_el_que_trae_pesos() {
        val texto = """
            TIENDA DON PEPE
            ARTICULOS 3
            EFECTIVO 500
            CAMBIO $50.00
        """.trimIndent()
        val r = TicketOcr.parse(texto)
        assertEquals(50.0, r.amount ?: -1.0, 0.0)
    }

    @Test
    fun sin_monto_devuelve_null() {
        val r = TicketOcr.parse("GRACIAS POR SU VISITA\nFOLIO 123")
        assertNull(r.amount)
        assertNull(r.merchant)
    }

    @Test
    fun comercio_en_mayusculas_primera_linea() {
        val texto = "WALMART MEXICO\nTOTAL $250.00"
        val r = TicketOcr.parse(texto)
        assertEquals("WALMART MEXICO", r.merchant)
        assertEquals(250.0, r.amount ?: -1.0, 0.0)
    }

    @Test
    fun formato_mexicano_miles_y_decimales() {
        assertEquals(1250.5, TicketOcr.parseMoney("1,250.50") ?: -1.0, 0.0)
        val r = TicketOcr.parse("SORIANA\nTOTAL $1,250.50")
        assertEquals(1250.5, r.amount ?: -1.0, 0.0)
    }

    @Test
    fun decimal_con_coma() {
        assertEquals(850.5, TicketOcr.parseMoney("850,50") ?: -1.0, 0.0)
        val r = TicketOcr.parse("CHEDRAUI\nTOTAL 850,50")
        assertEquals(850.5, r.amount ?: -1.0, 0.0)
    }

    @Test
    fun total_sin_signo_ni_decimales() {
        val r = TicketOcr.parse("MERCADO\nTOTAL 1500")
        assertEquals(1500.0, r.amount ?: -1.0, 0.0)
    }

    @Test
    fun ignora_folio_y_anio_sueltos_como_monto() {
        val r = TicketOcr.parse("FOLIO 123\nNOTA 2026")
        assertNull(r.amount)
    }

    @Test
    fun subtotal_solo_se_reporta_como_unico_monto() {
        // Sin línea de TOTAL, el único monto disponible se sugiere igual
        // (el usuario lo confirma en QuickEntry): diseño honesto, no invento.
        val r = TicketOcr.parse("ABARROTES LUPITA\nSUBTOTAL $100.00")
        assertEquals(100.0, r.amount ?: -1.0, 0.0)
    }

    @Test
    fun comercio_title_case_por_fallback_primera_linea() {
        val r = TicketOcr.parse("Walmart Universidad\nTotal $300.00")
        assertEquals("Walmart Universidad", r.merchant)
    }

    @Test
    fun texto_vacio_devuelve_nulls() {
        val r = TicketOcr.parse("   ")
        assertNull(r.amount)
        assertNull(r.merchant)
    }
}
