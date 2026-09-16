package com.fintrack.app.data

import com.fintrack.app.data.model.TransactionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Movimiento detectado que no pudo subirse (sin sesión o sin red). */
@Serializable
data class PendingTx(
    val tx: TransactionEntity,
    val detectedAt: Long = System.currentTimeMillis()
)

object PendingTxCodec {

    const val MAX_PENDING = 200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val serializer = ListSerializer(PendingTx.serializer())

    fun encode(list: List<PendingTx>): String = json.encodeToString(serializer, list)

    fun decode(raw: String?): List<PendingTx> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: emptyList()

    /**
     * Agrega evitando duplicados (misma app+monto+tipo+minuto: las
     * notificaciones se re-publican y los reintentos re-encolan).
     * Con tope: lo más viejo sale primero.
     */
    fun enqueue(current: List<PendingTx>, item: PendingTx): List<PendingTx> {
        if (current.any { sameDetection(it, item) }) return current
        return (current + item).takeLast(MAX_PENDING)
    }

    private fun sameDetection(a: PendingTx, b: PendingTx): Boolean =
        a.tx.amount == b.tx.amount &&
            a.tx.type == b.tx.type &&
            a.tx.merchant == b.tx.merchant &&
            a.tx.description == b.tx.description &&
            a.tx.source == b.tx.source &&
            a.detectedAt / 60_000 == b.detectedAt / 60_000
}
