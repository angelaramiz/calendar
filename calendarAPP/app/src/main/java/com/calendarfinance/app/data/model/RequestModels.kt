package com.calendarfinance.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CalendarDay(
    val date: String,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val movements: List<Movement> = emptyList()
)

@Serializable
data class CreateMovementRequest(
    val type: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val date: String,
    val amount: Double,
    val confirmed: Boolean = true,
    val income_pattern_id: String? = null,
    val expense_pattern_id: String? = null
)

@Serializable
data class CreatePatternRequest(
    val name: String,
    val description: String = "",
    val category: String = "",
    val base_amount: Double,
    val frequency: String = "monthly",
    val interval: Int = 1,
    val day_of_week: Int? = null,
    val day_of_month: Int? = null,
    val start_date: String,
    val end_date: String? = null,
    val active: Boolean = true,
    val is_essential: Boolean? = null
)
