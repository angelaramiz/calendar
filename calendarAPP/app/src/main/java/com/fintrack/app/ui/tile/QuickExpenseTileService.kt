package com.fintrack.app.ui.tile

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.DraftStore
import com.fintrack.app.data.PendingOp
import com.fintrack.app.data.PendingOpCodec
import com.fintrack.app.data.PendingOpKind
import com.fintrack.app.data.PendingOpStore
import com.fintrack.app.data.TxInsertPayload
import com.fintrack.app.data.WalletStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.domain.WalletResolver
import com.fintrack.app.domain.isRecoverableError
import com.fintrack.app.domain.kind
import com.fintrack.app.ui.quickentry.QuickEntryForm
import com.fintrack.app.ui.theme.FinTrackTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Ciclo de vida mínimo para hospedar Compose en un Dialog del sistema
 * (fuera de Activity). El orden importa: performRestore(null) ANTES de
 * ON_CREATE, si no el Recreator interno truena al consumir el estado.
 */
private class ServiceDialogOwners : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun attach() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onShow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDismiss() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

@RequiresApi(Build.VERSION_CODES.N)
class QuickExpenseTileService : TileService(), KoinComponent {

    private val transactionRepository: TransactionRepository by inject()
    private val authRepository: AuthRepository by inject()
    private val creditCardStore: CreditCardStore by inject()
    private val walletStore: WalletStore by inject()
    private val pendingOpStore: PendingOpStore by inject()
    // Borrador del formulario: sobrevive si el sistema mata el servicio
    // a media captura (al reabrir el tile se retoma donde iba).
    private val draftStore by lazy { DraftStore(this) }

