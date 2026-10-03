package com.fintrack.app.domain

import com.fintrack.app.data.CardPayment
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.MsiPlan
import com.fintrack.app.data.ServiceBillRow
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * D12 Reporte mensual: modelo puro del PDF de una página.
 *
 * Arma todas las secciones con datos fijos (testeable en JVM, sin Android):
 * ingresos/gastos/neto del mes (solo transacciones, mismo criterio que el
 * corto plazo de Presupuesto: lo desconocido cuenta como gasto), top 3
 * categorías, tarjetas (pagado del estado vs acumulado del ciclo con cargos
 * tagueados reales vía [CreditCardPlanner.summarize] + [CreditCardPlanner.currentCycle]),
 * servicios del mes, planes MSI activos (vía [MsiPlanner]) y metas con
 * progreso (vía [GoalContributions.progreso]).
 *
 * Huecos documentados (sin inventar datos):
 * - Los movimientos confirmados del calendario NO entran al neto del mes
 *   (evita doble conteo con Inicio); sí entran como cargos tagueados de tarjeta.
 * - Planes MSI cuya tarjeta ya no existe se omiten (sin tarjeta no hay corte).
 * - Sin stores nuevos: esta capa no persiste nada (sin sección de respaldo).
 */
object MonthlyReport {

    data class TopCategory(val nombre: String, val monto: Double)

    data class CardLine(
        val nombre: String,
        /** Cargos del estado abierto (periodo [corte anterior, corte abierto)). */
        val estadoCharges: Double,
        /** Pagos registrados contra el corte abierto. */
        val pagado: Double,
        /** Restante a pagar del estado abierto (nunca negativo). */
        val restante: Double,
        /** Acumulado del ciclo actual (corte abierto..hoy, próximo corte). */
        val ciclo: Double,
        val estadoPagado: Boolean,
        val vencimiento: LocalDate
    )

    data class ServiceLine(
        val nombre: String,
        val vencimiento: LocalDate,
        val monto: Double,
        val pagado: Boolean
    )

    data class MsiLine(
        val concepto: String,
        val hechos: Int,
        val total: Int,
        val parcial: Double,
        /** Último corte proyectado (null si el plan no proyecta). */
        val termina: LocalDate?
    )

    data class GoalLine(
        val nombre: String,
        val juntado: Double,
        val meta: Double,
        val progreso: Float,
        val restante: Double
    )

    data class ReportModel(
        val month: YearMonth,
        val ingresos: Double,
        val gastos: Double,
        val topCategorias: List<TopCategory>,
        val tarjetas: List<CardLine>,
        val servicios: List<ServiceLine>,
        val msi: List<MsiLine>,
        val metas: List<GoalLine>,
        /** null = sin ancla de ingreso (la UI oculta la fila, no inventa). */
        val limiteDiario: Double?
    ) {
        val neto: Double get() = ingresos - gastos
    }

    private fun TransactionEntity.isIncome(): Boolean = kind?.isIncome == true

    private fun inMonth(timestamp: Long, month: YearMonth, zone: ZoneId): Boolean =
        YearMonth.from(timestamp.toLocalDateIn(zone)) == month

    /**
     * Cargos tagueados de una tarjeta con fecha real (transacciones por
     * timestamp + movimientos confirmados por fecha ISO, sin archivados).
     * Reutilizado por la UI para el límite diario: misma fuente, un cálculo.
     */
    fun datedCharges(
        card: CreditCardRow,
        transactions: List<TransactionEntity>,
        movements: List<MovementRow>,
        charges: Map<String, String>,
        zone: ZoneId
    ): List<Pair<LocalDate, Double>> {
        val out = mutableListOf<Pair<LocalDate, Double>>()
        transactions
            .filter { charges["tx:${it.id}"] == card.id }
            .forEach { out.add(it.timestamp.toLocalDateIn(zone) to it.amount) }
        movements
            .filter { charges["mov:${it.id}"] == card.id && !it.archived }
            .forEach { mov ->
                runCatching { LocalDate.parse(mov.date) }.getOrNull()?.let {
                    out.add(it to mov.confirmed_amount)
                }
            }
        return out
    }

