package com.fintrack.app.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.domain.Occurrence
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
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
    viewModel: CalendarViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    uiState.confirmTarget?.let { target ->
        ConfirmOccurrenceDialog(
            occurrence = target,
            onDismiss = { viewModel.dismissConfirm() },
            onConfirm = { amount -> viewModel.confirmOccurrence(target, amount) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Calendario") })
        },
        bottomBar = {
            FinTrackBottomBar(
                selected = Routes.CALENDAR,
                onDashboard = onNavigateToDashboard,
                onCalendar = { }
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
                onNext = { viewModel.nextMonth() }
            )

            WeekdayRow()

            MonthGrid(
                yearMonth = uiState.yearMonth,
                days = uiState.days,
                selectedDate = uiState.selectedDate,
                onSelect = { viewModel.selectDate(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            DayDetail(
                date = uiState.selectedDate,
                dayData = uiState.days[uiState.selectedDate],
                onConfirm = { viewModel.askConfirm(it) }
            )
        }
    }
}

@Composable
private fun MonthHeader(yearMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
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
            fontWeight = FontWeight.Bold
        )
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
                    val dayData = days[date]
                    val hasIncome = dayData?.projected?.any { it.pattern.type == "INCOME" } == true ||
                        dayData?.confirmed?.any { it.type == "ingreso" } == true
                    val hasExpense = dayData?.projected?.any { it.pattern.type == "EXPENSE" } == true ||
                        dayData?.confirmed?.any { it.type == "gasto" } == true
                    val allConfirmed = dayData != null &&
                        dayData.projected.isEmpty() && dayData.confirmed.isNotEmpty()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .background(
                                if (date == selectedDate) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelect(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = if (inMonth) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline
                            )
                            Row {
                                if (hasIncome) Dot(if (allConfirmed) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.5f))
                                if (hasExpense) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Dot(if (allConfirmed) Color(0xFFF44336) else Color(0xFFF44336).copy(alpha = 0.5f))
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
        modifier = Modifier.size(6.dp).background(color, CircleShape)
    )
}

@Composable
private fun DayDetail(
    date: LocalDate,
    dayData: DayData?,
    onConfirm: (Occurrence) -> Unit
) {
    Text(
        "${date.dayOfWeek.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (dayData == null || (dayData.projected.isEmpty() && dayData.confirmed.isEmpty())) {
        Text("Sin movimientos este día", style = MaterialTheme.typography.bodyMedium)
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dayData.projected, key = { "p_${it.pattern.id}" }) { occ ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(occ.pattern.name, fontWeight = FontWeight.Medium)
                        Text(
                            "Proyectado · $${String.format("%.2f", occ.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                color = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336),
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
