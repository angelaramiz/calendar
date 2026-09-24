package com.fintrack.app.ui.flows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fintrack.app.ui.navigation.FinTrackBottomBar
import com.fintrack.app.ui.navigation.Routes
import com.fintrack.app.domain.ConditionNode
import com.fintrack.app.domain.ConditionOperator
import com.fintrack.app.domain.EnvelopeNode
import com.fintrack.app.domain.FlowEngine
import com.fintrack.app.domain.FlowNode
import com.fintrack.app.domain.FormulaNode
import com.fintrack.app.domain.IncomeNode
import com.fintrack.app.domain.IncomeSource
import com.fintrack.app.domain.Split
import org.koin.androidx.compose.koinViewModel

internal val incomeSourceLabels = mapOf(
    "FIXED" to "Monto fijo",
    "MONTH" to "Ingresos del mes",
    "CATEGORY" to "Total por categoria",
    "RECURRING" to "Recurrentes del mes"
)

internal val operatorLabels = mapOf(
    ConditionOperator.GREATER_THAN to "Mayor que (>)",
    ConditionOperator.LESS_THAN to "Menor que (<)",
    ConditionOperator.EQUALS to "Igual a (=)"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowsScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
    viewModel: FlowsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddMenu by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selectedNode = selectedId?.let { FlowEngine.findNode(uiState.nodes, it) }
    // Si el nodo seleccionado se eliminó, se cierra el editor.
    if (selectedId != null && selectedNode == null) {
        LaunchedEffect(selectedId) { selectedId = null }
    }

    uiState.error?.let { err ->
        LaunchedEffect(err) {
            snackbarHostState.showSnackbar(err.take(250))
            viewModel.clearError()
        }
    }
    uiState.savedMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSavedMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flujos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            FinTrackBottomBar(
                selected = Routes.FLOWS,
                onDashboard = onNavigateToDashboard,
                onCalendar = onNavigateToCalendar,
                onFlows = { },
                onBudget = onNavigateToBudget,
                onAccounts = onNavigateToAccounts
            )
        }
    ) { padding ->
        if (uiState.needsLogin) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Inicia sesion para usar tus flujos")
                    uiState.error?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onNavigateToAuth) { Text("Iniciar sesion") }
                    TextButton(onClick = { viewModel.loadFlow() }) { Text("Reintentar") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                OutlinedTextField(
                    value = uiState.flowName,
                    onValueChange = viewModel::setFlowName,
                    label = { Text("Nombre del flujo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            item {
                Text(
                    "Toca un nodo para configurarlo. Al ejecutar, el camino " +
                        "recorrido se ilumina con los montos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowCanvas(
                    nodes = uiState.nodes,
                    trace = uiState.trace,
                    selectedId = selectedId,
                    onSelect = { selectedId = it }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = showAddMenu,
                    onExpandedChange = { showAddMenu = it }
                ) {
                    OutlinedButton(
                        onClick = { showAddMenu = true },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    ) {
                        Icon(Icons.Default.Add, "Agregar nodo")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agregar nodo")
                    }
                    ExposedDropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ingreso (origen)") },
                            onClick = { viewModel.addNode(FlowNodeKind.INCOME); showAddMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Reparto (porcentaje o fijo)") },
                            onClick = { viewModel.addNode(FlowNodeKind.FORMULA); showAddMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Condicion (si / si no)") },
                            onClick = { viewModel.addNode(FlowNodeKind.CONDITION); showAddMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sobre (asignacion final)") },
                            onClick = { viewModel.addNode(FlowNodeKind.ENVELOPE); showAddMenu = false }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                if (FlowEngine.hasMixedIncomeBases(uiState.nodes)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Mezclas base proyectada (Recurrentes) con base real o simulada: " +
                                "el mismo dinero puede contarse dos veces. " +
                                "Usa una sola base por flujo, salvo un ingreso extra aparte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = { viewModel.runFlow() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isRunning && uiState.nodes.isNotEmpty()
                ) {
                    if (uiState.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, "Ejecutar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ejecutar flujo")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.saveFlow() }, modifier = Modifier.weight(1f)) {
                        Text("Guardar")
                    }
                    OutlinedButton(onClick = { viewModel.resetToSample() }, modifier = Modifier.weight(1f)) {
                        Text("Restablecer ejemplo")
                    }
                }
            }

            if (uiState.allocations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Resultado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${uiState.allocations.size} sobres",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                itemsIndexed(uiState.allocations) { _, allocation ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Savings, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(allocation.label, fontWeight = FontWeight.Bold)
                                allocation.category?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                "$${String.format("%.2f", allocation.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Total asignado",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$${String.format("%.2f", uiState.totalAssigned)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }
            } else {
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Editor del nodo tocado en el lienzo.
    selectedNode?.let { node ->
        val sheetTitle = when (node) {
            is IncomeNode -> "Ingreso"
            is FormulaNode -> "Reparto"
            is ConditionNode -> "Condición"
            is EnvelopeNode -> "Sobre"
        }
        ModalBottomSheet(onDismissRequest = { selectedId = null }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Configurar: $sheetTitle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        viewModel.removeNode(node.id)
                        selectedId = null
                    }) {
                        Icon(Icons.Default.Delete, "Eliminar nodo")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                when (node) {
                    is IncomeNode -> IncomeEditor(node, viewModel)
                    is FormulaNode -> FormulaEditor(node, viewModel)
                    is ConditionNode -> ConditionEditor(node, viewModel)
                    is EnvelopeNode -> EnvelopeFields(
                        label = node.label,
                        category = node.category ?: "",
                        onLabel = { viewModel.updateEnvelope(node.id, it, node.category) },
                        onCategory = { viewModel.updateEnvelope(node.id, node.label, it) }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IncomeEditor(node: IncomeNode, viewModel: FlowsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedKey = when (node.source) {
        is IncomeSource.Fixed -> "FIXED"
        is IncomeSource.MonthIncomes -> "MONTH"
        is IncomeSource.CategoryTotal -> "CATEGORY"
        is IncomeSource.RecurringMonth -> "RECURRING"
    }
    OutlinedTextField(
        value = node.label,
        onValueChange = { viewModel.updateIncome(node.id, it, node.source) },
        label = { Text("Etiqueta") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = incomeSourceLabels[selectedKey] ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Origen") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            incomeSourceLabels.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        val source: IncomeSource = when (key) {
                            "MONTH" -> IncomeSource.MonthIncomes
                            "RECURRING" -> IncomeSource.RecurringMonth
                            "CATEGORY" -> {
                                val current = (node.source as? IncomeSource.CategoryTotal)?.category ?: ""
                                IncomeSource.CategoryTotal(current)
                            }
                            else -> {
                                val current = (node.source as? IncomeSource.Fixed)?.amount ?: 1000.0
                                IncomeSource.Fixed(current)
                            }
                        }
                        viewModel.updateIncome(node.id, node.label, source)
                        expanded = false
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    when (val source = node.source) {
        is IncomeSource.Fixed -> NumberField(
            label = "Monto",
            value = source.amount,
            onValue = { viewModel.updateIncome(node.id, node.label, IncomeSource.Fixed(it)) }
        )
        is IncomeSource.MonthIncomes -> Text(
            "Usa la suma de tus ingresos del mes actual.",
            style = MaterialTheme.typography.bodySmall
        )
        is IncomeSource.RecurringMonth -> Text(
            "Usa la suma de tus recurrentes de ingreso del mes actual (los de Calendario).",
            style = MaterialTheme.typography.bodySmall
        )
        is IncomeSource.CategoryTotal -> OutlinedTextField(
            value = source.category,
            onValueChange = { viewModel.updateIncome(node.id, node.label, IncomeSource.CategoryTotal(it)) },
            label = { Text("Categoria") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
internal fun FormulaEditor(node: FormulaNode, viewModel: FlowsViewModel) {
    node.splits.forEachIndexed { index, split ->
        val isPercent = split is Split.Percent
        OutlinedTextField(
            value = split.label,
            onValueChange = {
                viewModel.updateSplit(
                    node.id, index,
                    if (split is Split.Percent) split.copy(label = it)
                    else if (split is Split.FixedAmount) split.copy(label = it)
                    else split
                )
            },
            label = { Text("Etiqueta del reparto ${index + 1}") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = isPercent,
                onClick = {
                    val current = splitValue(split)
                    val label = split.label
                    val category = splitCategory(split)
                    viewModel.updateSplit(
                        node.id, index,
                        if (isPercent) Split.FixedAmount(label, current, category)
                        else Split.Percent(label, current, category)
                    )
                },
                label = { Text(if (isPercent) "%" else "Fijo") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            NumberField(
                label = if (isPercent) "Porcentaje" else "Monto",
                value = splitValue(split),
                modifier = Modifier.weight(1f),
                onValue = {
                    val label = split.label
                    val category = splitCategory(split)
                    viewModel.updateSplit(
                        node.id, index,
                        if (isPercent) Split.Percent(label, it, category)
                        else Split.FixedAmount(label, it, category)
                    )
                }
            )
            IconButton(onClick = { viewModel.removeSplit(node.id, index) }) {
                Icon(Icons.Default.Delete, "Quitar reparto")
            }
        }
        OutlinedTextField(
            value = splitCategory(split) ?: "",
            onValueChange = {
                val label = split.label
                val value = splitValue(split)
                viewModel.updateSplit(
                    node.id, index,
                    if (isPercent) Split.Percent(label, value, it.ifBlank { null })
                    else Split.FixedAmount(label, value, it.ifBlank { null })
                )
            },
            label = { Text("Categoria (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    TextButton(onClick = { viewModel.addSplit(node.id) }) { Text("Agregar reparto") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionEditor(node: ConditionNode, viewModel: FlowsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = operatorLabels[node.operator] ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Si el monto") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            operatorLabels.forEach { (op, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        viewModel.updateCondition(node.id, op, node.threshold)
                        expanded = false
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    NumberField(
        label = "Umbral",
        value = node.threshold,
        onValue = { viewModel.updateCondition(node.id, node.operator, it) }
    )
    Spacer(modifier = Modifier.height(8.dp))
    BranchSection(
        title = "Rama Si",
        branch = node.trueBranch,
        onEnvelope = { index, envelope ->
            viewModel.updateBranchEnvelope(node.id, true, index, envelope)
        }
    )
    Spacer(modifier = Modifier.height(8.dp))
    BranchSection(
        title = "Rama Si no",
        branch = node.falseBranch,
        onEnvelope = { index, envelope ->
            viewModel.updateBranchEnvelope(node.id, false, index, envelope)
        }
    )
}

@Composable
internal fun BranchSection(
    title: String,
    branch: List<FlowNode>,
    onEnvelope: (Int, EnvelopeNode) -> Unit
) {
    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    if (branch.isEmpty()) {
        Text("Sin nodos.", style = MaterialTheme.typography.bodySmall)
        return
    }
    branch.forEachIndexed { index, child ->
        when (child) {
            is EnvelopeNode -> EnvelopeFields(
                label = child.label,
                category = child.category ?: "",
                onLabel = { onEnvelope(index, child.copy(label = it)) },
                onCategory = { onEnvelope(index, child.copy(category = it.ifBlank { null })) }
            )
            else -> Text(
                "Nodo ${child.id}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun EnvelopeFields(
    label: String,
    category: String,
    onLabel: (String) -> Unit,
    onCategory: (String) -> Unit
) {
    OutlinedTextField(
        value = label,
        onValueChange = onLabel,
        label = { Text("Etiqueta del sobre") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = category,
        onValueChange = onCategory,
        label = { Text("Categoria (opcional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
internal fun NumberField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onValue: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.replace(",", ".").toDoubleOrNull()?.let { onValue(it) }
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

internal fun splitValue(split: Split): Double = when (split) {
    is Split.Percent -> split.percent
    is Split.FixedAmount -> split.amount
}

internal fun splitCategory(split: Split): String? = when (split) {
    is Split.Percent -> split.category
    is Split.FixedAmount -> split.category
}

internal fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
