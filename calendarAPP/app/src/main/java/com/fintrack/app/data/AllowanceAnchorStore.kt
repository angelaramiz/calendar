package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.allowanceAnchorDataStore by preferencesDataStore("daily_allowance")

/**
 * D6 Ancla de ingreso para el límite diario: nómina quincenal o monto
 * manual que el usuario edita en Inicio. Sin ancla no se muestra nada
 * (nunca se inventa un ingreso).
 */
class AllowanceAnchorStore(private val context: Context) {

    private val incomeKey = doublePreferencesKey("quincena_ingreso")
    private val savingsKey = doublePreferencesKey("quincena_apartado_ahorro")

    /** null = sin ancla (ocultar sección). */
    val biweeklyIncome: Flow<Double?> = context.allowanceAnchorDataStore.data.map { prefs ->
        prefs[incomeKey]?.takeIf { it > 0.0 }
    }

    /** Apartado de ahorro por quincena (meta D4). 0 = sin apartado. */
    val savingsShare: Flow<Double> = context.allowanceAnchorDataStore.data.map { prefs ->
        prefs[savingsKey]?.coerceAtLeast(0.0) ?: 0.0
    }

    suspend fun snapshotIncome(): Double? = biweeklyIncome.first()

    suspend fun snapshotSavings(): Double = savingsShare.first()

    /** null o <= 0 borra el ancla. */
    suspend fun setIncome(monto: Double?) {
        context.allowanceAnchorDataStore.edit { prefs ->
            if (monto != null && monto > 0.0) prefs[incomeKey] = monto
            else prefs.remove(incomeKey)
        }
    }

    suspend fun setSavingsShare(monto: Double) {
        context.allowanceAnchorDataStore.edit { prefs ->
            prefs[savingsKey] = monto.coerceAtLeast(0.0)
        }
    }
}
