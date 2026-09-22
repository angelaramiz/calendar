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
import com.fintrack.app.data.OnboardingStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.WalletResolver
import com.fintrack.app.domain.friendlyErrorMessage
import com.fintrack.app.domain.isRecoverableError
import com.fintrack.app.domain.kind
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.OtaInstaller
import com.fintrack.app.data.repository.OtaUpdateInfo
import com.fintrack.app.data.repository.OtaUpdateRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.domain.onDay
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
    /** Sin red: se muestra cinta compacta en vez de error + snackbar. */
    val isOffline: Boolean = false,
    /** Operaciones pendientes de subir (cola offline visible). */
    val pendingCount: Int = 0,
    /** Asistente inicial de permisos (solo la primera vez). */
    val showOnboarding: Boolean = false,
    val needsLogin: Boolean = false,
    /** Hay credenciales guardadas: el bloqueo biométrico puede desbloquear. */
    val canUnlockWithBiometrics: Boolean = false,
    /** Entrando con huella (evita doble tap). */
    val unlocking: Boolean = false,
    /** Billeteras disponibles (locales). */
    val wallets: List<WalletRow> = emptyList(),
    /** Overrides tx:<id> -> walletId (para prellenar la edición). */
    val walletOverrides: Map<String, String> = emptyMap(),
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
    private val creditCardStore: CreditCardStore,
    private val onboardingStore: OnboardingStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadDashboard()
        syncPending()
        checkForUpdate()
        startAutoRefresh()
        refreshPendingCount()
        viewModelScope.launch {
            if (!runCatching { onboardingStore.isDone() }.getOrDefault(true)) {
                _uiState.value = _uiState.value.copy(showOnboarding = true)
            }
        }
    }

    /** Cierra el asistente inicial y no lo vuelve a mostrar. */
    fun dismissOnboarding() {
        viewModelScope.launch { runCatching { onboardingStore.markDone() } }
        _uiState.value = _uiState.value.copy(showOnboarding = false)
    }

    private var autoRefreshJob: Job? = null

    /**
     * Auto-refresh barato: la caché del repositorio (60 s) hace que cada
     * ciclo sea en memoria; el pintado es instantáneo y la red solo se toca
     * al expirar el TTL. Los cambios del usuario refrescan dirigido
     * (add/delete/update/sync llaman a loadDashboard).
     */
    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                // 30 s en línea / 60 s sin red (antes: 5 s siempre).
                kotlinx.coroutines.delay(if (_uiState.value.isOffline) 60000 else 30000)
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
        val downloadId = OtaInstaller.enqueueDownload(
            appContext, update.apkUrl, update.versionName, update.apkSha256
        )
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
                            // Solo se instala lo verificado: hash distinto o fila
                            // sin firma = se borra y se avisa (fail-closed).
                            val verified = file != null &&
                                OtaInstaller.verifySha256(file, update.apkSha256)
                            if (!verified) runCatching { file?.delete() }
                            _uiState.value = _uiState.value.copy(
                                otaProgress = null,
                                otaApkPath = file?.takeIf { verified }?.absolutePath,
                                updateAvailable = null,
                                updateMessage = when {
                                    file == null -> "Descarga completa pero no se encontró el archivo. Reintenta."
                                    !verified -> "La actualización no pasó la verificación. No se instalará."
                                    else -> null
                                }
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
                loadDashboard(forceRefresh = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "No se pudo entrar con huella. Usa tu contraseña."
                )
            }
        }
    }

    /**
     * Reintento manual desde la insignia de pendientes: sube la cola y
     * recarga. También refresca el contador.
     */
    fun retryPending() {
        syncPending()
        loadDashboard(silent = true, forceRefresh = true)
        refreshPendingCount()
    }

    /** Cuenta pendientes (detecciones + operaciones manuales). */
    fun refreshPendingCount() {
        viewModelScope.launch {
            val tx = runCatching { pendingTxStore.snapshot().size }.getOrDefault(0)
            val ops = runCatching { pendingOpStore.count() }.getOrDefault(0)
            _uiState.value = _uiState.value.copy(pendingCount = tx + ops)
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
                loadDashboard(forceRefresh = true)
            }
            refreshPendingCount()
        }
    }

    fun loadDashboard(silent: Boolean = false, forceRefresh: Boolean = false) {
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
                val transactions = transactionRepository.getTransactions(userId, forceRefresh)
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
                // Inicio muestra SOLO hoy en hora local del dispositivo: con
                // UTC la lista se vaciaba desde las 18:00 (hora México).
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zone)
                val todays = transactions.onDay(today, zone)
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
                    lastWalletId = lastWallet,
                    cards = cards,
                    cardCharges = cardCharges,
                    creditPendingToday = creditToday,
                    isLoading = false,
                    error = null,
                    isOffline = false
                )
            } catch (e: Exception) {
                // El auto-refresh silencioso sin red NO pone error: la cinta
                // "sin conexión" basta; si pusiera error, el snackbar
                // sonaría cada 5 s sin parar.
                if (silent && isRecoverableError(e)) {
                    _uiState.value = _uiState.value.copy(isLoading = false, isOffline = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyErrorMessage(e),
                        isOffline = isRecoverableError(e)
                    )
                }
            }
        }
    }

    /**
     * Recarga completa (pull-to-refresh de Inicio): baja movimientos,
     * sube la cola pendiente y revisa OTA silencioso.
     */
    fun refreshAll() {
        loadDashboard(forceRefresh = true)
        syncPending()
        checkForUpdate()
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
                        error = "No se pudo guardar. ${friendlyErrorMessage(e)}"
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
                transactionRepository.deleteTransaction(uid, id)
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
            // Al pasar a crédito se limpia el override de billetera rancio.
            if (walletId != null) {
                runCatching { walletStore.setOverride("tx:$id", walletId) }
            } else if (cardId != null) {
                runCatching { walletStore.clearOverride("tx:$id") }
            }
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
                transactionRepository.updateTransaction(uid, id, transaction)
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
                        error = "No se pudo modificar. ${friendlyErrorMessage(e)}"
                    )
                }
            }
        }
    }

    /** Alta de billetera propia (con terminación y tipo de cuenta) + recarga. */
    fun addWallet(name: String, last4: String = "", kind: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { walletStore.addWallet(name, last4, kind) }
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
        // La insignia de pendientes refleja el alta de inmediato.
        val tx = runCatching { pendingTxStore.snapshot().size }.getOrDefault(0)
        val ops = runCatching { pendingOpStore.count() }.getOrDefault(0)
        _uiState.value = _uiState.value.copy(pendingCount = tx + ops)
    }
}

fun TransactionEntity.isIncomeType(): Boolean = kind?.isIncome == true
