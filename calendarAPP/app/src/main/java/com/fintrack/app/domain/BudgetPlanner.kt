package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

data class CategoryBudget(
    val category: String,
    val cap: Double,
    val spent: Double
) {
    val usageRatio: Double get() = if (cap <= 0.0) 0.0 else spent / cap
    val nearLimit: Boolean get() = usageRatio >= BudgetPlanner.ALERT_THRESHOLD
    val overCap: Boolean get() = cap > 0.0 && spent > cap
}

data class ShortTermReport(
    val month: YearMonth,
    val monthIncome: Double,
    val items: List<CategoryBudget> = emptyList(),
    val totalSpent: Double = 0.0,
    val savingsCap: Double = 0.0
) {
    val estimatedSavings: Double get() = monthIncome - totalSpent
}

data class MonthProjection(
    val month: YearMonth,
    val projectedIncome: Double,
    val projectedExpense: Double
) {
    val surplus: Double get() = projectedIncome - projectedExpense
}

enum class GoalVerdict {
    FACTIBLE,
    AJUSTADO,
    INVIABLE
}

data class GoalEvaluation(
    val requiredMonthly: Double,
    val avgSurplus: Double,
    val verdict: GoalVerdict,
    val explanation: String
)

object BudgetPlanner {

    const val CAP_COMIDA = 0.25
    const val CAP_TRANSPORTE = 0.15
    const val CAP_SERVICIOS = 0.20
    const val CAP_OCIO = 0.10
    const val SAVINGS_RATIO = 0.30

    const val DEFAULT_CAP_RATIO = 0.05

    const val ALERT_THRESHOLD = 0.80

    const val TIGHT_BAND = 1.3

    const val MEDIUM_TERM_MONTHS = 3
    const val VARIABLE_AVERAGE_MONTHS = 3

    val CAP_RATIOS: Map<String, Double> = mapOf(
        "Comida" to CAP_COMIDA,
        "Transporte" to CAP_TRANSPORTE,
        "Servicios" to CAP_SERVICIOS,
        "Ocio" to CAP_OCIO
    )

    private fun TransactionEntity.isIncome(): Boolean = kind?.isIncome == true

    private fun Pattern.isIncomePattern(): Boolean = kind?.isIncome == true

