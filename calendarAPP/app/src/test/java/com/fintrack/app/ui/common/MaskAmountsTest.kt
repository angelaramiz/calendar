package com.fintrack.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Modo discreto (C10): el enmascarado de montos es puro y testeable;
 * solo la lectura del DataStore necesita Context (se verifica compilando).
 */
class MaskAmountsTest {

    @Test
    fun oculta_monto_simple() {
        assertEquals("$•••", maskAmounts("$1,234.00"))
    }

    @Test
    fun oculta_montos_dentro_de_frase() {
        assertEquals(
            "A pagar $••• (cargos $•••) el 5/9",
            maskAmounts("A pagar $150.000 (cargos $80.000) el 5/9")
        )
    }

    @Test
    fun deja_fechas_y_texto_intactos() {
        assertEquals(
            "Vence 5/9 · bimestral",
            maskAmounts("Vence 5/9 · bimestral")
        )
    }

    @Test
    fun sin_montos_devuelve_igual() {
        assertEquals("Sin cargos en este periodo.", maskAmounts("Sin cargos en este periodo."))
    }
}
