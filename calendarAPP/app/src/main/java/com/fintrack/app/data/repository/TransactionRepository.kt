package com.fintrack.app.data.repository

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TransactionRepository {

    private val db get() = SupabaseClientProvider.client

    suspend fun getTransactions(userId: String): List<TransactionEntity> = withContext(Dispatchers.IO) {
        db.from("fintrack_transactions").select {
            filter { eq("user_id", userId) }
            order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(50)
        }.decodeList<TransactionEntity>()
    }

    suspend fun insertTransaction(userId: String, transaction: TransactionEntity): TransactionEntity = withContext(Dispatchers.IO) {
        val data = buildJsonObject {
            put("user_id", userId)
            put("amount", transaction.amount)
            put("type", transaction.type)
            put("category", transaction.category)
            put("description", transaction.description)
            put("timestamp", transaction.timestamp)
            put("source", transaction.source)
        }
        db.from("fintrack_transactions").insert(data) { select() }.decodeSingle<TransactionEntity>()
    }

    suspend fun deleteTransaction(id: String) = withContext(Dispatchers.IO) {
        db.from("fintrack_transactions").delete {
            filter { eq("id", id) }
        }
    }
}
