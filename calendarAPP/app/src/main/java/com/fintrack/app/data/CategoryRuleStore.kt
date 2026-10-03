package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fintrack.app.domain.NotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.categoryRuleDataStore by preferencesDataStore("category_rules")

/**
 * Reglas de categorización que aprenden (C5): merchant normalizado →
 * categoría. La primera vez que corriges "Oxxo" a Compras, las siguientes
 * detecciones ya llegan categorizadas (hook en NotificationParser.parse).
 */
class CategoryRuleStore(private val context: Context) {

    private val rulesKey = stringPreferencesKey("category_rules_json")

    val rules: Flow<Map<String, String>> =
        context.categoryRuleDataStore.data.map { prefs ->
            PendingOpCodec.decodeStrings(prefs[rulesKey])
        }

    suspend fun snapshot(): Map<String, String> = rules.first()

    /**
     * Restaura un respaldo: reemplaza todas las reglas. Público para el
     * futuro respaldo (BackupManager), hoy solo lo usa la UI al corregir.
     */
    suspend fun restore(rules: Map<String, String>) {
        context.categoryRuleDataStore.edit { prefs ->
            prefs[rulesKey] = PendingOpCodec.encodeStrings(rules)
        }
    }

    /** Guarda merchant→categoría (llave normalizada; ignora vacíos). */
    suspend fun putRule(merchant: String, category: String) {
        val key = NotificationParser.normalizeKey(merchant)
        val cleanCategory = category.trim()
        if (key.isBlank() || cleanCategory.isEmpty()) return
        context.categoryRuleDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeStrings(prefs[rulesKey]).toMutableMap()
            current[key] = cleanCategory
            prefs[rulesKey] = PendingOpCodec.encodeStrings(current)
        }
    }

    suspend fun clearRule(merchant: String) {
        val key = NotificationParser.normalizeKey(merchant)
        if (key.isBlank()) return
        context.categoryRuleDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeStrings(prefs[rulesKey]).toMutableMap()
            current.remove(key)
            prefs[rulesKey] = PendingOpCodec.encodeStrings(current)
        }
    }

    suspend fun clear() {
        context.categoryRuleDataStore.edit { prefs ->
            prefs.remove(rulesKey)
        }
    }
}
