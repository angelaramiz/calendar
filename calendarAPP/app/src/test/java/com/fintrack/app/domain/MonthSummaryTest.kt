package com.fintrack.app.domain

import com.fintrack.app.data.repository.MovementRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthSummaryTest {

    private fun occurrence(
        type: String,
        amount: Double,
        id: String = "p1"
    ) = Occurrence(
        date = LocalDate.of(2026, 9, 5),
        pattern = Pattern(
            id = id,
            name = "Patron",
            type = type,
            baseAmount = amount,
            frequency = "monthly",
            startDate = LocalDate.of(2026, 1, 1)
        ),
        amount = amount
    )

    private fun movement(
        type: String,
        expected: Double,
        confirmed: Double = expected,
        id: String = "m1"
    ) = MovementRow(
        id = id,
        type = type,
        title = "Movimiento",
        category = "Otros",
        date = "2026-09-05",
        expected_amount = expected,
        confirmed_amount = confirmed
    )

    @Test
    fun empty_devuelveCeros() {
        val summary = computeMonthSummary(emptyList(), emptyList())

        assertEquals(0.0, summary.projectedIncome, 0.0)
        assertEquals(0.0, summary.projectedExpense, 0.0)
        assertEquals(0.0, summary.confirmedIncome, 0.0)
        assertEquals(0.0, summary.confirmedExpense, 0.0)
        assertEquals(0, summary.projectedCount)
        assertEquals(0, summary.confirmedCount)
        assertEquals(0.0, summary.netProjected, 0.0)
        assertEquals(0.0, summary.netConfirmed, 0.0)
    }

    @Test
    fun projected_sumaIngresosYGastosPorTipo() {
        val projected = listOf(
            occurrence("INCOME", 1000.0, "p1"),
            occurrence("INCOME", 500.0, "p2"),
            occurrence("EXPENSE", 300.0, "p3")
        )

        val summary = computeMonthSummary(projected, emptyList())

        assertEquals(1500.0, summary.projectedIncome, 0.0)
        assertEquals(300.0, summary.projectedExpense, 0.0)
        assertEquals(1200.0, summary.netProjected, 0.0)
        assertEquals(3, summary.projectedCount)
    }

    @Test
    fun projected_ignoraTiposDesconocidos() {
        val projected = listOf(occurrence("UNKNOWN", 999.0))

        val summary = computeMonthSummary(projected, emptyList())

        assertEquals(0.0, summary.projectedIncome, 0.0)
        assertEquals(0.0, summary.projectedExpense, 0.0)
    }

    @Test
    fun confirmed_sumaPorTipo() {
        val confirmed = listOf(
            movement("ingreso", 1000.0, id = "m1"),
            movement("gasto", 200.0, id = "m2"),
            movement("gasto", 50.0, id = "m3")
        )

        val summary = computeMonthSummary(emptyList(), confirmed)

        assertEquals(1000.0, summary.confirmedIncome, 0.0)
        assertEquals(250.0, summary.confirmedExpense, 0.0)
        assertEquals(750.0, summary.netConfirmed, 0.0)
        assertEquals(3, summary.confirmedCount)
    }

    @Test
    fun confirmed_usaMontoConfirmadoNoEsperado() {
        val confirmed = listOf(movement("gasto", expected = 100.0, confirmed = 80.0))

        val summary = computeMonthSummary(emptyList(), confirmed)

        assertEquals(80.0, summary.confirmedExpense, 0.0)
    }

    @Test
    fun confirmed_ignoraTiposDesconocidos() {
        val confirmed = listOf(movement("otro", 999.0))

        val summary = computeMonthSummary(emptyList(), confirmed)

        assertEquals(0.0, summary.confirmedIncome, 0.0)
        assertEquals(0.0, summary.confirmedExpense, 0.0)
    }

    @Test
    fun mixto_mesCompleto() {
        val projected = listOf(
            occurrence("INCOME", 2000.0, "p1"),
            occurrence("EXPENSE", 800.0, "p2")
        )
        val confirmed = listOf(
            movement("ingreso", 2000.0, id = "m1"),
            movement("gasto", 750.0, confirmed = 700.0, id = "m2")
        )

        val summary = computeMonthSummary(projected, confirmed)

        assertEquals(2000.0, summary.projectedIncome, 0.0)
        assertEquals(800.0, summary.projectedExpense, 0.0)
        assertEquals(2000.0, summary.confirmedIncome, 0.0)
        assertEquals(700.0, summary.confirmedExpense, 0.0)
        assertEquals(1200.0, summary.netProjected, 0.0)
        assertEquals(1300.0, summary.netConfirmed, 0.0)
    }
}
