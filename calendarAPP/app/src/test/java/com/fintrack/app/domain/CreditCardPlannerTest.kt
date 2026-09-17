package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CreditCardPlannerTest {

    @Test
    fun corte_y_pago_del_mes_actual() {
        // Corte día 10, pago día 30; hoy 12 sep: periodo 10 sep -> 10 oct.
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(
            LocalDate.of(2026, 9, 10) to 500.0,
            LocalDate.of(2026, 9, 11) to 200.0,
            LocalDate.of(2026, 9, 9) to 999.0 // periodo anterior, no cuenta
        )
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today)
        assertEquals(700.0, summary.periodCharges, 0.0)
        assertEquals(LocalDate.of(2026, 10, 10), summary.nextCutoff)
        assertEquals(LocalDate.of(2026, 10, 30), summary.nextPayment)
    }

    @Test
    fun antes_del_corte_el_periodo_es_el_anterior() {
        // Hoy 5 sep, corte 10: periodo 10 ago -> 10 sep, pago 30 sep.
        val today = LocalDate.of(2026, 9, 5)
        val charges = listOf(LocalDate.of(2026, 8, 15) to 300.0)
        val summary = CreditCardPlanner.summarize("plata", 10, 30, charges, today)
        assertEquals(300.0, summary.periodCharges, 0.0)
        assertEquals(LocalDate.of(2026, 9, 10), summary.nextCutoff)
        assertEquals(LocalDate.of(2026, 9, 30), summary.nextPayment)
    }

    @Test
    fun dia_31_se_ajusta_al_mes() {
        val today = LocalDate.of(2026, 2, 10)
        // Corte 31 en febrero -> 28 feb.
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CreditCardPlanner.nextCutoff(31, today)
        )
    }

    @Test
    fun pago_siempre_despues_del_corte() {
        // Corte 25, pago 5: el pago del corte del 25 sep es el 5 oct.
        val cutoff = LocalDate.of(2026, 9, 25)
        assertEquals(
            LocalDate.of(2026, 10, 5),
            CreditCardPlanner.paymentForCutoff(cutoff, 5)
        )
    }

    @Test
    fun sin_cargos_el_total_es_cero() {
        val summary = CreditCardPlanner.summarize(
            "nu", 10, 30, emptyList(), LocalDate.of(2026, 9, 12)
        )
        assertEquals(0.0, summary.periodCharges, 0.0)
        assertEquals(0.0, summary.remaining, 0.0)
    }

    @Test
    fun pagos_descuentan_del_restante_del_corte() {
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(LocalDate.of(2026, 9, 11) to 700.0)
        val payments = listOf(LocalDate.of(2026, 10, 10) to 500.0)
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today, payments)
        assertEquals(700.0, summary.periodCharges, 0.0)
        assertEquals(500.0, summary.paid, 0.0)
        assertEquals(200.0, summary.remaining, 0.0)
    }

    @Test
    fun pago_mayor_al_total_no_deja_negativo() {
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(LocalDate.of(2026, 9, 11) to 700.0)
        val payments = listOf(LocalDate.of(2026, 10, 10) to 1_000.0)
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today, payments)
        assertEquals(0.0, summary.remaining, 0.0)
    }

    @Test
    fun pago_de_otro_corte_no_descuenta() {
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(LocalDate.of(2026, 9, 11) to 700.0)
        // Pago contra el corte anterior (10 sep): no toca el periodo actual.
        val payments = listOf(LocalDate.of(2026, 9, 10) to 500.0)
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today, payments)
        assertEquals(700.0, summary.remaining, 0.0)
    }
}
