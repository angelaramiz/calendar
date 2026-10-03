package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyAllowanceTest {

    @Test
    fun calculo_basico_resta_fijos_y_ahorro_entre_dias() {
        // Quincena 1..15, hoy día 10: quedan 6 días.
        // (12_000 − 3_000 − 1_000) / 6 = 1_333.33
        val allowance = DailyAllowance.calculate(
            ingresoPeriodo = 12_000.0,
            fijosDelPeriodo = 3_000.0,
            apartadoAhorro = 1_000.0,
            diasRestantes = 6
        )

        assertEquals(1_333.33, allowance!!, 0.01)
    }

    @Test
    fun ultimo_dia_del_periodo_libera_todo() {
        val today = LocalDate.of(2026, 10, 15)

        assertEquals(1, DailyAllowance.daysRemaining(today))
        val allowance = DailyAllowance.calculate(
            ingresoPeriodo = 12_000.0,
            fijosDelPeriodo = 3_000.0,
            apartadoAhorro = 1_000.0,
            diasRestantes = DailyAllowance.daysRemaining(today)
        )
        assertEquals(8_000.0, allowance!!, 0.001)
    }

    @Test
    fun sin_ancla_devuelve_null_y_no_inventa() {
        assertNull(
            DailyAllowance.calculate(
                ingresoPeriodo = null,
                fijosDelPeriodo = 3_000.0,
                apartadoAhorro = 1_000.0,
                diasRestantes = 6
            )
        )
        assertNull(
            DailyAllowance.calculate(
                ingresoPeriodo = 0.0,
                fijosDelPeriodo = 0.0,
                apartadoAhorro = 0.0,
                diasRestantes = 6
            )
        )
    }

    @Test
    fun fijos_mayores_al_ingreso_dan_cero_no_negativo() {
        val allowance = DailyAllowance.calculate(
            ingresoPeriodo = 5_000.0,
            fijosDelPeriodo = 7_000.0,
            apartadoAhorro = 0.0,
            diasRestantes = 5
        )

        assertEquals(0.0, allowance!!, 0.001)
    }

    @Test
    fun periodo_primera_quincena_y_segunda() {
        val primera = DailyAllowance.periodFor(LocalDate.of(2026, 10, 3))
        assertEquals(LocalDate.of(2026, 10, 1), primera.start)
        assertEquals(LocalDate.of(2026, 10, 15), primera.end)

        val segunda = DailyAllowance.periodFor(LocalDate.of(2026, 10, 20))
        assertEquals(LocalDate.of(2026, 10, 16), segunda.start)
        assertEquals(LocalDate.of(2026, 10, 31), segunda.end)
    }

    @Test
    fun servicios_solo_suman_los_del_periodo() {
        val period = DailyAllowance.PayPeriod(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)
        )
        val total = DailyAllowance.servicesInPeriod(
            listOf(
                LocalDate.of(2026, 10, 5) to 800.0,
                LocalDate.of(2026, 10, 20) to 500.0
            ),
            period
        )

        assertEquals(800.0, total, 0.001)
    }

    @Test
    fun minimos_usan_summarize_existente() {
        // Tarjeta corte día 10, pago día 30; hoy 12-oct: estado abierto
        // [10-sep, 10-oct) con cargo de 4_000 → mínimo 4_000.
        val minimos = DailyAllowance.cardMinimums(
            cards = listOf(DailyAllowance.CardMinInput("nu", 10, 30)),
            chargesByCard = mapOf("nu" to listOf(LocalDate.of(2026, 9, 20) to 4_000.0)),
            paymentsByCard = emptyMap(),
            today = LocalDate.of(2026, 10, 12)
        )

        assertEquals(4_000.0, minimos.getValue("nu"), 0.001)
    }

    @Test
    fun aviso_suave_si_gasto_pasa_del_dia() {
        assertTrue(DailyAllowance.overDay(gastoHoy = 1_500.0, allowance = 1_000.0))
        assertFalse(DailyAllowance.overDay(gastoHoy = 800.0, allowance = 1_000.0))
        assertFalse(DailyAllowance.overDay(gastoHoy = 9_999.0, allowance = null))
    }
}
