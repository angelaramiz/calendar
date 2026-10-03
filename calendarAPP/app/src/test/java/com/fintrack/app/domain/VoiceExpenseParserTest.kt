package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceExpenseParserTest {

    @Test
    fun gasto_basico_con_monto_y_categoria() {
        val r = VoiceExpenseParser.parse("Gasté 200 en tacos")
        assertEquals(200.0, r.amount!!, 0.001)
        assertEquals("Comida", r.category)
        assertFalse(r.isIncome)
    }

    @Test
    fun monto_con_coma_de_miles() {
        val r = VoiceExpenseParser.parse("Pagué 1,500 de renta")
        assertEquals(1500.0, r.amount!!, 0.001)
        assertEquals("Vivienda", r.category)
    }

    @Test
    fun monto_con_punto_de_miles() {
        val r = VoiceExpenseParser.parse("Compré despensa por 1.500 pesos")
        assertEquals(1500.0, r.amount!!, 0.001)
    }

    @Test
    fun monto_decimal_con_punto() {
        val r = VoiceExpenseParser.parse("Gasté 200.50 en gasolina")
        assertEquals(200.50, r.amount!!, 0.001)
        assertEquals("Transporte", r.category)
    }

    @Test
    fun monto_decimal_con_coma() {
        val r = VoiceExpenseParser.parse("Gasté 200,50 en gasolina")
        assertEquals(200.50, r.amount!!, 0.001)
    }

    @Test
    fun sin_monto_devuelve_null() {
        val r = VoiceExpenseParser.parse("Gasté en tacos con Ana")
        assertNull(r.amount)
        assertEquals("Comida", r.category)
    }

    @Test
    fun categoria_por_palabra_clave() {
        assertEquals("Transporte", VoiceExpenseParser.parse("Uber 120").category)
        assertEquals("Ocio", VoiceExpenseParser.parse("Cine 300").category)
        assertEquals("Salud", VoiceExpenseParser.parse("Farmacia 450 pesos").category)
        assertEquals("Otros", VoiceExpenseParser.parse("Gasté 100 en algo").category)
    }

    @Test
    fun ingreso_detectado_con_sueldo() {
        val r = VoiceExpenseParser.parse("Me pagaron 8000 de la quincena")
        assertTrue(r.isIncome)
        assertEquals("Sueldo", r.category)
        assertEquals(8000.0, r.amount!!, 0.001)
    }

    @Test
    fun reembolso_es_ingreso() {
        val r = VoiceExpenseParser.parse("Recibí 350 de reembolso")
        assertTrue(r.isIncome)
        assertEquals("Reembolso", r.category)
    }
}
