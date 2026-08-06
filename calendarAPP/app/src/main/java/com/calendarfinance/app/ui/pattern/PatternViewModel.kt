package com.calendarfinance.app.ui.pattern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendarfinance.app.data.model.IncomePattern
import com.calendarfinance.app.data.model.ExpensePattern
import com.calendarfinance.app.data.model.CreatePatternRequest
import com.calendarfinance.app.data.repository.AuthRepository
import com.calendarfinance.app.data.repository.PatternRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PatternFormUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val patternType: String = "income",
    val name: String = "",
    val description: String = "",
    val amount: String = "",
    val category: String = "",
    val frequency: String = "monthly",
    val dayOfMonth: String = "1",
    val dayOfWeek: String = "0",
    val startDate: String = "",
    val isEssential: Boolean = false,
    val error: String? = null
)

class PatternViewModel(
    private val patternRepository: PatternRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatternFormUiState())
    val uiState: StateFlow<PatternFormUiState> = _uiState.asStateFlow()

    fun initForm(type: String, patternId: String?) {
        _uiState.value = _uiState.value.copy(patternType = type)
        if (patternId != null) {
            // TODO: load pattern for editing
        }
    }

    fun onTypeChange(value: String) { _uiState.value = _uiState.value.copy(patternType = value) }
    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(name = value) }
    fun onDescriptionChange(value: String) { _uiState.value = _uiState.value.copy(description = value) }
    fun onAmountChange(value: String) { _uiState.value = _uiState.value.copy(amount = value) }
    fun onCategoryChange(value: String) { _uiState.value = _uiState.value.copy(category = value) }
    fun onFrequencyChange(value: String) { _uiState.value = _uiState.value.copy(frequency = value) }
    fun onDayOfMonthChange(value: String) { _uiState.value = _uiState.value.copy(dayOfMonth = value) }
    fun onDayOfWeekChange(value: String) { _uiState.value = _uiState.value.copy(dayOfWeek = value) }
    fun onStartDateChange(value: String) { _uiState.value = _uiState.value.copy(startDate = value) }
    fun onEssentialChange(value: Boolean) { _uiState.value = _uiState.value.copy(isEssential = value) }

    fun save(userId: String) {
        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull() ?: run {
            _uiState.value = state.copy(error = "Ingresa un monto valido")
            return
        }
        if (state.name.isBlank()) {
            _uiState.value = state.copy(error = "Ingresa un nombre")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val request = CreatePatternRequest(
                    name = state.name,
                    description = state.description,
                    category = state.category,
                    base_amount = amount,
                    frequency = state.frequency,
                    interval = 1,
                    day_of_week = if (state.frequency == "weekly") state.dayOfWeek.toIntOrNull() else null,
                    day_of_month = if (state.frequency == "monthly") state.dayOfMonth.toIntOrNull() else null,
                    start_date = state.startDate.ifEmpty { java.time.LocalDate.now().toString() },
                    is_essential = state.isEssential
                )
                if (state.patternType == "income") {
                    patternRepository.createIncomePattern(userId, request)
                } else {
                    patternRepository.createExpensePattern(userId, request)
                }
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
