package com.fintrack.app.data.repository

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.SupabaseClientProvider
import com.fintrack.app.domain.findDuplicateTx
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TransactionRepository {

    private val db get() = SupabaseClientProvider.client

    /**
     * Caché de sesión: todas las pantallas pedían las mismas 200 filas en
     * cada entrada/refresh. TTL 60 s; se invalida/actualiza al escribir.
     */
    private var cache: List<TransactionEntity>? = null
    private var cacheAt: Long = 0L
    private var cacheUser: String = ""
    private val cacheTtlMs = 60_000L

    /** Último insert de esta sesión: el reintento tras respuesta perdida
     *  se resuelve en memoria sin ningún fetch (ver [insertTransaction]). */
    private var lastInsertKey: String? = null
    private var lastInsertResult: TransactionEntity? = null

    fun invalidate() {
        cache = null
        cacheAt = 0L
    }

    suspend fun getTransactions(
        userId: String,
        forceRefresh: Boolean = false
    ): List<TransactionEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hit = !forceRefresh && cache != null && cacheUser == userId &&
            (now - cacheAt) < cacheTtlMs
        if (hit) return@withContext cache!!
        db.from("fintrack_transactions").select {
            filter { eq("user_id", userId) }
            order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            // Ventana amplia: suscripciones (6 meses) y promedios la necesitan.
            limit(200)
        }.decodeList<TransactionEntity>().also {
            cache = it
            cacheAt = now
            cacheUser = userId
        }
    }

    private fun insertKeyOf(tx: TransactionEntity): String =
        listOf(
            tx.amount.toString(), tx.type, tx.category,
            tx.description, tx.timestamp.toString(), tx.source,
            tx.merchant ?: ""
        ).joinToString("|")

    suspend fun insertTransaction(userId: String, transaction: TransactionEntity): TransactionEntity = withContext(Dispatchers.IO) {
        val key = insertKeyOf(transaction)
        // 1) Reintento en memoria: misma sesión, cero red.
        if (key == lastInsertKey) {
            lastInsertResult?.let { return@withContext it }
        }
        // 2) Anti-duplicado contra caché (sin fetch si está fresca).
        val known = cache?.takeIf { cacheUser == userId }
            ?: runCatching { getTransactions(userId) }.getOrNull()
        known?.let { existing ->
            findDuplicateTx(transaction, existing)?.let {
                lastInsertKey = key
                lastInsertResult = it
                return@withContext it
            }
        }
        val data = buildJsonObject {
            put("user_id", userId)
            put("amount", transaction.amount)
            put("type", transaction.type)
            put("category", transaction.category)
            put("description", transaction.description)
            transaction.merchant?.let { put("merchant", it) }
            put("timestamp", transaction.timestamp)
            put("source", transaction.source)
        }
        db.from("fintrack_transactions").insert(data) { select() }.decodeSingle<TransactionEntity>().also { saved ->
            // Pintado instantáneo: la lista en memoria ya trae la nueva fila.
            cache = ((cache?.takeIf { cacheUser == userId } ?: emptyList()) + saved)
                .sortedByDescending { it.timestamp }.take(200)
            cacheAt = System.currentTimeMillis()
            cacheUser = userId
            lastInsertKey = key
            lastInsertResult = saved
        }
    }

    /**
     * Borrado con ownership: filtra por id + user_id para que ni con un
     * UUID ajeno se toque una fila de otro usuario (defensa en el cliente;
     * RLS en el servidor es la otra mitad).
     */
    suspend fun deleteTransaction(userId: String, id: String) = withContext(Dispatchers.IO) {
        db.from("fintrack_transactions").delete {
            filter {
                eq("id", id)
                eq("user_id", userId)
            }
        }.also {
            cache = cache?.filterNot { it.id == id }
        }
    }

    suspend fun updateTransaction(userId: String, id: String, transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        val data = buildJsonObject {
            put("amount", transaction.amount)
            put("type", transaction.type)
            put("category", transaction.category)
            put("description", transaction.description)
            transaction.merchant?.let { put("merchant", it) }
        }
        db.from("fintrack_transactions").update(data) {
            filter {
                eq("id", id)
                eq("user_id", userId)
            }
        }.also {
            cache = cache?.map { if (it.id == id) it.copyWith(transaction) else it }
        }
    }

    private fun TransactionEntity.copyWith(other: TransactionEntity) = copy(
        amount = other.amount,
        type = other.type,
        category = other.category,
        description = other.description,
        merchant = other.merchant
    )
}
