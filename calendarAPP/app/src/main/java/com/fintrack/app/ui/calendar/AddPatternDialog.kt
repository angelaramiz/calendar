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
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.PatternLink
import com.fintrack.app.data.PatternLinkKind
import com.fintrack.app.domain.Pattern
import com.fintrack.app.domain.TransactionCategories
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
    "bimonthly" to "Bimestral",
    "yearly" to "Anual"
)

/**
 * Ventana "Nuevo recurrente": crea un patrón de ingreso/gasto que el
 * calendario expande como proyecciones (misma lógica que la web).
 */
/**
 * Ventana de recurrente: crea uno nuevo o edita uno existente (precargado).
 * En edición el tipo Gasto/Ingreso se bloquea (cambiarlo implicaría mover
 * la fila de tabla) y aparece la opción Desactivar; al crear está libre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatternDialog(
    initialDate: LocalDate,
    existing: Pattern? = null,
    existingLink: PatternLink? = null,
    cards: List<CreditCardRow> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        isIncome: Boolean,
        name: String,
        description: String,
        category: String,
        baseAmount: Double,
        frequency: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        linkKind: String?,
        linkCardId: String?
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    isSaving: Boolean = false
) {
    val isEditing = existing != null
    var isIncome by remember(existing) { mutableStateOf(existing?.type != "EXPENSE") }
    val categories = TransactionCategories.forType(isIncome)
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var amount by remember(existing) {
        mutableStateOf(existing?.let { String.format("%.2f", it.baseAmount) } ?: "")
    }
    var category by remember(existing) {
        mutableStateOf(
            existing?.category?.takeIf { it in categories }
                ?: TransactionCategories.defaultFor(isIncome)
        )
    }
    var frequency by remember(existing) {
        mutableStateOf(existing?.frequency?.takeIf { it in FREQUENCY_LABELS.map { f -> f.first } } ?: "monthly")
    }
    var startDate by remember(existing) { mutableStateOf(existing?.startDate ?: initialDate) }
    var hasEndDate by remember(existing) { mutableStateOf(existing?.endDate != null) }
    var linkKind by remember(existing) { mutableStateOf(existingLink?.kind) }
    var linkCardId by remember(existing) {
        mutableStateOf(existingLink?.cardId?.takeIf { cards.any { c -> c.id == it } })
    }
    var endDate by remember(existing) { mutableStateOf(existing?.endDate ?: initialDate) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull()
    val valid = name.isNotBlank() && amountValue != null && amountValue > 0 && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (isEditing) "Editar recurrente" else "Nuevo recurrente") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to "Gasto", true to "Ingreso").forEach { (value, label) ->
                        FilterChip(
                            selected = isIncome == value,
                            onClick = {
                                isIncome = value
                                category = TransactionCategories.defaultFor(value)
                            },
                            enabled = !isEditing,
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

                Text("Tipo", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = linkKind == null,
                        onClick = {
                            linkKind = null
                            linkCardId = null
                        },
                        label = { Text("Normal") }
                    )
                    FilterChip(
                        selected = linkKind == PatternLinkKind.CREDIT,
                        onClick = { linkKind = PatternLinkKind.CREDIT },
                        label = { Text("Tarjeta") }
                    )
                    FilterChip(
                        selected = linkKind == PatternLinkKind.SERVICE,
                        onClick = {
                            linkKind = PatternLinkKind.SERVICE
                            linkCardId = null
                        },
                        label = { Text("Servicio") }
                    )
                    FilterChip(
                        selected = linkKind == PatternLinkKind.SUBSCRIPTION,
                        onClick = {
                            linkKind = PatternLinkKind.SUBSCRIPTION
                            linkCardId = null
                        },
                        label = { Text("Suscripción") }
                    )
                }
                if (linkKind == PatternLinkKind.CREDIT && cards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cards.forEach { card ->
                            FilterChip(
                                selected = linkCardId == card.id,
                                onClick = { linkCardId = card.id },
                                label = { Text(card.name) }
                            )
                        }
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
                        startDate, endDate.takeIf { hasEndDate },
                        linkKind,
                        linkCardId.takeIf { linkKind == PatternLinkKind.CREDIT }
                    )
                },
                enabled = valid
            ) { Text(if (isSaving) "Guardando…" else if (isEditing) "Guardar" else "Crear") }
        },
        dismissButton = {
            Row {
                if (isEditing && onDelete != null) {
                    TextButton(onClick = onDelete, enabled = !isSaving) {
                        Text("Desactivar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
            }
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
