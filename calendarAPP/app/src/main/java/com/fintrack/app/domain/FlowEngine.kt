package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.ui.dashboard.isIncomeType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.round

/**
 * Motor de flujos de dinero estilo n8n simplificado.
 *
 * Un flujo es una cadena vertical de nodos que se evalua en orden con un
 * "monto actual" que viaja de nodo en nodo:
 *
 * - [IncomeNode] establece (suma) el monto actual desde un origen
 *   (monto fijo, ingresos del mes o total de una categoria del mes).
 * - [FormulaNode] aparta una o varias partes del monto actual como
 *   asignaciones con etiqueta y deja el resto como monto actual.
 *   Los porcentajes se calculan sobre el monto actual al entrar al nodo.
 * - [ConditionNode] compara el monto actual con un umbral (>, <, =) y
 *   evalua los nodos de la rama elegida (Si / Si no) con ese monto.
 * - [EnvelopeNode] asigna todo el monto actual restante a un sobre
 *   con etiqueta y categoria, dejando el monto actual en cero.
 *
 * Ejemplo: Sueldo 2000 -> Formula 50% "Mitad" -> Condicion
 * (resto > 500 ? Sobre "Ahorro extra" : Sobre "Gasto libre")
 * produce [Mitad 1000, Ahorro extra 1000].
 */
@Serializable
sealed interface FlowNode {
    val id: String
}

/** Origen del dinero de un [IncomeNode]. */
@Serializable
sealed interface IncomeSource {
    /** Monto fijo en la moneda local. Debe ser mayor que 0. */
    @Serializable
    @SerialName("fixed")
    data class Fixed(val amount: Double) : IncomeSource

    /** Suma de los ingresos registrados en el mes en curso. */
    @Serializable
    @SerialName("month")
    data object MonthIncomes : IncomeSource

    /** Suma de los ingresos de una categoria en el mes en curso (insensible a mayusculas). */
    @Serializable
    @SerialName("category")
    data class CategoryTotal(val category: String) : IncomeSource
}

/** Nodo origen: aporta dinero al monto actual del flujo. */
@Serializable
@SerialName("income")
data class IncomeNode(
    override val id: String,
    val label: String = "Ingreso",
    val source: IncomeSource
) : FlowNode

/** Un reparto dentro de un [FormulaNode]: porcentaje del monto actual o monto fijo. */
@Serializable
sealed interface Split {
    val label: String

    @Serializable
    @SerialName("percent")
    data class Percent(
        override val label: String,
        val percent: Double,
        val category: String? = null
    ) : Split

    @Serializable
    @SerialName("fixed")
    data class FixedAmount(
        override val label: String,
        val amount: Double,
        val category: String? = null
    ) : Split
}

/**
 * Nodo de reparto: aparta cada [splits] del monto actual como asignacion
 * y deja el resto como monto actual para el siguiente nodo.
 * La suma de porcentajes no puede superar 100.
 */
@Serializable
@SerialName("formula")
data class FormulaNode(
    override val id: String,
    val splits: List<Split>
) : FlowNode

/** Operador de comparacion de un [ConditionNode]. */
@Serializable
enum class ConditionOperator {
    GREATER_THAN,
    LESS_THAN,
    EQUALS
}

/**
 * Nodo de condicion: si el monto actual cumple [operator] frente a
 * [threshold] evalua [trueBranch], si no evalua [falseBranch].
 * Al menos una rama debe tener nodos.
 */
@Serializable
@SerialName("condition")
data class ConditionNode(
    override val id: String,
    val operator: ConditionOperator,
    val threshold: Double,
    val trueBranch: List<FlowNode> = emptyList(),
    val falseBranch: List<FlowNode> = emptyList()
) : FlowNode

/** Nodo final: asigna todo el monto actual restante a un sobre con etiqueta. */
@Serializable
@SerialName("envelope")
data class EnvelopeNode(
    override val id: String,
    val label: String,
    val category: String? = null
) : FlowNode

/** Resultado del motor: dinero asignado a una etiqueta (y categoria opcional). */
@Serializable
data class Allocation(
    val label: String,
    val amount: Double,
    val category: String? = null
)

/** Flujo editable completo, persistido como JSON en DataStore. */
@Serializable
data class MoneyFlow(
    val name: String,
    val nodes: List<FlowNode>
)

/** Error de validacion del flujo con mensaje descriptivo en espanol. */
class FlowValidationException(message: String) : IllegalArgumentException(message)

object FlowEngine {

    private const val EQUALS_TOLERANCE = 0.01

    /**
     * Evalua la cadena de [nodes] en orden y devuelve las asignaciones.
     *
     * @param transactions movimientos del usuario (para origenes Mes y Categoria).
     * @param month mes considerado "actual" (por defecto el mes en curso).
     * @throws FlowValidationException si el flujo es invalido.
     */
    fun evaluate(
        nodes: List<FlowNode>,
        transactions: List<TransactionEntity> = emptyList(),
        month: YearMonth = YearMonth.now()
    ): List<Allocation> {
        val state = FlowState()
        process(nodes, transactions, month, state)
        return state.allocations
    }

    private class FlowState(
        var current: Double? = null,
        val allocations: MutableList<Allocation> = mutableListOf()
    )

