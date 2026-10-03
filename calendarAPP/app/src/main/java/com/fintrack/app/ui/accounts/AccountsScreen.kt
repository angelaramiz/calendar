package com.fintrack.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.AnomalyDismissStore
import com.fintrack.app.domain.Anomaly
import com.fintrack.app.domain.AnomalyChecker
import com.fintrack.app.ui.watch.AnomalyWatchCard
import com.fintrack.app.ui.watch.HormigaFreqSetting
import com.fintrack.app.ui.watch.StreakCard
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.CsvImport
import com.fintrack.app.ui.dashboard.DashboardViewModel
import com.fintrack.app.ui.import.CsvImportEntry
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import com.fintrack.app.ui.common.PullRefreshLayout
import org.koin.androidx.compose.koinViewModel

/**
 * Pestaña Cuentas: administración del dinero (débito, crédito, servicios,
 * suscripciones). Presupuesto queda como análisis financiero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToFlows: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    viewModel: AccountsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Al volver a la pestaña (ej. tras agregar un gasto en Inicio) los saldos
    // se recalculan: mismo patrón ON_RESUME de Permisos.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadAccounts()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    uiState.info?.let { info ->
        LaunchedEffect(info) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearInfo()
        }
    }

    // === Vigilante (D5): anomalías calculadas sobre lo cargado, menos descartes. ===
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var anomalies by remember { mutableStateOf<List<Anomaly>>(emptyList()) }
    LaunchedEffect(uiState.allTransactions) {
        val dismissed = runCatching {
            AnomalyDismissStore(context.applicationContext).snapshot()
        }.getOrDefault(emptySet())
        anomalies = AnomalyChecker.check(uiState.allTransactions)
            .filter { it.stableId !in dismissed }
    }
    // === Fin D5. ===

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuentas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            FinTrackBottomBar(
                selected = Routes.ACCOUNTS,
                onDashboard = onNavigateToDashboard,
                onCalendar = onNavigateToCalendar,
                onFlows = onNavigateToFlows,
                onBudget = onNavigateToBudget,
                onAccounts = { }
            )
        }
    ) { padding ->
        if (uiState.needsLogin) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Inicia sesión para ver tus cuentas.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNavigateToAuth) {
                    Text("Ir a iniciar sesión")
                }
            }
            return@Scaffold
        }

        if (uiState.isLoading && uiState.wallets.isEmpty() && uiState.cards.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val listState = rememberLazyListState()
        PullRefreshLayout(
            onRefresh = { viewModel.loadAccounts() },
            isLoading = uiState.isLoading,
            atTopProvider = {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            },
            modifier = Modifier.padding(padding)
        ) { pullModifier ->
        LazyColumn(
            state = listState,
            modifier = pullModifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            uiState.error?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            uiState.info?.let { info ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            text = info,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Cuentas de débito", subtitle = "Nómina, vales, ahorro")
            }
            item {
                WalletsCard(
                    wallets = uiState.wallets,
                    onAdd = { name, last4, kind -> viewModel.addWallet(name, last4, kind) },
                    onDelete = { viewModel.deleteWallet(it) },
                    flows = uiState.walletFlows
                )
            }

            item {
                SectionHeader(title = "Tarjetas de crédito", subtitle = "Corte y pago")
            }
            item {
                CreditCardsCard(
                    cards = uiState.cards,
                    charges = uiState.cardCharges,
                    payments = uiState.cardPayments,
                    transactions = uiState.allTransactions,
                    movements = uiState.recentMovements,
                    onSave = { id, name, cutoff, payment, last4, grace ->
                        viewModel.saveCard(id, name, cutoff, payment, last4, grace)
                    },
                    onDelete = { viewModel.deleteCard(it) },
                    onUntag = { viewModel.untagCharge(it) },
                    onPay = { cardId, cutoffIso, amount ->
                        viewModel.recordCardPayment(cardId, cutoffIso, amount)
                    },
                    // === Región A (MSI): planes por tarjeta + pasar cargos a MSI. ===
                    // El primer corte siempre es el próximo corte de la tarjeta.
                    msiPlans = uiState.msiPlans,
                    onMsiCrear = { cardId, concepto, monto, meses ->
                        viewModel.pasarCargoAMsi(cardId, concepto, monto, meses)
                    },
                    onMsiLiquidar = { viewModel.liquidarMsi(it) },
                    onMsiEliminar = { viewModel.deleteMsi(it) },
                    onPasarAMsi = { cardId, concepto, monto, meses ->
                        viewModel.pasarCargoAMsi(cardId, concepto, monto, meses)
                    }
                    // === Fin región A. ===
                )
            }

            item {
                SectionHeader(title = "Servicios", subtitle = "Vencimientos")
            }
            item {
                ServiceBillsCard(
                    bills = uiState.bills,
                    onSave = { id, name, amount, dueDay, dueMonth, frequency ->
                        viewModel.saveBill(id, name, amount, dueDay, dueMonth, frequency)
                    },
                    onDelete = { viewModel.deleteBill(it) },
                    onMarkPaid = { id, dueIso -> viewModel.markBillPaid(id, dueIso) },
                    onUnmarkPaid = { viewModel.unmarkBillPaid(it) }
                )
            }

            item {
                SectionHeader(title = "Suscripciones", subtitle = "Cargos repetidos")
            }
            item {
                SubscriptionCard(
                    subscriptions = uiState.subscriptions,
                    onCreate = { viewModel.createSubscriptionPattern(it) }
                )
            }

            // === Región D8 (deudas personales): grupo "Personas" al final. ===
            item {
                SectionHeader(title = "Personas", subtitle = "Quién te debe / a quién debes")
            }
            item {
                PersonDebtsCard(
                    debts = uiState.personDebts,
                    onSave = { id, nombre, monto, esMia, fecha ->
                        viewModel.savePersonDebt(id, nombre, monto, esMia, fecha)
                    },
                    onDelete = { viewModel.deletePersonDebt(it) },
                    onAbono = { debtId, amount -> viewModel.addPersonAbono(debtId, amount) },
                    onLiquidar = { viewModel.liquidarPersonDebt(it) }
                )
            }
            // === Fin región D8. ===

            // === Vigilante (D5) + Fuga/Rachas (D11) al final de Cuentas. ===
            item {
                SectionHeader(title = "Vigilante", subtitle = "Duplicados y subidas")
            }
            item {
                AnomalyWatchCard(anomalies = anomalies) { id ->
                    scope.launch {
                        runCatching {
                            AnomalyDismissStore(context.applicationContext).dismiss(id)
                        }
                        anomalies = anomalies.filterNot { it.stableId == id }
                    }
                }
            }
            item { HormigaFreqSetting() }
            item { StreakCard() }
            // === Fin D5/D11. ===

            // === Región D7 (importar CSV del banco + conciliación). ===
            // Entrada al final con navegación interna por estado local (sin
            // ruta nueva en NavGraph): picker SAF sin permisos; aceptar crea
            // vía DashboardViewModel.addTransaction, vincular usa
            // OccurrenceLink/CsvImport.exactMatch, Dedup evita re-imports.
            item {
                SectionHeader(title = "Importar CSV", subtitle = "Concilia tu banco")
            }
            item {
                val activity = LocalContext.current as ComponentActivity
                val dashVm: DashboardViewModel =
                    koinViewModel(viewModelStoreOwner = activity)
                CsvImportEntry(
                    existing = uiState.allTransactions,
                    onAccept = { candidate ->
                        val zone = java.time.ZoneId.systemDefault()
                        dashVm.addTransaction(
                            TransactionEntity(
                                amount = candidate.amount,
                                type = if (candidate.isIncome) "INCOME" else "EXPENSE",
                                category = CsvImport.toOccurrence(candidate).pattern.category,
                                description = candidate.concept,
                                timestamp = java.time.LocalDate.parse(candidate.dateIso)
                                    .atStartOfDay(zone).toInstant().toEpochMilli() +
                                    12 * 3600 * 1000,
                                source = "CSV"
                            ),
                            null,
                            null
                        )
                    }
                )
            }
            // === Fin región D7. ===

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        }
    }
}
