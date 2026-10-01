package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.txLinkDataStore by preferencesDataStore("tx_links")

/**
 * Vínculo registro de Inicio ↔ evento programado (local, sin columna en el
 * servidor: fintrack_transactions no tiene FK de patrón).
 * Clave: txId ("tx:<id>" no, el id pelado para buscar directo).
 */
@Serializable
data class TxLink(
    val patternId: String,
    /** Fecha de la ocurrencia vinculada (ISO yyyy-MM-dd). */
    val dateIso: String,
    val isIncome: Boolean,
    /** Nombre del evento (denormalizado, para mostrar sin buscar patrones). */
    val patternName: String = ""
)

class TxLinkStore(private val context: Context) {

    private val linksKey = stringPreferencesKey("tx_links_json")

    private val mapSerializer = MapSerializer(String.serializer(), TxLink.serializer())

    val links: Flow<Map<String, TxLink>> =
        context.txLinkDataStore.data.map { prefs ->
            prefs[linksKey]?.let { raw ->
                runCatching { PendingOpCodec.json.decodeFromString(mapSerializer, raw) }.getOrNull()
            } ?: emptyMap()
        }

    suspend fun snapshot(): Map<String, TxLink> = links.first()

    suspend fun link(txId: String, link: TxLink) {
        context.txLinkDataStore.edit { prefs ->
            val current = decode(prefs[linksKey]).toMutableMap()
            current[txId] = link
            prefs[linksKey] = PendingOpCodec.json.encodeToString(mapSerializer, current)
        }
    }

    suspend fun unlink(txId: String) {
        context.txLinkDataStore.edit { prefs ->
            val current = decode(prefs[linksKey]).toMutableMap()
            current.remove(txId)
            prefs[linksKey] = PendingOpCodec.json.encodeToString(mapSerializer, current)
        }
    }

    suspend fun clear() {
        context.txLinkDataStore.edit { prefs ->
            prefs.remove(linksKey)
        }
    }

    private fun decode(raw: String?): Map<String, TxLink> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { PendingOpCodec.json.decodeFromString(mapSerializer, raw) }.getOrNull()
            ?: emptyMap()
}
