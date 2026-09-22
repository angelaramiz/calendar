package com.fintrack.app.ui.quickentry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.model.TransactionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ventana IN: registro rápido dentro de la app (botón "+" de Inicio).
 * Se muestra como destino `dialog` del NavGraph.
 *
 * Persiana desde abajo con el formulario completo en una sola pantalla
 * ([QuickEntryFullForm]). La variante por pasos ([QuickEntryForm]) con
 * persiana desde arriba es exclusiva de la ventana OUT del Tile.
 */
@Composable
fun QuickEntryDialog(
    onSave: (TransactionEntity, String?, String?) -> Unit,
    onCancel: () -> Unit,
    cards: List<CreditCardRow> = emptyList(),
    wallets: List<WalletRow> = emptyList(),
    onCreateWallet: (name: String, last4: String, kind: String) -> Unit = { _, _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    fun requestClose() {
        if (!visible) return
        visible = false
        scope.launch {
            delay(280)
            onCancel()
        }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::requestClose
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    shape = RoundedCornerShape(
                        topStart = 28.dp, topEnd = 28.dp,
                        bottomStart = 0.dp, bottomEnd = 0.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    QuickEntryFullForm(
                        cards = cards,
                        wallets = wallets,
                        onSave = onSave,
                        onCancel = ::requestClose,
                        onCreateWallet = onCreateWallet
                    )
                }
            }
        }
    }
}
