package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.anomalyDataStore by preferencesDataStore("anomaly_watch")

/**
 * Descarte de un toque del Vigilante (D5): ids estables ya revisados.
 * La pasada diaria los excluye antes de avisar.
 */
class AnomalyDismissStore(private val context: Context) {

    private val dismissedKey = stringPreferencesKey("anomalies_dismissed_json")

    suspend fun snapshot(): Set<String> =
        context.anomalyDataStore.data.map { prefs ->
            PendingOpCodec.decodeStrings(prefs[dismissedKey]).keys
        }.first()

    suspend fun dismiss(stableId: String) {
        context.anomalyDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeStrings(prefs[dismissedKey]).toMutableMap()
            current[stableId] = "1"
            val pruned = current.entries.sortedBy { it.key }.takeLast(200)
                .associate { it.key to it.value }
            prefs[dismissedKey] = PendingOpCodec.encodeStrings(pruned)
        }
    }

    /** Restaura un respaldo: reemplaza los descartes. */
    suspend fun restore(ids: Set<String>) {
        context.anomalyDataStore.edit { prefs ->
            prefs[dismissedKey] = PendingOpCodec.encodeStrings(ids.associateWith { "1" })
        }
    }
}
