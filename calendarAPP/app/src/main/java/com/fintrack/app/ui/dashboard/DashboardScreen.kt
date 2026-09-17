package com.fintrack.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.OtaInstaller
import com.fintrack.app.ui.auth.BiometricLockScreen
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import com.fintrack.app.ui.theme.expenseColor
import com.fintrack.app.ui.theme.incomeColor
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToQuickEntry: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToFlows: () -> Unit,
    onNavigateToBudget: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    uiState.updateMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearUpdateMessage()
        }
    }

    // Bloqueo estilo banco: sin sesión pero con credenciales guardadas, la
    // huella desbloquea y sincroniza la cola sin pedir contraseña.
    if (uiState.needsLogin && uiState.canUnlockWithBiometrics) {
        BiometricLockScreen(
            unlocking = uiState.unlocking,
            unlockError = uiState.error,
            onUnlock = { viewModel.unlockWithSavedLogin() },
            onUsePassword = onNavigateToAuth
        )
        return
    }

    if (!uiState.needsLogin) {
        uiState.error?.let { err ->
            LaunchedEffect(err) {
                snackbarHostState.showSnackbar(err.take(200))
                viewModel.clearError()
            }
        }
    }

    uiState.updateAvailable?.let { update ->
        if (uiState.otaProgress == null && uiState.otaApkPath == null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdate() },
                title = { Text("Nueva versión disponible") },
                text = { Text("FinTrack ${update.versionName} está lista para descargar.") },
                confirmButton = {
                    TextButton(onClick = {
                        if (!OtaInstaller.canInstallUnknownApps(context)) {
                            OtaInstaller.openUnknownSourcesSettings(context)
                        } else {
                            viewModel.startUpdateDownload(context.applicationContext)
                        }
                    }) { Text("Descargar") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Después") }
                }
            )
        }
    }

    uiState.otaProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Descargando actualización") },
            text = {
                Column {
                    Text("FinTrack ${uiState.updateAvailable?.versionName ?: ""} · $progress%")
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelUpdateDownload(context.applicationContext) }) {
                    Text("Cancelar")
                }
            }
        )
    }

    uiState.otaApkPath?.let { apkPath ->
        val apkFile = remember(apkPath) { java.io.File(apkPath) }
        // Al completarse se abre solo el instalador (pide aceptar al usuario).
        LaunchedEffect(apkPath) {
            if (apkFile.exists()) {
                runCatching { OtaInstaller.promptInstall(context, apkFile) }
            }
        }
        AlertDialog(
            onDismissRequest = { viewModel.consumeReadyApk() },
            title = { Text("Descarga completa") },
            text = { Text("Se abrió el instalador: acepta para actualizar. Si no se abrió, toca Instalar.") },
            confirmButton = {
                TextButton(onClick = {
                    if (apkFile.exists()) {
                        runCatching { OtaInstaller.promptInstall(context, apkFile) }
                    }
                }) { Text("Instalar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.consumeReadyApk() }) { Text("Cerrar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FinTrack") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.checkForUpdate(manual = true) }) {
                        Icon(Icons.Default.SystemUpdate, "Buscar actualizaciones")
                    }
                    IconButton(onClick = onNavigateToPermissions) {
                        Icon(Icons.Default.Settings, "Permisos")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToQuickEntry) {
                Icon(Icons.Default.Add, "Agregar transaccion")
            }
        },
        bottomBar = {
            FinTrackBottomBar(
                selected = Routes.DASHBOARD,
                onDashboard = { },
                onCalendar = onNavigateToCalendar,
                onFlows = onNavigateToFlows,
                onBudget = onNavigateToBudget
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Balance Card
            item {
                BalanceCard(
                    balance = uiState.currentBalance,
                    income = uiState.totalIncome,
                    expenses = uiState.totalExpenses,
                    creditPending = uiState.creditPendingToday
                )
            }

            // Transactions Header
            item {
                Text("Transacciones de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Billeteras: neto del mes + filtro de la lista.
            if (uiState.wallets.isNotEmpty()) {
                item {
                    WalletFilterRow(
                        wallets = uiState.wallets,
                        totals = uiState.walletTotals,
                        selectedId = uiState.selectedWalletId,
                        onSelect = { viewModel.selectWallet(it) }
                    )
                }
            }

            if (uiState.recentTransactions.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Icon(
                                        Icons.Default.Receipt, null,
                                        modifier = Modifier.padding(16.dp).size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (uiState.needsLogin) "Bienvenido a FinTrack" else "Aún no hay movimientos",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (uiState.needsLogin) "Inicia sesion para ver tus transacciones" else "Agrega tu primera transacción con el botón +",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                uiState.error?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                if (uiState.needsLogin) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (uiState.canUnlockWithBiometrics) {
                                        Button(onClick = onNavigateToAuth) { Text("Desbloquear") }
                                    } else {
                                        Button(onClick = onNavigateToAuth) { Text("Iniciar sesión") }
                                    }
                                    TextButton(onClick = { viewModel.loadDashboard() }) { Text("Reintentar") }
                                }
                            }
                        }
                    }
                }
            } else {
                val cardNames = uiState.cards.associate { it.id to it.displayName }
                items(uiState.recentTransactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        cardName = uiState.cardCharges["tx:${transaction.id}"]
                            ?.let { cardNames[it] },
                        wallets = uiState.wallets,
                        cards = uiState.cards,
                        initialWalletId = uiState.walletOverrides["tx:${transaction.id}"]
                            ?: com.fintrack.app.domain.WalletResolver.walletForPackage(
                                transaction.source.takeIf { it != "MANUAL" },
                                uiState.wallets.map {
                                    com.fintrack.app.domain.WalletResolver.Wallet(
                                        it.id, it.name, it.packages
                                    )
                                }
                            ),
                        initialCardId = uiState.cardCharges["tx:${transaction.id}"],
                        onDelete = { viewModel.deleteTransaction(transaction.id) },
                        onUpdate = { updated, walletId, cardId ->
                            viewModel.updateTransaction(transaction.id, updated, walletId, cardId)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun WalletFilterRow(
    wallets: List<com.fintrack.app.data.WalletRow>,
    totals: Map<String, Double>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text("Todas") },
            modifier = Modifier.padding(end = 4.dp)
        )
        wallets.forEach { wallet ->
            val net = totals[wallet.id] ?: 0.0
            FilterChip(
                selected = selectedId == wallet.id,
                onClick = { onSelect(if (selectedId == wallet.id) null else wallet.id) },
                label = { Text("${wallet.name} · $${String.format("%.0f", net)}") },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun BalanceCard(balance: Double, income: Double, expenses: Double, creditPending: Double = 0.0) {
    val amountColor = if (balance >= 0) incomeColor() else expenseColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Balance Actual",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$${String.format("%.2f", balance)}",
                style = MaterialTheme.typography.displaySmall,
                color = amountColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (balance >= 0) "Tus finanzas van en positivo" else "Tus gastos superan tus ingresos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
            if (creditPending > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Por pagar en tarjetas: $${String.format("%.2f", creditPending)} (se paga al corte)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward, null,
                            tint = incomeColor(),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Ingresos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "+$${String.format("%.2f", income)}",
                                color = incomeColor(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward, null,
                            tint = expenseColor(),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Gastos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "-$${String.format("%.2f", expenses)}",
                                color = expenseColor(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    onDelete: () -> Unit,
    onUpdate: (TransactionEntity, String?, String?) -> Unit,
    cardName: String? = null,
    wallets: List<com.fintrack.app.data.WalletRow> = emptyList(),
    cards: List<com.fintrack.app.data.CreditCardRow> = emptyList(),
    initialWalletId: String? = null,
    initialCardId: String? = null
) {
    var showOptions by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val isIncome = transaction.isIncomeType()
    val icon = when (transaction.category) {
        "Comida" -> Icons.Default.Restaurant
        "Transporte" -> Icons.Default.DirectionsCar
        "Servicios" -> Icons.Default.Home
        "Ocio" -> Icons.Default.SportsEsports
        else -> Icons.Default.ShoppingCart
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(transaction.description.ifEmpty { transaction.category }) },
            text = {
                Text(
                    "${if (isIncome) "Ingreso" else "Gasto"} · $${String.format("%.2f", transaction.amount)} · ${transaction.category}" +
                        (transaction.merchant?.let { "\nComercio: $it" } ?: "") +
                        "\nOrigen: ${if (transaction.source == "AUTO") "Detectado" else "Manual"}"
                )
            },
            confirmButton = {
                TextButton(onClick = { showOptions = false; showEdit = true }) { Text("Editar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showOptions = false; showDelete = true }) { Text("Eliminar") }
                    TextButton(onClick = { showOptions = false }) { Text("Cerrar") }
                }
            }
        )
    }

    if (showEdit) {
        EditTransactionDialog(
            transaction = transaction,
            wallets = wallets,
            cards = cards,
            initialWalletId = initialWalletId,
            initialCardId = initialCardId,
            onDismiss = { showEdit = false },
            onSave = { updated, walletId, cardId ->
                showEdit = false
                onUpdate(updated, walletId, cardId)
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminar transacción") },
            text = { Text("¿Eliminar \"${transaction.description.ifEmpty { transaction.category }}\" por $${String.format("%.2f", transaction.amount)}?") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showOptions = true },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(40.dp).background(
                    if (isIncome) incomeColor().copy(alpha = 0.12f) else expenseColor().copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp)
                ).padding(8.dp),
                tint = if (isIncome) incomeColor() else expenseColor()
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.description.ifEmpty { transaction.category }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    transaction.category + (cardName?.let { " · 💳 $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${if (isIncome) "+" else "-"}$${String.format("%.2f", transaction.amount)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (isIncome) incomeColor() else expenseColor(),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity, String?, String?) -> Unit,
    wallets: List<com.fintrack.app.data.WalletRow> = emptyList(),
    cards: List<com.fintrack.app.data.CreditCardRow> = emptyList(),
    initialWalletId: String? = null,
    initialCardId: String? = null
) {
    var amount by remember(transaction) { mutableStateOf(String.format("%.2f", transaction.amount)) }
    var type by remember(transaction) { mutableStateOf(if (transaction.isIncomeType()) "INCOME" else "EXPENSE") }
    var category by remember(transaction) { mutableStateOf(transaction.category) }
    var description by remember(transaction) { mutableStateOf(transaction.description) }
    var merchant by remember(transaction) { mutableStateOf(transaction.merchant.orEmpty()) }
    var walletId by remember(transaction) { mutableStateOf(initialWalletId) }
    var cardId by remember(transaction) { mutableStateOf(initialCardId) }
    val isIncome = type == "INCOME"
    val categories = com.fintrack.app.domain.TransactionCategories.forType(isIncome)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar transacción") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    listOf("EXPENSE" to "Gasto", "INCOME" to "Ingreso").forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                category = com.fintrack.app.domain.TransactionCategories
                                    .defaultFor(t == "INCOME")
                                if (t == "INCOME") cardId = null
                            },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Comercio (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Nota") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (wallets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Billetera", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        wallets.forEach { wallet ->
                            FilterChip(
                                selected = walletId == wallet.id,
                                onClick = { walletId = wallet.id },
                                label = { Text(wallet.name) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
                if (!isIncome && cards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tarjeta", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
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
                                label = { Text(card.name) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toDoubleOrNull() ?: return@TextButton
                if (value <= 0) return@TextButton
                onSave(
                    transaction.copy(
                        amount = value,
                        type = type,
                        category = category,
                        description = description,
                        merchant = merchant.ifBlank { null }
                    ),
                    walletId,
                    if (type == "INCOME") null else cardId
                )
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
