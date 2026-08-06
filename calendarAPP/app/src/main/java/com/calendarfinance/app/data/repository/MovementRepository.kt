package com.calendarfinance.app.data.repository

import com.calendarfinance.app.data.model.Movement
import com.calendarfinance.app.data.model.BalanceSummary
import com.calendarfinance.app.data.model.MonthlyBalance
import com.calendarfinance.app.data.model.CreateMovementRequest
import com.calendarfinance.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MovementRepository {

    private val db get() = SupabaseClientProvider.client

    suspend fun getMovementsByMonth(userId: String, yearMonth: String): List<Movement> = withContext(Dispatchers.IO) {
        val startDate = "$yearMonth-01"
        val endDate = if (yearMonth.endsWith("12")) {
            "${yearMonth.substring(0, 4).toInt() + 1}-01-01"
        } else {
            val month = yearMonth.substring(5).toInt()
            "${yearMonth.substring(0, 5)}${(month + 1).toString().padStart(2, '0')}-01"
        }
        db.postgrest["movements"].select {
            filter { eq("user_id", userId) }
            filter { gte("date", startDate) }
            filter { lt("date", endDate) }
            filter { eq("archived", false) }
            order("date")
        }.decodeList<Movement>()
    }

    suspend fun createMovement(userId: String, request: CreateMovementRequest): Movement = withContext(Dispatchers.IO) {
        db.postgrest["movements"].insert(mapOf(
            "user_id" to userId,
            "type" to request.type,
            "title" to request.title,
            "description" to request.description,
            "category" to request.category,
            "date" to request.date,
            "expected_amount" to request.amount,
            "confirmed_amount" to if (request.confirmed) request.amount else 0.0,
            "confirmed" to request.confirmed,
            "income_pattern_id" to request.income_pattern_id,
            "expense_pattern_id" to request.expense_pattern_id
        )) {
            select()
        }.decodeSingle<Movement>()
    }

    suspend fun updateMovement(movement: Movement): Movement = withContext(Dispatchers.IO) {
        db.postgrest["movements"].update({
            set("title", movement.title)
            set("description", movement.description)
            set("category", movement.category)
            set("date", movement.date)
            set("expected_amount", movement.expected_amount)
            set("confirmed_amount", movement.confirmed_amount)
            set("confirmed", movement.confirmed)
            set("archived", movement.archived)
        }) { filter { eq("id", movement.id) } }.decodeSingle<Movement>()
    }

    suspend fun deleteMovement(id: String) = withContext(Dispatchers.IO) {
        db.postgrest["movements"].update({ set("archived", true) }) {
            filter { eq("id", id) }
        }
    }

    suspend fun getBalance(userId: String): BalanceSummary = withContext(Dispatchers.IO) {
        db.postgrest["confirmed_balance_summary"].select {
            filter { eq("user_id", userId) }
        }.decodeSingle<BalanceSummary>()
    }

    suspend fun getMonthlyBalances(userId: String, limit: Int = 6): List<MonthlyBalance> = withContext(Dispatchers.IO) {
        db.postgrest["monthly_confirmed_balance"].select {
            filter { eq("user_id", userId) }
            order("month", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(limit)
        }.decodeList<MonthlyBalance>()
    }

    suspend fun getMonthMovements(userId: String, yearMonth: String): List<Movement> = withContext(Dispatchers.IO) {
        val startDate = "$yearMonth-01"
        val (year, month) = yearMonth.split("-").map { it.toInt() }
        val nextMonth = if (month == 12) "${year + 1}-01-01" else "${year}-${(month + 1).toString().padStart(2, '0')}-01"
        db.postgrest["movements"].select {
            filter { eq("user_id", userId) }
            filter { gte("date", startDate) }
            filter { lt("date", nextMonth) }
            filter { eq("archived", false) }
            order("date")
        }.decodeList<Movement>()
    }
}
