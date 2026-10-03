package com.fintrack.app.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.HormigaFrequency
import com.fintrack.app.data.HormigaStore
import com.fintrack.app.data.StreakStore
import com.fintrack.app.domain.Anomaly
import com.fintrack.app.domain.AnomalyKind
import kotlinx.coroutines.launch

/**
 * UI minima de Vigilancia (D5) y Fuga hormiga + Rachas (D11).
 * Composables autocontenidos: no se cablean solos a ninguna pantalla
 * (NavGraph no se toca en esta oleada). Ver snippets en el mensaje final.
 */

/** Lista de anomalias con descarte de un toque. */
@Composable
fun AnomalyWatchCard(
    anomalies: List<Anomaly>,
    onDismiss: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Vigilante", fontWeight = FontWeight.Bold)
            if (anomalies.isEmpty()) {
                Text(
                    "Sin anomalias: ni duplicados ni subidas de precio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                anomalies.forEach { anomaly ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (anomaly.kind == AnomalyKind.DUPLICATE) {
                                    "Posible doble cobro en ${anomaly.merchant}"
                                } else {
                                    "${anomaly.merchant} subio de precio"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                anomaly.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onDismiss(anomaly.stableId) }) {
                            Text("Descartar")
                        }
                    }
                }
            }
        }
    }
}

/** Selector de frecuencia del aviso hormiga (semanal/quincenal/off). */
@Composable
fun HormigaFreqSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = HormigaStore(context)
    val freq by produceState<HormigaFrequency>(
        initialValue = HormigaFrequency.SEMANAL
    ) {
        value = runCatching { store.frequency() }
            .getOrDefault(HormigaFrequency.SEMANAL)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Fuga hormiga", fontWeight = FontWeight.Bold)
            Text(
                "Aviso del lunes con tu comercio chico mas visitado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HormigaFrequency.values().forEach { option ->
                    val selected = option == freq
                    if (selected) {
                        Button(onClick = { }) { Text(option.raw) }
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                store.setFrequency(option)
                            }
                        }) { Text(option.raw) }
                    }
                }
            }
        }
    }
}

/** Rachas de quincenas bajo tope por categoria. */
@Composable
fun StreakCard() {
    val context = LocalContext.current
    val streaks by produceState<Map<String, Int>>(initialValue = emptyMap()) {
        value = runCatching { StreakStore(context).snapshot() }.getOrDefault(emptyMap())
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Rachas bajo tope", fontWeight = FontWeight.Bold)
            val active = streaks.filterValues { it > 0 }
            if (active.isEmpty()) {
                Text(
                    "Cierra una quincena sin pasarte de tu tope para empezar una racha.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                active.toList().sortedByDescending { (_, n) -> n }.forEach { (cat, n) ->
                    Text("$cat: $n ${if (n == 1) "quincena" else "quincenas"} seguidas")
                }
            }
        }
    }
}
