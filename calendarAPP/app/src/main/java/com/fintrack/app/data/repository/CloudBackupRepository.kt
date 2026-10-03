package com.fintrack.app.data.repository

import com.fintrack.app.data.remote.SupabaseClientProvider
import com.fintrack.app.domain.SealedPayload
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Respaldo en nube cifrada (§B): una fila por usuario en la tabla existente
 * `public.fintrack_backups(user_id PK, updated_at, schema_v, payload)`.
 * El `payload` es el [SealedPayload] serializado (JSON cifrado en base64);
 * el servidor solo ve sal en claro + bytes opacos. RLS `auth.uid()=user_id`.
 * Espeja el patrón de `PatternRepository`/`TransactionRepository`: cliente vía
 * [SupabaseClientProvider], inserts con `buildJsonObject { put(...) }`.
 */
@Serializable
data class CloudBackupRow(
    val user_id: String = "",
    val updated_at: String = "",
    val schema_v: Int = 1,
    val payload: String = ""
)

class CloudBackupRepository {

    private val db get() = SupabaseClientProvider.client
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val TABLE = "fintrack_backups"
    }

    /**
     * Meta remota o null si el usuario nunca subió. Triple:
     * (updatedAt en epoch-millis, schemaV, payload sellado).
     * Payload ilegible (fila corrupta) = null, como si no hubiera fila.
     */
    suspend fun fetchMeta(userId: String): Triple<Long, Int, SealedPayload>? =
        withContext(Dispatchers.IO) {
            val rows = db.from(TABLE).select {
                filter { eq("user_id", userId) }
                limit(1)
            }.decodeList<CloudBackupRow>()
            val row = rows.firstOrNull() ?: return@withContext null
            val sealed = runCatching {
                json.decodeFromString(SealedPayload.serializer(), row.payload)
            }.getOrNull() ?: return@withContext null
            val updatedAt = runCatching {
                java.time.Instant.parse(row.updated_at).toEpochMilli()
            }.getOrNull() ?: return@withContext null
            Triple(updatedAt, row.schema_v, sealed)
        }

    /** Upsert por `user_id` con `updated_at=now()` del servidor. */
    suspend fun push(userId: String, sealed: SealedPayload, schemaV: Int) =
        withContext(Dispatchers.IO) {
            val data = buildJsonObject {
                put("user_id", userId)
                put("updated_at", java.time.Instant.now().toString())
                put("schema_v", schemaV)
                put("payload", json.encodeToString(SealedPayload.serializer(), sealed))
            }
            db.from(TABLE).upsert(data)
        }
}
