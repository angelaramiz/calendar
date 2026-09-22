package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore("onboarding")

/** Marca si ya se mostró el asistente inicial de permisos (solo una vez). */
class OnboardingStore(private val context: Context) {

    private val doneKey = booleanPreferencesKey("onboarding_done")

    suspend fun isDone(): Boolean =
        context.onboardingDataStore.data.map { it[doneKey] ?: false }.first()

    suspend fun markDone() {
        context.onboardingDataStore.edit { it[doneKey] = true }
    }
}
