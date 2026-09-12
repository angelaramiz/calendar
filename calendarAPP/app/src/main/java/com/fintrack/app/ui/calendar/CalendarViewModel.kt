package com.fintrack.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.MonthSummary
import com.fintrack.app.domain.Occurrence
import com.fintrack.app.domain.PatternExpander
import com.fintrack.app.domain.computeMonthSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayData(
    val date: LocalDate,
    val projected: List<Occurrence> = emptyList(),
    val confirmed: List<MovementRow> = emptyList()
)

data class CalendarUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val days: Map<LocalDate, DayData> = emptyMap(),
    val monthSummary: MonthSummary = MonthSummary(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val confirmTarget: Occurrence? = null,
    val needsLogin: Boolean = false
)

class CalendarViewModel(
    private val patternRepository: PatternRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadMonth(YearMonth.now())
    }

    fun loadMonth(yearMonth: YearMonth) {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(needsLogin = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, yearMonth = yearMonth)
            try {
                val from = yearMonth.atDay(1)
                val to = yearMonth.atEndOfMonth()

                val income = patternRepository.getIncomePatterns(userId)
                    .mapNotNull { it.toDomain("INCOME") }
                val expense = patternRepository.getExpensePatterns(userId)
                    .mapNotNull { it.toDomain("EXPENSE") }
                val movements = patternRepository.getMovementsForMonth(
                    userId, from.toString(), to.toString()
                )

                val confirmedKeys = movements
                    .filter { it.income_pattern_id != null || it.expense_pattern_id != null }
                    .map {
                        "${it.income_pattern_id ?: it.expense_pattern_id}_${it.date}"
                    }.toSet()

                val projected = (income + expense)
                    .flatMap { PatternExpander.expand(it, from, to) }
                    .filter { occ ->
                        val key = "${occ.pattern.id}_${occ.date}"
                        !confirmedKeys.contains(key)
                    }
                val projectedByDate = projected.groupBy { it.date }

                val confirmedByDate = movements.groupBy {
                    runCatching { LocalDate.parse(it.date) }.getOrNull()
                }.filterKeys { it != null }.mapKeys { it.key!! }

                val allDates = (projectedByDate.keys + confirmedByDate.keys).associateWith { date ->
                    DayData(
                        date = date,
                        projected = projectedByDate[date].orEmpty(),
                        confirmed = confirmedByDate[date].orEmpty()
                    )
                }

                _uiState.value = _uiState.value.copy(
                    days = allDates,
                    monthSummary = computeMonthSummary(
                        projected,
                        confirmedByDate.values.flatten()
                    ),
                    isLoading = false,
                    needsLogin = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun prevMonth() = loadMonth(_uiState.value.yearMonth.minusMonths(1))
    fun nextMonth() = loadMonth(_uiState.value.yearMonth.plusMonths(1))

    fun retry() = loadMonth(_uiState.value.yearMonth)

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun askConfirm(occurrence: Occurrence) {
        _uiState.value = _uiState.value.copy(confirmTarget = occurrence)
    }

    fun dismissConfirm() {
        _uiState.value = _uiState.value.copy(confirmTarget = null)
    }

    fun confirmOccurrence(occurrence: Occurrence, actualAmount: Double) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                patternRepository.confirmOccurrence(
                    userId, occurrence, actualAmount, occurrence.date.toString()
                )
                _uiState.value = _uiState.value.copy(confirmTarget = null)
                loadMonth(_uiState.value.yearMonth)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    confirmTarget = null,
                    error = e.message
                )
            }
        }
    }
}
