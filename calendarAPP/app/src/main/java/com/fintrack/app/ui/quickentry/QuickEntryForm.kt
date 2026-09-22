package com.fintrack.app.ui.quickentry

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.TransactionCategories

private const val STEP_TYPE = 0
private const val STEP_AMOUNT = 1
private const val STEP_CATEGORY = 2
private const val STEP_NOTE = 3
private const val STEP_ACCOUNT = 4
private const val TOTAL_STEPS = 5

/**
 * Registro rápido por pasos (wizard), pensado para la persiana que baja
 * desde arriba —tanto dentro de la app como en el diálogo flotante del Tile—:
 * 1) tipo de movimiento, 2) monto, 3) categoría, 4) nota, 5) método de pago
 * o cuenta destino (con alta de cuentas débito/nómina/vales/ahorro).
 *
 * [onSave] recibe la transacción + billetera elegida (null = Efectivo /
 * regla por defecto) + tarjeta de crédito tageada (null = no es a crédito).
 * [onCreateWallet] persiste la cuenta nueva; cuando [wallets] se actualice
 * con ella, el paso 5 la selecciona solo.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class)
@Composable
fun QuickEntryForm(
    cards: List<CreditCardRow> = emptyList(),
    wallets: List<WalletRow> = emptyList(),
    onSave: (TransactionEntity, String?, String?) -> Unit,
    onCancel: () -> Unit,
    onCreateWallet: (name: String, last4: String, kind: String) -> Unit = { _, _, _ -> }
) {
    var step by remember { mutableIntStateOf(STEP_TYPE) }
    var type by remember { mutableStateOf("EXPENSE") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TransactionCategories.defaultFor(false)) }
    var description by remember { mutableStateOf("") }
    // Paso 5: selección exclusiva — o billetera (débito/efectivo/…) o tarjeta.
    var walletId by remember { mutableStateOf<String?>(null) }
    var cardId by remember { mutableStateOf<String?>(null) }
    // Alta de cuenta dentro del paso 5.
    var showNewAccount by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLast4 by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf("Débito") }
    // Nombre pendiente de aparecer en [wallets] para autoseleccionarlo.
    var pendingNewName by remember { mutableStateOf<String?>(null) }

    val isIncome = type == "INCOME"
    val categories = TransactionCategories.forType(isIncome)
    val amountValue = amount.toDoubleOrNull()
    val amountValid = amountValue != null && amountValue > 0

    // La cuenta recién creada aparece en la lista del padre: seleccionarla.
    LaunchedEffect(wallets, pendingNewName) {
        val pending = pendingNewName ?: return@LaunchedEffect
        wallets.firstOrNull { it.name.trim().equals(pending, ignoreCase = true) }?.let {
            walletId = it.id
            cardId = null
            pendingNewName = null
            showNewAccount = false
            newName = ""
            newLast4 = ""
        }
    }

    val accountTitle = if (isIncome) "Cuenta destino" else "Método de pago"
    val stepTitle = when (step) {
        STEP_TYPE -> "Tipo de movimiento"
        STEP_AMOUNT -> "Monto"
        STEP_CATEGORY -> "Categoría"
        STEP_NOTE -> "Nota"
        else -> accountTitle
    }
    // Al cambiar de tipo, la categoría anterior ya no aplica.
    fun selectType(t: String) {
        type = t
        category = TransactionCategories.defaultFor(t == "INCOME")
        if (t == "INCOME") cardId = null
    }

    val canContinue = when (step) {
        STEP_AMOUNT -> amountValid
        STEP_ACCOUNT -> pendingNewName == null
        else -> true
    }
    val isLast = step == STEP_ACCOUNT

    Column(modifier = Modifier.padding(24.dp)) {
        // Encabezado + progreso.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Paso ${step + 1} de $TOTAL_STEPS · $stepTitle",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (step + 1) / TOTAL_STEPS.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(onClick = onCancel) { Text("Cerrar") }
        }
        // Resumen compacto de lo ya elegido.
        if (step > STEP_TYPE) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                buildString {
                    append(if (isIncome) "Ingreso" else "Gasto")
                    if (step > STEP_AMOUNT) {
                        append(" · $")
                        append(amount.ifBlank { "0" })
                    }
                    if (step > STEP_CATEGORY) append(" · $category")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "quickEntryStep"
        ) { current ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (current) {
                    STEP_TYPE -> StepType(selected = type, onSelect = ::selectType)
                    STEP_AMOUNT -> StepAmount(
                        value = amount,
                        onChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }
                    )
                    STEP_CATEGORY -> StepCategory(
                        categories = categories,
                        selected = category,
                        onSelect = { category = it }
                    )
                    STEP_NOTE -> StepNote(
                        value = description,
                        onChange = { description = it }
                    )
                    else -> StepAccount(
                        isIncome = isIncome,
                        wallets = wallets,
                        cards = cards,
                        walletId = walletId,
                        cardId = cardId,
                        onSelectWallet = { walletId = it; cardId = null },
                        onSelectCard = { cardId = it; walletId = null },
                        showNewAccount = showNewAccount,
                        onToggleNewAccount = { showNewAccount = !showNewAccount },
                        newName = newName,
                        onNewName = { newName = it },
                        newLast4 = newLast4,
                        onNewLast4 = { newLast4 = it.filter { c -> c.isDigit() }.take(4) },
                        newKind = newKind,
                        onNewKind = { newKind = it },
                        savingAccount = pendingNewName != null,
                        onAddAccount = {
                            val name = newName.trim()
                            if (name.isNotBlank() && pendingNewName == null) {
                                pendingNewName = name
                                onCreateWallet(name, newLast4, newKind)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step == STEP_TYPE) {
                TextButton(onClick = onCancel) { Text("Cancelar") }
            } else {
                TextButton(onClick = { step-- }) { Text("Atrás") }
            }
            Button(
                onClick = {
                    if (isLast) {
                        val value = amount.toDoubleOrNull() ?: return@Button
                        if (value <= 0) return@Button
                        onSave(
                            TransactionEntity(
                                amount = value,
                                type = type,
                                category = category,
                                description = description,
                                timestamp = System.currentTimeMillis(),
                                source = "MANUAL"
                            ),
                            // Ingresos nunca llevan tag de tarjeta.
                            walletId,
                            if (isIncome) null else cardId
                        )
                    } else {
                        step++
                    }
                },
                enabled = canContinue
            ) { Text(if (isLast) "Guardar" else "Continuar") }
        }
    }
}

@Composable
private fun StepType(selected: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf("EXPENSE" to "Gasto", "INCOME" to "Ingreso").forEach { (t, label) ->
            val isSelected = selected == t
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(t) }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
        Text(
            if (selected == "INCOME") "Dinero que entra a tus cuentas."
            else "Dinero que sale de tus cuentas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepAmount(value: String, onChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("¿Cuánto?") },
            leadingIcon = {
                Text("$", style = MaterialTheme.typography.headlineSmall)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("50", "100", "200", "500", "1000").forEach { quick ->
                OutlinedButton(onClick = { onChange(quick) }) { Text("$$quick") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCategory(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                label = { Text(cat) }
            )
        }
    }
}

@Composable
private fun StepNote(value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("Nota (opcional)") },
            placeholder = { Text("Ej. tacos con Ana") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Puedes continuar sin nota.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepAccount(
    isIncome: Boolean,
    wallets: List<WalletRow>,
    cards: List<CreditCardRow>,
    walletId: String?,
    cardId: String?,
    onSelectWallet: (String?) -> Unit,
    onSelectCard: (String?) -> Unit,
    showNewAccount: Boolean,
    onToggleNewAccount: () -> Unit,
    newName: String,
    onNewName: (String) -> Unit,
    newLast4: String,
    onNewLast4: (String) -> Unit,
    newKind: String,
    onNewKind: (String) -> Unit,
    savingAccount: Boolean,
    onAddAccount: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (savingAccount) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Guardando cuenta…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            if (isIncome) "¿A qué cuenta llega?"
            else "¿De dónde sale?",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (wallets.isEmpty()) {
                FilterChip(
                    selected = walletId == null,
                    onClick = { onSelectWallet(null) },
                    label = { Text("Efectivo") }
                )
            } else {
                wallets.forEach { wallet ->
                    FilterChip(
                        selected = walletId == wallet.id ||
                            (walletId == null && cardId == null && wallet.id == "efectivo"),
                        onClick = { onSelectWallet(wallet.id) },
                        label = { Text(wallet.displayWithKind) }
                    )
                }
            }
        }

        if (!isIncome && cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("¿O es a crédito?", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cards.forEach { card ->
                    FilterChip(
                        selected = cardId == card.id,
                        onClick = { onSelectCard(card.id) },
                        label = { Text(card.displayName) }
                    )
                }
            }
            Text(
                "El cargo a crédito no resta al balance: se suma a tu próximo pago.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onToggleNewAccount) {
            Text(if (showNewAccount) "Ocultar alta" else "+ Nueva cuenta")
        }
        if (showNewAccount) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = onNewName,
                label = { Text("Nombre de la cuenta") },
                placeholder = { Text("Ej. Nómina BBVA, Vales…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newLast4,
                    onValueChange = onNewLast4,
                    label = { Text("Term.") },
                    placeholder = { Text("1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAddAccount,
                    enabled = newName.isNotBlank() && !savingAccount,
                    modifier = Modifier.weight(0.6f)
                ) { Text("Añadir") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tipo de cuenta", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WalletRow.ACCOUNT_KINDS.forEach { kind ->
                    FilterChip(
                        selected = newKind == kind,
                        onClick = { onNewKind(kind) },
                        label = { Text(kind) }
                    )
                }
            }
        }
    }
}
