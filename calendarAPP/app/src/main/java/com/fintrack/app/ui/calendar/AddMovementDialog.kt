package com.fintrack.app.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.WalletRow
import com.fintrack.app.domain.TransactionCategories
import com.fintrack.app.domain.WalletResolver
import com.fintrack.app.ui.wallet.NewWalletDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val UTC = ZoneId.of("UTC")
private val ES = Locale("es")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMovementDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        date: LocalDate,
        isIncome: Boolean,
        title: String,
        category: String,
        amount: Double,
        description: String,
        walletId: String?,
        cardId: String?
    ) -> Unit,
    isSaving: Boolean = false,
    wallets: List<WalletRow> = emptyList(),
    cards: List<CreditCardRow> = emptyList(),
    onAddWallet: (String) -> Unit = {}
) {
    var isIncome by remember(date) { mutableStateOf(false) }
    var amount by remember(date) { mutableStateOf("") }
    var title by remember(date) { mutableStateOf("") }
    var category by remember(date) { mutableStateOf(TransactionCategories.defaultFor(false)) }
    var description by remember(date) { mutableStateOf("") }
    var walletId by remember(date, wallets) {
        mutableStateOf(
            wallets.firstOrNull { it.id == WalletResolver.EFECTIVO_ID }?.id
                ?: wallets.firstOrNull()?.id
        )
    }
    var cardId by remember(date) { mutableStateOf<String?>(null) }
    var showNewWallet by remember { mutableStateOf(false) }
    var currentDate by remember(date) { mutableStateOf(date) }
    var showPicker by remember { mutableStateOf(false) }
    val categories = TransactionCategories.forType(isIncome)

    val amountValue = amount.toDoubleOrNull()
    val valid = amountValue != null && amountValue > 0 && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (isIncome) "Agregar ingreso" else "Agregar gasto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, "Cambiar fecha")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${currentDate.dayOfWeek.getDisplayName(TextStyle.FULL, ES)
                            .replaceFirstChar { it.uppercase() }} " +
                            "${currentDate.dayOfMonth} de " +
                            currentDate.month.getDisplayName(TextStyle.FULL, ES)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to "Gasto", true to "Ingreso").forEach { (value, label) ->
                        FilterChip(
                            selected = isIncome == value,
                            onClick = {
                                isIncome = value
                                category = TransactionCategories.defaultFor(value)
                                if (value) cardId = null
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

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título (opcional)") },
                    singleLine = true,
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
                        currentDate, isIncome, title, category, value, description,
                        walletId, if (isIncome) null else cardId
                    )
                },
                enabled = valid
            ) { Text(if (isSaving) "Guardando…" else "Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
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

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentDate.atStartOfDay(UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        currentDate = Instant.ofEpochMilli(it).atZone(UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("Elegir") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
