package com.fintrack.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.CredentialStore
import com.fintrack.app.data.PendingTx
import com.fintrack.app.data.PendingTxStore
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
    /** Hay credenciales guardadas: el bloqueo biométrico puede desbloquear. */
    val canUnlockWithBiometrics: Boolean = false,
    /** Entrando con huella (evita doble tap). */
    val unlocking: Boolean = false,
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
    private val otaUpdateRepository: OtaUpdateRepository,
    private val credentialStore: CredentialStore,
    private val pendingTxStore: PendingTxStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadDashboard()
        syncPending()
        checkForUpdate()
        startAutoRefresh()
    }

    private var autoRefreshJob: Job? = null

    /** Refresca los datos cada 5 s para que las detecciones aparezcan solas. */
    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                loadDashboard(silent = true)
            }
        }
    }

    override fun onCleared() {
        autoRefreshJob?.cancel()
        otaPollJob?.cancel()
        super.onCleared()
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

    /**
     * Desbloqueo estilo banco: la huella ya validó al usuario en la UI, aquí
     * se entra con las credenciales cifradas y se sincroniza la cola local.
     */
    fun unlockWithSavedLogin() {
        val email = credentialStore.email()
        val password = credentialStore.password()
        if (email.isNullOrBlank() || password.isNullOrBlank() || _uiState.value.unlocking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(unlocking = true, error = null)
            val ok = authRepository.login(email, password).isSuccess
            _uiState.value = _uiState.value.copy(unlocking = false)
            if (ok) {
                syncPending()
                loadDashboard()
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "No se pudo entrar con huella. Usa tu contraseña."
                )
            }
        }
    }

    /**
     * Sube la cola local de detecciones (guardadas sin sesión) y la vacía.
     * Se llama al abrir la app y tras cada login/desbloqueo.
     */
    fun syncPending() {
        viewModelScope.launch {
            val uid = authRepository.ensureSession() ?: return@launch
            val queued = runCatching { pendingTxStore.snapshot() }.getOrNull()
                ?: return@launch
            if (queued.isEmpty()) return@launch
            val synced = mutableListOf<PendingTx>()
            for (item in queued) {
                runCatching {
                    transactionRepository.insertTransaction(uid, item.tx)
                }.onSuccess { synced.add(item) }.onFailure { break }
            }
            if (synced.isNotEmpty()) {
                runCatching { pendingTxStore.removeAll(synced) }
                _uiState.value = _uiState.value.copy(
                    updateMessage = "${synced.size} movimiento(s) del teléfono sincronizados."
                )
                loadDashboard()
            }
        }
    }

    fun loadDashboard(silent: Boolean = false) {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                needsLogin = true,
                canUnlockWithBiometrics = credentialStore.hasCredentials(),
                error = "Inicia sesión para ver tus transacciones."
            )
            return
        }
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false, error = null)
            }
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
