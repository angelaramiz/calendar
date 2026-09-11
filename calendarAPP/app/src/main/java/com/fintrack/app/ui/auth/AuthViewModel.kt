package com.fintrack.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.remote.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isLoginMode = !_uiState.value.isLoginMode,
            error = null
        )
    }

    fun submit(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Ingresa correo y contraseña.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = if (_uiState.value.isLoginMode) {
                authRepository.login(email.trim(), password)
            } else {
                authRepository.register(email.trim(), password)
            }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyMessage(e)
                )
            }
        }
    }

    fun consumeLoggedIn() {
        _uiState.value = _uiState.value.copy(loggedIn = false)
    }

    private fun friendlyMessage(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            "invalid login credentials" in msg -> "Correo o contraseña incorrectos."
            "already registered" in msg || "already exists" in msg -> "Ese correo ya está registrado. Inicia sesión."
            "weak" in msg.lowercase() -> "Contraseña débil: usa 8+ caracteres con mayúscula, minúscula, número y símbolo."
            "network" in msg.lowercase() || "timeout" in msg.lowercase() || "Unable to resolve" in msg ->
                "Sin conexión. Revisa tu internet e intenta de nuevo."
            else -> "No se pudo completar: ${msg.take(120)}"
        }
    }
}
