package com.fintrack.app.data.repository

import com.fintrack.app.data.remote.SupabaseClientProvider
import com.fintrack.app.domain.Occurrence
import com.fintrack.app.domain.Pattern
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class PatternRow(
    val id: String = "",
    val user_id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "Otros",
    val base_amount: Double = 0.0,
    val frequency: String = "monthly",
    val interval: Int = 1,
    val day_of_week: Int? = null,
    val day_of_month: Int? = null,
    val start_date: String = "",
    val end_date: String? = null,
    val active: Boolean = true
)

@Serializable
data class MovementRow(
    val id: String = "",
    val user_id: String = "",
    val type: String = "gasto",
    val title: String = "",
    val description: String = "",
    val category: String = "Otros",
    val date: String = "",
    val expected_amount: Double = 0.0,
    val confirmed_amount: Double = 0.0,
    val confirmed: Boolean = true,
    val archived: Boolean = false,
    val income_pattern_id: String? = null,
    val expense_pattern_id: String? = null,
    val loan_id: String? = null
)

class PatternRepository {

    private val db get() = SupabaseClientProvider.client

    suspend fun getIncomePatterns(userId: String): List<PatternRow> = withContext(Dispatchers.IO) {
        db.from("income_patterns").select {
            filter {
                eq("user_id", userId)
                eq("active", true)
            }
        }.decodeList<PatternRow>()
    }

    suspend fun getExpensePatterns(userId: String): List<PatternRow> = withContext(Dispatchers.IO) {
        db.from("expense_patterns").select {
            filter {
                eq("user_id", userId)
                eq("active", true)
            }
        }.decodeList<PatternRow>()
    }

    suspend fun getMovementsForMonth(userId: String, monthStart: String, monthEnd: String): List<MovementRow> =
        withContext(Dispatchers.IO) {
            db.from("movements").select {
                filter {
                    eq("user_id", userId)
                    gte("date", monthStart)
                    lte("date", monthEnd)
                }
            }.decodeList<MovementRow>()
        }

    suspend fun confirmOccurrence(
        userId: String,
        occurrence: Occurrence,
        actualAmount: Double,
        dateIso: String
    ): MovementRow = withContext(Dispatchers.IO) {
        val isIncome = occurrence.pattern.type == "INCOME"
        val data = buildJsonObject {
            put("user_id", userId)
            put("type", if (isIncome) "ingreso" else "gasto")
            put("title", occurrence.pattern.name)
            put("description", occurrence.pattern.description)
            put("category", occurrence.pattern.category)
            put("date", dateIso)
            put("expected_amount", occurrence.pattern.baseAmount)
            put("confirmed_amount", actualAmount)
            put("confirmed", true)
            put("archived", false)
            if (isIncome) put("income_pattern_id", occurrence.pattern.id)
            else put("expense_pattern_id", occurrence.pattern.id)
        }
        db.from("movements").insert(data) { select() }.decodeSingle<MovementRow>()
    }

    /**
     * Crea un patrón recurrente (ingreso o gasto) que el calendario expande
     * como proyecciones. day_of_week/day_of_month se derivan del inicio
     * para compatibilidad con la web.
     */
    suspend fun insertPattern(
        userId: String,
        isIncome: Boolean,
        name: String,
        description: String,
        category: String,
        baseAmount: Double,
        frequency: String,
        startDateIso: String,
        endDateIso: String? = null
    ): PatternRow = withContext(Dispatchers.IO) {
        val start = runCatching { java.time.LocalDate.parse(startDateIso) }.getOrNull()
        val data = buildJsonObject {
            put("user_id", userId)
            put("name", name)
            put("description", description)
            put("category", category)
            put("base_amount", baseAmount)
            put("frequency", frequency)
            put("interval", 1)
            put("day_of_week", start?.dayOfWeek?.value)
            put("day_of_month", start?.dayOfMonth)
            put("start_date", startDateIso)
            put("end_date", endDateIso)
            put("active", true)
        }
        val table = if (isIncome) "income_patterns" else "expense_patterns"
        db.from(table).insert(data) { select() }.decodeSingle<PatternRow>()
    }

    suspend fun addManualMovement(
        userId: String,
        dateIso: String,
        isIncome: Boolean,
        title: String,
        description: String,
        category: String,
        amount: Double
    ): MovementRow = withContext(Dispatchers.IO) {
        val data = buildJsonObject {
            put("user_id", userId)
            put("type", if (isIncome) "ingreso" else "gasto")
            put("title", title)
            put("description", description)
            put("category", category)
            put("date", dateIso)
            put("expected_amount", amount)
            put("confirmed_amount", amount)
            put("confirmed", true)
            put("archived", false)
        }
        db.from("movements").insert(data) { select() }.decodeSingle<MovementRow>()
    }
}

fun PatternRow.toDomain(type: String): Pattern? {
    val start = runCatching { java.time.LocalDate.parse(start_date) }.getOrNull()
        ?: return null
    val end = end_date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    return Pattern(
        id = id,
        name = name,
        type = type,
        baseAmount = base_amount,
        frequency = frequency,
        interval = interval.coerceAtLeast(1),
        startDate = start,
        endDate = end,
        active = active,
        category = category,
        description = description
    )
}
