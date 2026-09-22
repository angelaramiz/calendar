package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    /** Última decisión del detector: "epoch|resultado|paquete|título". */
    private val diagKey = stringPreferencesKey("listener_diag")
    /** Defaults ya sembrados: para fusionar bancos nuevos sin revivir bajas. */
    private val seededKey = stringSetPreferencesKey("seeded_defaults")

    val allowedPackages: Flow<Set<String>> = context.appFilterDataStore.data.map { prefs ->
        prefs[allowedKey] ?: NotificationParser.DEFAULT_PACKAGES
    }

    val lastDecision: Flow<String?> = context.appFilterDataStore.data.map { prefs ->
        prefs[diagKey]
    }

    /**
     * Fusiona bancos nuevos de DEFAULT_PACKAGES en la lista guardada.
     * Respeta las bajas del usuario (solo agrega lo nunca sembrado).
     */
    suspend fun ensureDefaults() {
        context.appFilterDataStore.edit { prefs ->
            val seeded = prefs[seededKey] ?: emptySet()
            val fresh = NotificationParser.DEFAULT_PACKAGES - seeded
            if (fresh.isNotEmpty()) {
                val current = (prefs[allowedKey] ?: NotificationParser.DEFAULT_PACKAGES).toMutableSet()
                current.addAll(fresh)
                prefs[allowedKey] = current
            }
            prefs[seededKey] = NotificationParser.DEFAULT_PACKAGES
        }
    }

    suspend fun recordDecision(line: String) {
        context.appFilterDataStore.edit { prefs ->
            prefs[diagKey] = line
        }
    }

    val seenPackages: Flow<List<String>> = context.appFilterDataStore.data.map { prefs ->
        (prefs[seenKey] ?: emptySet()).sorted()
    }

    suspend fun allowedSnapshot(): Set<String> =
        context.appFilterDataStore.data.map { prefs ->
            prefs[allowedKey] ?: NotificationParser.DEFAULT_PACKAGES
        }.first()

    /** Restaura un respaldo: reemplaza la allowlist exacta. */
    suspend fun replaceAllowed(packages: Set<String>) {
        context.appFilterDataStore.edit { prefs ->
            prefs[allowedKey] = packages
            prefs[seededKey] = NotificationParser.DEFAULT_PACKAGES
        }
    }

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
