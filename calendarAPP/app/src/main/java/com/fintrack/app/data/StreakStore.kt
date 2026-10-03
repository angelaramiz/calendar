package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fintrack.app.domain.StreakPlanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.streakDataStore by preferencesDataStore("streaks")

/**
 * Rachas de quincenas bajo tope por categoria (D11).
 * La logica vive en [StreakPlanner]; aqui solo persiste.
 */
class StreakStore(private val context: Context) {

    private val streaksKey = stringPreferencesKey("streaks_json")
    private val lastPeriodKey = stringPreferencesKey("streaks_last_period")

    suspend fun snapshot(): Map<String, Int> =
        context.streakDataStore.data.map { prefs ->
            PendingOpCodec.decodeStrings(prefs[streaksKey])
                .mapValues { (_, v) -> v.toIntOrNull() ?: 0 }
        }.first()

    suspend fun lastPeriod(): String? =
        context.streakDataStore.data.map { prefs -> prefs[lastPeriodKey] }.first()

    /**
     * Registra el resultado de una quincena cerrada. Idempotente por
     * periodo: repetir el mismo [period] no cuenta doble.
     */
    suspend fun recordPeriod(period: String, under: Map<String, Boolean>) {
        context.streakDataStore.edit { prefs ->
            val last = prefs[lastPeriodKey]
            if (last == period) return@edit
            val current = PendingOpCodec.decodeStrings(prefs[streaksKey])
                .mapValues { (_, v) -> v.toIntOrNull() ?: 0 }
            val (next, _) = StreakPlanner.nextStreaks(current, last, period, under)
            prefs[streaksKey] = PendingOpCodec.encodeStrings(
                next.mapValues { (_, v) -> v.toString() }
            )
            prefs[lastPeriodKey] = period
        }
    }

    /** Restaura un respaldo: reemplaza rachas y periodo. */
    suspend fun restore(streaks: Map<String, Int>, lastPeriod: String?) {
        context.streakDataStore.edit { prefs ->
            prefs[streaksKey] = PendingOpCodec.encodeStrings(
                streaks.mapValues { (_, v) -> v.toString() }
            )
            if (lastPeriod == null) prefs.remove(lastPeriodKey)
            else prefs[lastPeriodKey] = lastPeriod
        }
    }
}
