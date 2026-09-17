package com.fintrack.app.data

import com.fintrack.app.data.model.TransactionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Bandeja de salida offline: operaciones manuales que no pudieron subirse
 * (sin sesión o sin red). Se sincronizan en orden al recuperar sesión.
 */
@Serializable
data class PendingOp(
    val id: String,
    val kind: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis()
)

object PendingOpKind {
    const val TX_INSERT = "tx_insert"
    const val TX_UPDATE = "tx_update"
    const val TX_DELETE = "tx_delete"
    const val MOV_INSERT = "mov_insert"
    const val MOV_CONFIRM = "mov_confirm"
    const val PATTERN_INSERT = "pattern_insert"
    const val PATTERN_UPDATE = "pattern_update"
    const val PATTERN_DEACTIVATE = "pattern_deactivate"
}

@Serializable
data class TxInsertPayload(
    val tx: TransactionEntity,
    val walletId: String? = null,
    val cardId: String? = null
)

@Serializable
data class TxUpdatePayload(val id: String, val tx: TransactionEntity)

@Serializable
data class TxIdPayload(val id: String)

@Serializable
data class MovInsertPayload(
    val dateIso: String,
    val isIncome: Boolean,
    val title: String,
    val description: String,
    val category: String,
    val amount: Double,
    val walletId: String? = null,
    val cardId: String? = null
)

@Serializable
data class MovConfirmPayload(
    val patternId: String,
    val isIncome: Boolean,
    val name: String,
    val description: String,
    val category: String,
    val baseAmount: Double,
    val actualAmount: Double,
    val dateIso: String
)

@Serializable
data class PatternOpPayload(
    val patternId: String? = null,
    val isIncome: Boolean,
    val name: String = "",
    val description: String = "",
    val category: String = "Otros",
    val baseAmount: Double = 0.0,
    val frequency: String = "monthly",
    val startDateIso: String = "",
    val endDateIso: String? = null
)

object PendingOpCodec {

    const val MAX_OPS = 200

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val serializer = ListSerializer(PendingOp.serializer())

    fun encode(list: List<PendingOp>): String = json.encodeToString(serializer, list)

    fun decode(raw: String?): List<PendingOp> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: emptyList()

    fun enqueue(current: List<PendingOp>, op: PendingOp): List<PendingOp> =
        (current + op).takeLast(MAX_OPS)

    inline fun <reified T> payload(op: PendingOp): T? =
        runCatching { json.decodeFromString<T>(op.payload) }.getOrNull()

    private val capsSerializer = MapSerializer(String.serializer(), Double.serializer())

    fun encodeCaps(caps: Map<String, Double>): String =
        json.encodeToString(capsSerializer, caps)

    fun decodeCaps(raw: String?): Map<String, Double> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString(capsSerializer, raw) }.getOrNull() ?: emptyMap()

    private val stringsSerializer = MapSerializer(String.serializer(), String.serializer())

    fun encodeStrings(map: Map<String, String>): String =
        json.encodeToString(stringsSerializer, map)

    fun decodeStrings(raw: String?): Map<String, String> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString(stringsSerializer, raw) }.getOrNull() ?: emptyMap()

    private val cardsSerializer =
        ListSerializer(com.fintrack.app.data.CreditCardRow.serializer())

    fun encodeCards(cards: List<com.fintrack.app.data.CreditCardRow>): String =
        json.encodeToString(cardsSerializer, cards)

    fun decodeCards(raw: String?): List<com.fintrack.app.data.CreditCardRow> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(cardsSerializer, raw) }.getOrNull() ?: emptyList()
}
