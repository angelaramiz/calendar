package com.fintrack.app.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.domain.PatternValidator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val UTC = ZoneId.of("UTC")
private val ES = Locale("es")

private val FREQUENCY_LABELS = listOf(
    "weekly" to "Semanal",
    "biweekly" to "Quincenal",
    "monthly" to "Mensual",
    "yearly" to "Anual"
)

/**
 * Ventana "Nuevo recurrente": crea un patrón de ingreso/gasto que el
 * calendario expande como proyecciones (misma lógica que la web).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatternDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        isIncome: Boolean,
        name: String,
        description: String,
        category: String,
        baseAmount: Double,
        frequency: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ) -> Unit,
    isSaving: Boolean = false
) {
    var isIncome by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Sueldo") }
    var frequency by remember { mutableStateOf("monthly") }
    var startDate by remember(initialDate) { mutableStateOf(initialDate) }
    var hasEndDate by remember { mutableStateOf(false) }
    var endDate by remember(initialDate) { mutableStateOf(initialDate) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val categories = listOf("Sueldo", "Comida", "Transporte", "Servicios", "Ocio", "Otros")

    val amountValue = amount.toDoubleOrNull()
    val valid = name.isNotBlank() && amountValue != null && amountValue > 0 && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Nuevo recurrente") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to "Gasto", true to "Ingreso").forEach { (value, label) ->
                        FilterChip(
                            selected = isIncome == value,
                            onClick = { isIncome = value },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Sueldo, Renta)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto de cada cobro/pago") },
                    leadingIcon = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Se repite", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FREQUENCY_LABELS.forEach { (value, label) ->
                        FilterChip(
                            selected = frequency == value,
                            onClick = { frequency = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                DateButton(
                    label = "Primer cobro/pago",
                    date = startDate,
                    onClick = { pickingStart = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                FilterChip(
                    selected = hasEndDate,
                    onClick = { hasEndDate = !hasEndDate },
                    label = { Text("Con fecha de fin") }
                )
                if (hasEndDate) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DateButton(
                        label = "Último cobro/pago",
                        date = endDate,
                        onClick = { pickingEnd = true }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = amount.toDoubleOrNull() ?: return@TextButton
                    onSave(
                        isIncome, name, "", category, value, frequency,
                        startDate, endDate.takeIf { hasEndDate }
                    )
                },
                enabled = valid
            ) { Text(if (isSaving) "Guardando…" else "Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        }
    )

    if (pickingStart || pickingEnd) {
        val current = if (pickingStart) startDate else endDate
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = {
                pickingStart = false
                pickingEnd = false
            },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(UTC).toLocalDate()
                        if (pickingStart) startDate = picked else endDate = picked
                    }
                    pickingStart = false
                    pickingEnd = false
                }) { Text("Elegir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pickingStart = false
                    pickingEnd = false
                }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.DateRange, label)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "$label: ${date.dayOfMonth} de " +
                date.month.getDisplayName(TextStyle.FULL, ES) + " ${date.year}"
        )
    }
}
