package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ServiceBillsTest {

    @Test
    fun vencimiento_este_mes_si_aun_no_pasa() {
        assertEquals(
            LocalDate.of(2026, 9, 20),
            ServiceBills.nextDue(20, "monthly", LocalDate.of(2026, 9, 12))
        )
    }

    @Test
    fun vencimiento_hoy_cuenta_como_proximo() {
        assertEquals(
            LocalDate.of(2026, 9, 12),
            ServiceBills.nextDue(12, "monthly", LocalDate.of(2026, 9, 12))
        )
    }

    @Test
    fun vencimiento_pasado_salta_al_mes_siguiente() {
        assertEquals(
            LocalDate.of(2026, 10, 5),
            ServiceBills.nextDue(5, "monthly", LocalDate.of(2026, 9, 12))
        )
    }

    @Test
    fun bimestral_salta_dos_meses() {
        assertEquals(
            LocalDate.of(2026, 11, 5),
            ServiceBills.nextDue(5, "bimonthly", LocalDate.of(2026, 9, 12))
        )
    }

    @Test
    fun dia_31_se_ajusta_al_mes() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            ServiceBills.nextDue(31, "monthly", LocalDate.of(2026, 2, 10))
        )
    }

    @Test
    fun duesInRange_devuelve_solo_el_rango() {
        val dues = ServiceBills.duesInRange(
            5, "monthly",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)),
            dues
        )
    }

    @Test
    fun duesInRange_bimestral_cada_dos_meses() {
        val dues = ServiceBills.duesInRange(
            5, "bimonthly",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 11, 5)),
            dues
        )
    }

    @Test
    fun duesInRange_rango_invertido_vacio() {
        assertTrue(
            ServiceBills.duesInRange(
                5, "monthly",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)
            ).isEmpty()
        )
    }
}
