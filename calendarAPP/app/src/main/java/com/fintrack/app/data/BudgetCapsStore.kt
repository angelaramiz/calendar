package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.budgetCapsDataStore by preferencesDataStore("budget_caps")

/** Topes mensuales por categoría definidos por el usuario (reemplazan los automáticos). */
class BudgetCapsStore(private val context: Context) {

    private val capsKey = stringPreferencesKey("budget_caps_json")

    private val mapSerializer = MapSerializer(String.serializer(), Double.serializer())

    val caps: Flow<Map<String, Double>> = context.budgetCapsDataStore.data.map { prefs ->
        prefs[capsKey]?.let { raw ->
            runCatching { PendingOpCodec.json.decodeFromString(mapSerializer, raw) }.getOrNull()
        } ?: emptyMap()
    }

    suspend fun snapshot(): Map<String, Double> = caps.first()

    suspend fun setCap(category: String, cap: Double) {
        context.budgetCapsDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeCaps(prefs[capsKey]).toMutableMap()
            if (cap > 0.0) current[category] = cap else current.remove(category)
            prefs[capsKey] = PendingOpCodec.encodeCaps(current)
        }
    }

    suspend fun clear() {
        context.budgetCapsDataStore.edit { prefs ->
            prefs.remove(capsKey)
        }
    }
}
