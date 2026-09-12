package com.fintrack.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.domain.CategoryBudget
import com.fintrack.app.domain.GoalVerdict
import com.fintrack.app.domain.MonthProjection
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import org.koin.androidx.compose.koinViewModel
import java.time.format.TextStyle
import java.util.Locale

private val Spanish = Locale("es")

private fun formatMoney(value: Double): String =
    "%,.0f".format(value).replace(',', '.')

private fun MonthProjection.monthLabel(): String {
    val name = month.month.getDisplayName(TextStyle.FULL, Spanish)
        .replaceFirstChar { it.uppercase() }
    return "$name ${month.year}"
}

private fun GoalVerdict.label(): String = when (this) {
    GoalVerdict.FACTIBLE -> "Factible"
    GoalVerdict.AJUSTADO -> "Ajustado"
    GoalVerdict.INVIABLE -> "Inviable"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToFlows: () -> Unit = {},
    viewModel: BudgetViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presupuesto") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            FinTrackBottomBar(
                selected = Routes.BUDGET,
                onDashboard = onNavigateToDashboard,
                onCalendar = onNavigateToCalendar,
                onFlows = onNavigateToFlows,
                onBudget = { }
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
                Text("Inicia sesión para ver tu presupuesto.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNavigateToAuth) {
                    Text("Ir a iniciar sesión")
                }
            }
            return@Scaffold
        }

        if (uiState.isLoading && uiState.shortTerm == null) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
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

            item {
                Text("Corto plazo: este mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                val report = uiState.shortTerm
                if (report == null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("Sin datos este mes.", modifier = Modifier.padding(12.dp))
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ingreso del mes: ${formatMoney(report.monthIncome)}")
                            Text("Ahorro estimado: ${formatMoney(report.estimatedSavings)}")
                            report.items.forEach { item ->
                                CategoryBudgetRow(item = item)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Mediano plazo: próximos 3 meses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (uiState.mediumTerm.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("Sin proyección disponible.", modifier = Modifier.padding(12.dp))
                    }
                }
            } else {
                uiState.mediumTerm.forEach { projection ->
                    item {
                        MonthProjectionCard(projection = projection)
                    }
                }
            }

            item {
                Text("Largo plazo: meta de ahorro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                var amountText by remember(uiState.goalAmount) { mutableStateOf(formatMoney(uiState.goalAmount)) }
                var monthsText by remember(uiState.goalMonths) { mutableStateOf(uiState.goalMonths.toString()) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw ->
                                amountText = raw
                                raw.replace(".", "").replace(",", "").toDoubleOrNull()?.let {
                                    viewModel.updateGoal(it, monthsText.toIntOrNull() ?: uiState.goalMonths)
                                }
                            },
                            label = { Text("Monto objetivo") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = monthsText,
                            onValueChange = { raw ->
                                monthsText = raw.filter { it.isDigit() }
                                viewModel.updateGoal(
                                    amountText.replace(".", "").replace(",", "").toDoubleOrNull()
                                        ?: uiState.goalAmount,
                                    monthsText.toIntOrNull() ?: 0
                                )
                            },
                            label = { Text("Meses de plazo") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        val evaluation = uiState.goalEvaluation
                        if (evaluation == null) {
                            Text("Define tu meta para ver el veredicto.")
                        } else {
                            Text(
                                "Ahorro mensual requerido: ${formatMoney(evaluation.requiredMonthly)}",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Veredicto: ${evaluation.verdict.label()}", fontWeight = FontWeight.Bold)
                            Text(evaluation.explanation)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CategoryBudgetRow(item: CategoryBudget) {
    val progress = item.usageRatio.toFloat().coerceIn(0f, 1f)
    val barColor = when {
        item.overCap -> MaterialTheme.colorScheme.error
        item.nearLimit -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.category, fontWeight = FontWeight.SemiBold)
            Text("${formatMoney(item.spent)} / ${formatMoney(item.cap)}")
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = barColor
        )
        when {
            item.overCap -> Text(
                "Tope superado",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            item.nearLimit -> Text(
                "Cerca del tope: superaste el 80%",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MonthProjectionCard(projection: MonthProjection) {
    val surplusColor = if (projection.surplus >= 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(projection.monthLabel(), fontWeight = FontWeight.SemiBold)
            Text("Ingresos estimados: ${formatMoney(projection.projectedIncome)}")
            Text("Gastos estimados: ${formatMoney(projection.projectedExpense)}")
            Text(
                "Balance estimado: ${formatMoney(projection.surplus)}",
                color = surplusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
