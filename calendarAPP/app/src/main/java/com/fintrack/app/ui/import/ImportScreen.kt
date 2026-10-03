package com.fintrack.app.ui.import

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.BankFormat
import com.fintrack.app.domain.CsvCandidate
import com.fintrack.app.domain.CsvImport
import com.fintrack.app.domain.findLinkCandidates

/**
 * Conciliación CSV (§D7): picker SAF (sin permisos) + cada fila se acepta
 * (crea vía repos existentes), se vincula (usa `OccurrenceLink` existente)
 * o se descarta. `Dedup` (clave fecha+monto+lado+concepto) evita dobles
 * en re-imports.
 */
private enum class RowStatus { PENDIENTE, ACEPTADO, VINCULADO, DESCARTADO }

private data class RowState(
    val candidate: CsvCandidate,
    val status: RowStatus = RowStatus.PENDIENTE,
    /** Registro existente con el que coincide (vínculo exacto). */
    val matchTitle: String? = null
)

/** Entrada al final de Cuentas: botón + conciliación inline (estado local). */
@Composable
fun CsvImportEntry(
    existing: List<TransactionEntity>,
    onAccept: (CsvCandidate) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Trae tus movimientos del banco sin APIs: exporta el CSV " +
                    "(BBVA, Banamex, Santander) y concílialo aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { show = !show }) {
                Text(if (show) "Ocultar importación" else "Elegir archivo CSV")
            }
        }
    }
    if (show) {
        Spacer(modifier = Modifier.height(4.dp))
        ImportScreen(
            existing = existing,
            onAccept = onAccept,
            onBack = { show = false }
        )
    }
}

@Composable
fun ImportScreen(
    existing: List<TransactionEntity>,
    onAccept: (CsvCandidate) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var fileLabel by remember { mutableStateOf<String?>(null) }
    var format by remember { mutableStateOf<BankFormat?>(null) }
    var rows by remember { mutableStateOf<List<RowState>>(emptyList()) }
    // Claves ya conciliadas en esta sesión: el re-import no duplica.
    var doneKeys by remember { mutableStateOf(setOf<String>()) }

    fun load(uri: Uri) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()
        if (text.isNullOrBlank()) {
            fileLabel = "No se pudo leer el archivo."
            return
        }
        val first = text.lines().firstOrNull { it.isNotBlank() } ?: return
        val detected = CsvImport.detectFormat(CsvImport.splitRow(first))
        format = detected
        fileLabel = (uri.lastPathSegment ?: "archivo").takeLast(32)
        if (detected == BankFormat.DESCONOCIDO) {
            rows = emptyList()
            return
        }
        rows = CsvImport.parse(text).map { c ->
            val key = CsvImport.dedupKey(c)
            val match = CsvImport.exactMatch(c, existing)
            val linkTitle = match?.let {
                (it.merchant ?: it.description).ifBlank { it.category }
            } ?: findLinkCandidates(CsvImport.toOccurrence(c), emptyList(), existing)
                .firstOrNull()?.title
            when {
                key in doneKeys || match != null -> RowState(c, RowStatus.VINCULADO, linkTitle)
                else -> RowState(c, RowStatus.PENDIENTE, linkTitle)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::load) }

    val aceptados = rows.count { it.status == RowStatus.ACEPTADO }
    val vinculados = rows.count { it.status == RowStatus.VINCULADO }
    val descartados = rows.count { it.status == RowStatus.DESCARTADO }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Conciliación CSV", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onBack) { Text("Cerrar") }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { picker.launch(arrayOf("text/*", "text/csv", "*/*")) }
            ) {
                Text(if (fileLabel == null) "Elegir archivo" else "Cambiar archivo")
            }
            fileLabel?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$it · ${format?.name ?: "?"} · ${rows.size} filas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (format == BankFormat.DESCONOCIDO) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Formato no reconocido: usa el CSV de tu banco " +
                        "(BBVA, Banamex o Santander).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (rows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$aceptados aceptados · $vinculados vinculados · " +
                        "$descartados descartados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            rows.forEachIndexed { i, row ->
                ImportRow(
                    row = row,
                    onAccept = {
                        onAccept(row.candidate)
                        doneKeys = doneKeys + CsvImport.dedupKey(row.candidate)
                        rows = rows.toMutableList().also {
                            it[i] = row.copy(status = RowStatus.ACEPTADO)
                        }
                    },
                    onLink = {
                        doneKeys = doneKeys + CsvImport.dedupKey(row.candidate)
                        rows = rows.toMutableList().also {
                            it[i] = row.copy(status = RowStatus.VINCULADO)
                        }
                    },
                    onDiscard = {
                        rows = rows.toMutableList().also {
                            it[i] = row.copy(status = RowStatus.DESCARTADO)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ImportRow(
    row: RowState,
    onAccept: () -> Unit,
    onLink: () -> Unit,
    onDiscard: () -> Unit
) {
    val c = row.candidate
    val container = when (row.status) {
        RowStatus.ACEPTADO -> MaterialTheme.colorScheme.secondaryContainer
        RowStatus.VINCULADO -> MaterialTheme.colorScheme.tertiaryContainer
        RowStatus.DESCARTADO -> MaterialTheme.colorScheme.surfaceVariant
        RowStatus.PENDIENTE -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                (if (c.isIncome) "+ " else "− ") + "$" + "%,.2f".format(c.amount),
                style = MaterialTheme.typography.titleMedium
            )
            Text(c.concept, style = MaterialTheme.typography.bodyMedium)
            Text(
                c.dateIso + estadoTexto(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            row.matchTitle?.let {
                Text(
                    "Coincide con: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (row.status == RowStatus.PENDIENTE) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept) { Text("Aceptar") }
                    if (row.matchTitle != null) {
                        OutlinedButton(onClick = onLink) { Text("Vincular") }
                    }
                    OutlinedButton(onClick = onDiscard) { Text("Descartar") }
                }
            }
        }
    }
}

private fun estadoTexto(row: RowState): String = when (row.status) {
    RowStatus.ACEPTADO -> " · aceptado"
    RowStatus.VINCULADO -> " · vinculado (no duplica)"
    RowStatus.DESCARTADO -> " · descartado"
    RowStatus.PENDIENTE -> ""
}
