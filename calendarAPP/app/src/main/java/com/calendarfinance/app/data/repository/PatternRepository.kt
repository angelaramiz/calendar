package com.calendarfinance.app.data.repository

import com.calendarfinance.app.data.model.IncomePattern
import com.calendarfinance.app.data.model.ExpensePattern
import com.calendarfinance.app.data.model.CreatePatternRequest
import com.calendarfinance.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PatternRepository {

    private val db get() = SupabaseClientProvider.client

    suspend fun getIncomePatterns(userId: String): List<IncomePattern> = withContext(Dispatchers.IO) {
        db.postgrest["income_patterns"].select {
            filter { eq("user_id", userId) }
            filter { eq("active", true) }
            order("created_at")
        }.decodeList<IncomePattern>()
    }

    suspend fun getExpensePatterns(userId: String): List<ExpensePattern> = withContext(Dispatchers.IO) {
        db.postgrest["expense_patterns"].select {
            filter { eq("user_id", userId) }
            filter { eq("active", true) }
            order("created_at")
        }.decodeList<ExpensePattern>()
    }

    suspend fun createIncomePattern(userId: String, request: CreatePatternRequest): IncomePattern = withContext(Dispatchers.IO) {
        db.postgrest["income_patterns"].insert(mapOf(
            "user_id" to userId,
            "name" to request.name,
            "description" to request.description,
            "category" to request.category,
            "base_amount" to request.base_amount,
            "frequency" to request.frequency,
            "interval" to request.interval,
            "day_of_week" to request.day_of_week,
            "day_of_month" to request.day_of_month,
            "start_date" to request.start_date,
            "end_date" to request.end_date,
            "active" to request.active
        )) { select() }.decodeSingle<IncomePattern>()
    }

    suspend fun createExpensePattern(userId: String, request: CreatePatternRequest): ExpensePattern = withContext(Dispatchers.IO) {
        db.postgrest["expense_patterns"].insert(mapOf(
            "user_id" to userId,
            "name" to request.name,
            "description" to request.description,
            "category" to request.category,
            "base_amount" to request.base_amount,
            "frequency" to request.frequency,
            "interval" to request.interval,
            "day_of_week" to request.day_of_week,
            "day_of_month" to request.day_of_month,
            "start_date" to request.start_date,
            "end_date" to request.end_date,
            "active" to request.active,
            "is_essential" to (request.is_essential ?: false)
        )) { select() }.decodeSingle<ExpensePattern>()
    }

    suspend fun updateIncomePattern(pattern: IncomePattern): IncomePattern = withContext(Dispatchers.IO) {
        db.postgrest["income_patterns"].update({
            set("name", pattern.name)
            set("description", pattern.description)
            set("category", pattern.category)
            set("base_amount", pattern.base_amount)
            set("frequency", pattern.frequency)
            set("interval", pattern.interval)
            set("day_of_week", pattern.day_of_week)
            set("day_of_month", pattern.day_of_month)
            set("start_date", pattern.start_date)
            set("end_date", pattern.end_date)
            set("active", pattern.active)
        }) { filter { eq("id", pattern.id) } }.decodeSingle<IncomePattern>()
    }

    suspend fun updateExpensePattern(pattern: ExpensePattern): ExpensePattern = withContext(Dispatchers.IO) {
        db.postgrest["expense_patterns"].update({
            set("name", pattern.name)
            set("description", pattern.description)
            set("category", pattern.category)
            set("base_amount", pattern.base_amount)
            set("frequency", pattern.frequency)
            set("interval", pattern.interval)
            set("day_of_week", pattern.day_of_week)
            set("day_of_month", pattern.day_of_month)
            set("start_date", pattern.start_date)
            set("end_date", pattern.end_date)
            set("active", pattern.active)
            set("is_essential", pattern.is_essential)
        }) { filter { eq("id", pattern.id) } }.decodeSingle<ExpensePattern>()
    }

    suspend fun deactivatePattern(table: String, id: String) = withContext(Dispatchers.IO) {
        db.postgrest[table].update({ set("active", false) }) {
            filter { eq("id", id) }
        }
    }
}
