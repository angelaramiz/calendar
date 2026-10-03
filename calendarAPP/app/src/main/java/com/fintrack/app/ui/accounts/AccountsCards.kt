package com.fintrack.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.MsiPlan
import com.fintrack.app.data.WalletRow
import com.fintrack.app.domain.WalletResolver
import com.fintrack.app.domain.toLocalDateIn
import com.fintrack.app.ui.common.AmountText
import com.fintrack.app.ui.theme.expenseColor
import com.fintrack.app.ui.theme.incomeColor

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
    onDelete: (String) -> Unit,
    flows: Map<String, WalletResolver.WalletMonthFlow> = emptyMap()
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
                val flow = flows[wallet.id]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wallet.displayWithKind, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (wallet.custom) "Propia" else "Fija") +
                                (flow?.let {
                                    " · +${formatMoney(it.income)} −${formatMoney(it.expense)}"
                                } ?: " · sin movimientos este mes"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Saldo del mes: se mueve con cada ingreso y gasto.
                        if (flow != null && (flow.income > 0.0 || flow.expense > 0.0)) {
                            val net = flow.net
                            Text(
                                "Neto " +
                                    (if (net >= 0.0) "+${formatMoney(net)}" else formatMoney(net)),
                                fontWeight = FontWeight.Bold,
                                color = if (net >= 0.0) incomeColor() else expenseColor()
                            )
                        }
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

/** Fila del estado de cuenta: concepto + fecha + monto. */
internal data class StatementRow(
    val label: String,
    val date: java.time.LocalDate,
    val amount: Double
)

/** Datos del modal "Edo. cuenta": cargos del periodo del estado abierto. */
internal data class StatementView(
    val cardName: String,
    val periodStart: java.time.LocalDate,
    val statementCutoff: java.time.LocalDate,
    val dueDate: java.time.LocalDate,
    val rows: List<StatementRow>,
    val paid: Double
) {
    val total: Double get() = rows.sumOf { it.amount }
    val remaining: Double get() = (total - paid).coerceAtLeast(0.0)
}

/** Cargos tagueados de una tarjeta + pagos registrados contra sus cortes. */
internal data class CardChargeData(
    val all: List<Triple<String, String, Pair<java.time.LocalDate, Double>>>,
    val cardPayments: List<Pair<java.time.LocalDate, Double>>
)

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
    onPay: (String, String, Double) -> Unit,
    // === Región A (MSI): planes por tarjeta, opcionales para no romper llamadas. ===
    msiPlans: List<MsiPlan> = emptyList(),
    onMsiCrear: (cardId: String, concepto: String, monto: Double, meses: Int) -> Unit = { _, _, _, _ -> },
    onMsiLiquidar: (String) -> Unit = {},
    onMsiEliminar: (String) -> Unit = {},
    onPasarAMsi: (cardId: String, concepto: String, monto: Double, meses: Int) -> Unit = { _, _, _, _ -> }
    // === Fin región A. ===
) {
    var editing by remember { mutableStateOf<com.fintrack.app.data.CreditCardRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<com.fintrack.app.domain.CreditCardPlanner.CardSummary?>(null) }
    var statementCard by remember { mutableStateOf<com.fintrack.app.data.CreditCardRow?>(null) }
    var statementPeriod by remember { mutableStateOf(0) }
    // === Región A (MSI): cargo del ciclo actual a pasar a MSI. ===
    var msiCard by remember { mutableStateOf<com.fintrack.app.data.CreditCardRow?>(null) }
    var msiChargeLabel by remember { mutableStateOf("") }
    var msiChargeAmount by remember { mutableStateOf(0.0) }
    // === Fin región A. ===
    // Periodo del corte en hora local: con UTC el periodo brincaba a las 18:00.
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    fun chargesOf(card: com.fintrack.app.data.CreditCardRow): CardChargeData {
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
        val cardPayments = payments
            .filter { it.cardId == card.id }
            .mapNotNull { pay ->
                val cutoff = runCatching {
                    java.time.LocalDate.parse(pay.statementCutoffIso)
                }.getOrNull() ?: return@mapNotNull null
                cutoff to pay.amount
            }
        return CardChargeData(txCharges + movCharges, cardPayments)
    }
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
                    val (all, cardPayments) = chargesOf(card)
                    val summary = com.fintrack.app.domain.CreditCardPlanner.summarize(
                        card.id, card.cutoffDay, card.paymentDay,
                        all.map { it.third }, today, cardPayments,
                        graceDays = card.graceDays
                    )
                    val periodOnly = all.filter { (_, _, dated) ->
                        !dated.first.isBefore(summary.periodStart) &&
                            dated.first.isBefore(summary.statementCutoff)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                TextButton(onClick = {
                                    statementCard = card
                                    statementPeriod = 0
                                }) { Text("Edo. cuenta") }
                                TextButton(onClick = { editing = card }) { Text("Editar") }
                                TextButton(onClick = { onDelete(card.id) }) { Text("Eliminar") }
                            }
                        }
                        AmountText(
                            "A pagar $${formatMoney(summary.remaining)} " +
                                "(cargos $${formatMoney(summary.periodCharges)}" +
                                if (summary.paid > 0.0) " − pagos $${formatMoney(summary.paid)}" else "" +
                                ") el ${summary.dueDate.dayOfMonth}/${summary.dueDate.monthValue}",
                            fontWeight = FontWeight.Bold
                        )
                        // === Región A (MSI): informativo "incluye $X de MSI (N planes)". ===
                        val msiDelCorte = com.fintrack.app.domain.MsiPlanner.msiEnCorte(
                            msiPlans.filter { it.cardId == card.id },
                            card.cutoffDay,
                            summary.statementCutoff
                        )
                        val msiActivos = msiPlans.count { it.cardId == card.id && !it.liquidado }
                        if (msiDelCorte > 0.0) {
                            AmountText(
                                "Incluye $${formatMoney(msiDelCorte)} de MSI ($msiActivos planes)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // === Fin región A. ===
                        if (summary.isPaid) {
                            Text(
                                "✓ Pagado (${formatMoney(summary.paid)})",
                                fontWeight = FontWeight.Bold,
                                color = incomeColor()
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { paying = summary }) {
                                    Text("Marcar pago")
                                }
                            }
                        }
                        val cycle = com.fintrack.app.domain.CreditCardPlanner.currentCycle(
                            all, { it.third.first }, summary.statementCutoff, today
                        )
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
                                    AmountText(
                                        "$label · $${formatMoney(dated.second)} · " +
                                            "${dated.first.dayOfMonth}/${dated.first.monthValue}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    TextButton(onClick = { onUntag(key) }) { Text("Quitar") }
                                }
                            }
                        }
                        val nextCut = com.fintrack.app.domain.CreditCardPlanner.nextCutoff(card.cutoffDay, today)
                        AmountText(
                            "Ciclo actual (corte ${nextCut.dayOfMonth}/${nextCut.monthValue}): " +
                                "$${formatMoney(cycle.sumOf { it.third.second })} · se paga el próximo corte",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (cycle.isEmpty()) {
                            Text(
                                "Sin cargos en el ciclo actual.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            cycle.forEach { (_, label, dated) ->
                                // === Región A (MSI): fila del ciclo con "Pasar a MSI". ===
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AmountText(
                                        "$label · $${formatMoney(dated.second)} · " +
                                            "${dated.first.dayOfMonth}/${dated.first.monthValue}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        msiCard = card
                                        msiChargeLabel = label
                                        msiChargeAmount = dated.second
                                    }) { Text("Pasar a MSI") }
                                }
                                // === Fin región A. ===
                            }
                        }
                        // === Región A (MSI): sección "MSI activos" por tarjeta. ===
                        MsiCardSection(
                            card = card,
                            planes = msiPlans,
                            cargosTagueados = all.map { it.third },
                            onCrear = onMsiCrear,
                            onLiquidar = onMsiLiquidar,
                            onEliminar = onMsiEliminar
                        )
                        // === Fin región A. ===
                    }
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar tarjeta")
            }
        }
    }
    // === Región A (MSI): diálogo "Pasar a MSI" desde el ciclo actual. ===
    msiCard?.let { target ->
        MsiPlanDialog(
            conceptoInicial = msiChargeLabel.substringBefore("·").trim().ifBlank { msiChargeLabel },
            montoInicial = msiChargeAmount,
            onDismiss = { msiCard = null },
            onSave = { concepto, monto, meses ->
                onPasarAMsi(target.id, concepto, monto, meses)
                msiCard = null
            }
        )
    }
    // === Fin región A. ===
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
                onPay(summary.cardId, summary.statementCutoff.toString(), amount)
                paying = null
            }
        )
    }
    statementCard?.let { card ->
        val planner = com.fintrack.app.domain.CreditCardPlanner
        val periods = planner.statementPeriods(card.cutoffDay, today)
        val index = statementPeriod.coerceIn(0, periods.size - 1)
        val (prev, open) = periods[index]
        val (all, cardPayments) = chargesOf(card)
        // Historial = el mismo summarize con today = cada corte.
        val hist = planner.summarize(
            card.id, card.cutoffDay, card.paymentDay,
            all.map { it.third }, open, cardPayments,
            graceDays = card.graceDays
        )
        CardStatementDialog(
            view = StatementView(
                cardName = card.displayName,
                periodStart = prev,
                statementCutoff = open,
                dueDate = hist.dueDate,
                rows = all.filter { (_, _, dated) ->
                    !dated.first.isBefore(prev) && dated.first.isBefore(open)
                }.map { (_, label, dated) ->
                    StatementRow(label, dated.first, dated.second)
                },
                paid = hist.paid
            ),
            position = "${index + 1}/${periods.size}",
            canNewer = index > 0,
            canOlder = index < periods.size - 1,
            onNewer = { statementPeriod = index - 1 },
            onOlder = { statementPeriod = index + 1 },
            onDismiss = { statementCard = null }
        )
    }
}

