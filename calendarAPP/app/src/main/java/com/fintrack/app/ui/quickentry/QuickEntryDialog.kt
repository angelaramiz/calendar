package com.fintrack.app.ui.quickentry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.TransactionCategories
import com.fintrack.app.domain.WalletResolver
import com.fintrack.app.ui.wallet.NewWalletDialog

/**
 * Registro rápido como ventana (AlertDialog), no pantalla completa.
 * Se muestra como destino `dialog` del NavGraph: funciona igual desde
 * el FAB del dashboard, el Tile y el Widget.
 */
@Composable
fun QuickEntryDialog(
    onSave: (TransactionEntity, String?, String?) -> Unit,
    onCancel: () -> Unit,
    wallets: List<WalletRow> = emptyList(),
    initialWalletId: String? = null,
    cards: List<CreditCardRow> = emptyList(),
    onAddWallet: (String) -> Unit = {}
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf(TransactionCategories.defaultFor(false)) }
    var description by remember { mutableStateOf("") }
    var walletId by remember(wallets, initialWalletId) {
        mutableStateOf(
            initialWalletId?.takeIf { id -> wallets.any { it.id == id } }
                ?: WalletResolver.EFECTIVO_ID
        )
    }
    // Tag de tarjeta de crédito (solo gastos): se registra pero se paga al corte.
    var cardId by remember { mutableStateOf<String?>(null) }
    var showNewWallet by remember { mutableStateOf(false) }
    val isIncome = type == "INCOME"
    val categories = TransactionCategories.forType(isIncome)
    val title = if (isIncome) "Ingreso rápido" else "Gasto rápido"

    val amountValue = amount.toDoubleOrNull()
    val valid = amountValue != null && amountValue > 0

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("EXPENSE" to "Gasto", "INCOME" to "Ingreso").forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                // Al cambiar de tipo, la categoría anterior ya no
                                // aplica: se reinicia al default de ese tipo.
                                category = TransactionCategories.defaultFor(t == "INCOME")
                                // La tarjeta solo aplica a gastos.
                                if (t == "INCOME") cardId = null
                            },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto") },
                    leadingIcon = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Nota (opcional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (wallets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Billetera", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        wallets.forEach { wallet ->
                            FilterChip(
                                selected = walletId == wallet.id,
                                onClick = { walletId = wallet.id },
                                label = { Text(wallet.name) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = { showNewWallet = true },
                            label = { Text("+ Nueva") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                if (!isIncome && cards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Pagar con tarjeta (se paga al corte)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = cardId == null,
                            onClick = { cardId = null },
                            label = { Text("Débito / Efectivo") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        cards.forEach { card ->
                            FilterChip(
                                selected = cardId == card.id,
                                onClick = { cardId = card.id },
                                label = { Text(card.displayName) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = amount.toDoubleOrNull() ?: return@TextButton
                    if (value <= 0) return@TextButton
                    onSave(
                        TransactionEntity(
                            amount = value,
                            type = type,
                            category = category,
                            description = description,
                            timestamp = System.currentTimeMillis(),
                            source = "MANUAL"
                        ),
                        walletId,
                        if (isIncome) null else cardId
                    )
                },
                enabled = valid
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    )

    if (showNewWallet) {
        NewWalletDialog(
            onDismiss = { showNewWallet = false },
            onSave = { name ->
                onAddWallet(name)
                showNewWallet = false
            }
        )
    }
}
