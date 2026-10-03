package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Alerta de corte próximo (C3): tarjeta que corta exactamente en 3 días
 * con cargos del ciclo actual > 0. Lógica pura sobre [CreditCardPlanner].
 */
class CutoffAlertTest {

    private val today = LocalDate.of(2026, 9, 12)

    private fun card(id: String = "c1", name: String = "Nu", cutoffDay: Int = 15) =
        CreditCardPlanner.CutoffCard(id = id, name = name, cutoffDay = cutoffDay)

    @Test
    fun avisa_si_corta_en_3_dias_con_cargos() {
        val alerts = CreditCardPlanner.cutoffAlerts(
            cards = listOf(card()),
            charges = mapOf("c1" to listOf(today.minusDays(1) to 500.0)),
            today = today
        )
        assertEquals(1, alerts.size)
        assertEquals("c1", alerts[0].cardId)
        assertEquals("Nu", alerts[0].cardName)
        assertEquals(LocalDate.of(2026, 9, 15), alerts[0].cutoff)
        assertEquals(500.0, alerts[0].cycleTotal, 0.001)
    }

    @Test
    fun no_avisa_si_no_hay_cargos_en_el_ciclo() {
        val alerts = CreditCardPlanner.cutoffAlerts(
            cards = listOf(card()),
            charges = emptyMap(),
            today = today
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun no_avisa_si_el_corte_no_es_en_3_dias() {
        // Corte día 20: faltan 8 días, no 3.
        val alerts = CreditCardPlanner.cutoffAlerts(
            cards = listOf(card(cutoffDay = 20)),
            charges = mapOf("c1" to listOf(today to 500.0)),
            today = today
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun cargos_del_estado_anterior_no_cuentan() {
        // Corte día 15: el ciclo actual es [15-ago, hoy]. El cargo del 10-ago
        // ya pertenece al estado abierto anterior, no acumula al próximo corte.
        val alerts = CreditCardPlanner.cutoffAlerts(
            cards = listOf(card()),
            charges = mapOf("c1" to listOf(LocalDate.of(2026, 8, 10) to 900.0)),
            today = today
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun solo_la_tarjeta_que_corta_en_3_dias_avisa() {
        val alerts = CreditCardPlanner.cutoffAlerts(
            cards = listOf(card("c1", "Nu", 15), card("c2", "Plata", 20)),
            charges = mapOf(
                "c1" to listOf(today to 100.0),
                "c2" to listOf(today to 200.0)
            ),
            today = today
        )
        assertEquals(1, alerts.size)
        assertEquals("c1", alerts[0].cardId)
    }
}
