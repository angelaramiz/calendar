package com.fintrack.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.DataQuery
import com.fintrack.app.domain.QueryResult
import com.fintrack.app.domain.SavingsGoal
import java.time.LocalDate
import java.time.ZoneId

private fun formatMoney(value: Double): String =
    "$" + "%,.0f".format(value).replace(',', '.')

/**
 * Pregunta a tus datos (D9): campo de pregunta en español + tarjeta de
 * respuesta con cifra y hasta 3 ejemplos. Si no matchea ninguna plantilla
 * dice "no entendí" con ejemplos válidos (cero alucinaciones por diseño).
 * Lectura local, sin red ni LLM.
 */
@Composable
fun DataQuerySection(
    transactions: List<TransactionEntity>,
    goals: List<SavingsGoal>,
    modifier: Modifier = Modifier
) {
    var pregunta by remember { mutableStateOf("") }
    var resultado by remember { mutableStateOf<QueryResult?>(null) }
    val zone = remember { ZoneId.systemDefault() }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Pregunta en tus palabras, ej. «¿cuánto gasté en tacos en marzo?»",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = pregunta,
                onValueChange = { pregunta = it },
                label = { Text("Tu pregunta") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )
            Button(
                onClick = {
                    resultado = DataQuery.answer(
                        question = pregunta,
                        transactions = transactions,
                        goals = goals,
                        today = LocalDate.now(zone),
                        zone = zone
                    )
                },
                enabled = pregunta.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preguntar")
            }
            when (val r = resultado) {
                null -> Unit
                is QueryResult.Entendido -> {
                    val a = r.answer
                    Text(a.titulo, fontWeight = FontWeight.Bold)
                    Text(
                        formatMoney(a.cifra),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        a.detalle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    a.ejemplos.forEach { e ->
                        Text(
                            "${e.fecha} · ${e.concepto} · ${formatMoney(e.monto)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is QueryResult.NoEntendido -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "No entendí tu pregunta. Prueba con:",
                                fontWeight = FontWeight.SemiBold
                            )
                            DataQuery.SUGERENCIAS.forEach { s ->
                                Text(
                                    "· $s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
