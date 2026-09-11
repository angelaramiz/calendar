package com.fintrack.app.ui.quickentry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.model.TransactionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(
    onSave: (TransactionEntity) -> Unit,
    onCancel: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("Comida") }
    var description by remember { mutableStateOf("") }
    val categories = listOf("Comida", "Transporte", "Servicios", "Ocio", "Otros")
    val title = if (type == "INCOME") "Ingreso Rápido" else "Gasto Rápido"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "Cancelar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("EXPENSE" to "Gasto", "INCOME" to "Ingreso").forEach { (t, label) ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Monto") },
                leadingIcon = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Categoria", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                categories.forEach { cat ->
                    FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) }, modifier = Modifier.padding(end = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Nota (opcional)") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull() ?: return@Button
                    if (amountValue <= 0) return@Button
                    onSave(TransactionEntity(amount = amountValue, type = type, category = category, description = description, timestamp = System.currentTimeMillis(), source = "MANUAL"))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Guardar") }
        }
    }
}
