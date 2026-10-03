package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DebtPlannerTest {

    private val desde = YearMonth.of(2026, 10)

    private fun tarjeta(saldo: Double, cat: Double = 60.0) =
        DebtPlanner.Deuda("tarjeta", "Nu", saldo, cat)

    @Test
    fun agregacion_suma_tarjetas_msi_y_personales() {
        // Saldo de tarjeta vía summarize con cargos reales.
        // Hoy 5 oct, corte día 10: estado abierto = 10 sep, periodo [10 ago, 10 sep).
        val resumen = CreditCardPlanner.summarize(
            cardId = "nu",
            cutoffDay = 10,
            paymentDay = 25,
            charges = listOf(LocalDate.of(2026, 9, 5) to 3_000.0),
            today = LocalDate.of(2026, 10, 5)
        )
        val deudas = DebtPlanner.agregar(
            tarjetas = listOf(DebtPlanner.Deuda("nu", "Nu", resumen.remaining, 60.0)),
            msi = listOf(DebtPlanner.Deuda("msi1", "Celular MSI", 8_500.0, 0.0)),
            personales = listOf(DebtPlanner.Deuda("juan", "Juan", 300.0, 0.0))
        )
        assertEquals(3, deudas.size)
        assertEquals(3_000.0 + 8_500.0 + 300.0, DebtPlanner.total(deudas), 0.001)
    }

    @Test
    fun libertad_llega_a_cero_con_fecha() {
        val resultado = DebtPlanner.simular(
            listOf(tarjeta(3_000.0)),
            pagoMensual = 1_000.0,
            desde = desde,
            estrategia = DebtPlanner.Estrategia.NIEVE
        )
        assertNotNull(resultado.fechaLibertad)
        assertEquals(0.0, resultado.filas.last().saldoRestante, 0.01)
        assertTrue(resultado.filas.size <= 5)
    }

    @Test
    fun pago_insuficiente_no_libera() {
        // Interés mensual de $10,000 al 60% = $500; pagar $400 nunca termina.
        val resultado = DebtPlanner.simular(
            listOf(tarjeta(10_000.0)),
            pagoMensual = 400.0,
            desde = desde,
            estrategia = DebtPlanner.Estrategia.NIEVE
        )
        assertNull(resultado.fechaLibertad)
    }

    @Test
    fun nieve_liquida_antes_una_deuda() {
        val chica = DebtPlanner.Deuda("chica", "Chica", 1_000.0, 20.0)
        val grande = DebtPlanner.Deuda("grande", "Grande", 9_000.0, 80.0)
        val nieve = DebtPlanner.simular(listOf(chica, grande), 2_000.0, desde, DebtPlanner.Estrategia.NIEVE)
        val avalancha = DebtPlanner.simular(listOf(chica, grande), 2_000.0, desde, DebtPlanner.Estrategia.AVALANCHA)
        // Nieve ataca la chica primero: su último pago llega antes que en avalancha.
        fun ultimoMesConPago(resultado: DebtPlanner.Resultado, id: String): Int =
            resultado.filas.indexOfLast { it.pagos.containsKey(id) }
        assertEquals("chica", nieve.orden.first())
        assertEquals("grande", avalancha.orden.first())
        assertTrue(ultimoMesConPago(nieve, "chica") <= ultimoMesConPago(avalancha, "chica"))
    }

    @Test
    fun avalancha_paga_menos_interes() {
        val chica = DebtPlanner.Deuda("chica", "Chica", 5_000.0, 20.0)
        val grande = DebtPlanner.Deuda("grande", "Grande", 5_000.0, 90.0)
        val nieve = DebtPlanner.simular(listOf(chica, grande), 2_000.0, desde, DebtPlanner.Estrategia.NIEVE)
        val avalancha = DebtPlanner.simular(listOf(chica, grande), 2_000.0, desde, DebtPlanner.Estrategia.AVALANCHA)
        assertNotNull(nieve.fechaLibertad)
        assertNotNull(avalancha.fechaLibertad)
        assertTrue(avalancha.interesTotal <= nieve.interesTotal)
    }
}
