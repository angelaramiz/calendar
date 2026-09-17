package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.walletDataStore by preferencesDataStore("wallets")

/**
 * Billeteras locales + overrides manuales por movimiento.
 * Sin columna en el servidor: todo vive en el teléfono.
 */
class WalletStore(private val context: Context) {

    private val walletsKey = stringPreferencesKey("wallets_json")
    private val overridesKey = stringPreferencesKey("wallet_overrides_json")
    private val lastKey = stringPreferencesKey("wallet_last")

    private val walletListSerializer =
        kotlinx.serialization.builtins.ListSerializer(WalletRow.serializer())

    val wallets: Flow<List<WalletRow>> = context.walletDataStore.data.map { prefs ->
        prefs[walletsKey]?.let { raw ->
            runCatching {
                PendingOpCodec.json.decodeFromString(walletListSerializer, raw)
            }.getOrNull()
        } ?: emptyList()
    }

    val overrides: Flow<Map<String, String>> =
        context.walletDataStore.data.map { prefs ->
            PendingOpCodec.decodeStrings(prefs[overridesKey])
        }

    /** Fusiona las billeteras por defecto sin borrar las del usuario. */
    suspend fun ensureDefaults() {
        context.walletDataStore.edit { prefs ->
            val current = prefs[walletsKey]?.let { raw ->
                runCatching {
                    PendingOpCodec.json.decodeFromString(walletListSerializer, raw)
                }.getOrNull()
            } ?: emptyList()
            val ids = current.map { it.id }.toSet()
            val missing = com.fintrack.app.domain.WalletResolver.DEFAULT_WALLETS
                .filter { it.id !in ids }
                .map { WalletRow(it.id, it.name, it.packages) }
            if (missing.isNotEmpty()) {
                prefs[walletsKey] = PendingOpCodec.json.encodeToString(
                    walletListSerializer, current + missing
                )
            }
        }
    }

    suspend fun snapshot(): List<WalletRow> = wallets.first()

    suspend fun overridesSnapshot(): Map<String, String> = overrides.first()

    suspend fun setOverride(key: String, walletId: String) {
        context.walletDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeStrings(prefs[overridesKey]).toMutableMap()
            current[key] = walletId
            prefs[overridesKey] = PendingOpCodec.encodeStrings(current)
        }
    }

    suspend fun setLast(walletId: String) {
        context.walletDataStore.edit { prefs ->
            prefs[lastKey] = walletId
        }
    }

    suspend fun lastSnapshot(): String? =
        context.walletDataStore.data.map { it[lastKey] }.first()
}

@kotlinx.serialization.Serializable
data class WalletRow(
    val id: String,
    val name: String,
    val packages: List<String> = emptyList()
)

fun WalletRow.toResolver() = com.fintrack.app.domain.WalletResolver.Wallet(id, name, packages)
