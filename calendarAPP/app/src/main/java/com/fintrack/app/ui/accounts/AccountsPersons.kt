package com.fintrack.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.PersonDebt
import com.fintrack.app.domain.PersonDebtPlanner
import com.fintrack.app.ui.common.AmountText
import com.fintrack.app.ui.theme.expenseColor
import com.fintrack.app.ui.theme.incomeColor

/**
 * Grupo "Personas" (D8): quién te debe / a quién debes, con abono parcial
 * y liquidar. Todo local vía [PersonDebtStore].
 */
@Composable
internal fun PersonDebtsCard(
    debts: List<PersonDebt>,
    onSave: (id: String?, nombre: String, monto: Double, esDeudaMia: Boolean, fechaIso: String) -> Unit,
    onDelete: (String) -> Unit,
    onAbono: (debtId: String, amount: Double) -> Unit,
    onLiquidar: (String) -> Unit
) {
    var editing by remember { mutableStateOf<PersonDebt?>(null) }
    var adding by remember { mutableStateOf(false) }
    var abonoA by remember { mutableStateOf<PersonDebt?>(null) }
    val totales = PersonDebtPlanner.totales(debts)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Prestamos y cuentas divididas: registra quién te debe o a quién debes, " +
                    "abona por partes y liquida al terminar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AmountText(
                "Te deben $${formatMoney(totales.porCobrar)} · " +
                    "Debes $${formatMoney(totales.porPagar)}",
                fontWeight = FontWeight.Bold
            )
            if (debts.isEmpty()) {
                Text(
                    "Sin deudas personales. Ej. le presté $500 a Juan.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                debts.forEach { debt ->
                    val saldo = PersonDebtPlanner.saldo(debt)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debt.nombre, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (debt.esDeudaMia) "Yo debo" else "Me debe") +
                                    " · Saldo $${formatMoney(saldo)}" +
                                    if (PersonDebtPlanner.abonado(debt) > 0.0) {
                                        " (abonado $${formatMoney(PersonDebtPlanner.abonado(debt))})"
                                    } else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (debt.esDeudaMia) expenseColor() else incomeColor()
                            )
                        }
                        Row {
                            TextButton(onClick = { abonoA = debt }) { Text("Abonar") }
                            TextButton(onClick = { editing = debt }) { Text("Editar") }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onLiquidar(debt.id) }) { Text("Liquidar") }
                        TextButton(onClick = { onDelete(debt.id) }) { Text("Eliminar") }
                    }
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar deuda")
            }
        }
    }
    if (adding) {
        PersonDebtDialog(
            existing = null,
            onDismiss = { adding = false },
            onSave = { _, nombre, monto, esMia, fecha ->
                onSave(null, nombre, monto, esMia, fecha)
                adding = false
            }
        )
    }
    editing?.let { debt ->
        PersonDebtDialog(
            existing = debt,
            onDismiss = { editing = null },
            onSave = { id, nombre, monto, esMia, fecha ->
                onSave(id, nombre, monto, esMia, fecha)
                editing = null
            }
        )
    }
    abonoA?.let { debt ->
        AbonoDialog(
            nombre = debt.nombre,
            saldo = PersonDebtPlanner.saldo(debt),
            onDismiss = { abonoA = null },
            onSave = { monto ->
                onAbono(debt.id, monto)
                abonoA = null
            }
        )
    }
}

@Composable
private fun PersonDebtDialog(
    existing: PersonDebt?,
    onDismiss: () -> Unit,
    onSave: (id: String?, nombre: String, monto: Double, esDeudaMia: Boolean, fechaIso: String) -> Unit
) {
    var nombre by remember(existing) { mutableStateOf(existing?.nombre ?: "") }
    var montoText by remember(existing) {
        mutableStateOf(existing?.let { formatMoney(it.monto) } ?: "")
    }
    var esMia by remember(existing) { mutableStateOf(existing?.esDeudaMia ?: false) }
    val hoy = remember {
        java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nueva deuda" else "Editar deuda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Persona") },
                    placeholder = { Text("Juan…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = montoText,
                    onValueChange = { raw ->
                        montoText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !esMia,
                        onClick = { esMia = false },
                        label = { Text("Me debe") }
                    )
                    FilterChip(
                        selected = esMia,
                        onClick = { esMia = true },
                        label = { Text("Yo debo") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val monto = montoText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
                onSave(existing?.id, nombre, monto, esMia, existing?.fechaIso ?: hoy)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AbonoDialog(
    nombre: String,
    saldo: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var montoText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abonar a $nombre") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Saldo: $${formatMoney(saldo)}. Puedes abonar por partes.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = montoText,
                    onValueChange = { raw ->
                        montoText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Abono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val monto = montoText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
                onSave(monto)
            }) { Text("Abonar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
