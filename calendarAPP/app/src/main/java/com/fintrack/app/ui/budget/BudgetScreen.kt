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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.fintrack.app.domain.CashPlan
import com.fintrack.app.domain.CategoryBudget
import com.fintrack.app.domain.CreditPlan
import com.fintrack.app.domain.GoalVerdict
import com.fintrack.app.domain.MonthProjection
import com.fintrack.app.domain.SavingsGoal
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import com.fintrack.app.ui.theme.expenseColor
import com.fintrack.app.ui.theme.incomeColor
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

    uiState.info?.let { info ->
        LaunchedEffect(info) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearInfo()
        }
    }

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
                SectionHeader(title = "Corto plazo", subtitle = "Este mes")
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
                SectionHeader(title = "Topes del mes", subtitle = "Límites por categoría")
            }
            item {
                CapEditorCard(
                    items = uiState.shortTerm?.items ?: emptyList(),
                    customCaps = uiState.customCaps,
                    onSetCap = { category, cap -> viewModel.setCap(category, cap) }
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

            item {
                SectionHeader(title = "Mediano plazo", subtitle = "Próximos 3 meses")
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
                SectionHeader(title = "Largo plazo", subtitle = "Meta de ahorro")
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
                            VerdictBadge(verdict = evaluation.verdict)
                            Text(evaluation.explanation)
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Objetivos", subtitle = "Contado o crédito")
            }
            item {
                var goalName by remember { mutableStateOf("") }
                var goalPriceText by remember { mutableStateOf("") }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Agrega un objetivo, por ejemplo comprar un carro.")
                        OutlinedTextField(
                            value = goalName,
                            onValueChange = { goalName = it },
                            label = { Text("Nombre del objetivo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = goalPriceText,
                            onValueChange = { goalPriceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text("Precio objetivo") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val price = goalPriceText.replace(".", "").replace(",", "")
                                    .toDoubleOrNull() ?: 0.0
                                viewModel.addGoal(goalName, price)
                                goalName = ""
                                goalPriceText = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Agregar objetivo")
                        }
                    }
                }
            }
            if (uiState.goals.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Aun no tienes objetivos. Agrega uno para comparar pagarlo de contado o con credito.",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                uiState.goals.forEach { goal ->
                    item {
                        val cash = viewModel.cashPlanFor(goal)
                        val credit = viewModel.creditPlanFor(goal)
                        val selected = goal.id == uiState.selectedGoalId
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selected) {
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                CardDefaults.cardColors()
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(goal.name, fontWeight = FontWeight.SemiBold)
                                    Text(formatMoney(goal.price), fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "Contado: " + if (cash.monthsNeeded == null) {
                                        "inviable por ahora"
                                    } else {
                                        "${cash.monthsNeeded} meses"
                                    } + " | Credito: ${formatMoney(credit.monthlyPayment)} al mes"
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.selectGoal(goal.id) }) {
                                        Text("Ver detalle")
                                    }
                                    Button(onClick = { viewModel.removeGoal(goal.id) }) {
                                        Text("Eliminar")
                                    }
                                }
                            }
                        }
                    }
                }
                uiState.goals.firstOrNull { it.id == uiState.selectedGoalId }?.let { goal ->
                    item {
                        GoalDetailCard(
                            goal = goal,
                            cashMonths = viewModel.cashPlanFor(goal),
                            credit = viewModel.creditPlanFor(goal),
                            onUpdate = { viewModel.updateGoal(it) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CapEditorCard(
    items: List<CategoryBudget>,
    customCaps: Map<String, Double>,
    onSetCap: (String, Double) -> Unit
) {
    var editing by remember { mutableStateOf<String?>(null) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Toca Definir para poner tu propio tope; sin tope se usa el automático según tu ingreso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (items.isEmpty()) {
                Text("Sin movimientos este mes.")
            } else {
                items.forEach { item ->
                    val custom = customCaps[item.category]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(item.category, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (custom != null) "Tope propio: ${formatMoney(custom)}"
                                else "Tope auto: ${formatMoney(item.cap)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { editing = item.category }) {
                            Text("Definir")
                        }
                    }
                }
            }
        }
    }
    editing?.let { category ->
        CapEditDialog(
            category = category,
            currentCap = customCaps[category],
            onDismiss = { editing = null },
            onSave = { cap ->
                onSetCap(category, cap)
                editing = null
            }
        )
    }
}

@Composable
private fun CapEditDialog(
    category: String,
    currentCap: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var capText by remember(category) {
        mutableStateOf(currentCap?.let { formatMoney(it) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tope de $category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = capText,
                    onValueChange = { raw ->
                        capText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Tope mensual") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (currentCap != null) {
                    Text(
                        "Tope actual: ${formatMoney(currentCap)} (vacía y guarda para volver al automático).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cap = capText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
                onSave(cap)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SubscriptionCard(
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
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            subtitle.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VerdictBadge(verdict: GoalVerdict) {
    val container = when (verdict) {
        GoalVerdict.FACTIBLE -> MaterialTheme.colorScheme.primaryContainer
        GoalVerdict.AJUSTADO -> MaterialTheme.colorScheme.tertiaryContainer
        GoalVerdict.INVIABLE -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (verdict) {
        GoalVerdict.FACTIBLE -> MaterialTheme.colorScheme.onPrimaryContainer
        GoalVerdict.AJUSTADO -> MaterialTheme.colorScheme.onTertiaryContainer
        GoalVerdict.INVIABLE -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            "Veredicto: ${verdict.label()}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
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
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
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
private fun GoalDetailCard(
    goal: SavingsGoal,
    cashMonths: CashPlan,
    credit: CreditPlan,
    onUpdate: (SavingsGoal) -> Unit
) {
    var nameText by remember(goal.id) { mutableStateOf(goal.name) }
    var priceText by remember(goal.id) { mutableStateOf(formatMoney(goal.price)) }
    var downText by remember(goal.id) { mutableStateOf(goal.downPercent.toString()) }
    var rateText by remember(goal.id) { mutableStateOf(goal.annualRatePercent.toString()) }
    var termText by remember(goal.id) { mutableStateOf(goal.termMonths.toString()) }
    val extraCost = credit.totalCost - goal.price
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Detalle: ${goal.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = nameText,
                onValueChange = { raw ->
                    nameText = raw
                    onUpdate(goal.copy(name = raw.trim().ifBlank { goal.name }))
                },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { raw ->
                    priceText = raw
                    raw.replace(".", "").replace(",", "").toDoubleOrNull()?.let {
                        onUpdate(goal.copy(price = if (it < 0.0) 0.0 else it))
                    }
                },
                label = { Text("Precio") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = downText,
                onValueChange = { raw ->
                    downText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    downText.replace(",", ".").toDoubleOrNull()?.let {
                        onUpdate(goal.copy(downPercent = it.coerceIn(0.0, 100.0), downAmount = null))
                    }
                },
                label = { Text("Enganche (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = rateText,
                onValueChange = { raw ->
                    rateText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    rateText.replace(",", ".").toDoubleOrNull()?.let {
                        onUpdate(goal.copy(annualRatePercent = if (it < 0.0) 0.0 else it))
                    }
                },
                label = { Text("Tasa anual (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = termText,
                onValueChange = { raw ->
                    termText = raw.filter { it.isDigit() }
                    termText.toIntOrNull()?.let {
                        if (it > 0) onUpdate(goal.copy(termMonths = it))
                    }
                },
                label = { Text("Plazo (meses)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("Opcion contado", fontWeight = FontWeight.Bold)
            if (cashMonths.monthsNeeded == null) {
                VerdictBadge(verdict = GoalVerdict.INVIABLE)
            } else {
                Text("Meses para lograrlo: ${cashMonths.monthsNeeded}", fontWeight = FontWeight.Bold)
                VerdictBadge(verdict = cashMonths.verdict)
            }
            Text(cashMonths.explanation)
            Text("Opcion credito", fontWeight = FontWeight.Bold)
            Text("Enganche: ${formatMoney(credit.downPayment)}")
            Text("Monto a financiar: ${formatMoney(credit.financedAmount)}")
            Text("Mensualidad: ${formatMoney(credit.monthlyPayment)}", fontWeight = FontWeight.Bold)
            Text("Intereses totales: ${formatMoney(credit.totalInterest)}")
            Text("Costo total con credito: ${formatMoney(credit.totalCost)}")
            VerdictBadge(verdict = credit.verdict)
            Text(credit.explanation)
            Text(
                "Comparador: de contado pagas ${formatMoney(goal.price)} y con credito " +
                    "${formatMoney(credit.totalCost)}. El credito te cuesta ${formatMoney(extraCost)} mas.",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MonthProjectionCard(projection: MonthProjection) {
    val surplusColor = if (projection.surplus >= 0) incomeColor() else expenseColor()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(projection.monthLabel(), fontWeight = FontWeight.SemiBold)
            Text(
                "Ingresos estimados: ${formatMoney(projection.projectedIncome)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Gastos estimados: ${formatMoney(projection.projectedExpense)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Balance estimado: ${formatMoney(projection.surplus)}",
                color = surplusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
