package com.fintrack.app.domain

import com.fintrack.app.data.CardPayment
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.MsiPlan
import com.fintrack.app.data.ServiceBillRow
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** D12 Reporte mensual: modelo puro con datos fijos. */
class MonthlyReportTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val month = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 12)

    private fun millis(day: Int): Long =
        LocalDate.of(2026, 10, day).atStartOfDay(utc).toInstant().toEpochMilli()

    private fun tx(
        id: String,
        amount: Double,
        type: String,
        category: String,
        day: Int
    ) = TransactionEntity(
        id = id,
        amount = amount,
        type = type,
        category = category,
        description = id,
        timestamp = millis(day)
    )

    private val card = CreditCardRow(
        id = "nu",
        name = "Nu",
        cutoffDay = 10,
        paymentDay = 20
    )

    private fun base(
        transactions: List<TransactionEntity> = emptyList(),
        movements: List<MovementRow> = emptyList(),
        cards: List<CreditCardRow> = emptyList(),
        charges: Map<String, String> = emptyMap(),
        payments: List<CardPayment> = emptyList(),
        bills: List<ServiceBillRow> = emptyList(),
        msi: List<MsiPlan> = emptyList(),
        goals: List<SavingsGoal> = emptyList(),
        limite: Double? = null
    ) = MonthlyReport.build(
        month = month,
        transactions = transactions,
        movements = movements,
        cards = cards,
        charges = charges,
        payments = payments,
        bills = bills,
        msiPlans = msi,
        goals = goals,
        limiteDiario = limite,
        today = today,
        zone = utc
    )

    @Test
    fun ingresos_gastos_neto_del_mes() {
        val model = base(
            transactions = listOf(
                tx("i1", 12_000.0, "INCOME", "Nómina", 5),
                tx("g1", 800.0, "EXPENSE", "Comida", 6),
                tx("g2", 200.0, "EXPENSE", "Transporte", 7)
            )
        )

        assertEquals(12_000.0, model.ingresos, 0.001)
        assertEquals(1_000.0, model.gastos, 0.001)
        assertEquals(11_000.0, model.neto, 0.001)
    }

    @Test
    fun mes_vacio_da_ceros_y_secciones_vacias() {
        val model = base()

        assertEquals(0.0, model.ingresos, 0.001)
        assertEquals(0.0, model.gastos, 0.001)
        assertEquals(0.0, model.neto, 0.001)
        assertTrue(model.topCategorias.isEmpty())
        assertTrue(model.tarjetas.isEmpty())
        assertTrue(model.servicios.isEmpty())
        assertTrue(model.msi.isEmpty())
        assertTrue(model.metas.isEmpty())
        assertNull(model.limiteDiario)
    }

    @Test
    fun top3_ordenado_y_con_tope() {
        val model = base(
            transactions = listOf(
                tx("g1", 500.0, "EXPENSE", "Comida", 2),
                tx("g2", 1_500.0, "EXPENSE", "Renta", 3),
                tx("g3", 300.0, "EXPENSE", "Ocio", 4),
                tx("g4", 900.0, "EXPENSE", "Transporte", 5),
                tx("g5", 100.0, "EXPENSE", "Otros", 6)
            )
        )

        assertEquals(3, model.topCategorias.size)
        assertEquals("Renta", model.topCategorias[0].nombre)
        assertEquals(1_500.0, model.topCategorias[0].monto, 0.001)
        assertEquals("Transporte", model.topCategorias[1].nombre)
        assertEquals("Comida", model.topCategorias[2].nombre)
    }

    @Test
    fun tarjeta_estado_vs_ciclo_con_cargos_reales() {
        // Corte día 10, hoy 12-oct: estado abierto [10-sep, 10-oct),
        // ciclo actual [10-oct, 12-oct].
        val model = base(
            transactions = listOf(
                tx("t1", 4_000.0, "EXPENSE", "Compras", 20).copy(
                    timestamp = LocalDate.of(2026, 9, 20).atStartOfDay(utc)
                        .toInstant().toEpochMilli()
                ),
                tx("t2", 1_200.0, "EXPENSE", "Comida", 11)
            ),
            cards = listOf(card),
            charges = mapOf("tx:t1" to "nu", "tx:t2" to "nu")
        )

        val linea = model.tarjetas.single()
        assertEquals("Nu", linea.nombre)
        assertEquals(4_000.0, linea.estadoCharges, 0.001)
        assertEquals(0.0, linea.pagado, 0.001)
        assertEquals(4_000.0, linea.restante, 0.001)
        assertEquals(1_200.0, linea.ciclo, 0.001)
        assertFalse(linea.estadoPagado)
    }

    @Test
    fun tarjeta_pagada_marca_estado_liquidado() {
        val model = base(
            transactions = listOf(
                tx("t1", 4_000.0, "EXPENSE", "Compras", 5).copy(
                    timestamp = LocalDate.of(2026, 9, 20).atStartOfDay(utc)
                        .toInstant().toEpochMilli()
                )
            ),
            cards = listOf(card),
            charges = mapOf("tx:t1" to "nu"),
            payments = listOf(
                CardPayment(
                    id = "p1",
                    cardId = "nu",
                    amount = 4_000.0,
                    dateIso = "2026-10-11",
                    statementCutoffIso = "2026-10-10"
                )
            )
        )

        val linea = model.tarjetas.single()
        assertEquals(4_000.0, linea.pagado, 0.001)
        assertEquals(0.0, linea.restante, 0.001)
        assertTrue(linea.estadoPagado)
    }

    @Test
    fun movimientos_tagueados_entran_como_cargos() {
        val mov = MovementRow(
            id = "m1",
            type = "gasto",
            title = "Súper",
            category = "Comida",
            date = "2026-10-11",
            confirmed_amount = 650.0,
            confirmed = true
        )
        val model = base(
            movements = listOf(mov),
            cards = listOf(card),
            charges = mapOf("mov:m1" to "nu")
        )

        assertEquals(650.0, model.tarjetas.single().ciclo, 0.001)
    }

    @Test
    fun servicios_del_mes_con_estado_de_pagado() {
        val model = base(
            bills = listOf(
                ServiceBillRow(
                    id = "luz",
                    name = "Luz",
                    estimatedAmount = 800.0,
                    dueDay = 15,
                    frequency = "monthly"
                ),
                ServiceBillRow(
                    id = "predial",
                    name = "Predial",
                    estimatedAmount = 2_000.0,
                    dueDay = 5,
                    dueMonth = 1,
                    frequency = "yearly"
                )
            )
        )

        // Solo Luz vence en octubre; el predial anual de enero no entra.
        assertEquals(1, model.servicios.size)
        assertEquals("Luz", model.servicios[0].nombre)
        assertEquals(LocalDate.of(2026, 10, 15), model.servicios[0].vencimiento)
        assertFalse(model.servicios[0].pagado)
    }

    @Test
    fun msi_activo_con_progreso_y_liquidados_fuera() {
        val plan = MsiPlan(
            id = "msi-1",
            cardId = "nu",
            concepto = "Celular",
            montoTotal = 12_000.0,
            meses = 12,
            primerCorteIso = "2026-07-10"
        )
        val liquidado = plan.copy(id = "msi-2", concepto = "Viejo", liquidado = true)
        val huerfano = plan.copy(id = "msi-3", concepto = "Sin tarjeta", cardId = "otra")
        val model = base(cards = listOf(card), msi = listOf(plan, liquidado, huerfano))

        assertEquals(1, model.msi.size)
        val linea = model.msi.single()
        assertEquals("Celular", linea.concepto)
        assertEquals(12, linea.total)
        assertEquals(4, linea.hechos)
        assertEquals(1_000.0, linea.parcial, 0.001)
    }

    @Test
    fun metas_con_progreso_de_aportaciones() {
        val goal = SavingsGoal(
            id = "g1",
            name = "Carro",
            price = 20_000.0,
            aportado = 8_000.0
        )
        val model = base(goals = listOf(goal))

        val linea = model.metas.single()
        assertEquals("Carro", linea.nombre)
        assertEquals(8_000.0, linea.juntado, 0.001)
        assertEquals(0.4f, linea.progreso, 0.001f)
        assertEquals(12_000.0, linea.restante, 0.001)
    }

    @Test
    fun limite_diario_pasa_tal_cual_si_hay_ancla() {
        assertEquals(1_250.0, base(limite = 1_250.0).limiteDiario!!, 0.001)
        assertNull(base(limite = null).limiteDiario)
    }
}
