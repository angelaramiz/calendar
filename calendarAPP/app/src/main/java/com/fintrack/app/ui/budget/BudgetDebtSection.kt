package com.fintrack.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.domain.DebtPlanner
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val SpanishDebt = Locale("es")

private fun YearMonth.etiqueta(): String {
    val nombre = month.getDisplayName(TextStyle.SHORT, SpanishDebt)
        .replaceFirstChar { it.uppercase() }
    return "$nombre $year"
}

private fun dinero(value: Double): String =
    "%,.0f".format(value).replace(',', '.')

/**
 * Estrategia de deudas (§D2): bola de nieve vs avalancha. Sección nueva al
 * FINAL del análisis de Presupuesto: agrega tarjetas + MSI + personales,
 * simula ambas estrategias con el mismo pago mensual y muestra tabla mes a
 * mes + fecha de libertad. Motor puro: [DebtPlanner].
 */
@Composable
internal fun DebtStrategySection(
    deudas: List<DebtPlanner.Deuda>,
    cards: List<CreditCardRow>,
    onSetCat: (cardId: String, cat: Double) -> Unit
) {
    val total = DebtPlanner.total(deudas)
    var estrategia by remember { mutableStateOf(DebtPlanner.Estrategia.NIEVE) }
    var pagoText by remember(total) {
        mutableStateOf(if (total > 0.0) dinero(total / 12.0 + total * 0.05) else "")
    }
    var editandoCat by remember { mutableStateOf<CreditCardRow?>(null) }
    val pagoMensual = pagoText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
    val desde = remember { YearMonth.now() }
    val nieve = remember(deudas, pagoMensual, desde) {
        DebtPlanner.simular(deudas, pagoMensual, desde, DebtPlanner.Estrategia.NIEVE)
    }
    val avalancha = remember(deudas, pagoMensual, desde) {
        DebtPlanner.simular(deudas, pagoMensual, desde, DebtPlanner.Estrategia.AVALANCHA)
    }
    val activa = if (estrategia == DebtPlanner.Estrategia.NIEVE) nieve else avalancha
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Suma todo lo que debes y calcula tu fecha de libertad. Mismo pago " +
                    "mensual repartido por menor saldo (nieve, ganas rápido) o por " +
                    "mayor interés (avalancha, pagas menos).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (deudas.isEmpty()) {
                Text("Sin deudas: no debes nada en tarjetas, MSI ni personas.")
            } else {
                Text(
                    "Debes en total $${dinero(total)} en ${deudas.size} " +
                        if (deudas.size == 1) "deuda." else "deudas.",
                    fontWeight = FontWeight.Bold
                )
                deudas.forEach { deuda ->
                    Text(
                        "${deuda.nombre}: $${dinero(deuda.saldo)}" +
                            if (deuda.catAnual > 0.0) " · CAT ${dinero(deuda.catAnual)}%"
                            else " · sin intereses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // CAT editable por tarjeta (default 60%).
                cards.forEach { card ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${card.displayName}: CAT ${dinero(card.catAnual)}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { editandoCat = card }) { Text("Editar") }
                    }
                }
                OutlinedTextField(
                    value = pagoText,
                    onValueChange = { raw ->
                        pagoText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Pago mensual total") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = estrategia == DebtPlanner.Estrategia.NIEVE,
                        onClick = { estrategia = DebtPlanner.Estrategia.NIEVE },
                        label = { Text("Nieve (menor saldo)") }
                    )
                    FilterChip(
                        selected = estrategia == DebtPlanner.Estrategia.AVALANCHA,
                        onClick = { estrategia = DebtPlanner.Estrategia.AVALANCHA },
                        label = { Text("Avalancha (mayor interés)") }
                    )
                }
                ComparativaRow(nieve = nieve, avalancha = avalancha)
                if (activa.fechaLibertad == null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "Con ese pago no terminas: el interés mensual supera tu pago. " +
                                "Sube el monto.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else {
                    Text(
                        "Libre en ${activa.fechaLibertad.etiqueta()} " +
                            "(${activa.filas.size} meses) · " +
                            "intereses $${dinero(activa.interesTotal)}",
                        fontWeight = FontWeight.Bold
                    )
                    TablaMeses(filas = activa.filas)
                }
            }
        }
    }
    editandoCat?.let { card ->
        CatEditDialog(
            cardName = card.displayName,
            currentCat = card.catAnual,
            onDismiss = { editandoCat = null },
            onSave = { cat ->
                onSetCat(card.id, cat)
                editandoCat = null
            }
        )
    }
}

@Composable
private fun ComparativaRow(nieve: DebtPlanner.Resultado, avalancha: DebtPlanner.Resultado) {
    fun resumen(resultado: DebtPlanner.Resultado): String =
        if (resultado.fechaLibertad == null) "no libera"
        else "${resultado.fechaLibertad.etiqueta()} · $${dinero(resultado.interesTotal)} interés"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Nieve: ${resumen(nieve)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Avalancha: ${resumen(avalancha)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TablaMeses(filas: List<DebtPlanner.FilaMes>) {
    val visibles = if (filas.size <= 13) filas
    else filas.take(12) + listOf(filas.last())
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visibles.forEach { fila ->
            val esUltima = fila == filas.last() && filas.size > 13
            if (esUltima) {
                Text("…", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${fila.mes.etiqueta()} · pago $${dinero(fila.pagos.values.sum())} · " +
                    "interés $${dinero(fila.interesDelMes)} · resta $${dinero(fila.saldoRestante)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CatEditDialog(
    cardName: String,
    currentCat: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var catText by remember(cardName) { mutableStateOf(dinero(currentCat)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CAT de $cardName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "CAT anual en % (default 60%). Se usa para calcular intereses.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = catText,
                    onValueChange = { raw ->
                        catText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("CAT %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cat = catText.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
                onSave(cat)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