    private fun process(
        nodes: List<FlowNode>,
        transactions: List<TransactionEntity>,
        month: YearMonth,
        state: FlowState
    ) {
        nodes.forEach { node ->
            when (node) {
                is IncomeNode -> {
                    val amount = resolveIncome(node, transactions, month)
                    state.current = (state.current ?: 0.0) + amount
                }
                is FormulaNode -> applyFormula(node, state)
                is ConditionNode -> applyCondition(node, transactions, month, state)
                is EnvelopeNode -> applyEnvelope(node, state)
            }
        }
    }

    private fun resolveIncome(
        node: IncomeNode,
        transactions: List<TransactionEntity>,
        month: YearMonth
    ): Double {
        return when (val source = node.source) {
            is IncomeSource.Fixed -> {
                if (source.amount <= 0) {
                    throw FlowValidationException(
                        "El ingreso '${node.label}' debe ser mayor que 0 (recibido ${source.amount})."
                    )
                }
                round2(source.amount)
            }
            is IncomeSource.MonthIncomes -> {
                val total = transactions
                    .filter { it.isIncomeType() && yearMonthOf(it.timestamp) == month }
                    .sumOf { it.amount }
                if (total <= 0) {
                    throw FlowValidationException(
                        "No hay ingresos en el mes actual para '${node.label}'."
                    )
                }
                round2(total)
            }
            is IncomeSource.CategoryTotal -> {
                val wanted = source.category.trim()
                if (wanted.isEmpty()) {
                    throw FlowValidationException("La categoria del ingreso '${node.label}' esta vacia.")
                }
                val total = transactions
                    .filter {
                        it.isIncomeType() &&
                            yearMonthOf(it.timestamp) == month &&
                            it.category.trim().equals(wanted, ignoreCase = true)
                    }
                    .sumOf { it.amount }
                if (total <= 0) {
                    throw FlowValidationException(
                        "No hay ingresos en la categoria '$wanted' este mes para '${node.label}'."
                    )
                }
                round2(total)
            }
        }
    }

    private fun applyFormula(node: FormulaNode, state: FlowState) {
        val current = state.current
            ?: throw FlowValidationException("La formula necesita un ingreso previo en el flujo.")
        if (node.splits.isEmpty()) {
            throw FlowValidationException("La formula debe tener al menos un reparto.")
        }
        node.splits.forEach { split ->
            if (split.label.isBlank()) {
                throw FlowValidationException("Cada reparto de la formula necesita una etiqueta.")
            }
            when (split) {
                is Split.Percent -> {
                    if (split.percent <= 0 || split.percent > 100) {
                        throw FlowValidationException(
                            "El porcentaje de '${split.label}' debe estar entre 0 y 100 (recibido ${split.percent})."
                        )
                    }
                }
                is Split.FixedAmount -> {
                    if (split.amount <= 0) {
                        throw FlowValidationException(
                            "El monto de '${split.label}' debe ser mayor que 0 (recibido ${split.amount})."
                        )
                    }
                }
            }
        }
        val percentSum = node.splits.filterIsInstance<Split.Percent>().sumOf { it.percent }
        if (percentSum > 100) {
            throw FlowValidationException(
                "Los porcentajes suman $percentSum%, deben sumar 100% como maximo."
            )
        }
        val total = round2(
            node.splits.sumOf { split ->
                when (split) {
                    is Split.Percent -> current * split.percent / 100.0
                    is Split.FixedAmount -> split.amount
                }
            }
        )
        if (total > current + EQUALS_TOLERANCE) {
            throw FlowValidationException(
                "El reparto total ($total) supera el monto disponible ($current)."
            )
        }
        node.splits.forEach { split ->
            val part = round2(
                when (split) {
                    is Split.Percent -> current * split.percent / 100.0
                    is Split.FixedAmount -> split.amount
                }
            )
            val category = when (split) {
                is Split.Percent -> split.category
                is Split.FixedAmount -> split.category
            }
            state.allocations.add(Allocation(split.label, part, category))
        }
        state.current = round2(current - total)
    }

    private fun applyCondition(
        node: ConditionNode,
        transactions: List<TransactionEntity>,
        month: YearMonth,
        state: FlowState
    ) {
        val current = state.current
            ?: throw FlowValidationException("La condicion necesita un ingreso previo en el flujo.")
        if (node.threshold <= 0) {
            throw FlowValidationException(
                "El umbral de la condicion debe ser mayor que 0 (recibido ${node.threshold})."
            )
        }
        if (node.trueBranch.isEmpty() && node.falseBranch.isEmpty()) {
            throw FlowValidationException(
                "La condicion necesita al menos una rama (Si o Si no) con nodos."
            )
        }
        val matches = when (node.operator) {
            ConditionOperator.GREATER_THAN -> current > node.threshold
            ConditionOperator.LESS_THAN -> current < node.threshold
            ConditionOperator.EQUALS -> abs(current - node.threshold) <= EQUALS_TOLERANCE
        }
        val branch = if (matches) node.trueBranch else node.falseBranch
        process(branch, transactions, month, state)
    }

    private fun applyEnvelope(node: EnvelopeNode, state: FlowState) {
        val current = state.current
            ?: throw FlowValidationException("El sobre necesita un ingreso previo en el flujo.")
        if (node.label.isBlank()) {
            throw FlowValidationException("El sobre necesita una etiqueta.")
        }
        if (current <= 0) {
            throw FlowValidationException(
                "No queda monto por asignar al sobre '${node.label}'."
            )
        }
        state.allocations.add(Allocation(node.label, round2(current), node.category))
        state.current = 0.0
    }

    private fun yearMonthOf(timestamp: Long): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate())

    private fun round2(value: Double): Double = round(value * 100) / 100.0
}
