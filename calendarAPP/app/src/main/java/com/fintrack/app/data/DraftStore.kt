package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.entryDraftDataStore by preferencesDataStore("entry_draft")

/**
 * Borrador del Registro rápido: si la ventana se cierra a media captura
 * (toque fuera, muerte del TileService, segundo tap al tile), al reabrir
 * sigue donde ibas en vez de empezar de cero. Se limpia al guardar o
 * cancelar. Efímero por diseño: también viaja en el respaldo local por si
 * reinstalas con el formulario a medias.
 */
@Serializable
data class EntryDraft(
    val type: String = "EXPENSE",
    val amount: String = "",
    val category: String = "Comida",
    val description: String = "",
    val walletId: String? = null,
    val cardId: String? = null,
    /** Paso del wizard por pasos (OUT); la forma completa (IN) lo ignora. */
    val step: Int = 0
)

class DraftStore(private val context: Context) {

    private val draftKey = stringPreferencesKey("borrador_json")

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val draft: Flow<EntryDraft?> = context.entryDraftDataStore.data.map { prefs ->
        prefs[draftKey]?.let { raw ->
            runCatching { json.decodeFromString(EntryDraft.serializer(), raw) }.getOrNull()
        }
    }

    suspend fun snapshot(): EntryDraft? = draft.first()

    suspend fun save(draft: EntryDraft) {
        context.entryDraftDataStore.edit { prefs ->
            prefs[draftKey] = json.encodeToString(EntryDraft.serializer(), draft)
        }
    }

    suspend fun restore(draft: EntryDraft?) {
        if (draft == null) clear() else save(draft)
    }

    suspend fun clear() {
        context.entryDraftDataStore.edit { prefs -> prefs.remove(draftKey) }
    }
}
