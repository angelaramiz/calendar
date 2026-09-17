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

private val Context.creditCardDataStore by preferencesDataStore("credit_cards")

@Serializable
data class CreditCardRow(
    val id: String,
    /** Nombre/banco de la tarjeta (ej. Nu, Plata). */
    val name: String,
    /** Día de corte (1-31). */
    val cutoffDay: Int,
    /** Día de pago (1-31). */
    val paymentDay: Int
)

/**
 * Tarjetas de crédito + tag de cargos (local, sin columna en el servidor).
 * Clave de cargo: "tx:<id>" o "mov:<id>".
 */
class CreditCardStore(private val context: Context) {

    private val cardsKey = stringPreferencesKey("credit_cards_json")
    private val chargesKey = stringPreferencesKey("credit_charges_json")

    private val listSerializer = ListSerializer(CreditCardRow.serializer())

    val cards: Flow<List<CreditCardRow>> = context.creditCardDataStore.data.map { prefs ->
        prefs[cardsKey]?.let { raw ->
            runCatching { PendingOpCodec.json.decodeFromString(listSerializer, raw) }.getOrNull()
        } ?: emptyList()
    }

    val charges: Flow<Map<String, String>> = context.creditCardDataStore.data.map { prefs ->
        PendingOpCodec.decodeStrings(prefs[chargesKey])
    }

    suspend fun cardsSnapshot(): List<CreditCardRow> = cards.first()

    suspend fun chargesSnapshot(): Map<String, String> = charges.first()

    suspend fun upsertCard(card: CreditCardRow) {
        context.creditCardDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeCards(prefs[cardsKey]).toMutableList()
            val index = current.indexOfFirst { it.id == card.id }
            if (index >= 0) current[index] = card else current.add(card)
            prefs[cardsKey] = PendingOpCodec.encodeCards(current)
        }
    }

    suspend fun deleteCard(id: String) {
        context.creditCardDataStore.edit { prefs ->
            val remaining = PendingOpCodec.decodeCards(prefs[cardsKey])
                .filterNot { it.id == id }
            prefs[cardsKey] = PendingOpCodec.encodeCards(remaining)
            // Sus cargos vuelven a ser gasto normal.
            val charges = PendingOpCodec.decodeStrings(prefs[chargesKey])
                .filterValues { it != id }
            prefs[chargesKey] = PendingOpCodec.encodeStrings(charges)
        }
    }

    suspend fun setCharge(key: String, cardId: String?) {
        context.creditCardDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeStrings(prefs[chargesKey]).toMutableMap()
            if (cardId == null) current.remove(key) else current[key] = cardId
            prefs[chargesKey] = PendingOpCodec.encodeStrings(current)
        }
    }
}
