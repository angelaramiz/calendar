package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.discreteModeDataStore by preferencesDataStore("discrete_mode")

/** Modo discreto (C10): oculta montos ($•••) al mostrar la app en público. */
class DiscreteModeStore(private val context: Context) {

    private val hiddenKey = booleanPreferencesKey("montos_ocultos")

    val hidden: Flow<Boolean> = context.discreteModeDataStore.data.map { prefs ->
        prefs[hiddenKey] ?: false
    }

    suspend fun snapshot(): Boolean = hidden.first()

    suspend fun set(hidden: Boolean) {
        context.discreteModeDataStore.edit { prefs ->
            prefs[hiddenKey] = hidden
        }
    }
}
