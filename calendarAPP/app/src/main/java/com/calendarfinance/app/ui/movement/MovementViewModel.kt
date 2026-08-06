package com.calendarfinance.app.ui.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendarfinance.app.data.model.Movement
import com.calendarfinance.app.data.model.CreateMovementRequest
import com.calendarfinance.app.data.repository.AuthRepository
import com.calendarfinance.app.data.repository.MovementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MovementFormUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val movement: Movement? = null,
    val title: String = "",
    val description: String = "",
    val amount: String = "",
    val category: String = "",
    val type: String = "gasto",
    val date: String = "",
    val error: String? = null
)

class MovementViewModel(
    private val movementRepository: MovementRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovementFormUiState())
    val uiState: StateFlow<MovementFormUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    fun initForm(movementId: String?, date: String?) {
        _uiState.value = _uiState.value.copy(date = date ?: "")
        if (movementId != null) {
            loadMovement(movementId)
        }
    }

    private fun loadMovement(movementId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val movements = movementRepository.getMonthMovements(userId, "")
                val movement = movements.find { it.id == movementId }
                if (movement != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movement = movement,
                        title = movement.title,
                        description = movement.description,
                        amount = movement.expected_amount.toString().dropLastWhile { it == '0' }.dropLastWhile { it == '.' },
                        category = movement.category,
                        type = movement.type,
                        date = movement.date
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onTitleChange(value: String) { _uiState.value = _uiState.value.copy(title = value) }
    fun onDescriptionChange(value: String) { _uiState.value = _uiState.value.copy(description = value) }
    fun onAmountChange(value: String) { _uiState.value = _uiState.value.copy(amount = value) }
    fun onCategoryChange(value: String) { _uiState.value = _uiState.value.copy(category = value) }
    fun onTypeChange(value: String) { _uiState.value = _uiState.value.copy(type = value) }
    fun onDateChange(value: String) { _uiState.value = _uiState.value.copy(date = value) }

    fun save() {
        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull() ?: run {
            _uiState.value = state.copy(error = "Ingresa un monto valido")
            return
        }
        if (state.date.isEmpty()) {
            _uiState.value = state.copy(error = "Selecciona una fecha")
            return
        }
        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = "Ingresa un titulo")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                if (state.movement != null) {
                    movementRepository.updateMovement(
                        state.movement.copy(
                            title = state.title,
                            description = state.description,
                            category = state.category,
                            date = state.date,
                            expected_amount = amount,
                            confirmed_amount = if (state.movement.confirmed) amount else state.movement.confirmed_amount
                        )
                    )
                } else {
                    movementRepository.createMovement(userId, CreateMovementRequest(
                        type = state.type,
                        title = state.title,
                        description = state.description,
                        category = state.category,
                        date = state.date,
                        amount = amount,
                        confirmed = true
                    ))
                }
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun deleteMovement() {
        val movement = _uiState.value.movement ?: return
        viewModelScope.launch {
            try {
                movementRepository.deleteMovement(movement.id)
                _uiState.value = _uiState.value.copy(isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
