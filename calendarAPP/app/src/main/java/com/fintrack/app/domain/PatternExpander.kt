package com.fintrack.app.domain

import java.time.LocalDate

data class Pattern(
    val id: String,
    val name: String,
    val type: String,
    val baseAmount: Double,
    val frequency: String,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    val category: String = "Otros",
    val description: String = ""
)

data class Occurrence(
    val date: LocalDate,
    val pattern: Pattern,
    val amount: Double = pattern.baseAmount
)

object PatternExpander {

    fun expand(pattern: Pattern, from: LocalDate, to: LocalDate): List<Occurrence> {
        val end = if (pattern.endDate != null && pattern.endDate < to) pattern.endDate else to
        if (end < from || end < pattern.startDate) return emptyList()

        val dates = when (pattern.frequency) {
            "biweekly" -> everyDays(pattern.startDate, end, 14L)
            "weekly" -> everyDays(pattern.startDate, end, 7L * pattern.interval.coerceAtLeast(1))
            "bimonthly" -> everyMonths(pattern.startDate, end, 2 * pattern.interval.coerceAtLeast(1))
            "monthly" -> everyMonths(pattern.startDate, end, pattern.interval.coerceAtLeast(1))
            "yearly" -> everyYears(pattern.startDate, end, pattern.interval.coerceAtLeast(1))
            // Quincena de nómina: 15 y último día del mes. Los bancos no
            // procesan fin de semana: sábado/domingo se recorre al viernes.
            "semimonthly" -> semiMonthly(pattern.startDate, end)
            else -> emptyList()
        }
        return dates
            .filter { !it.isBefore(from) && !it.isAfter(end) }
            .map { Occurrence(date = it, pattern = pattern) }
    }

    private fun everyDays(start: LocalDate, end: LocalDate, stepDays: Long): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            out.add(current)
            current = current.plusDays(stepDays)
        }
        return out
    }

    private fun everyMonths(start: LocalDate, end: LocalDate, stepMonths: Int): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            out.add(current)
            // Paridad web: deriva al último día (31 ene -> 28 feb -> 28 mar)
            val next = current.plusMonths(stepMonths.toLong())
            val anchorDay = minOf(current.dayOfMonth, next.lengthOfMonth())
            current = LocalDate.of(next.year, next.month, anchorDay)
        }
        return out
    }

    private fun everyYears(start: LocalDate, end: LocalDate, stepYears: Int): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            out.add(current)
            current = current.plusYears(stepYears.toLong())
        }
        return out
    }

    /**
     * Quincena de nómina: día 15 y último día de cada mes entre [start, end].
     * Fin de semana bancario: sábado -> viernes, domingo -> viernes (el
     * corrimiento nunca cruza de mes: 15-2=13, fin-2>=26).
     */
    private fun semiMonthly(start: LocalDate, end: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var month = java.time.YearMonth.from(start)
        val lastMonth = java.time.YearMonth.from(end)
        while (!month.isAfter(lastMonth)) {
            listOf(minOf(15, month.lengthOfMonth()), month.lengthOfMonth())
                .distinct()
                .map { shiftToFriday(month.atDay(it)) }
                .filter { !it.isBefore(start) && !it.isAfter(end) }
                .forEach { out.add(it) }
            month = month.plusMonths(1)
        }
        return out.sorted()
    }

    private fun shiftToFriday(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY -> date.minusDays(1)
        java.time.DayOfWeek.SUNDAY -> date.minusDays(2)
        else -> date
    }
}
