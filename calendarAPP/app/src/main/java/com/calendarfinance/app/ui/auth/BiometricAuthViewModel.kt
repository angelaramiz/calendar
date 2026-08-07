package com.calendarfinance.app.ui.auth

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calendarfinance.app.data.remote.BiometricHelper
import com.calendarfinance.app.data.remote.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BiometricUiState(
    val hasStoredSession: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class BiometricAuthViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val _uiState = MutableStateFlow(BiometricUiState())
    val uiState: StateFlow<BiometricUiState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val session = sessionManager.getSession()
            val biometricEnabled = sessionManager.isBiometricEnabled()
            _uiState.value = _uiState.value.copy(
                hasStoredSession = session != null,
                isBiometricEnabled = biometricEnabled
            )
        }
    }

    fun checkBiometricAvailability(activity: FragmentActivity) {
        val helper = BiometricHelper(activity)
        _uiState.value = _uiState.value.copy(
            isBiometricAvailable = helper.isBiometricAvailable
        )
    }

    fun authenticateWithBiometric(activity: FragmentActivity, onAuthenticated: (userId: String, email: String, username: String, name: String) -> Unit) {
        val helper = BiometricHelper(activity)
        if (!helper.isBiometricAvailable) {
            _uiState.value = _uiState.value.copy(error = "Biometria no disponible")
            return
        }

        viewModelScope.launch {
            helper.authenticate(
                onSuccess = {
                    viewModelScope.launch {
                        val session = sessionManager.getSession()
                        if (session != null) {
                            _uiState.value = _uiState.value.copy(isLoading = true)
                            onAuthenticated(session.userId, session.email, session.username, session.name)
                        } else {
                            _uiState.value = _uiState.value.copy(error = "Sesion no encontrada. Inicia sesion manualmente.")
                        }
                    }
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(error = error)
                },
                onFailed = {
                    _uiState.value = _uiState.value.copy(error = "Huella no reconocida")
                }
            )
        }
    }

    fun saveSessionAndEnableBiometric(userId: String, email: String, username: String, name: String) {
        viewModelScope.launch {
            sessionManager.saveSession(userId, email, username, name)
            sessionManager.setBiometricEnabled(true)
            _uiState.value = _uiState.value.copy(
                hasStoredSession = true,
                isBiometricEnabled = true,
                error = null
            )
        }
    }

    fun clearSession() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _uiState.value = _uiState.value.copy(
                hasStoredSession = false,
                isBiometricEnabled = false
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
