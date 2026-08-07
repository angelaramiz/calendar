package com.calendarfinance.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendarfinance.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val username: String = "",
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (authRepository.isLoggedIn) {
            val userId = authRepository.currentUserId ?: ""
            _uiState.value = AuthUiState(isLoggedIn = true, userId = userId)
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = if (identifier.contains("@")) {
                authRepository.login(identifier, password)
            } else {
                authRepository.loginWithUsername(identifier, password)
            }
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState(isLoggedIn = true, userId = user.id, username = user.username)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Error de autenticación")
                }
            )
        }
    }

    fun register(email: String, password: String, username: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.register(email, password, username, name)
                .fold(
                    onSuccess = { user ->
                        _uiState.value = AuthUiState(isLoggedIn = true, userId = user.id, username = user.username)
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Error al registrarse")
                    }
                )
        }
    }

    fun recoverPassword(identifier: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.recoverPassword(identifier)
                .fold(
                    onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false) },
                    onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
                )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState()
        }
    }

    fun loginDirect(userId: String, email: String, username: String, name: String) {
        _uiState.value = AuthUiState(isLoggedIn = true, userId = userId, username = username)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
