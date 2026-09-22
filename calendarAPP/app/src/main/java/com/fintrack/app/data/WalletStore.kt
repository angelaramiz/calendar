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
    /** Ids de fijas que el usuario quitó (para no revivirlas en ensureDefaults). */
    private val hiddenKey = stringPreferencesKey("wallets_hidden_json")

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

    /** Fusiona las billeteras por defecto sin borrar las del usuario ni revivir quitadas. */
    suspend fun ensureDefaults() {
        context.walletDataStore.edit { prefs ->
            val current = prefs[walletsKey]?.let { raw ->
                runCatching {
                    PendingOpCodec.json.decodeFromString(walletListSerializer, raw)
                }.getOrNull()
            } ?: emptyList()
            val hidden = PendingOpCodec.decodeStringList(prefs[hiddenKey])
            val ids = current.map { it.id }.toSet()
            val missing = com.fintrack.app.domain.WalletResolver.DEFAULT_WALLETS
                .filter { it.id !in ids && it.id !in hidden }
                .map {
                    WalletRow(
                        it.id, it.name, it.packages, custom = false,
                        kind = if (it.id == com.fintrack.app.domain.WalletResolver.EFECTIVO_ID) "Efectivo" else ""
                    )
                }
            if (missing.isNotEmpty()) {
                prefs[walletsKey] = PendingOpCodec.json.encodeToString(
                    walletListSerializer, current + missing
                )
            }
        }
    }

    /** Agrega una billetera propia (ej. Mercado Pago + terminación de débito) y devuelve su id. */
    suspend fun addWallet(name: String, last4: String = "", kind: String = ""): String {
        val id = "wallet-${System.currentTimeMillis()}"
        context.walletDataStore.edit { prefs ->
            val current = prefs[walletsKey]?.let { raw ->
                runCatching {
                    PendingOpCodec.json.decodeFromString(walletListSerializer, raw)
                }.getOrNull()
            } ?: emptyList()
            prefs[walletsKey] = PendingOpCodec.json.encodeToString(
                walletListSerializer,
                current + WalletRow(
                    id, name.trim(), emptyList(), custom = true,
                    last4 = last4.filter { it.isDigit() }.take(4),
                    kind = kind.trim()
                )
            )
        }
        return id
    }

    /**
     * Quita cualquier billetera. Si era fija, se marca oculta para que
     * [ensureDefaults] no la reviva (ej. Azteca que no se usa).
     */
    suspend fun deleteWallet(id: String) {
        context.walletDataStore.edit { prefs ->
            val current = prefs[walletsKey]?.let { raw ->
                runCatching {
                    PendingOpCodec.json.decodeFromString(walletListSerializer, raw)
                }.getOrNull()
            } ?: emptyList()
            val removed = current.firstOrNull { it.id == id }
            prefs[walletsKey] = PendingOpCodec.json.encodeToString(
                walletListSerializer,
                current.filterNot { it.id == id }
            )
            if (removed != null && !removed.custom) {
                prefs[hiddenKey] = PendingOpCodec.encodeStringList(
                    PendingOpCodec.decodeStringList(prefs[hiddenKey]) + id
                )
            }
        }
    }

    suspend fun snapshot(): List<WalletRow> = wallets.first()

    suspend fun hiddenSnapshot(): List<String> =
        context.walletDataStore.data.map { prefs ->
            PendingOpCodec.decodeStringList(prefs[hiddenKey])
        }.first()

    /** Restaura un respaldo: reemplaza billeteras, ocultas y overrides. */
    suspend fun restore(
        wallets: List<WalletRow>,
        hidden: List<String>,
        overrides: Map<String, String>
    ) {
        context.walletDataStore.edit { prefs ->
            prefs[walletsKey] = PendingOpCodec.json.encodeToString(walletListSerializer, wallets)
            prefs[hiddenKey] = PendingOpCodec.encodeStringList(hidden)
            prefs[overridesKey] = PendingOpCodec.encodeStrings(overrides)
        }
    }

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
    val packages: List<String> = emptyList(),
    /** Creada por el usuario (las fijas no se pueden borrar). */
    val custom: Boolean = false,
    /** Terminación de la tarjeta de débito asociada (ej. Mercado Pago "1234"). */
    val last4: String = "",
    /** Tipo de cuenta: Efectivo, Débito, Nómina, Vales, Ahorro, Otra. "" = sin clasificar. */
    val kind: String = ""
) {
    /** "Mercado Pago •1234" o solo el nombre si no hay terminación. */
    val displayName: String get() = if (last4.isBlank()) name else "$name •$last4"
    /** "Mercado Pago •1234 · Nómina" si hay tipo registrado (y distinto del nombre). */
    val displayWithKind: String get() =
        if (kind.isBlank() || kind.equals(name, ignoreCase = true)) displayName
        else "$displayName · $kind"

    companion object {
        /** Tipos ofrecidos al registrar una cuenta (paso 5 del registro rápido). */
        val ACCOUNT_KINDS = listOf("Débito", "Nómina", "Vales", "Ahorro", "Efectivo", "Otra")
    }
}

fun WalletRow.toResolver() = com.fintrack.app.domain.WalletResolver.Wallet(id, name, packages)
