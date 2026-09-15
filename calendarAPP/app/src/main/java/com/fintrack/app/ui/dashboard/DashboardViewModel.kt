package com.fintrack.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.OtaInstaller
import com.fintrack.app.data.repository.OtaUpdateInfo
import com.fintrack.app.data.repository.OtaUpdateRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.domain.onDayUtc
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val currentBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val updateAvailable: OtaUpdateInfo? = null,
    val updateMessage: String? = null,
    /** Progreso 0..100 mientras descarga (null = sin descarga activa). */
    val otaProgress: Int? = null,
    /** Ruta del APK listo para instalar (null = aún no). */
    val otaApkPath: String? = null
)

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val otaUpdateRepository: OtaUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadDashboard()
        checkForUpdate()
    }

    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            val update = otaUpdateRepository.checkForUpdate()
            if (update != null) {
                _uiState.value = _uiState.value.copy(updateAvailable = update, updateMessage = null)
            } else if (manual) {
                _uiState.value = _uiState.value.copy(updateMessage = "Ya tienes la última versión.")
            }
        }
    }

    fun clearUpdateMessage() {
        _uiState.value = _uiState.value.copy(updateMessage = null)
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(updateAvailable = null)
    }

    private var otaPollJob: Job? = null
    private var otaDownloadId: Long? = null

    /** Inicia la descarga con progreso visible. */
    fun startUpdateDownload(appContext: android.content.Context) {
        val update = _uiState.value.updateAvailable ?: return
        if (_uiState.value.otaProgress != null) return
        otaPollJob?.cancel()
        val downloadId = OtaInstaller.enqueueDownload(appContext, update.apkUrl, update.versionName)
        otaDownloadId = downloadId
        _uiState.value = _uiState.value.copy(otaProgress = 0)
        otaPollJob = viewModelScope.launch {
            while (true) {
                delay(500)
                when (val progress = OtaInstaller.queryProgress(appContext, downloadId)) {
                    null -> continue
                    -1 -> {
                        _uiState.value = _uiState.value.copy(
                            otaProgress = null,
                            updateMessage = "La descarga falló. Reintenta."
                        )
                        break
                    }
                    else -> {
                        if (progress >= 100) {
                            val file = OtaInstaller.downloadedFile(appContext, update.versionName)
                            _uiState.value = _uiState.value.copy(
                                otaProgress = null,
                                otaApkPath = file?.absolutePath,
                                updateAvailable = null,
                                updateMessage = if (file == null) {
                                    "Descarga completa pero no se encontró el archivo. Reintenta."
                                } else null
                            )
                            break
                        } else {
                            _uiState.value = _uiState.value.copy(otaProgress = progress)
                        }
                    }
                }
            }
        }
    }

    /** Cancela la descarga en curso. */
    fun cancelUpdateDownload(appContext: android.content.Context) {
        otaPollJob?.cancel()
        otaPollJob = null
        otaDownloadId?.let { OtaInstaller.cancelDownload(appContext, it) }
        otaDownloadId = null
        _uiState.value = _uiState.value.copy(otaProgress = null)
    }

    /** Limpia la ruta del APK tras lanzar el instalador o cerrar el diálogo. */
    fun consumeReadyApk() {
        _uiState.value = _uiState.value.copy(otaApkPath = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun loadDashboard() {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                needsLogin = true,
                error = "Inicia sesión para ver tus transacciones."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false, error = null)
            try {
                val transactions = transactionRepository.getTransactions(userId)
                // Inicio muestra SOLO hoy: al cambiar de día la lista se limpia
                // sola y todo lo anterior vive en Calendario/Presupuesto.
                val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                val todays = transactions.onDayUtc(today)
                val income = todays.filter { it.isIncomeType() }.sumOf { it.amount }
                val expenses = todays.filter { !it.isIncomeType() }.sumOf { it.amount }

                _uiState.value = _uiState.value.copy(
                    currentBalance = income - expenses,
                    totalIncome = income,
                    totalExpenses = expenses,
                    recentTransactions = todays,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun addTransaction(transaction: TransactionEntity) {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                needsLogin = true,
                error = "Inicia sesión para guardar transacciones."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                transactionRepository.insertTransaction(userId, transaction)
                loadDashboard()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No se pudo guardar: ${e.message?.take(150)}"
                )
            }
        }
    }

    fun deleteTransaction(id: String) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(id)
                loadDashboard()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "No se pudo eliminar: ${e.message?.take(150)}"
                )
            }
        }
    }

    fun updateTransaction(id: String, transaction: TransactionEntity) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                transactionRepository.updateTransaction(id, transaction)
                loadDashboard()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No se pudo modificar: ${e.message?.take(150)}"
                )
            }
        }
    }
}

fun TransactionEntity.isIncomeType(): Boolean =
    type.equals("INCOME", ignoreCase = true) || type.equals("ingreso", ignoreCase = true)
