package com.fintrack.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.remote.RegisterOutcome
import com.fintrack.app.domain.AuthAction
import com.fintrack.app.domain.authActionFor
import com.fintrack.app.domain.friendlyAuthMessage
import com.fintrack.app.domain.isValidEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Mensaje informativo (éxito de registro, correo reenviado, etc.). */
    val info: String? = null,
    /** Acción de recuperación que acompaña al error actual. */
    val pendingAction: AuthAction = AuthAction.NONE,
    /** Último email usado, para reenviar confirmación sin reescribirlo. */
    val lastEmail: String = "",
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
            error = null,
            info = null,
            pendingAction = AuthAction.NONE
        )
    }

    /** Lleva a modo login (para el caso "ya registrado"). */
    fun goToLogin() {
        _uiState.value = _uiState.value.copy(
            isLoginMode = true,
            error = null,
            info = null,
            pendingAction = AuthAction.NONE
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, info = null)
    }

    fun submit(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Ingresa correo y contraseña.",
                info = null,
                pendingAction = AuthAction.NONE
            )
            return
        }
        if (!isValidEmail(cleanEmail)) {
            _uiState.value = _uiState.value.copy(
                error = "Ese correo no es válido. Revísalo e intenta de nuevo.",
                info = null,
                pendingAction = AuthAction.NONE,
                lastEmail = cleanEmail
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, error = null, info = null,
                pendingAction = AuthAction.NONE, lastEmail = cleanEmail
            )
            if (_uiState.value.isLoginMode) {
                authRepository.login(cleanEmail, password).onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true)
                }.onFailure { e ->
                    val raw = e.message.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyAuthMessage(raw),
                        pendingAction = authActionFor(raw)
                    )
                }
            } else {
                authRepository.register(cleanEmail, password).onSuccess { outcome ->
                    when (outcome) {
                        is RegisterOutcome.LoggedIn ->
                            _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true)
                        is RegisterOutcome.NeedsConfirmation ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoginMode = true,
                                info = "Cuenta creada. Te enviamos un correo de confirmación a $cleanEmail: " +
                                    "confírmalo y luego inicia sesión.",
                                pendingAction = AuthAction.RESEND_CONFIRMATION
                            )
                    }
                }.onFailure { e ->
                    val raw = e.message.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyAuthMessage(raw),
                        pendingAction = authActionFor(raw)
                    )
                }
            }
        }
    }

    /** Reenvía el correo de confirmación al último email usado. */
    fun resendConfirmation() {
        val email = _uiState.value.lastEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.resendConfirmation(email).onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    info = "Correo reenviado a $email. Revisa tu bandeja (y el spam).",
                    pendingAction = AuthAction.NONE
                )
            }.onFailure { e ->
                val raw = e.message.orEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyAuthMessage(raw),
                    pendingAction = authActionFor(raw)
                )
            }
        }
    }

    fun consumeLoggedIn() {
        _uiState.value = _uiState.value.copy(loggedIn = false)
    }

    fun refreshSession() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = null, loggedIn = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Sesión caducada. Ingresa tu correo y contraseña."
                )
            }
        }
    }
}
