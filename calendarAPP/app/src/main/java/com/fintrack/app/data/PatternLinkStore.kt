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

private val Context.patternLinkDataStore by preferencesDataStore("pattern_links")

/** Clasificación local de un recurrente (sin columna en el servidor). */
@Serializable
data class PatternLink(
    /** "credit", "service" o "subscription". */
    val kind: String,
    /** Tarjeta asociada (solo kind == "credit"). */
    val cardId: String? = null
)

object PatternLinkKind {
    const val CREDIT = "credit"
    const val SERVICE = "service"
    const val SUBSCRIPTION = "subscription"
}

class PatternLinkStore(private val context: Context) {

    private val linksKey = stringPreferencesKey("pattern_links_json")

    private val mapSerializer = MapSerializer(String.serializer(), PatternLink.serializer())

    val links: Flow<Map<String, PatternLink>> =
        context.patternLinkDataStore.data.map { prefs ->
            prefs[linksKey]?.let { raw ->
                runCatching { PendingOpCodec.json.decodeFromString(mapSerializer, raw) }.getOrNull()
            } ?: emptyMap()
        }

    suspend fun snapshot(): Map<String, PatternLink> = links.first()

    suspend fun clear() {
        context.patternLinkDataStore.edit { prefs ->
            prefs.remove(linksKey)
        }
    }

    /** kind null = sin clasificar (borra el link). */
    suspend fun setLink(patternId: String, kind: String?, cardId: String?) {
        context.patternLinkDataStore.edit { prefs ->
            val current = decode(prefs[linksKey]).toMutableMap()
            if (kind == null) current.remove(patternId)
            else current[patternId] = PatternLink(kind, cardId?.takeIf { kind == PatternLinkKind.CREDIT })
            prefs[linksKey] = PendingOpCodec.json.encodeToString(mapSerializer, current)
        }
    }

    private fun decode(raw: String?): Map<String, PatternLink> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { PendingOpCodec.json.decodeFromString(mapSerializer, raw) }.getOrNull()
            ?: emptyMap()
}
