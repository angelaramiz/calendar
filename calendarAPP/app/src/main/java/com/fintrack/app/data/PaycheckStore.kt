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

private val Context.paycheckDataStore by preferencesDataStore("paycheck ritual")

/** Último reparto de quincena guardado (marcadores del periodo). */
@Serializable
data class PaycheckSnapshot(
    val fechaIso: String,
    val ingreso: Double = 0.0,
    val fijos: Double = 0.0,
    val minimos: Double = 0.0,
    val extraDeuda: Double = 0.0,
    val ahorro: Double = 0.0,
    val libre: Double = 0.0,
    val markers: List<String> = emptyList()
)

class PaycheckStore(private val context: Context) {

    private val lastKey = stringPreferencesKey("ultimo_reparto_json")

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val last: Flow<PaycheckSnapshot?> = context.paycheckDataStore.data.map { prefs ->
        prefs[lastKey]?.let { raw ->
            runCatching { json.decodeFromString(PaycheckSnapshot.serializer(), raw) }.getOrNull()
        }
    }

    suspend fun snapshot(): PaycheckSnapshot? = last.first()

    suspend fun save(snapshot: PaycheckSnapshot) {
        context.paycheckDataStore.edit { prefs ->
            prefs[lastKey] = json.encodeToString(PaycheckSnapshot.serializer(), snapshot)
        }
    }

    suspend fun clear() {
        context.paycheckDataStore.edit { prefs -> prefs.remove(lastKey) }
    }
}