private fun shortDate(date: java.time.LocalDate): String =
    "${date.dayOfMonth}/${date.monthValue}"

/** Modal "Edo. cuenta": tabla con todos los cargos del periodo + historial ◀ ▶. */
@Composable
internal fun CardStatementDialog(
    view: StatementView,
    position: String,
    canNewer: Boolean,
    canOlder: Boolean,
    onNewer: () -> Unit,
    onOlder: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Estado de cuenta",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onNewer, enabled = canNewer) { Text("◀") }
                    Text(
                        position,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onOlder, enabled = canOlder) { Text("▶") }
                }
                Text(
                    "${view.cardName} · Periodo ${shortDate(view.periodStart)} → " +
                        "${shortDate(view.statementCutoff)} · Vence ${shortDate(view.dueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Fecha",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Concepto",
                        modifier = Modifier.weight(2f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Monto",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End
                    )
                }
                if (view.rows.isEmpty()) {
                    Text(
                        "Sin cargos en este periodo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        view.rows.forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    shortDate(row.date),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    row.label,
                                    modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    formatMoney(row.amount),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Total",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.weight(2f))
                    Text(
                        formatMoney(view.total),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End
                    )
                }
                if (view.paid > 0.0) {
                    Text(
                        "Pagados ${formatMoney(view.paid)} · Resta ${formatMoney(view.remaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
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
                        "(corte ${summary.statementCutoff.dayOfMonth}/${summary.statementCutoff.monthValue}, " +
                        "límite ${summary.dueDate.dayOfMonth}/${summary.dueDate.monthValue}). " +
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
    onSave: (String?, String, Double, Int, Int, String) -> Unit,
    onDelete: (String) -> Unit,
    onMarkPaid: (String, String) -> Unit,
    onUnmarkPaid: (String) -> Unit
) {
    var editing by remember { mutableStateOf<com.fintrack.app.data.ServiceBillRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    // "Vence" en hora local: con UTC el vencimiento se adelantaba de noche.
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Toca para dar de alta luz, agua, internet, etc. Te avisamos 3 días antes, 1 día antes y el día del vencimiento. " +
                    "Al pagar, márcalo como pagado para que ya no te avisemos de ese vencimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (bills.isEmpty()) {
                Text("Sin servicios programados.")
            } else {
                bills.forEach { bill ->
                    val due = com.fintrack.app.domain.ServiceBills.nextDue(
                        bill.dueDay, bill.frequency, today, bill.dueMonth
                    )
                    val paid = com.fintrack.app.domain.ServiceBills.isPaidFor(
                        bill.lastPaidDueIso, due
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bill.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Vence ${due.dayOfMonth}/${due.monthValue}" +
                                        (if (bill.estimatedAmount > 0.0) " · aprox. ${formatMoney(bill.estimatedAmount)}" else "") +
                                        (if (bill.frequency == "bimonthly") " · bimestral" else "") +
                                        (if (bill.frequency == "yearly") " · anual" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                TextButton(onClick = { editing = bill }) { Text("Editar") }
                                TextButton(onClick = { onDelete(bill.id) }) { Text("Eliminar") }
                            }
                        }
                        if (paid) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✓ Pagado",
                                    fontWeight = FontWeight.Bold,
                                    color = incomeColor()
                                )
                                TextButton(onClick = { onUnmarkPaid(bill.id) }) { Text("Desmarcar") }
                            }
                        } else {
                            Button(onClick = { onMarkPaid(bill.id, due.toString()) }) {
                                Text("Marcar pagado")
                            }
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
            onSave = { _, name, amount, dueDay, dueMonth, frequency ->
                onSave(null, name, amount, dueDay, dueMonth, frequency)
                adding = false
            }
        )
    }
    editing?.let { bill ->
        BillEditDialog(
            existing = bill,
            onDismiss = { editing = null },
            onSave = { id, name, amount, dueDay, dueMonth, frequency ->
                onSave(id, name, amount, dueDay, dueMonth, frequency)
                editing = null
            }
        )
    }
}

