package com.fintrack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.domain.MonthSummary
import com.fintrack.app.domain.Occurrence
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import com.fintrack.app.ui.theme.expenseColor
import com.fintrack.app.ui.theme.incomeColor
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale("es")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToFlows: () -> Unit,
    onNavigateToBudget: () -> Unit,
    viewModel: CalendarViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var fabExpanded by remember { mutableStateOf(false) }

    uiState.confirmTarget?.let { target ->
        ConfirmOccurrenceDialog(
            occurrence = target,
            onDismiss = { viewModel.dismissConfirm() },
            onConfirm = { amount -> viewModel.confirmOccurrence(target, amount) }
        )
    }

    uiState.addTarget?.let { target ->
        AddMovementDialog(
            date = target,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.dismissAddMovement() },
            onSave = { date, isIncome, title, category, amount, description ->
                viewModel.saveManualMovement(date, isIncome, title, category, amount, description)
            }
        )
    }

    if (uiState.showPatternDialog) {
        AddPatternDialog(
            initialDate = uiState.selectedDate,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.dismissPatternDialog() },
            onSave = { isIncome, name, description, category, amount, frequency, start, end ->
                viewModel.savePattern(
                    isIncome, name, description, category,
                    amount, frequency, start, end
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Calendario") })
        },
        floatingActionButton = {
            // Igual que Inicio: + flotante que abre Registrar y Recurrente.
            Column(horizontalAlignment = Alignment.End) {
                if (fabExpanded) {
                    FabAction(
                        label = "Registrar en este día",
                        icon = Icons.Default.Create,
                        onClick = {
                            fabExpanded = false
                            viewModel.showAddMovement(uiState.selectedDate)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FabAction(
                        label = "Nuevo recurrente",
                        icon = Icons.Default.EventRepeat,
                        onClick = {
                            fabExpanded = false
                            viewModel.showPatternDialog()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                    Icon(
                        if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        if (fabExpanded) "Cerrar" else "Agregar"
                    )
                }
            }
        },
        bottomBar = {
            FinTrackBottomBar(
                selected = Routes.CALENDAR,
                onDashboard = onNavigateToDashboard,
                onCalendar = { },
                onFlows = onNavigateToFlows,
                onBudget = onNavigateToBudget
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            if (uiState.needsLogin) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Inicia sesión para ver tu calendario")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onNavigateToAuth) { Text("Iniciar sesión") }
                    }
                }
                return@Column
            }

            MonthHeader(
                yearMonth = uiState.yearMonth,
                onPrev = { viewModel.prevMonth() },
                onNext = { viewModel.nextMonth() },
                onRefresh = { viewModel.retry() }
            )

            WeekdayRow()

            MonthGrid(
                yearMonth = uiState.yearMonth,
                days = uiState.days,
                selectedDate = uiState.selectedDate,
                onSelect = { viewModel.selectDate(it) }
            )

            LegendRow()

            Spacer(modifier = Modifier.height(4.dp))

            MonthSummaryCard(summary = uiState.monthSummary)

            Spacer(modifier = Modifier.height(12.dp))

            uiState.error?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.retry() }) { Text("Reintentar") }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (!uiState.isLoading && uiState.error == null && uiState.days.isEmpty()) {
                Text(
                    "Sin movimientos este mes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            DayDetail(
                date = uiState.selectedDate,
                dayData = uiState.days[uiState.selectedDate],
                onConfirm = { viewModel.askConfirm(it) },
                onAdd = { viewModel.showAddMovement(uiState.selectedDate) }
            )
        }
    }
}

@Composable
private fun FabAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, label)
        }
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mes anterior") }
        Text(
            yearMonth.month.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() } +
                " ${yearMonth.year}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Recargar") }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Mes siguiente") }
    }
}