    private val serviceScope = MainScope()
    private var entryDialog: Dialog? = null

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }
    }

    override fun onDestroy() {
        entryDialog?.dismiss()
        entryDialog = null
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Ventana OUT: isla flotante SOBRE la app en uso (sin abrir FinTrack).
     * Nace como píldora compacta y se despliega al formulario con rebote
     * (spring); NO se cierra al tocar fuera: un toque accidental a media
     * captura no debe tirar el registro. Solo Cerrar/Cancelar/Guardar la
     * quitan. Con teléfono bloqueado pide huella/PIN primero.
     */
    override fun onClick() {
        super.onClick()
        entryDialog?.let {
            if (it.isShowing) {
                it.dismiss()
                return
            }
        }
        if (isLocked) {
            unlockAndRun { showEntryOverlay() }
        } else {
            showEntryOverlay()
        }
    }

    private fun showEntryOverlay() {
        serviceScope.launch {
            val cards = runCatching { creditCardStore.cardsSnapshot() }
                .getOrDefault(emptyList())
            val initialWallets = runCatching {
                walletStore.ensureDefaults()
                walletStore.snapshot()
            }.getOrDefault(emptyList())
            // Borrador previo (si la ventana murió a medias): se retoma.
            val initialDraft = runCatching { draftStore.snapshot() }.getOrNull()
            val dialog = Dialog(this@QuickExpenseTileService).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
            }
            var closing = false
            fun requestClose() {
                if (closing) return
                closing = true
                runCatching { dialog.dismiss() }
            }
            val owners = ServiceDialogOwners()
            owners.attach()
            val content = ComposeView(this@QuickExpenseTileService).apply {
                setViewTreeLifecycleOwner(owners)
                setViewTreeSavedStateRegistryOwner(owners)
                setContent {
                    var visible by remember { mutableStateOf(false) }
                    // Cuentas locales vivas: el alta del paso 5 refresca aquí.
                    var wallets by remember { mutableStateOf(initialWallets) }
                    // Isla: la ventana nace píldora y se despliega sola al
                    // formulario; tocarla la despliega antes si hay prisa.
                    var unfolded by remember { mutableStateOf(false) }
                    val composeScope = rememberCoroutineScope()
                    LaunchedEffect(Unit) {
                        visible = true
                        delay(350)
                        unfolded = true
                    }
                    fun animatedClose() {
                        if (!visible) return
                        visible = false
                        // Cierre intencional: el borrador se descarta.
                        serviceScope.launch { runCatching { draftStore.clear() } }
                        composeScope.launch {
                            delay(280)
                            requestClose()
                        }
                    }
                    FinTrackTheme {
                        Box(
                            // Sin clickable: tocar fuera NO cierra la ventana
                            // OUT (el registro a medias se protege).
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ComposeColor.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AnimatedVisibility(
                                visible = visible,
                                enter = slideInVertically(
                                    initialOffsetY = { -it },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(200)),
                                exit = slideOutVertically(
                                    targetOffsetY = { -it },
                                    animationSpec = tween(250)
                                ) + fadeOut(animationSpec = tween(200))
                            ) {
                                AnimatedContent(
                                    targetState = unfolded,
                                    transitionSpec = {
                                        (scaleIn(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            initialScale = 0.55f
                                        ) + fadeIn(animationSpec = tween(150)))
                                            .togetherWith(
                                                fadeOut(animationSpec = tween(120))
                                            ) using SizeTransform(clip = false)
                                    },
                                    label = "isla-registro"
                                ) { target ->
                                    if (!target) {
                                        Surface(
                                            onClick = { unfolded = true },
                                            shape = RoundedCornerShape(28.dp),
                                            color = ComposeColor.Black,
                                            contentColor = ComposeColor.White,
                                            tonalElevation = 6.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 20.dp,
                                                    vertical = 12.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = ComposeColor.White
                                                )
                                                Text("Registro rápido")
                                            }
                                        }
                                    } else {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {}
                                                ),
                                            shape = RoundedCornerShape(
                                                topStart = 0.dp, topEnd = 0.dp,
                                                bottomStart = 28.dp, bottomEnd = 28.dp
                                            ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                    QuickEntryForm(
                                        cards = cards,
                                        wallets = wallets,
                                        onSave = { tx, walletId, cardId ->
                                            saveAndClose(dialog, tx, walletId, cardId)
                                        },
                                        onCancel = ::animatedClose,
                                        onCreateWallet = { name, last4, kind ->
                                            serviceScope.launch {
                                                runCatching {
                                                    walletStore.addWallet(name, last4, kind)
                                                    wallets = walletStore.snapshot()
                                                }
                                            }
                                        },
                                        initialDraft = initialDraft,
                                        onDraftChange = { draft ->
                                            serviceScope.launch {
                                                runCatching { draftStore.save(draft) }
                                            }
                                        }
                                    )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            dialog.setContentView(content)
            dialog.setOnShowListener { owners.onShow() }
            // La ventana OUT no se descarta con toques fuera: solo sus botones.
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnDismissListener {
                owners.onDismiss()
                if (entryDialog === dialog) entryDialog = null
            }
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.setGravity(Gravity.TOP)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            entryDialog = dialog
            showDialog(dialog)
        }
    }

    /** Guarda igual que el registro dentro de la app (o encola si no hay red). */
    private fun saveAndClose(
        dialog: Dialog,
        tx: TransactionEntity,
        walletId: String?,
        cardId: String?
    ) {
        serviceScope.launch {
            // Guardado: el borrador cumplió, se descarta.
            runCatching { draftStore.clear() }
            val uid = authRepository.ensureSession()
            if (uid == null) {
                runCatching { pendingOpStore.enqueue(newTxOp(tx, walletId, cardId)) }
                toast("Sin conexión: se guardará al entrar.")
                dialog.dismiss()
                return@launch
            }
            try {
                val saved = transactionRepository.insertTransaction(uid, tx)
                walletId?.let {
                    runCatching { walletStore.setOverride("tx:${saved.id}", it) }
                }
                runCatching {
                    walletStore.setLast(walletId ?: WalletResolver.EFECTIVO_ID)
                }
                cardId?.let { runCatching { creditCardStore.setCharge("tx:${saved.id}", it) } }
                toast(if (tx.kind?.isIncome == true) "Ingreso guardado." else "Gasto guardado.")
            } catch (e: Exception) {
                if (isRecoverableError(e)) {
                    runCatching { pendingOpStore.enqueue(newTxOp(tx, walletId, cardId)) }
                    toast("Sin conexión: se guardará al entrar.")
                } else {
                    toast("No se pudo guardar.")
                }
            }
            dialog.dismiss()
        }
    }

    private fun newTxOp(
        tx: TransactionEntity,
        walletId: String?,
        cardId: String?
    ) = PendingOp(
        id = "op-${System.currentTimeMillis()}-${(0..9999).random()}",
        kind = PendingOpKind.TX_INSERT,
        payload = PendingOpCodec.json.encodeToString(
            TxInsertPayload.serializer(), TxInsertPayload(tx, walletId, cardId)
        )
    )

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_QUICK_ENTRY = "com.fintrack.app.QUICK_ENTRY"
    }
}
