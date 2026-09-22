package com.fintrack.app.domain

import com.fintrack.app.data.repository.MovementRow

data class MonthSummary(
    val projectedIncome: Double = 0.0,
    val projectedExpense: Double = 0.0,
    val confirmedIncome: Double = 0.0,
    val confirmedExpense: Double = 0.0,
    val projectedCount: Int = 0,
    val confirmedCount: Int = 0
) {
    val netProjected: Double get() = projectedIncome - projectedExpense
    val netConfirmed: Double get() = confirmedIncome - confirmedExpense
}

fun computeMonthSummary(
    projected: List<Occurrence>,
    confirmed: List<MovementRow>
): MonthSummary {
    var projectedIncome = 0.0
    var projectedExpense = 0.0
    projected.forEach {
        when (it.pattern.kind) {
            TxKind.INCOME -> projectedIncome += it.amount
            TxKind.EXPENSE -> projectedExpense += it.amount
            null -> Unit
        }
    }
    var confirmedIncome = 0.0
    var confirmedExpense = 0.0
    confirmed.forEach {
        when (it.kind) {
            TxKind.INCOME -> confirmedIncome += it.confirmed_amount
            TxKind.EXPENSE -> confirmedExpense += it.confirmed_amount
            null -> Unit
        }
    }
    return MonthSummary(
        projectedIncome = projectedIncome,
        projectedExpense = projectedExpense,
        confirmedIncome = confirmedIncome,
        confirmedExpense = confirmedExpense,
        projectedCount = projected.size,
        confirmedCount = confirmed.size
    )
}
