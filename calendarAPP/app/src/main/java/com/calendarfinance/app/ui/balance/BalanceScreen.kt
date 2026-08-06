package com.calendarfinance.app.ui.balance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calendarfinance.app.data.model.BalanceSummary
import com.calendarfinance.app.data.model.MonthlyBalance
import com.calendarfinance.app.data.repository.AuthRepository
import com.calendarfinance.app.ui.theme.Green500
import com.calendarfinance.app.ui.theme.GreenLight
import com.calendarfinance.app.ui.theme.Red500
import com.calendarfinance.app.ui.theme.RedLight
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: BalanceViewModel = koinViewModel(),
    authRepository: AuthRepository = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userId = authRepository.currentUserId ?: ""

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) viewModel.loadBalance(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Balance financiero") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) {
                item { BalanceCard(uiState.balance) }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { Text("Ultimos meses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(uiState.monthlyBalances) { month ->
                    MonthlyBalanceItem(month)
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: BalanceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Balance general", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("Ingresos", "$ ${"%.2f".format(balance.total_income)}", Green500)
                StatItem("Gastos", "$ ${"%.2f".format(balance.total_expenses)}", Red500)
                StatItem("Balance", "$ ${"%.2f".format(balance.balance)}", if (balance.balance >= 0) Green500 else Red500)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("Movimientos", "${balance.income_count + balance.expense_count}", MaterialTheme.colorScheme.onSurfaceVariant)
                StatItem("Ingresos", "${balance.income_count}", MaterialTheme.colorScheme.onSurfaceVariant)
                StatItem("Gastos", "${balance.expense_count}", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthlyBalanceItem(month: MonthlyBalance) {
    val isPositive = month.balance >= 0
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive) GreenLight.copy(alpha = 0.15f) else RedLight.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                null,
                tint = if (isPositive) Green500 else Red500
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(month.month, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row {
                    Text("+ ${"%.2f".format(month.total_income)}", color = Green500, style = MaterialTheme.typography.bodySmall)
                    Text("  - ${"%.2f".format(month.total_expenses)}", color = Red500, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "$ ${"%.2f".format(month.balance)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (isPositive) Green500 else Red500,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
