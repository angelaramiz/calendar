package com.calendarfinance.app.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_NAME = stringPreferencesKey("name")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    suspend fun saveSession(userId: String, email: String, username: String = "", name: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_EMAIL] = email
            prefs[KEY_USERNAME] = username
            prefs[KEY_NAME] = name
        }
    }

    suspend fun getSession(): SessionData? {
        return context.dataStore.data.map { prefs ->
            val userId = prefs[KEY_USER_ID] ?: return@map null
            SessionData(
                userId = userId,
                email = prefs[KEY_EMAIL] ?: "",
                username = prefs[KEY_USERNAME] ?: "",
                name = prefs[KEY_NAME] ?: ""
            )
        }.first()
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_NAME)
            prefs[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun isBiometricEnabled(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] ?: false
        }.first()
    }
}

data class SessionData(
    val userId: String,
    val email: String,
    val username: String,
    val name: String
)