    /** Pagos de una tarjeta como (corte del estado, monto) para summarize(). */
    fun paymentsOf(cardId: String, payments: List<CardPayment>): List<Pair<LocalDate, Double>> =
        payments
            .filter { it.cardId == cardId }
            .mapNotNull { pay ->
                runCatching { LocalDate.parse(pay.statementCutoffIso) }.getOrNull()?.let {
                    it to pay.amount
                }
            }

    @Suppress("LongParameterList")
    fun build(
        month: YearMonth,
        transactions: List<TransactionEntity>,
        movements: List<MovementRow>,
        cards: List<CreditCardRow>,
        charges: Map<String, String>,
        payments: List<CardPayment>,
        bills: List<ServiceBillRow>,
        msiPlans: List<MsiPlan>,
        goals: List<SavingsGoal>,
        limiteDiario: Double?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): ReportModel {
        val monthTx = transactions.filter { inMonth(it.timestamp, month, zone) }
        val ingresos = monthTx.filter { it.isIncome() }.sumOf { it.amount }
        val gastosTx = monthTx.filter { !it.isIncome() }
        val gastos = gastosTx.sumOf { it.amount }
        val top = gastosTx
            .groupBy { it.category.ifBlank { "Otros" } }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { TopCategory(it.key, it.value) }

        val tarjetas = cards.map { card ->
            val dated = datedCharges(card, transactions, movements, charges, zone)
            val summary = CreditCardPlanner.summarize(
                cardId = card.id,
                cutoffDay = card.cutoffDay,
                paymentDay = card.paymentDay,
                charges = dated,
                today = today,
                payments = paymentsOf(card.id, payments),
                graceDays = card.graceDays
            )
            val ciclo = CreditCardPlanner.currentCycle(
                dated,
                { it.first },
                summary.statementCutoff,
                today
            ).sumOf { it.second }
            CardLine(
                nombre = card.displayName,
                estadoCharges = summary.periodCharges,
                pagado = summary.paid,
                restante = summary.remaining,
                ciclo = ciclo,
                estadoPagado = summary.isPaid,
                vencimiento = summary.dueDate
            )
        }

        val servicios = bills.flatMap { bill ->
            ServiceBills.duesInRange(
                bill.dueDay,
                bill.frequency,
                month.atDay(1),
                month.atEndOfMonth(),
                bill.dueMonth
            ).map { due ->
                ServiceLine(
                    nombre = bill.name,
                    vencimiento = due,
                    monto = bill.estimatedAmount,
                    pagado = ServiceBills.isPaidFor(bill.lastPaidDueIso, due)
                )
            }
        }.sortedBy { it.vencimiento }

        val msi = msiPlans
            .filter { !it.liquidado }
            .mapNotNull { plan ->
                val card = cards.firstOrNull { it.id == plan.cardId } ?: return@mapNotNull null
                val cuotas = MsiPlanner.cuotas(plan, card.cutoffDay)
                if (cuotas.isEmpty()) return@mapNotNull null
                val (hechas, total) = MsiPlanner.progreso(plan, card.cutoffDay, today)
                val parcialActual = cuotas.firstOrNull { !it.corteIso.isBefore(today) }
                    ?: cuotas.last()
                MsiLine(
                    concepto = plan.concepto,
                    hechos = hechas,
                    total = total,
                    parcial = parcialActual.parcial,
                    termina = cuotas.last().corteIso
                )
            }

        val metas = goals.map { goal ->
            GoalLine(
                nombre = goal.name,
                juntado = goal.aportado,
                meta = goal.price,
                progreso = GoalContributions.progreso(goal),
                restante = GoalContributions.restante(goal)
            )
        }

        return ReportModel(
            month = month,
            ingresos = ingresos,
            gastos = gastos,
            topCategorias = top,
            tarjetas = tarjetas,
            servicios = servicios,
            msi = msi,
            metas = metas,
            limiteDiario = limiteDiario
        )
    }
}