@Composable
private fun WeekdayRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("L", "M", "X", "J", "V", "S", "D").forEach {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    days: Map<LocalDate, DayData>,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val firstDay = yearMonth.atDay(1)
    val offset = (firstDay.dayOfWeek.value - 1) % 7
    val cells = remember(yearMonth) {
        val start = firstDay.minusDays(offset.toLong())
        (0 until 42).map { start.plusDays(it.toLong()) }
    }

    Column {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = date.month == yearMonth.month
                    val isToday = date == LocalDate.now()
                    val dayData = days[date]
                    val hasIncome = dayData?.projected?.any { it.pattern.type == "INCOME" } == true ||
                        dayData?.confirmed?.any { it.type == "ingreso" } == true
                    val hasExpense = dayData?.projected?.any { it.pattern.type == "EXPENSE" } == true ||
                        dayData?.confirmed?.any { it.type == "gasto" } == true
                    val allConfirmed = dayData != null &&
                        dayData.projected.isEmpty() && dayData.confirmed.isNotEmpty()

                    val isSelected = date == selectedDate
                    val incomeDot = incomeColor()
                    val expenseDot = expenseColor()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else if (allConfirmed) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .then(
                                if (isToday && !isSelected) Modifier.border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.tertiary,
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .clickable(onClickLabel = "Ver día ${date.dayOfMonth}") { onSelect(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = if (isToday || isSelected) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row {
                                if (hasIncome) Dot(if (allConfirmed) incomeDot else incomeDot.copy(alpha = 0.45f))
                                if (hasExpense) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Dot(if (allConfirmed) expenseDot else expenseDot.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier.size(8.dp).background(color, CircleShape)
    )
}

@Composable
private fun LegendRow() {
    val incomeDot = incomeColor()
    val expenseDot = expenseColor()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(incomeDot)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Ingreso", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        Dot(expenseDot)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Gasto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Dot(incomeDot.copy(alpha = 0.45f))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Proyectado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.size(10.dp).border(1.5.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Hoy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthSummaryCard(summary: MonthSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Resumen del mes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryLine("Proyectado", summary.projectedIncome, summary.projectedExpense)
            Spacer(modifier = Modifier.height(4.dp))
            SummaryLine("Confirmado", summary.confirmedIncome, summary.confirmedExpense)
        }
    }
}

@Composable
private fun SummaryLine(label: String, income: Double, expense: Double) {
    val incomeTint = incomeColor()
    val expenseTint = expenseColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
        Row {
            Text(
                "+$${String.format("%.2f", income)}",
                style = MaterialTheme.typography.bodySmall,
                color = incomeTint,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "-$${String.format("%.2f", expense)}",
                style = MaterialTheme.typography.bodySmall,
                color = expenseTint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    dayData: DayData?,
    onConfirm: (Occurrence) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${date.dayOfWeek.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAdd) { Text("Agregar") }
    }
    Spacer(modifier = Modifier.height(8.dp))

    if (dayData == null || (dayData.projected.isEmpty() && dayData.confirmed.isEmpty())) {
        Text("Sin movimientos este día", style = MaterialTheme.typography.bodyMedium)
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dayData.projected, key = { "p_${it.pattern.id}" }) { occ ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(occ.pattern.name, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    "Proyectado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "$${String.format("%.2f", occ.amount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { onConfirm(occ) }) { Text("Confirmar") }
                }
            }
        }
        items(dayData.confirmed, key = { "c_${it.id}" }) { mov ->
            ConfirmedRow(mov)
        }
    }
}

@Composable
private fun ConfirmedRow(mov: MovementRow) {
    val isIncome = mov.type == "ingreso"
    val amountTint = if (isIncome) incomeColor() else expenseColor()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dp).background(amountTint, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mov.title.ifEmpty { mov.category }, fontWeight = FontWeight.Medium)
                Text(
                    mov.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${if (isIncome) "+" else "-"}$${String.format("%.2f", mov.confirmed_amount)}",
                style = MaterialTheme.typography.titleSmall,
                color = amountTint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConfirmOccurrenceDialog(
    occurrence: Occurrence,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amount by remember(occurrence) { mutableStateOf(String.format("%.2f", occurrence.amount)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar: ${occurrence.pattern.name}") },
        text = {
            Column {
                Text("Monto esperado: $${String.format("%.2f", occurrence.amount)}")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto real") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toDoubleOrNull() ?: return@TextButton
                if (value > 0) onConfirm(value)
            }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
