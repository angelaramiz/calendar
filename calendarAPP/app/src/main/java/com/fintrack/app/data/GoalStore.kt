package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fintrack.app.domain.SavingsGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.goalDataStore by preferencesDataStore("savings_goals")

/**
 * Persistencia de las metas de ahorro en DataStore como JSON.
 * Si no hay metas guardadas (o el JSON esta corrupto) se expone lista vacía:
 * las metas ya no se pierden al cerrar la app.
 */
class GoalStore(private val context: Context) {

    private val goalsKey = stringPreferencesKey("savings_goals_json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val listSerializer = ListSerializer(SavingsGoal.serializer())

    val goals: Flow<List<SavingsGoal>> = context.goalDataStore.data.map { prefs ->
        prefs[goalsKey]?.let { raw ->
            runCatching { json.decodeFromString(listSerializer, raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun snapshot(): List<SavingsGoal> = goals.first()

    suspend fun save(goals: List<SavingsGoal>) {
        context.goalDataStore.edit { prefs ->
            prefs[goalsKey] = json.encodeToString(listSerializer, goals)
        }
    }

    suspend fun clear() {
        context.goalDataStore.edit { prefs ->
            prefs.remove(goalsKey)
        }
    }
}
