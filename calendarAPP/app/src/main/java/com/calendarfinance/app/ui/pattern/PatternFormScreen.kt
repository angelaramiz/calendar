package com.calendarfinance.app.ui.pattern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calendarfinance.app.data.repository.AuthRepository
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternFormScreen(
    patternType: String,
    patternId: String?,
    onNavigateBack: () -> Unit,
    viewModel: PatternViewModel = koinViewModel(),
    authRepository: AuthRepository = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userId = authRepository.currentUserId ?: ""

    LaunchedEffect(patternType, patternId) {
        viewModel.initForm(patternType, patternId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (patternId != null) "Editar patron" else
                        if (patternType == "income") "Nuevo ingreso recurrente"
                        else "Nuevo gasto recurrente"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Type toggle
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("income" to "Ingreso", "expense" to "Gasto").forEach { (type, label) ->
                    FilterChip(
                        selected = uiState.patternType == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nombre del patron") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descripcion") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Monto") },
                leadingIcon = { Text("$") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.category,
                onValueChange = viewModel::onCategoryChange,
                label = { Text("Categoria") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Frequency selector
            Text("Frecuencia", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("weekly" to "Semanal", "biweekly" to "Quincenal", "monthly" to "Mensual", "yearly" to "Anual").forEach { (freq, label) ->
                    FilterChip(
                        selected = uiState.frequency == freq,
                        onClick = { viewModel.onFrequencyChange(freq) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.frequency == "monthly") {
                OutlinedTextField(
                    value = uiState.dayOfMonth,
                    onValueChange = viewModel::onDayOfMonthChange,
                    label = { Text("Dia del mes (1-31)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.frequency == "weekly") {
                OutlinedTextField(
                    value = uiState.dayOfWeek,
                    onValueChange = viewModel::onDayOfWeekChange,
                    label = { Text("Dia de la semana (0=Dom, 6=Sab)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = uiState.startDate,
                onValueChange = viewModel::onStartDateChange,
                label = { Text("Fecha de inicio (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.patternType == "expense") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.isEssential, onCheckedChange = viewModel::onEssentialChange)
                    Text("Gasto esencial")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.save(userId) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Guardar patron")
                }
            }
        }
    }
}