private val MESES_CORTO = listOf(
    "Ene", "Feb", "Mar", "Abr", "May", "Jun",
    "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BillEditDialog(
    existing: com.fintrack.app.data.ServiceBillRow?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Double, Int, Int, String) -> Unit
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
    var dueMonth by remember(existing) {
        mutableStateOf(existing?.dueMonth?.takeIf { it in 1..12 } ?: 1)
    }
    var frequency by remember(existing) {
        mutableStateOf(
            existing?.frequency?.takeIf { it == "bimonthly" || it == "yearly" } ?: "monthly"
        )
    }
    val valid = name.isNotBlank() && (dueText.toIntOrNull() in 1..31)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo servicio" else "Editar servicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                existing?.lastPaidDueIso?.let { paidIso ->
                    Text(
                        "✓ Pagado el $paidIso (ya no llegan sus avisos).",
                        style = MaterialTheme.typography.bodySmall,
                        color = incomeColor()
                    )
                }
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
                    listOf(
                        "monthly" to "Mensual",
                        "bimonthly" to "Bimestral",
                        "yearly" to "Anual"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = frequency == value,
                            onClick = { frequency = value },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                if (frequency == "yearly") {
                    Text(
                        "Mes del vencimiento anual (ej. predial, verificación)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MESES_CORTO.forEachIndexed { index, label ->
                            val month = index + 1
                            FilterChip(
                                selected = dueMonth == month,
                                onClick = { dueMonth = month },
                                label = { Text(label) }
                            )
                        }
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
                        dueMonth,
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
