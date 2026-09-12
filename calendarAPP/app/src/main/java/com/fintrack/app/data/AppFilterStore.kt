package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fintrack.app.domain.NotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appFilterDataStore by preferencesDataStore("app_filter")

/**
 * Allowlist de paquetes escuchados por el listener + paquetes vistos
 * recientemente (para descubrir el ID real del banco sin QUERY_ALL_PACKAGES).
 */
class AppFilterStore(private val context: Context) {

    private val allowedKey = stringSetPreferencesKey("allowed_packages")
    private val seenKey = stringSetPreferencesKey("seen_packages")

    val allowedPackages: Flow<Set<String>> = context.appFilterDataStore.data.map { prefs ->
        prefs[allowedKey] ?: NotificationParser.DEFAULT_PACKAGES
    }

    val seenPackages: Flow<List<String>> = context.appFilterDataStore.data.map { prefs ->
        (prefs[seenKey] ?: emptySet()).sorted()
    }

    suspend fun allowedSnapshot(): Set<String> =
        context.appFilterDataStore.data.map { prefs ->
            prefs[allowedKey] ?: NotificationParser.DEFAULT_PACKAGES
        }.first()

    suspend fun setAllowed(packageName: String, allowed: Boolean) {
        context.appFilterDataStore.edit { prefs ->
            val current = (prefs[allowedKey] ?: NotificationParser.DEFAULT_PACKAGES).toMutableSet()
            if (allowed) current.add(packageName) else current.remove(packageName)
            prefs[allowedKey] = current
        }
    }

    suspend fun addCustom(packageName: String) {
        val clean = packageName.trim()
        if (clean.isEmpty()) return
        context.appFilterDataStore.edit { prefs ->
            val current = (prefs[allowedKey] ?: NotificationParser.DEFAULT_PACKAGES).toMutableSet()
            current.add(clean)
            prefs[allowedKey] = current
        }
    }

    suspend fun recordSeen(packageName: String) {
        context.appFilterDataStore.edit { prefs ->
            val current = (prefs[seenKey] ?: emptySet()).toMutableSet()
            if (current.add(packageName) && current.size > 20) {
                prefs[seenKey] = current.sorted().takeLast(20).toSet()
            } else {
                prefs[seenKey] = current
            }
        }
    }
}