    private fun Long.toYearMonthUtc(): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate())

    fun monthIncome(transactions: List<TransactionEntity>, month: YearMonth): Double =
        transactions
            .filter { it.isIncome() && it.timestamp.toYearMonthUtc() == month }
            .sumOf { it.amount }

    fun spentByCategory(transactions: List<TransactionEntity>, month: YearMonth): Map<String, Double> =
        transactions
            .filter { !it.isIncome() && it.timestamp.toYearMonthUtc() == month }
            .groupBy { it.category.ifBlank { "Otros" } }
            .mapValues { (_, items) -> items.sumOf { it.amount } }

    fun categoryCaps(monthIncome: Double): Map<String, Double> {
        if (monthIncome <= 0.0) return CAP_RATIOS.mapValues { 0.0 }
        return CAP_RATIOS.mapValues { (_, ratio) -> monthIncome * ratio }
    }

    fun buildShortTerm(
        transactions: List<TransactionEntity>,
        month: YearMonth,
        customCaps: Map<String, Double> = emptyMap()
    ): ShortTermReport {
        val income = monthIncome(transactions, month)
        val spent = spentByCategory(transactions, month)
        val caps = categoryCaps(income)
        val categories = (caps.keys + spent.keys + customCaps.keys).sorted()
        val items = categories.map { category ->
            val custom = customCaps[category]?.takeIf { it > 0.0 }
            CategoryBudget(
                category = category,
                cap = custom
                    ?: if (income <= 0.0) 0.0 else (caps[category] ?: income * DEFAULT_CAP_RATIO),
                spent = spent[category] ?: 0.0
            )
        }
        return ShortTermReport(
            month = month,
            monthIncome = income,
            items = items,
            totalSpent = spent.values.sum(),
            savingsCap = if (income <= 0.0) 0.0 else income * SAVINGS_RATIO
        )
    }

    fun projectRecurring(
        patterns: List<Pattern>,
        startMonth: YearMonth,
        months: Int = MEDIUM_TERM_MONTHS
    ): List<MonthProjection> {
        if (months <= 0) return emptyList()
        val from = startMonth.atDay(1)
        val to = startMonth.plusMonths(months.toLong() - 1).atEndOfMonth()
        val totals = mutableMapOf<YearMonth, Pair<Double, Double>>()
        patterns.filter { it.active }.forEach { pattern ->
            PatternExpander.expand(pattern, from, to).forEach { occurrence ->
                val key = YearMonth.from(occurrence.date)
                val (income, expense) = totals[key] ?: (0.0 to 0.0)
                totals[key] = if (pattern.isIncomePattern()) {
                    (income + occurrence.amount) to expense
                } else {
                    income to (expense + occurrence.amount)
                }
            }
        }
        return (0 until months).map { offset ->
            val month = startMonth.plusMonths(offset.toLong())
            val (income, expense) = totals[month] ?: (0.0 to 0.0)
            MonthProjection(month = month, projectedIncome = income, projectedExpense = expense)
        }
    }

    fun averageMonthlyExpense(
        transactions: List<TransactionEntity>,
        referenceMonth: YearMonth,
        monthsBack: Int = VARIABLE_AVERAGE_MONTHS
    ): Double {
        if (monthsBack <= 0) return 0.0
        val window = (1..monthsBack).map { referenceMonth.minusMonths(it.toLong()) }.toSet()
        val total = transactions
            .filter { !it.isIncome() && it.timestamp.toYearMonthUtc() in window }
            .sumOf { it.amount }
        return total / monthsBack
    }

    fun buildMediumTerm(
        patterns: List<Pattern>,
        transactions: List<TransactionEntity>,
        startMonth: YearMonth,
        months: Int = MEDIUM_TERM_MONTHS
    ): List<MonthProjection> {
        val variable = averageMonthlyExpense(transactions, startMonth)
        return projectRecurring(patterns, startMonth, months).map {
            it.copy(projectedExpense = it.projectedExpense + variable)
        }
    }

    fun averageSurplus(projections: List<MonthProjection>): Double {
        if (projections.isEmpty()) return 0.0
        return projections.sumOf { it.surplus } / projections.size
    }

    fun requiredMonthlySaving(goalAmount: Double, months: Int): Double {
        if (goalAmount <= 0.0 || months <= 0) return 0.0
        return goalAmount / months
    }

    fun evaluateGoal(
        goalAmount: Double,
        months: Int,
        avgMonthlySurplus: Double
    ): GoalEvaluation {
        val required = requiredMonthlySaving(goalAmount, months)
        if (required <= 0.0) {
            return GoalEvaluation(
                requiredMonthly = 0.0,
                avgSurplus = avgMonthlySurplus,
                verdict = GoalVerdict.INVIABLE,
                explanation = "Indica un monto y un plazo validos para calcular tu plan de ahorro."
            )
        }
        if (avgMonthlySurplus <= 0.0 || required <= avgMonthlySurplus) {
            if (avgMonthlySurplus <= 0.0) {
                return GoalEvaluation(
                    requiredMonthly = required,
                    avgSurplus = avgMonthlySurplus,
                    verdict = GoalVerdict.INVIABLE,
                    explanation = "Con tus numeros actuales no queda superavit. Necesitas ahorrar " +
                        "${formatAmount(required)} al mes: recorta gastos o sube ingresos primero."
                )
            }
            return GoalEvaluation(
                requiredMonthly = required,
                avgSurplus = avgMonthlySurplus,
                verdict = GoalVerdict.FACTIBLE,
                explanation = "Meta factible: necesitas ${formatAmount(required)} al mes y tu " +
                    "superavit promedio es ${formatAmount(avgMonthlySurplus)}."
            )
        }
        if (required <= avgMonthlySurplus * TIGHT_BAND) {
            val shortfall = required - avgMonthlySurplus
            return GoalEvaluation(
                requiredMonthly = required,
                avgSurplus = avgMonthlySurplus,
                verdict = GoalVerdict.AJUSTADO,
                explanation = "Meta ajustada: te faltan ${formatAmount(shortfall)} al mes. " +
                    "Recorta un gasto variable o alarga el plazo para lograrla."
            )
        }
        return GoalEvaluation(
            requiredMonthly = required,
            avgSurplus = avgMonthlySurplus,
            verdict = GoalVerdict.INVIABLE,
            explanation = "Meta inviable con tu ritmo actual: necesitas ${formatAmount(required)} " +
                "al mes pero tu superavit promedio es ${formatAmount(avgMonthlySurplus)}. " +
                "Baja el monto o alarga el plazo."
        )
    }

    private fun formatAmount(value: Double): String =
        "%,.0f".format(value).replace(',', '.')
}
