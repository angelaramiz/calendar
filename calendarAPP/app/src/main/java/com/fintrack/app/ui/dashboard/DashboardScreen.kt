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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.OtaInstaller
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToQuickEntry: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToCalendar: () -> Unit,
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

    if (!uiState.needsLogin) {
        uiState.error?.let { err ->
            LaunchedEffect(err) {
                snackbarHostState.showSnackbar(err.take(200))
                viewModel.clearError()
            }
        }
    }

    uiState.updateAvailable?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Nueva versión disponible") },
            text = { Text("FinTrack ${update.versionName} está lista para descargar.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!OtaInstaller.canInstallUnknownApps(context)) {
                        OtaInstaller.openUnknownSourcesSettings(context)
                    } else {
                        OtaInstaller.downloadAndInstall(context, update.apkUrl, update.versionName)
                        viewModel.dismissUpdate()
                    }
                }) { Text("Descargar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Después") }
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
                onCalendar = onNavigateToCalendar
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
                    expenses = uiState.totalExpenses
                )
            }

            // Transactions Header
            item {
                Text("Transacciones recientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (uiState.recentTransactions.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(if (uiState.needsLogin) "Inicia sesion para ver tus transacciones" else "Sin transacciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                uiState.error?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                if (uiState.needsLogin) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = onNavigateToAuth) { Text("Iniciar sesión") }
                                    TextButton(onClick = { viewModel.loadDashboard() }) { Text("Reintentar") }
                                }
                            }
                        }
                    }
                }
            } else {
                items(uiState.recentTransactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onDelete = { viewModel.deleteTransaction(transaction.id) },
                        onUpdate = { updated -> viewModel.updateTransaction(transaction.id, updated) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun BalanceCard(balance: Double, income: Double, expenses: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Balance Actual", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$${String.format("%.2f", balance)}",
                style = MaterialTheme.typography.headlineLarge,
                color = if (balance >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ingresos", style = MaterialTheme.typography.bodySmall)
                    Text("+$${String.format("%.2f", income)}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gastos", style = MaterialTheme.typography.bodySmall)
                    Text("-$${String.format("%.2f", expenses)}", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    onDelete: () -> Unit,
    onUpdate: (TransactionEntity) -> Unit
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
            onDismiss = { showEdit = false },
            onSave = { updated -> showEdit = false; onUpdate(updated) }
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(40.dp).background(
                    if (isIncome) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFF44336).copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp)
                ).padding(8.dp),
                tint = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.description.ifEmpty { transaction.category }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(transaction.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${if (isIncome) "+" else "-"}$${String.format("%.2f", transaction.amount)}",
                color = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336),
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
    onSave: (TransactionEntity) -> Unit
) {
    var amount by remember(transaction) { mutableStateOf(String.format("%.2f", transaction.amount)) }
    var type by remember(transaction) { mutableStateOf(if (transaction.isIncomeType()) "INCOME" else "EXPENSE") }
    var category by remember(transaction) { mutableStateOf(transaction.category) }
    var description by remember(transaction) { mutableStateOf(transaction.description) }
    var merchant by remember(transaction) { mutableStateOf(transaction.merchant.orEmpty()) }
    val categories = listOf("Comida", "Transporte", "Servicios", "Ocio", "Otros")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar transacción") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    listOf("EXPENSE" to "Gasto", "INCOME" to "Ingreso").forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
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
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
