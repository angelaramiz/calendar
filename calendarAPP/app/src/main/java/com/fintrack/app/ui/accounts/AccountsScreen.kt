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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
                    }
                )
            }

            item {
                SectionHeader(title = "Servicios", subtitle = "Vencimientos")
            }
            item {
                ServiceBillsCard(
                    bills = uiState.bills,
                    onSave = { id, name, amount, dueDay, frequency ->
                        viewModel.saveBill(id, name, amount, dueDay, frequency)
                    },
                    onDelete = { viewModel.deleteBill(it) }
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

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        }
    }
}
