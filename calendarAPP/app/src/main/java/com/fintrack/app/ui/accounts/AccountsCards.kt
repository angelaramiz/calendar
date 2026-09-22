package com.fintrack.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.WalletRow
import com.fintrack.app.domain.toLocalDateIn

internal fun formatMoney(value: Double): String =
    "%,.0f".format(value).replace(',', '.')

@Composable
internal fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            subtitle.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Cuentas de débito / billeteras locales (nómina, vales, ahorro…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalletsCard(
    wallets: List<WalletRow>,
    onAdd: (name: String, last4: String, kind: String) -> Unit,
    onDelete: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newLast4 by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf("Débito") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Efectivo viene por defecto. Agrega tus cuentas (débito, nómina, " +
                    "vales, ahorro…) con su terminación y quita las fijas que no " +
                    "uses: ya no vuelven a aparecer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            wallets.forEach { wallet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wallet.displayWithKind, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (wallet.custom) "Propia" else "Fija",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onDelete(wallet.id) }) { Text("Quitar") }
                }
            }
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nueva cuenta") },
                placeholder = { Text("Nómina BBVA…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newLast4,
                    onValueChange = { newLast4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Term.") },
                    placeholder = { Text("1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAdd(newName, newLast4, newKind)
                            newName = ""
                            newLast4 = ""
                        }
                    },
                    modifier = Modifier.weight(0.6f)
                ) { Text("Añadir") }
            }
            Text("Tipo de cuenta", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WalletRow.ACCOUNT_KINDS.forEach { kind ->
                    FilterChip(
                        selected = newKind == kind,
                        onClick = { newKind = kind },
                        label = { Text(kind) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SubscriptionCard(
    subscriptions: List<com.fintrack.app.domain.SubscriptionCandidate>,
    onCreate: (com.fintrack.app.domain.SubscriptionCandidate) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (subscriptions.isEmpty()) {
                Text("Sin suscripciones detectadas. Aparecen con 3+ cobros iguales del mismo comercio.")
            } else {
                subscriptions.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(sub.merchant, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatMoney(sub.amount)} al mes · ${sub.months.size} meses · " +
                                    "próximo ${sub.nextExpected.dayOfMonth}/${sub.nextExpected.monthValue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { onCreate(sub) }) {
                            Text("Crear recurrente")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreditCardsCard(
    cards: List<com.fintrack.app.data.CreditCardRow>,
    charges: Map<String, String>,
    payments: List<com.fintrack.app.data.CardPayment>,
    transactions: List<com.fintrack.app.data.model.TransactionEntity>,
    movements: List<com.fintrack.app.data.repository.MovementRow>,
    onSave: (String?, String, Int, Int, String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onUntag: (String) -> Unit,
    onPay: (String, String, Double) -> Unit
) {
    var editing by remember { mutableStateOf<com.fintrack.app.data.CreditCardRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<com.fintrack.app.domain.CreditCardPlanner.CardSummary?>(null) }
    // Periodo del corte en hora local: con UTC el periodo brincaba a las 18:00.
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Los gastos con tag de tarjeta no restan al balance: se acumulan para pagarse al corte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cards.isEmpty()) {
                Text("Sin tarjetas. Agrega tu Nu, Plata, etc. con su día de corte y pago.")
            } else {
                cards.forEach { card ->
                    val txCharges = transactions
                        .filter { charges["tx:${it.id}"] == card.id }
                        .mapNotNull { tx ->
                            val date = tx.timestamp.toLocalDateIn()
                            Triple("tx:${tx.id}", txLabel(tx), date to tx.amount)
                        }
                    val movCharges = movements
                        .filter { charges["mov:${it.id}"] == card.id && !it.archived }
                        .mapNotNull { mov ->
                            val date = runCatching {
                                java.time.LocalDate.parse(mov.date)
                            }.getOrNull() ?: return@mapNotNull null
                            Triple("mov:${mov.id}", mov.title.ifBlank { mov.category }, date to mov.confirmed_amount)
                        }
                    val all = (txCharges + movCharges)
                    val cardPayments = payments
                        .filter { it.cardId == card.id }
                        .mapNotNull { pay ->
                            val cutoff = runCatching {
                                java.time.LocalDate.parse(pay.statementCutoffIso)
                            }.getOrNull() ?: return@mapNotNull null
                            cutoff to pay.amount
                        }
                    val summary = com.fintrack.app.domain.CreditCardPlanner.summarize(
                        card.id, card.cutoffDay, card.paymentDay,
                        all.map { it.third }, today, cardPayments,
                        graceDays = card.graceDays
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(card.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (card.usesGrace)
                                        "Corte día ${card.cutoffDay} · Pago +${card.graceDays} días"
                                    else
                                        "Corte día ${card.cutoffDay} · Pago día ${card.paymentDay}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                TextButton(onClick = { editing = card }) { Text("Editar") }
                                TextButton(onClick = { onDelete(card.id) }) { Text("Eliminar") }
                            }
                        }
                        Text(
                            "A pagar ${formatMoney(summary.remaining)} " +
                                "(cargos ${formatMoney(summary.periodCharges)}" +
                                if (summary.paid > 0.0) " − pagos ${formatMoney(summary.paid)}" else "" +
                                ") el ${summary.nextPayment.dayOfMonth}/${summary.nextPayment.monthValue}",
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { paying = summary }) {
                                Text("Marcar pago")
                            }
                        }
                        val periodOnly = all.filter { (_, _, dated) ->
                            !dated.first.isBefore(summary.lastCutoff) &&
                                dated.first.isBefore(summary.nextCutoff)
                        }
                        if (periodOnly.isEmpty()) {
                            Text(
                                "Sin cargos en este periodo.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            periodOnly.forEach { (key, label, dated) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "$label · ${formatMoney(dated.second)} · " +
                                            "${dated.first.dayOfMonth}/${dated.first.monthValue}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    TextButton(onClick = { onUntag(key) }) { Text("Quitar") }
                                }
                            }
                        }
                    }
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar tarjeta")
            }
        }
    }
    if (adding) {
        CardEditDialog(
            existing = null,
            onDismiss = { adding = false },
            onSave = { _, name, cutoff, payment, last4, grace ->
                onSave(null, name, cutoff, payment, last4, grace)
                adding = false
            },
            onDelete = null
        )
    }
    editing?.let { card ->
        CardEditDialog(
            existing = card,
            onDismiss = { editing = null },
                    onSave = { id, name, cutoff, payment, last4, grace ->
                        onSave(id, name, cutoff, payment, last4, grace)
                    },
            onDelete = { onDelete(card.id); editing = null }
        )
    }
    paying?.let { summary ->
        CardPayDialog(
            summary = summary,
            cardName = cards.firstOrNull { it.id == summary.cardId }?.displayName,
            onDismiss = { paying = null },
            onSave = { amount ->
                onPay(summary.cardId, summary.nextCutoff.toString(), amount)
                paying = null
            }
        )
    }
}

@Composable
internal fun CardPayDialog(
    summary: com.fintrack.app.domain.CreditCardPlanner.CardSummary,
    cardName: String?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountText by remember(summary) {
        mutableStateOf(formatMoney(summary.remaining))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcar pago${cardName?.let { " · $it" } ?: ""}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Estimado a pagar: ${formatMoney(summary.remaining)} " +
                        "(corte ${summary.nextCutoff.dayOfMonth}/${summary.nextCutoff.monthValue}, " +
                        "límite ${summary.nextPayment.dayOfMonth}/${summary.nextPayment.monthValue}). " +
                        "Corrige con lo que en realidad pagaste.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        amountText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Monto pagado") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
                if (amount > 0.0) onSave(amount)
            }) { Text("Guardar pago") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
internal fun ServiceBillsCard(
    bills: List<com.fintrack.app.data.ServiceBillRow>,
    onSave: (String?, String, Double, Int, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<com.fintrack.app.data.ServiceBillRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    // "Vence" en hora local: con UTC el vencimiento se adelantaba de noche.
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Toca para dar de alta luz, agua, internet, etc. Te avisamos 3 días antes, 1 día antes y el día del vencimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (bills.isEmpty()) {
                Text("Sin servicios programados.")
            } else {
                bills.forEach { bill ->
                    val due = com.fintrack.app.domain.ServiceBills.nextDue(
                        bill.dueDay, bill.frequency, today
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(bill.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Vence ${due.dayOfMonth}/${due.monthValue}" +
                                    (if (bill.estimatedAmount > 0.0) " · aprox. ${formatMoney(bill.estimatedAmount)}" else "") +
                                    (if (bill.frequency == "bimonthly") " · bimestral" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            TextButton(onClick = { editing = bill }) { Text("Editar") }
                            TextButton(onClick = { onDelete(bill.id) }) { Text("Eliminar") }
                        }
                    }
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar servicio")
            }
        }
    }
    if (adding) {
        BillEditDialog(
            existing = null,
            onDismiss = { adding = false },
            onSave = { _, name, amount, dueDay, frequency ->
                onSave(null, name, amount, dueDay, frequency)
                adding = false
            }
        )
    }
    editing?.let { bill ->
        BillEditDialog(
            existing = bill,
            onDismiss = { editing = null },
            onSave = { id, name, amount, dueDay, frequency ->
                onSave(id, name, amount, dueDay, frequency)
                editing = null
            }
        )
    }
}

@Composable
internal fun BillEditDialog(
    existing: com.fintrack.app.data.ServiceBillRow?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Double, Int, String) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var amountText by remember(existing) {
        mutableStateOf(
            existing?.takeIf { it.estimatedAmount > 0.0 }?.let { formatMoney(it.estimatedAmount) } ?: ""
        )
    }
    var dueText by remember(existing) {
        mutableStateOf(existing?.dueDay?.toString() ?: "")
    }
    var frequency by remember(existing) {
        mutableStateOf(existing?.frequency?.takeIf { it == "bimonthly" } ?: "monthly")
    }
    val valid = name.isNotBlank() && (dueText.toIntOrNull() in 1..31)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo servicio" else "Editar servicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. CFE, Telmex)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        amountText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Monto estimado (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dueText,
                    onValueChange = { dueText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Día de vencimiento (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("monthly" to "Mensual", "bimonthly" to "Bimestral").forEach { (value, label) ->
                        FilterChip(
                            selected = frequency == value,
                            onClick = { frequency = value },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existing?.id, name,
                        amountText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0,
                        dueText.toIntOrNull() ?: 1,
                        frequency
                    )
                },
                enabled = valid
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

internal fun txLabel(tx: com.fintrack.app.data.model.TransactionEntity): String =
    tx.description.ifBlank { tx.category } +
        (tx.merchant?.let { " ($it)" } ?: "")

@Composable
internal fun CardEditDialog(
    existing: com.fintrack.app.data.CreditCardRow?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Int, Int, String, Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var last4Text by remember(existing) { mutableStateOf(existing?.last4 ?: "") }
    var cutoffText by remember(existing) {
        mutableStateOf(existing?.cutoffDay?.toString() ?: "")
    }
    var useGrace by remember(existing) {
        mutableStateOf((existing?.graceDays ?: 0) > 0)
    }
    var paymentText by remember(existing) {
        mutableStateOf(existing?.paymentDay?.toString() ?: "")
    }
    var graceText by remember(existing) {
        mutableStateOf(existing?.graceDays?.takeIf { it > 0 }?.toString() ?: "30")
    }
    val valid = name.isNotBlank() &&
        (cutoffText.toIntOrNull() in 1..31) &&
        (if (useGrace) (graceText.toIntOrNull() in 1..90)
        else (paymentText.toIntOrNull() in 1..31))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nueva tarjeta" else "Editar tarjeta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre / banco (ej. Plata)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = last4Text,
                    onValueChange = { last4Text = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Terminación (4 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = cutoffText,
                    onValueChange = { cutoffText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Día de corte (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useGrace,
                        onClick = { useGrace = false },
                        label = { Text("Día fijo") }
                    )
                    FilterChip(
                        selected = useGrace,
                        onClick = { useGrace = true },
                        label = { Text("+ N días (Plata)") }
                    )
                }
                if (useGrace) {
                    OutlinedTextField(
                        value = graceText,
                        onValueChange = { graceText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Días después del corte (1-90)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "Plata usa 30: del primer día del periodo al corte " +
                            "son ~30 días y del corte al pago otros 30 " +
                            "(~60 días totales).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = paymentText,
                        onValueChange = { paymentText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Día de pago (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existing?.id, name,
                        cutoffText.toIntOrNull() ?: 1,
                        paymentText.toIntOrNull() ?: 1,
                        last4Text,
                        if (useGrace) (graceText.toIntOrNull() ?: 30) else 0
                    )
                },
                enabled = valid
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (existing != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
