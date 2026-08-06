package com.calendarfinance.app.ui.ota

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendarfinance.app.data.model.AppVersionInfo
import com.calendarfinance.app.data.repository.OtaUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OtaUpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,
    val updateInfo: AppVersionInfo? = null,
    val message: String? = null,
    val error: String? = null
)

class OtaUpdateViewModel(
    private val otaRepository: OtaUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtaUpdateUiState())
    val uiState: StateFlow<OtaUpdateUiState> = _uiState.asStateFlow()

    fun autoCheck(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, error = null)
            otaRepository.checkForUpdate(context)?.let { update ->
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    updateInfo = update
                )
            } ?: run {
                _uiState.value = _uiState.value.copy(isChecking = false)
            }
        }
    }

    fun manualCheck(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, error = null)
            otaRepository.checkForUpdate(context)?.let { update ->
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    updateInfo = update,
                    message = "Actualizacion disponible: ${update.versionName}"
                )
            } ?: run {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    message = "Ya tienes la ultima version"
                )
            }
        }
    }

    fun downloadAndInstall(context: Context) {
        val update = _uiState.value.updateInfo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloading = true, error = null)
            val file = otaRepository.downloadApk(context, update.apkUrl)
            if (file != null) {
                _uiState.value = _uiState.value.copy(isDownloading = false, isInstalling = true)
                try {
                    val intent = otaRepository.installApk(context, file)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isInstalling = false,
                        error = "No se pudo iniciar la instalacion"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = "Error al descargar la actualizacion"
                )
            }
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(updateInfo = null, message = null, error = null)
    }
}
