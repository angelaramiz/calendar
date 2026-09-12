package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fintrack.app.domain.FormulaNode
import com.fintrack.app.domain.IncomeNode
import com.fintrack.app.domain.IncomeSource
import com.fintrack.app.domain.MoneyFlow
import com.fintrack.app.domain.Split
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.flowDataStore by preferencesDataStore("money_flows")

/**
 * Persistencia del flujo de dinero en DataStore como JSON.
 * Si no hay flujo guardado (o el JSON esta corrupto) se expone el
 * flujo de ejemplo "Sueldo -> 50/30/20".
 */
class FlowStore(private val context: Context) {

    private val flowKey = stringPreferencesKey("money_flow_json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val flow: Flow<MoneyFlow> = context.flowDataStore.data.map { prefs ->
        prefs[flowKey]?.let { raw ->
            runCatching { json.decodeFromString<MoneyFlow>(raw) }.getOrNull()
        } ?: sampleFlow()
    }

    suspend fun snapshot(): MoneyFlow = flow.first()

    suspend fun save(flow: MoneyFlow) {
        context.flowDataStore.edit { prefs ->
            prefs[flowKey] = json.encodeToString(MoneyFlow.serializer(), flow)
        }
    }

    suspend fun resetToSample() {
        save(sampleFlow())
    }

    suspend fun clear() {
        context.flowDataStore.edit { prefs ->
            prefs.remove(flowKey)
        }
    }

    companion object {
        fun sampleFlow(): MoneyFlow = MoneyFlow(
            name = "Sueldo -> 50/30/20",
            nodes = listOf(
                IncomeNode(
                    id = "income-sueldo",
                    label = "Sueldo",
                    source = IncomeSource.Fixed(3000.0)
                ),
                FormulaNode(
                    id = "formula-reparto",
                    splits = listOf(
                        Split.Percent(label = "Necesidades", percent = 50.0, category = "Necesidades"),
                        Split.Percent(label = "Gustos", percent = 30.0, category = "Ocio"),
                        Split.Percent(label = "Ahorro", percent = 20.0, category = "Ahorro")
                    )
                )
            )
        )
    }
}
