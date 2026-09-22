package com.fintrack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
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
import com.fintrack.app.domain.TxKind
import com.fintrack.app.domain.kind
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import com.fintrack.app.ui.common.PullRefreshLayout
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
    onNavigateToAccounts: () -> Unit = {},
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
            cards = uiState.cards,
            wallets = uiState.wallets,
            onDismiss = { viewModel.dismissAddMovement() },
            onSave = { date, isIncome, title, category, amount, description, walletId, cardId ->
                viewModel.saveManualMovement(
                    date, isIncome, title, category, amount, description, walletId, cardId
                )
            }
        )
    }

    if (uiState.showPatternDialog) {
        AddPatternDialog(
            initialDate = uiState.selectedDate,
            existing = uiState.patternEditTarget,
            existingLink = uiState.patternEditTarget?.let { uiState.links[it.id] },
            cards = uiState.cards,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.dismissPatternDialog() },
            onSave = { isIncome, name, description, category, amount, frequency, start, end, kind, cardId ->
                viewModel.savePattern(
                    isIncome, name, description, category,
                    amount, frequency, start, end, kind, cardId
                )
            },
            onDelete = { viewModel.deactivatePattern() }
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
                onBudget = onNavigateToBudget,
                onAccounts = onNavigateToAccounts
            )
        }
    ) { padding ->
        // Un solo LazyColumn (como Inicio/Flujos/Presupuesto): antes era Column
        // fija + LazyColumn anidada y el scroll fallaba.
        val listState = rememberLazyListState()
        PullRefreshLayout(
            onRefresh = { viewModel.retry() },
            isLoading = uiState.isLoading,
            atTopProvider = {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            },
            modifier = Modifier.padding(padding)
        ) { pullModifier ->
        LazyColumn(
            state = listState,
            modifier = pullModifier.padding(horizontal = 16.dp)
        ) {
            if (uiState.needsLogin) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Inicia sesión para ver tu calendario")
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onNavigateToAuth) { Text("Iniciar sesión") }
                        }
                    }
                }
            } else {
                item {
                    MonthHeader(
                        yearMonth = uiState.yearMonth,
                        onPrev = { viewModel.prevMonth() },
                        onNext = { viewModel.nextMonth() },
                        onRefresh = { viewModel.retry() }
                    )
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                item { BalanceCard(balance = uiState.balance) }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item { WeekdayRow() }

                item {
                    MonthGrid(
                        yearMonth = uiState.yearMonth,
                        days = uiState.days,
                        markers = uiState.markers,
                        selectedDate = uiState.selectedDate,
                        onSelect = { viewModel.selectDate(it) }
                    )
                }

                item { LegendRow() }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item { MonthSummaryCard(summary = uiState.monthSummary) }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            uiState.error?.let {
                item {
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
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!uiState.isLoading && uiState.error == null && uiState.days.isEmpty()) {
                item {
                    Text(
                        "Sin movimientos este mes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            item {
                DayDetailHeader(
                    date = uiState.selectedDate,
                    onAdd = { viewModel.showAddMovement(uiState.selectedDate) }
                )
            }

            val dayData = uiState.days[uiState.selectedDate]
            val dayMarkers = uiState.markers[uiState.selectedDate].orEmpty()
            val hasMovements = dayData != null &&
                (dayData.projected.isNotEmpty() || dayData.confirmed.isNotEmpty() || dayData.quick.isNotEmpty())
            if (!hasMovements && dayMarkers.isEmpty()) {
                item {
                    Text("Sin movimientos este día", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                if (dayMarkers.isNotEmpty()) {
                    items(dayMarkers, key = { "m_$it" }) { marker ->
                        MarkerRow(marker)
                    }
                }
                dayData?.let { data ->
                    val cardNames = uiState.cards.associate { it.id to it.displayName }
                    items(data.projected, key = { "p_${it.pattern.id}" }) { occ ->
                        val link = uiState.links[occ.pattern.id]
                        ProjectedCard(
                            occurrence = occ,
                            onConfirm = { viewModel.askConfirm(occ) },
                            onEdit = { viewModel.showPatternEdit(occ.pattern) },
                            linkKind = link?.kind,
                            cardName = link?.cardId?.let { cardNames[it] }
                        )
                    }
                    items(data.confirmed, key = { "c_${it.id}" }) { mov ->
                        ConfirmedRow(mov)
                    }
                    items(data.quick, key = { "q_${it.id}" }) { tx ->
                        QuickRow(tx)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
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
    markers: Map<LocalDate, List<String>>,
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
                    val hasIncome = dayData?.projected?.any { it.pattern.kind?.isIncome == true } == true ||
                        dayData?.confirmed?.any { it.kind?.isIncome == true } == true ||
                        dayData?.quick?.any { it.kind?.isIncome == true } == true
                    val hasExpense = dayData?.projected?.any { it.pattern.kind == TxKind.EXPENSE } == true ||
                        dayData?.confirmed?.any { it.kind == TxKind.EXPENSE } == true ||
                        dayData?.quick?.any { it.kind == TxKind.EXPENSE } == true
                    val allConfirmed = dayData != null &&
                        dayData.projected.isEmpty() && dayData.confirmed.isNotEmpty()
                    val hasMarker = markers[date]?.isNotEmpty() == true

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
                                if (hasMarker) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Dot(MaterialTheme.colorScheme.tertiary)
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
        Spacer(modifier = Modifier.width(12.dp))
        Dot(MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Aviso", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MarkerRow(marker: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Text(
            "🔔 $marker",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
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
private fun BalanceCard(balance: CalendarBalance) {
    val incomeTint = incomeColor()
    val expenseTint = expenseColor()
    val balanceTint = if (balance.balance >= 0) incomeTint else expenseTint
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Balance actual",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${if (balance.balance >= 0) "+" else "-"}$${String.format("%.2f", kotlin.math.abs(balance.balance))}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = balanceTint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Ingresos $${String.format("%.2f", balance.income)} · " +
                    "Gastos $${String.format("%.2f", balance.expense)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                "Incluye confirmados y registros de Inicio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
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
private fun DayDetailHeader(
    date: LocalDate,
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
}

@Composable
private fun ProjectedCard(
    occurrence: Occurrence,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    linkKind: String? = null,
    cardName: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(occurrence.pattern.name, fontWeight = FontWeight.Medium)
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
                        "$${String.format("%.2f", occurrence.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                linkLabel(linkKind, cardName)?.let { label ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Editar recurrente")
            }
            TextButton(onClick = onConfirm) { Text("Confirmar") }
        }
    }
}

private fun linkLabel(linkKind: String?, cardName: String?): String? = when (linkKind) {
    com.fintrack.app.data.PatternLinkKind.CREDIT ->
        "💳 Tarjeta" + (cardName?.let { " $it" } ?: "")
    com.fintrack.app.data.PatternLinkKind.SERVICE -> "🧾 Servicio"
    com.fintrack.app.data.PatternLinkKind.SUBSCRIPTION -> "🔁 Suscripción"
    else -> null
}

@Composable
private fun ConfirmedRow(mov: MovementRow) {
    val isIncome = mov.kind?.isIncome == true
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
private fun QuickRow(tx: com.fintrack.app.data.model.TransactionEntity) {
    val isIncome = tx.kind?.isIncome == true
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
                Text(
                    tx.description.ifEmpty { tx.merchant ?: tx.category },
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Inicio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        tx.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "${if (isIncome) "+" else "-"}$${String.format("%.2f", tx.amount)}",
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
