package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hormigaDataStore by preferencesDataStore("hormiga")

/** Frecuencia del aviso de fuga hormiga (D11). */
enum class HormigaFrequency(val raw: String) {
    SEMANAL("semanal"),
    QUINCENAL("quincenal"),
    OFF("off");

    companion object {
        fun of(raw: String?): HormigaFrequency =
            values().firstOrNull { it.raw == raw } ?: SEMANAL
    }
}

/**
 * Configuracion minima de la fuga hormiga: frecuencia + ultima semana
 * avisada (clave "2026-W37"). Todo local en DataStore.
 */
class HormigaStore(private val context: Context) {

    private val freqKey = stringPreferencesKey("hormiga_freq")
    private val lastSentKey = stringPreferencesKey("hormiga_last_sent")

    suspend fun frequency(): HormigaFrequency =
        context.hormigaDataStore.data.map { prefs ->
            HormigaFrequency.of(prefs[freqKey])
        }.first()

    suspend fun setFrequency(freq: HormigaFrequency) {
        context.hormigaDataStore.edit { prefs -> prefs[freqKey] = freq.raw }
    }

    suspend fun lastSentWeek(): String? =
        context.hormigaDataStore.data.map { prefs -> prefs[lastSentKey] }.first()

    suspend fun markSent(weekKey: String) {
        context.hormigaDataStore.edit { prefs -> prefs[lastSentKey] = weekKey }
    }

    suspend fun snapshot(): Pair<String, String?> =
        context.hormigaDataStore.data.map { prefs ->
            (prefs[freqKey] ?: HormigaFrequency.SEMANAL.raw) to prefs[lastSentKey]
        }.first()

    /** Restaura un respaldo: reemplaza frecuencia y semana avisada. */
    suspend fun restore(frequency: HormigaFrequency, lastSentWeek: String?) {
        context.hormigaDataStore.edit { prefs ->
            prefs[freqKey] = frequency.raw
            if (lastSentWeek == null) prefs.remove(lastSentKey)
            else prefs[lastSentKey] = lastSentWeek
        }
    }
}
