package com.fintrack.app.domain

import kotlin.math.ceil
import kotlin.math.pow

data class SavingsGoal(
    val id: String,
    val name: String,
    val price: Double,
    val downPercent: Double = 20.0,
    val downAmount: Double? = null,
    val annualRatePercent: Double = 15.0,
    val termMonths: Int = 48
)

data class CashPlan(
    val monthsNeeded: Int?,
    val verdict: GoalVerdict,
    val explanation: String
)

data class CreditPlan(
    val downPayment: Double,
    val financedAmount: Double,
    val monthlyPayment: Double,
    val totalInterest: Double,
    val totalCost: Double,
    val downPaymentMonths: Int?,
    val verdict: GoalVerdict,
    val explanation: String
)

data class GoalComparison(
    val cashTotalCost: Double,
    val creditTotalCost: Double
) {
    val extraCost: Double get() = creditTotalCost - cashTotalCost
}

object GoalPlanner {

    const val AFFORDABLE_RATIO = 0.30
    const val STRETCH_RATIO = 0.40
    const val MAX_DOWN_PAYMENT_MONTHS = 6

    fun cashMonths(price: Double, avgSurplus: Double): Int? {
        if (price <= 0.0 || avgSurplus <= 0.0) return null
        return ceil(price / avgSurplus).toInt().coerceAtLeast(1)
    }

    fun evaluateCash(price: Double, avgSurplus: Double): CashPlan {
        if (price <= 0.0) {
            return CashPlan(
                monthsNeeded = null,
                verdict = GoalVerdict.INVIABLE,
                explanation = "Indica un precio valido mayor a cero para calcular tu plan de contado."
            )
        }
        val months = cashMonths(price, avgSurplus)
        if (months == null) {
            return CashPlan(
                monthsNeeded = null,
                verdict = GoalVerdict.INVIABLE,
                explanation = "Con tus numeros actuales no queda superavit para ahorrar. " +
                    "Recorta gastos o sube ingresos primero para juntar ${formatAmount(price)}."
            )
        }
        return CashPlan(
            monthsNeeded = months,
            verdict = GoalVerdict.FACTIBLE,
            explanation = "Si ahorras todo tu superavit (${formatAmount(avgSurplus)} al mes), " +
                "juntas ${formatAmount(price)} en $months meses."
        )
    }

    fun monthlyPayment(principal: Double, annualRatePercent: Double, months: Int): Double {
        if (principal <= 0.0 || months <= 0) return 0.0
        if (annualRatePercent <= 0.0) return principal / months
        val r = annualRatePercent / 12.0 / 100.0
        val factor = (1.0 + r).pow(months.toDouble())
        if (factor <= 1.0) return principal / months
        return principal * r * factor / (factor - 1.0)
    }

    fun downPaymentAmount(price: Double, percent: Double?, amount: Double?): Double {
        if (price <= 0.0) return 0.0
        if (amount != null && amount > 0.0) return amount.coerceIn(0.0, price)
        if (percent != null && percent > 0.0) return (price * percent / 100.0).coerceIn(0.0, price)
        return 0.0
    }

    fun downPaymentMonths(downPayment: Double, avgSurplus: Double): Int? {
        if (downPayment <= 0.0) return 0
        if (avgSurplus <= 0.0) return null
        return ceil(downPayment / avgSurplus).toInt().coerceAtLeast(1)
    }

    fun evaluateCredit(
        price: Double,
        downPayment: Double,
        annualRatePercent: Double,
        months: Int,
        monthlyIncome: Double,
        avgSurplus: Double
    ): CreditPlan {
        val safeDown = if (price <= 0.0) 0.0 else downPayment.coerceIn(0.0, price)
        val financed = (price - safeDown).coerceAtLeast(0.0)
        val payment = if (price <= 0.0) 0.0 else monthlyPayment(financed, annualRatePercent, months)
        val totalPaid = payment * months.coerceAtLeast(0)
        val interest = (totalPaid - financed).coerceAtLeast(0.0)
        val totalCost = safeDown + totalPaid
        val downMonths = downPaymentMonths(safeDown, avgSurplus)

        if (price <= 0.0 || months <= 0 || monthlyIncome <= 0.0) {
            return CreditPlan(
                downPayment = safeDown,
                financedAmount = financed,
                monthlyPayment = payment,
                totalInterest = interest,
                totalCost = totalCost,
                downPaymentMonths = downMonths,
                verdict = GoalVerdict.INVIABLE,
                explanation = "Revisa los datos del credito: precio, plazo e ingreso mensual deben ser mayores a cero."
            )
        }

        val ratio = payment / monthlyIncome
        var verdict = when {
            ratio <= AFFORDABLE_RATIO -> GoalVerdict.FACTIBLE
            ratio <= STRETCH_RATIO -> GoalVerdict.AJUSTADO
            else -> GoalVerdict.INVIABLE
        }
        val baseExplanation = when (verdict) {
            GoalVerdict.FACTIBLE ->
                "La mensualidad de ${formatAmount(payment)} usa menos del 30% de tu ingreso " +
                    "(${formatAmount(monthlyIncome)}). Es un nivel seguro."
            GoalVerdict.AJUSTADO ->
                "La mensualidad de ${formatAmount(payment)} usa entre el 30% y el 40% de tu ingreso. " +
                    "Puedes pagarla, pero te deja poco margen para imprevistos."
            GoalVerdict.INVIABLE ->
                "La mensualidad de ${formatAmount(payment)} supera el 40% de tu ingreso " +
                    "(${formatAmount(monthlyIncome)}). Con ese nivel corres riesgo de sobreendeudarte: " +
                    "pide mas plazo, da un enganche mayor o busca un precio menor."
        }

        var extra = ""
        if (safeDown > 0.0) {
            if (downMonths == null) {
                verdict = GoalVerdict.INVIABLE
                extra = " Ademas, sin superavit no puedes juntar el enganche de ${formatAmount(safeDown)}."
            } else if (downMonths > MAX_DOWN_PAYMENT_MONTHS) {
                verdict = when (verdict) {
                    GoalVerdict.FACTIBLE -> GoalVerdict.AJUSTADO
                    GoalVerdict.AJUSTADO -> GoalVerdict.INVIABLE
                    GoalVerdict.INVIABLE -> GoalVerdict.INVIABLE
                }
                extra = " Ademas, el enganche de ${formatAmount(safeDown)} te toma $downMonths meses " +
                    "de ahorro y lo sano es juntarlo en $MAX_DOWN_PAYMENT_MONTHS meses o menos."
            }
        }

        val costSummary = " En total pagas ${formatAmount(totalCost)} " +
            "(${formatAmount(interest)} de intereses)."
        return CreditPlan(
            downPayment = safeDown,
            financedAmount = financed,
            monthlyPayment = payment,
            totalInterest = interest,
            totalCost = totalCost,
            downPaymentMonths = downMonths,
            verdict = verdict,
            explanation = baseExplanation + extra + costSummary
        )
    }

    fun compareCashVsCredit(price: Double, creditTotalCost: Double): GoalComparison =
        GoalComparison(
            cashTotalCost = price.coerceAtLeast(0.0),
            creditTotalCost = creditTotalCost.coerceAtLeast(0.0)
        )

    fun resolveDownPayment(goal: SavingsGoal): Double =
        downPaymentAmount(goal.price, goal.downPercent, goal.downAmount)

    private fun formatAmount(value: Double): String =
        "%,.0f".format(value).replace(',', '.')
}
