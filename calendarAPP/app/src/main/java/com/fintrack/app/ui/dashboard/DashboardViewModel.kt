package com.fintrack.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.CredentialStore
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.PendingOp
import com.fintrack.app.data.PendingOpCodec
import com.fintrack.app.data.PendingOpKind
import com.fintrack.app.data.PendingOpStore
import com.fintrack.app.data.PendingOpSync
import com.fintrack.app.data.PendingTx
import com.fintrack.app.data.PendingTxStore
import com.fintrack.app.data.TxIdPayload
import com.fintrack.app.data.TxInsertPayload
import com.fintrack.app.data.TxUpdatePayload
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.WalletStore
import com.fintrack.app.data.toResolver
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.WalletResolver
import com.fintrack.app.domain.isRecoverableError
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
import kotlinx.serialization.KSerializer

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
    /** Billeteras disponibles (locales). */
    val wallets: List<WalletRow> = emptyList(),
    /** Overrides tx:<id> -> walletId (para prellenar la edición). */
    val walletOverrides: Map<String, String> = emptyMap(),
    /** Neto del mes por billetera. */
    val walletTotals: Map<String, Double> = emptyMap(),
    /** Filtro por billetera (null = todas). */
    val selectedWalletId: String? = null,
    /** Última billetera usada en registro manual. */
    val lastWalletId: String? = null,
    /** Tarjetas de crédito para el selector y el tag. */
    val cards: List<CreditCardRow> = emptyList(),
    /** Cargos a tarjeta: "tx:<id>" -> cardId. */
    val cardCharges: Map<String, String> = emptyMap(),
    /** Suma de gastos a crédito de hoy (no restan al balance). */
    val creditPendingToday: Double = 0.0,
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
    private val pendingTxStore: PendingTxStore,
    private val pendingOpStore: PendingOpStore,
    private val walletStore: WalletStore,
    private val opSync: PendingOpSync,
    private val creditCardStore: CreditCardStore
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
     * Sube la cola local de detecciones y la bandeja de operaciones manuales.
     * Se llama al abrir la app y tras cada login/desbloqueo.
     */
    fun syncPending() {
        viewModelScope.launch {
            val uid = authRepository.ensureSession() ?: return@launch
            var syncedTx = 0
            val queued = runCatching { pendingTxStore.snapshot() }.getOrNull()
                ?: emptyList()
            if (queued.isNotEmpty()) {
                val synced = mutableListOf<PendingTx>()
                for (item in queued) {
                    runCatching {
                        transactionRepository.insertTransaction(uid, item.tx)
                    }.onSuccess { synced.add(item) }.onFailure { break }
                }
                if (synced.isNotEmpty()) {
                    runCatching { pendingTxStore.removeAll(synced) }
                    syncedTx = synced.size
                }
            }
            val syncedOps = runCatching { opSync.sync(uid) }.getOrDefault(0)
            val total = syncedTx + syncedOps
            if (total > 0) {
                _uiState.value = _uiState.value.copy(
                    updateMessage = "$total movimiento(s) del teléfono sincronizados."
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
                val wallets = runCatching {
                    walletStore.ensureDefaults()
                    walletStore.snapshot()
                }.getOrDefault(emptyList())
                val overrides = runCatching { walletStore.overridesSnapshot() }
                    .getOrDefault(emptyMap())
                val lastWallet = runCatching { walletStore.lastSnapshot() }.getOrNull()
                val cards = runCatching { creditCardStore.cardsSnapshot() }
                    .getOrDefault(emptyList())
                val cardCharges = runCatching { creditCardStore.chargesSnapshot() }
                    .getOrDefault(emptyMap())
                val resolverWallets = wallets.map { it.toResolver() }
                // Inicio muestra SOLO hoy: al cambiar de día la lista se limpia
                // sola y todo lo anterior vive en Calendario/Presupuesto.
                val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                val todaysAll = transactions.onDayUtc(today)
                // Neto diario por billetera (incluye crédito: cuadra con la lista).
                val dailyTotals = WalletResolver.dayNet(
                    todaysAll, today, resolverWallets, overrides
                )
                val todays = todaysAll
                    .filter { selectedWalletMatches(it, resolverWallets, overrides) }
                // Gastos con tag de tarjeta: se registran pero no se aplican
                // al balance del momento (se pagan al corte).
                val creditToday = todays
                    .filter { !it.isIncomeType() && cardCharges["tx:${it.id}"] != null }
                    .sumOf { it.amount }
                val cashToday = todays.filter { cardCharges["tx:${it.id}"] == null }
                val income = cashToday.filter { it.isIncomeType() }.sumOf { it.amount }
                val expenses = cashToday.filter { !it.isIncomeType() }.sumOf { it.amount }

                _uiState.value = _uiState.value.copy(
                    currentBalance = income - expenses,
                    totalIncome = income,
                    totalExpenses = expenses,
                    recentTransactions = todays,
                    wallets = wallets,
                    walletOverrides = overrides,
                    walletTotals = dailyTotals,
                    lastWalletId = lastWallet,
                    cards = cards,
                    cardCharges = cardCharges,
                    creditPendingToday = creditToday,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun addTransaction(
        transaction: TransactionEntity,
        walletId: String? = null,
        cardId: String? = null
    ) {
        viewModelScope.launch {
            val uid = authRepository.ensureSession()
            if (uid == null) {
                enqueueOp(
                    PendingOpKind.TX_INSERT,
                    TxInsertPayload.serializer(),
                    TxInsertPayload(transaction, walletId, cardId)
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    updateMessage = "Sin conexión: se guardará al entrar."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val saved = transactionRepository.insertTransaction(uid, transaction)
                walletId?.let { runCatching { walletStore.setOverride("tx:${saved.id}", it) } }
                cardId?.let { runCatching { creditCardStore.setCharge("tx:${saved.id}", it) } }
                runCatching { walletStore.setLast(walletId ?: WalletResolver.EFECTIVO_ID) }
                loadDashboard()
            } catch (e: Exception) {
                if (isRecoverableError(e)) {
                    enqueueOp(
                        PendingOpKind.TX_INSERT,
                        TxInsertPayload.serializer(),
                        TxInsertPayload(transaction, walletId, cardId)
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        updateMessage = "Sin conexión: se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo guardar: ${e.message?.take(150)}"
                    )
                }
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            val uid = authRepository.ensureSession()
            if (uid == null) {
                enqueueOp(PendingOpKind.TX_DELETE, TxIdPayload.serializer(), TxIdPayload(id))
                _uiState.value = _uiState.value.copy(
                    updateMessage = "Sin conexión: se eliminará al entrar."
                )
                return@launch
            }
            try {
                transactionRepository.deleteTransaction(id)
                loadDashboard()
            } catch (e: Exception) {
                if (isRecoverableError(e)) {
                    enqueueOp(PendingOpKind.TX_DELETE, TxIdPayload.serializer(), TxIdPayload(id))
                    _uiState.value = _uiState.value.copy(
                        updateMessage = "Sin conexión: se eliminará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "No se pudo eliminar: ${e.message?.take(150)}"
                    )
                }
            }
        }
    }

    fun updateTransaction(
        id: String,
        transaction: TransactionEntity,
        walletId: String? = null,
        cardId: String? = null
    ) {
        viewModelScope.launch {
            // Los tags son locales: se aplican de inmediato, con o sin red.
            walletId?.let { runCatching { walletStore.setOverride("tx:$id", it) } }
            if (transaction.isIncomeType()) {
                runCatching { creditCardStore.setCharge("tx:$id", null) }
            } else {
                runCatching { creditCardStore.setCharge("tx:$id", cardId) }
            }
            val uid = authRepository.ensureSession()
            if (uid == null) {
                enqueueOp(
                    PendingOpKind.TX_UPDATE,
                    TxUpdatePayload.serializer(),
                    TxUpdatePayload(id, transaction)
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    updateMessage = "Sin conexión: se modificará al entrar."
                )
                loadDashboard(silent = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                transactionRepository.updateTransaction(id, transaction)
                loadDashboard()
            } catch (e: Exception) {
                if (isRecoverableError(e)) {
                    enqueueOp(
                        PendingOpKind.TX_UPDATE,
                        TxUpdatePayload.serializer(),
                        TxUpdatePayload(id, transaction)
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        updateMessage = "Sin conexión: se modificará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo modificar: ${e.message?.take(150)}"
                    )
                }
            }
        }
    }

    /** Filtro por billetera (null = todas) + recarga. */
    fun selectWallet(walletId: String?) {
        _uiState.value = _uiState.value.copy(selectedWalletId = walletId)
        loadDashboard(silent = true)
    }

    /** Alta de billetera propia + recarga de la lista. */
    fun addWallet(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { walletStore.addWallet(name) }
            val wallets = runCatching {
                walletStore.ensureDefaults()
                walletStore.snapshot()
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(wallets = wallets)
        }
    }

    private suspend fun <T> enqueueOp(
        kind: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        payload: T
    ) {
        runCatching {
            pendingOpStore.enqueue(
                PendingOp(
                    id = "op-${System.currentTimeMillis()}-${(0..9999).random()}",
                    kind = kind,
                    payload = PendingOpCodec.json.encodeToString(serializer, payload)
                )
            )
        }
    }

    private fun selectedWalletMatches(
        tx: TransactionEntity,
        wallets: List<WalletResolver.Wallet>,
        overrides: Map<String, String>
    ): Boolean {
        val selected = _uiState.value.selectedWalletId ?: return true
        return WalletResolver.resolve(tx, wallets, overrides) == selected
    }
}

fun TransactionEntity.isIncomeType(): Boolean =
    type.equals("INCOME", ignoreCase = true) || type.equals("ingreso", ignoreCase = true)
