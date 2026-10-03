package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private val Context.msiDataStore by preferencesDataStore("msi_plans")

/**
 * Plan MSI (§A): capa de lectura sobre cargos tagueados. No duplica
 * movimientos ni altera `summarize()`: proyecta cuotas sobre los cortes
 * de su tarjeta y cada cuota se verifica contra cargos tagueados del periodo.
 */
@Serializable
data class MsiPlan(
    val id: String,
    val cardId: String,
    val concepto: String,
    val montoTotal: Double,
    /** Meses del plan (3/6/9/12/18/24, lo que ofrecen los bancos). */
    val meses: Int,
    /** Primer corte del plan (ISO yyyy-MM-dd). */
    val primerCorteIso: String,
    /** Liquidación anticipada: deja de proyectar. */
    val liquidado: Boolean = false
)

/** Planes MSI activos (local, sin DDL). */
class MsiStore(private val context: Context) {

    private val plansKey = stringPreferencesKey("msi_plans_json")

    private val listSerializer = ListSerializer(MsiPlan.serializer())

    val plans: Flow<List<MsiPlan>> = context.msiDataStore.data.map { prefs ->
        prefs[plansKey]?.let { raw ->
            runCatching { PendingOpCodec.json.decodeFromString(listSerializer, raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun snapshot(): List<MsiPlan> = plans.first()

    /** Restaura un respaldo: reemplaza todos los planes MSI. */
    suspend fun restore(plans: List<MsiPlan>) {
        context.msiDataStore.edit { prefs ->
            prefs[plansKey] = PendingOpCodec.json.encodeToString(listSerializer, plans)
        }
    }

    suspend fun upsert(plan: MsiPlan) {
        context.msiDataStore.edit { prefs ->
            val current = decode(prefs[plansKey]).toMutableList()
            val index = current.indexOfFirst { it.id == plan.id }
            if (index >= 0) current[index] = plan else current.add(plan)
            prefs[plansKey] = PendingOpCodec.json.encodeToString(listSerializer, current)
        }
    }

    suspend fun delete(id: String) {
        context.msiDataStore.edit { prefs ->
            prefs[plansKey] = PendingOpCodec.json.encodeToString(
                listSerializer, decode(prefs[plansKey]).filterNot { it.id == id }
            )
        }
    }

    /** Liquidación anticipada: marca liquidado, deja de proyectar. */
    suspend fun liquidar(id: String) {
        context.msiDataStore.edit { prefs ->
            val current = decode(prefs[plansKey]).map {
                if (it.id == id) it.copy(liquidado = true) else it
            }
            prefs[plansKey] = PendingOpCodec.json.encodeToString(listSerializer, current)
        }
    }

    private fun decode(raw: String?): List<MsiPlan> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { PendingOpCodec.json.decodeFromString(listSerializer, raw) }.getOrNull()
            ?: emptyList()
}
