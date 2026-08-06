package com.calendarfinance.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendarfinance.app.data.model.CalendarDay
import com.calendarfinance.app.data.model.IncomePattern
import com.calendarfinance.app.data.model.ExpensePattern
import com.calendarfinance.app.data.model.Movement
import com.calendarfinance.app.data.repository.MovementRepository
import com.calendarfinance.app.data.repository.PatternRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalendarUiState(
    val isLoading: Boolean = false,
    val currentYearMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val incomePatterns: List<IncomePattern> = emptyList(),
    val expensePatterns: List<ExpensePattern> = emptyList(),
    val selectedMonthMovements: List<Movement> = emptyList(),
    val monthTotalIncome: Double = 0.0,
    val monthTotalExpenses: Double = 0.0,
    val error: String? = null
)

class CalendarViewModel(
    private val movementRepository: MovementRepository,
    private val patternRepository: PatternRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun loadMonth(userId: String, yearMonth: YearMonth) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val movements = movementRepository.getMonthMovements(userId, yearMonth.toString())
                val incomePatterns = patternRepository.getIncomePatterns(userId)
                val expensePatterns = patternRepository.getExpensePatterns(userId)
                val calendarDays = buildCalendarGrid(yearMonth, movements)
                val totals = calculateMonthTotals(movements)

                _uiState.value = CalendarUiState(
                    isLoading = false,
                    currentYearMonth = yearMonth,
                    calendarDays = calendarDays,
                    incomePatterns = incomePatterns,
                    expensePatterns = expensePatterns,
                    selectedMonthMovements = movements,
                    monthTotalIncome = totals.first,
                    monthTotalExpenses = totals.second
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun navigateToPreviousMonth(userId: String) {
        loadMonth(userId, _uiState.value.currentYearMonth.minusMonths(1))
    }

    fun navigateToNextMonth(userId: String) {
        loadMonth(userId, _uiState.value.currentYearMonth.plusMonths(1))
    }

    private fun buildCalendarGrid(yearMonth: YearMonth, movements: List<Movement>): List<CalendarDay> {
        val today = LocalDate.now()
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()
        val startDayOfWeek = firstDay.dayOfWeek.value % 7
        val totalDays = startDayOfWeek + lastDay.dayOfMonth
        val rows = (totalDays + 6) / 7
        val totalCells = rows * 7

        return (0 until totalCells).map { i ->
            val dayOffset = i - startDayOfWeek
            val date = firstDay.plusDays(dayOffset.toLong())
            val isCurrentMonth = date.monthValue == yearMonth.monthValue
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val dayMovements = if (isCurrentMonth) movements.filter { it.date == dateStr } else emptyList()
            val incomeTotal = dayMovements.filter { it.type == "ingreso" }.sumOf { it.confirmed_amount }
            val expenseTotal = dayMovements.filter { it.type == "gasto" }.sumOf { it.confirmed_amount }

            CalendarDay(
                date = dateStr,
                dayNumber = date.dayOfMonth,
                isCurrentMonth = isCurrentMonth,
                isToday = date == today,
                incomeTotal = incomeTotal,
                expenseTotal = expenseTotal,
                movements = dayMovements
            )
        }
    }

    private fun calculateMonthTotals(movements: List<Movement>): Pair<Double, Double> {
        val income = movements.filter { it.type == "ingreso" && it.confirmed }.sumOf { it.confirmed_amount }
        val expenses = movements.filter { it.type == "gasto" && it.confirmed }.sumOf { it.confirmed_amount }
        return income to expenses
    }
}
