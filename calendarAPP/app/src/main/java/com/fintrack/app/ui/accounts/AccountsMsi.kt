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
import androidx.compose.material3.LinearProgressIndicator
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
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.MsiPlan
import com.fintrack.app.domain.MsiPlanner
import com.fintrack.app.ui.common.AmountText
import java.time.format.TextStyle
import java.util.Locale

private val SpanishMsi = Locale("es")

private fun mesCorto(fecha: java.time.LocalDate): String {
    val nombre = fecha.month.getDisplayName(TextStyle.SHORT, SpanishMsi)
    return "$nombre ${fecha.year}"
}

/**
 * Sección "MSI activos" (§A) por tarjeta: Concepto · n/N · parcial del corte ·
 * fin, con barra de progreso, detalle de cuotas (cubierta/pendiente),
 * "Pasar a MSI" desde el ciclo actual, liquidar y eliminar.
 *
 * Los cargos tagueados por periodo son (fecha, monto) de la tarjeta.
 */
@Composable
internal fun MsiCardSection(
    card: CreditCardRow,
    planes: List<MsiPlan>,
    cargosTagueados: List<Pair<java.time.LocalDate, Double>>,
    onCrear: (cardId: String, concepto: String, monto: Double, meses: Int) -> Unit,
    onLiquidar: (String) -> Unit,
    onEliminar: (String) -> Unit
) {
    var creando by remember { mutableStateOf(false) }
    val hoy = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    val deLaTarjeta = planes.filter { it.cardId == card.id && !it.liquidado }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmountText(
            "MSI activos (${deLaTarjeta.size})",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        if (deLaTarjeta.isEmpty()) {
            Text(
                "Sin MSI en ${card.displayName}. Crea uno o pasa un cargo del ciclo actual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            deLaTarjeta.forEach { plan ->
                MsiPlanRow(
                    plan = plan,
                    cutoffDay = card.cutoffDay,
                    cargosTagueados = cargosTagueados,
                    today = hoy,
                    onLiquidar = { onLiquidar(plan.id) },
                    onEliminar = { onEliminar(plan.id) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { creando = true }) { Text("Nuevo MSI") }
        }
    }
    if (creando) {
        MsiPlanDialog(
            onDismiss = { creando = false },
            onSave = { concepto, monto, meses ->
                onCrear(card.id, concepto, monto, meses)
                creando = false
            }
        )
    }
}

@Composable
private fun MsiPlanRow(
    plan: MsiPlan,
    cutoffDay: Int,
    cargosTagueados: List<Pair<java.time.LocalDate, Double>>,
    today: java.time.LocalDate,
    onLiquidar: () -> Unit,
    onEliminar: () -> Unit
) {
    val cuotas = MsiPlanner.cuotas(plan, cutoffDay)
    val (hechas, total) = MsiPlanner.progreso(plan, cutoffDay, today)
    val parcialActual = cuotas.firstOrNull { !it.corteIso.isBefore(today) }
        ?: cuotas.lastOrNull()
    var verDetalle by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AmountText(
            "${plan.concepto} · $hechas/$total · " +
                "$${formatMoney(parcialActual?.parcial ?: 0.0)} este corte · " +
                "termina ${cuotas.lastOrNull()?.let { mesCorto(it.corteIso) } ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            progress = { if (total > 0) hechas.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { verDetalle = !verDetalle }) {
                Text(if (verDetalle) "Ocultar cuotas" else "Ver cuotas")
            }
            TextButton(onClick = onLiquidar) { Text("Liquidar") }
            TextButton(onClick = onEliminar) { Text("Eliminar") }
        }
        if (verDetalle) {
            cuotas.forEach { cuota ->
                val cubierta = MsiPlanner.cuotaCubierta(cuota, cutoffDay, cargosTagueados)
                Text(
                    "Cuota ${cuota.numero}/${cuota.total} · corte " +
                        "${cuota.corteIso.dayOfMonth}/${cuota.corteIso.monthValue} · " +
                        "$${formatMoney(cuota.parcial)} · " +
                        if (cubierta) "cubierta" else "pendiente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun MsiPlanDialog(
    conceptoInicial: String = "",
    montoInicial: Double = 0.0,
    onDismiss: () -> Unit,
    onSave: (concepto: String, monto: Double, meses: Int) -> Unit
) {
    var concepto by remember { mutableStateOf(conceptoInicial) }
    var montoText by remember {
        mutableStateOf(if (montoInicial > 0.0) formatMoney(montoInicial) else "")
    }
    var meses by remember { mutableStateOf(12) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plan MSI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = concepto,
                    onValueChange = { concepto = it },
                    label = { Text("Concepto") },
                    placeholder = { Text("Celular…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = montoText,
                    onValueChange = { raw ->
                        montoText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Monto total") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Meses (lo que ofrece tu banco)",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 6, 12).forEach { opcion ->
                        FilterChip(
                            selected = meses == opcion,
                            onClick = { meses = opcion },
                            label = { Text("$opcion") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(9, 18, 24).forEach { opcion ->
                        FilterChip(
                            selected = meses == opcion,
                            onClick = { meses = opcion },
                            label = { Text("$opcion") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val monto = montoText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
                onSave(concepto, monto, meses)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Tarjeta contenedora del grupo MSI si se usa fuera de CreditCardsCard. */
@Composable
internal fun MsiStandaloneCard(
    cards: List<CreditCardRow>,
    planes: List<MsiPlan>,
    cargosPorTarjeta: Map<String, List<Pair<java.time.LocalDate, Double>>>,
    onCrear: (cardId: String, concepto: String, monto: Double, meses: Int) -> Unit,
    onLiquidar: (String) -> Unit,
    onEliminar: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cards.isEmpty()) {
                Text("Agrega una tarjeta para llevar sus MSI.")
            } else {
                cards.forEach { card ->
                    MsiCardSection(
                        card = card,
                        planes = planes,
                        cargosTagueados = cargosPorTarjeta.getOrDefault(card.id, emptyList()),
                        onCrear = onCrear,
                        onLiquidar = onLiquidar,
                        onEliminar = onEliminar
                    )
                }
            }
        }
    }
}
