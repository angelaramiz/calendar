package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone

class FlowEngineTest {

    private val zone = ZoneId.systemDefault()
    private val currentMonth = YearMonth.now()
    private val previousMonth = currentMonth.minusMonths(1)

    private fun timestampOf(month: YearMonth, day: Int = 10): Long {
        val safeDay = day.coerceAtMost(month.lengthOfMonth())
        return month.atDay(safeDay).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun transaction(
        amount: Double,
        type: String = "INCOME",
        category: String = "Sueldo",
        month: YearMonth = currentMonth
    ) = TransactionEntity(
        id = "tx-$category-$amount-${month.monthValue}",
        amount = amount,
        type = type,
        category = category,
        description = "test",
        timestamp = timestampOf(month)
    )

    private fun incomeNode(amount: Double, id: String = "n-income") =
        IncomeNode(id = id, label = "Sueldo", source = IncomeSource.Fixed(amount))

    private fun envelope(id: String, label: String, category: String? = null) =
        EnvelopeNode(id = id, label = label, category = category)

    @Test
    fun reparto_porcentaje_aparta_parte_y_deja_resto() {
        val nodes = listOf(
            incomeNode(1000.0),
            FormulaNode(
                id = "n-formula",
                splits = listOf(Split.Percent(label = "Ahorro", percent = 50.0, category = "Ahorro"))
            ),
            envelope("n-sobre", "Resto", "Otros")
        )

        val result = FlowEngine.evaluate(nodes)

        assertEquals(2, result.size)
        assertEquals("Ahorro", result[0].label)
        assertEquals(500.0, result[0].amount, 0.001)
        assertEquals("Ahorro", result[0].category)
        assertEquals("Resto", result[1].label)
        assertEquals(500.0, result[1].amount, 0.001)
    }

    @Test
    fun reparto_monto_fijo_aparta_y_deja_resto() {
        val nodes = listOf(
            incomeNode(1000.0),
            FormulaNode(
                id = "n-formula",
                splits = listOf(Split.FixedAmount(label = "Fijo", amount = 200.0, category = "Servicios"))
            ),
            envelope("n-sobre", "Resto", "Otros")
        )

        val result = FlowEngine.evaluate(nodes)

        assertEquals(2, result.size)
        assertEquals(200.0, result[0].amount, 0.001)
        assertEquals(800.0, result[1].amount, 0.001)
    }

    @Test
    fun condicion_verdadera_toma_rama_si() {
        val nodes = listOf(
            incomeNode(1000.0),
            ConditionNode(
                id = "n-cond",
                operator = ConditionOperator.GREATER_THAN,
                threshold = 500.0,
                trueBranch = listOf(envelope("n-si", "Ahorro extra", "Ahorro")),
                falseBranch = listOf(envelope("n-no", "Gasto libre", "Ocio"))
            )
        )

        val result = FlowEngine.evaluate(nodes)

        assertEquals(1, result.size)
        assertEquals("Ahorro extra", result[0].label)
        assertEquals(1000.0, result[0].amount, 0.001)
    }

    @Test
    fun condicion_falsa_toma_rama_no() {
        val nodes = listOf(
            incomeNode(300.0),
            ConditionNode(
                id = "n-cond",
                operator = ConditionOperator.GREATER_THAN,
                threshold = 500.0,
                trueBranch = listOf(envelope("n-si", "Ahorro extra", "Ahorro")),
                falseBranch = listOf(envelope("n-no", "Gasto libre", "Ocio"))
            )
        )

        val result = FlowEngine.evaluate(nodes)

        assertEquals(1, result.size)
        assertEquals("Gasto libre", result[0].label)
        assertEquals(300.0, result[0].amount, 0.001)
    }

    @Test
    fun condicion_menor_que_evalua_correcto() {
        val nodes = listOf(
            incomeNode(300.0),
            ConditionNode(
                id = "n-cond",
                operator = ConditionOperator.LESS_THAN,
                threshold = 500.0,
                trueBranch = listOf(envelope("n-si", "Rama si")),
                falseBranch = listOf(envelope("n-no", "Rama no"))
            )
        )

        val result = FlowEngine.evaluate(nodes)

        assertEquals(1, result.size)
        assertEquals("Rama si", result[0].label)
    }

    @Test
    fun condicion_igual_con_tolerancia() {
        val nodes = listOf(
            incomeNode(500.0),
            ConditionNode(
                id = "n-cond",
                operator = ConditionOperator.EQUALS,
                threshold = 500.0,
                trueBranch = listOf(envelope("n-si", "Exacto")),
                falseBranch = listOf(envelope("n-no", "Distinto"))
            )
        )

        val result = FlowEngine.evaluate(nodes)

        assertEquals(1, result.size)
        assertEquals("Exacto", result[0].label)
    }

    @Test
    fun falla_si_porcentajes_superan_100() {
        val nodes = listOf(
            incomeNode(1000.0),
            FormulaNode(
                id = "n-formula",
                splits = listOf(
                    Split.Percent(label = "A", percent = 70.0),
                    Split.Percent(label = "B", percent = 50.0)
                )
            )
        )

        val error = assertThrows(FlowValidationException::class.java) {
            FlowEngine.evaluate(nodes)
        }
        assertTrue(error.message!!.contains("100"))
    }

    @Test
    fun ingreso_desde_transacciones_del_mes_suma_solo_mes_actual() {
        val transactions = listOf(
            transaction(1000.0, "INCOME", "Sueldo", currentMonth),
            transaction(500.0, "ingreso", "Extra", currentMonth),
            transaction(200.0, "EXPENSE", "Comida", currentMonth),
            transaction(999.0, "INCOME", "Sueldo", previousMonth)
        )
        val nodes = listOf(
            IncomeNode(id = "n-income", label = "Mes", source = IncomeSource.MonthIncomes),
            envelope("n-sobre", "Todo", "Ahorro")
        )

        val result = FlowEngine.evaluate(nodes, transactions)

        assertEquals(1, result.size)
        assertEquals(1500.0, result[0].amount, 0.001)
    }

    @Test
    fun origen_total_por_categoria_filtra_categoria_del_mes() {
        val transactions = listOf(
            transaction(800.0, "INCOME", "Ventas", currentMonth),
            transaction(200.0, "INCOME", "Sueldo", currentMonth),
            transaction(500.0, "INCOME", "Ventas", previousMonth)
        )
        val nodes = listOf(
            IncomeNode(id = "n-income", label = "Ventas", source = IncomeSource.CategoryTotal("ventas")),
            envelope("n-sobre", "Ventas mes", "Ventas")
        )

        val result = FlowEngine.evaluate(nodes, transactions)

        assertEquals(1, result.size)
        assertEquals(800.0, result[0].amount, 0.001)
    }

    @Test
    fun cadena_completa_ingreso_formula_condicion_sobres() {
        val nodes = listOf(
            IncomeNode(id = "n-income", label = "Sueldo", source = IncomeSource.Fixed(2000.0)),
            FormulaNode(
                id = "n-formula",
                splits = listOf(Split.Percent(label = "Mitad", percent = 50.0, category = "Necesidades"))
            ),
            ConditionNode(
                id = "n-cond",
                operator = ConditionOperator.GREATER_THAN,
                threshold = 500.0,
                trueBranch = listOf(envelope("n-si", "Ahorro extra", "Ahorro")),
                falseBranch = listOf(envelope("n-no", "Gasto libre", "Ocio"))
            )
        )

        val result = FlowEngine.evaluate(nodes)

        assertEquals(2, result.size)
        assertEquals("Mitad", result[0].label)
        assertEquals(1000.0, result[0].amount, 0.001)
        assertEquals("Necesidades", result[0].category)
        assertEquals("Ahorro extra", result[1].label)
        assertEquals(1000.0, result[1].amount, 0.001)
        assertEquals("Ahorro", result[1].category)
        assertEquals(2000.0, result.sumOf { it.amount }, 0.001)
    }

    @Test
    fun falla_si_monto_fijo_no_positivo() {
        val cero = listOf(incomeNode(0.0))
        val negativo = listOf(incomeNode(-100.0))

        val errorCero = assertThrows(FlowValidationException::class.java) {
            FlowEngine.evaluate(cero)
        }
        val errorNegativo = assertThrows(FlowValidationException::class.java) {
            FlowEngine.evaluate(negativo)
        }
        assertTrue(errorCero.message!!.contains("mayor que 0"))
        assertTrue(errorNegativo.message!!.contains("mayor que 0"))
    }

    @Test
    fun falla_si_formula_sin_ingreso_previo() {
        val nodes = listOf(
            FormulaNode(
                id = "n-formula",
                splits = listOf(Split.Percent(label = "Ahorro", percent = 10.0))
            )
        )

        val error = assertThrows(FlowValidationException::class.java) {
            FlowEngine.evaluate(nodes)
        }
        assertTrue(error.message!!.contains("ingreso previo"))
    }

    @Test
    fun falla_si_condicion_no_tiene_ramas() {
        val nodes = listOf(
            incomeNode(1000.0),
            ConditionNode(
                id = "n-cond",
                operator = ConditionOperator.GREATER_THAN,
                threshold = 500.0,
                trueBranch = emptyList(),
                falseBranch = emptyList()
            )
        )

        val error = assertThrows(FlowValidationException::class.java) {
            FlowEngine.evaluate(nodes)
        }
        assertTrue(error.message!!.contains("rama"))
    }

    @Test
    fun ingreso_en_borde_de_mes_se_atribuye_en_utc() {
        // 2026-09-01 00:30 UTC es 31 de agosto en America/Mexico_City:
        // con UTC el ingreso cae en septiembre, con zona local no.
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
        try {
            val timestamp = Instant.parse("2026-09-01T00:30:00Z").toEpochMilli()
            val tx = TransactionEntity(
                id = "tx-borde",
                amount = 2_000.0,
                type = "INCOME",
                category = "Sueldo",
                description = "test",
                timestamp = timestamp
            )
            val nodes = listOf(
                IncomeNode(id = "n-income", label = "Sueldo", source = IncomeSource.MonthIncomes),
                envelope("n-sobre", "Todo", "Otros")
            )

            val result = FlowEngine.evaluate(nodes, listOf(tx), YearMonth.of(2026, 9))

            assertEquals(1, result.size)
            assertEquals(2_000.0, result[0].amount, 0.001)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
