package com.calendarfinance.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarfinance.app.data.model.CalendarDay
import com.calendarfinance.app.data.model.Movement
import com.calendarfinance.app.data.repository.AuthRepository
import com.calendarfinance.app.ui.ota.OtaUpdateDialog
import com.calendarfinance.app.ui.ota.OtaUpdateViewModel
import com.calendarfinance.app.ui.theme.Green500
import com.calendarfinance.app.ui.theme.Red500
import com.calendarfinance.app.ui.theme.Blue500
import com.calendarfinance.app.ui.theme.GreenLight
import com.calendarfinance.app.ui.theme.RedLight
import com.calendarfinance.app.ui.theme.Orange500
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToMovement: (String) -> Unit,
    onNavigateToPattern: (String) -> Unit,
    onNavigateToBalance: () -> Unit,
    onLogout: () -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
    otaViewModel: OtaUpdateViewModel = koinViewModel(),
    authRepository: AuthRepository = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val otaState by otaViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userId = authRepository.currentUserId ?: ""
    var selectedDay by remember { mutableStateOf<CalendarDay?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            viewModel.loadMonth(userId, YearMonth.now())
        }
    }

    // Auto-check OTA on start
    LaunchedEffect(Unit) {
        otaViewModel.autoCheck(context)
    }

    // Show OTA dialog when update available
    if (otaState.updateInfo != null) {
        OtaUpdateDialog(viewModel = otaViewModel, onDismiss = {})
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CalendarFinance") },
                actions = {
                    IconButton(onClick = onNavigateToBalance) {
                        Icon(Icons.Default.AccountBalanceWallet, "Balance")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Verificar actualizacion") },
                            leadingIcon = { Icon(Icons.Default.SystemUpdate, null) },
                            onClick = { showMenu = false; otaViewModel.manualCheck(context) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Nuevo patron de ingreso") },
                            leadingIcon = { Icon(Icons.Default.AddCircle, null, tint = Green500) },
                            onClick = { showMenu = false; onNavigateToPattern("income") }
                        )
                        DropdownMenuItem(
                            text = { Text("Nuevo patron de gasto") },
                            leadingIcon = { Icon(Icons.Default.RemoveCircle, null, tint = Red500) },
                            onClick = { showMenu = false; onNavigateToPattern("expense") }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Cerrar sesion") },
                            leadingIcon = { Icon(Icons.Default.Logout, null) },
                            onClick = { showMenu = false; coroutineScope.launch { authRepository.logout(); onLogout() } }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Month navigation
            MonthHeader(
                yearMonth = uiState.currentYearMonth,
                onPrevious = { viewModel.navigateToPreviousMonth(userId) },
                onNext = { viewModel.navigateToNextMonth(userId) }
            )

            // Summary bar
            MonthSummaryBar(
                income = uiState.monthTotalIncome,
                expenses = uiState.monthTotalExpenses
            )

            // Day of week headers
            WeekDayHeader()

            // Calendar grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .padding(horizontal = 4.dp)
            ) {
                items(uiState.calendarDays) { day ->
                    CalendarDayCell(
                        day = day,
                        onClick = { selectedDay = day }
                    )
                }
            }

            // Patterns section
            PatternsSummary(
                incomePatterns = uiState.incomePatterns,
                expensePatterns = uiState.expensePatterns,
                onAddIncome = { onNavigateToPattern("income") },
                onAddExpense = { onNavigateToPattern("expense") }
            )

            // Selected day movements
            if (selectedDay != null) {
                DayMovementList(
                    selectedDay = selectedDay!!,
                    onAddMovement = {
                        onNavigateToMovement(selectedDay!!.date)
                        selectedDay = null
                    },
                    onDismiss = { selectedDay = null }
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(yearMonth: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Anterior")
        }
        Text(
            text = yearMonth.month.getDisplayName(TextStyle.FULL, Locale("es")) + " " + yearMonth.year,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Siguiente")
        }
    }
}

@Composable
private fun MonthSummaryBar(income: Double, expenses: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ingresos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$${"%.2f".format(income)}", color = Green500, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gastos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$${"%.2f".format(expenses)}", color = Red500, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$${"%.2f".format(income - expenses)}", color = if (income >= expenses) Green500 else Red500, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeekDayHeader() {
    val days = listOf("Dom", "Lun", "Mar", "Mie", "Jue", "Vie", "Sab")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        days.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CalendarDayCell(day: CalendarDay, onClick: () -> Unit) {
    val bgColor = when {
        !day.isCurrentMonth -> Color.Transparent
        day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        day.incomeTotal > 0 && day.expenseTotal > 0 -> Orange500.copy(alpha = 0.1f)
        day.incomeTotal > 0 -> GreenLight.copy(alpha = 0.3f)
        day.expenseTotal > 0 -> RedLight.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(enabled = day.isCurrentMonth) { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.dayNumber.toString(),
            fontSize = 12.sp,
            color = if (day.isToday) MaterialTheme.colorScheme.primary else
                     if (!day.isCurrentMonth) MaterialTheme.colorScheme.outline
                     else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
        )
        if (day.incomeTotal > 0) {
            Box(modifier = Modifier.size(4.dp).background(Green500, CircleShape))
        } else if (day.expenseTotal > 0) {
            Box(modifier = Modifier.size(4.dp).background(Red500, CircleShape))
        }
    }
}

@Composable
private fun PatternsSummary(
    incomePatterns: List<com.calendarfinance.app.data.model.IncomePattern>,
    expensePatterns: List<com.calendarfinance.app.data.model.ExpensePattern>,
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ingresos recurrentes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onAddIncome) { Text("+ Agregar") }
        }
        incomePatterns.take(3).forEach { pattern ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(pattern.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("$ ${"%.2f".format(pattern.base_amount)}", color = Green500, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gastos recurrentes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onAddExpense) { Text("+ Agregar") }
        }
        expensePatterns.take(3).forEach { pattern ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(pattern.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("$ ${"%.2f".format(pattern.base_amount)}", color = Red500, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DayMovementList(selectedDay: CalendarDay, onAddMovement: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dia ${selectedDay.dayNumber}", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = onAddMovement) { Icon(Icons.Default.Add, null); Text("Movimiento") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
            }

            if (selectedDay.movements.isEmpty()) {
                Text(
                    "Sin movimientos este dia",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(selectedDay.movements) { movement ->
                        MovementItem(movement)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementItem(movement: Movement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (movement.type == "ingreso") GreenLight.copy(alpha = 0.2f) else RedLight.copy(alpha = 0.2f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(movement.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (movement.category.isNotEmpty()) {
                Text(movement.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${if (movement.type == "ingreso") "+" else "-"} $${"%.2f".format(movement.confirmed_amount)}",
            color = if (movement.type == "ingreso") Green500 else Red500,
            fontWeight = FontWeight.Bold
        )
    }
}
