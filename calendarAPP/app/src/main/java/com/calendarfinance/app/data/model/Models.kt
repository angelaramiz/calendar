package com.calendarfinance.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class User(
    val id: String,
    val email: String,
    val username: String = "",
    val name: String = "",
    val created_at: String = "",
    val updated_at: String = ""
)

@Serializable
data class IncomePattern(
    val id: String = "",
    val user_id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val base_amount: Double = 0.0,
    val frequency: String = "monthly",
    val interval: Int = 1,
    val day_of_week: Int? = null,
    val day_of_month: Int? = null,
    val start_date: String = "",
    val end_date: String? = null,
    val active: Boolean = true,
    val created_at: String = "",
    val updated_at: String = ""
)

@Serializable
data class ExpensePattern(
    val id: String = "",
    val user_id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val base_amount: Double = 0.0,
    val frequency: String = "monthly",
    val interval: Int = 1,
    val day_of_week: Int? = null,
    val day_of_month: Int? = null,
    val start_date: String = "",
    val end_date: String? = null,
    val active: Boolean = true,
    val is_essential: Boolean = false,
    val created_at: String = "",
    val updated_at: String = ""
)

@Serializable
data class Movement(
    val id: String = "",
    val user_id: String = "",
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val date: String = "",
    val expected_amount: Double = 0.0,
    val confirmed_amount: Double = 0.0,
    val confirmed: Boolean = false,
    val archived: Boolean = false,
    val income_pattern_id: String? = null,
    val expense_pattern_id: String? = null,
    val loan_id: String? = null,
    val created_at: String = "",
    val updated_at: String = ""
)

@Serializable
data class BalanceSummary(
    val total_income: Double = 0.0,
    val total_expenses: Double = 0.0,
    val balance: Double = 0.0,
    val income_count: Long = 0,
    val expense_count: Long = 0
)

@Serializable
data class MonthlyBalance(
    val month: String,
    val total_income: Double,
    val total_expenses: Double,
    val balance: Double
)

@Serializable
data class Plan(
    val id: String = "",
    val user_id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val target_amount: Double = 0.0,
    val current_amount: Double = 0.0,
    val start_date: String = "",
    val target_date: String = "",
    val status: String = "active",
    val priority: Int = 5,
    val created_at: String = "",
    val updated_at: String = "",
    val completed_at: String? = null
)

@Serializable
data class Loan(
    val id: String = "",
    val user_id: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "received",
    val counterparty: String = "",
    val original_amount: Double = 0.0,
    val remaining_amount: Double = 0.0,
    val loan_date: String = "",
    val due_date: String = "",
    val status: String = "active",
    val created_at: String = "",
    val updated_at: String = ""
)
