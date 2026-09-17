package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pendingOpDataStore by preferencesDataStore("pending_ops")

/** Bandeja de salida de operaciones manuales pendientes de subir. */
class PendingOpStore(private val context: Context) {

    private val opsKey = stringPreferencesKey("pending_ops_json")

    val ops: Flow<List<PendingOp>> = context.pendingOpDataStore.data.map { prefs ->
        PendingOpCodec.decode(prefs[opsKey])
    }

    suspend fun snapshot(): List<PendingOp> = ops.first()

    suspend fun count(): Int = snapshot().size

    suspend fun enqueue(op: PendingOp) {
        context.pendingOpDataStore.edit { prefs ->
            prefs[opsKey] = PendingOpCodec.encode(
                PendingOpCodec.enqueue(PendingOpCodec.decode(prefs[opsKey]), op)
            )
        }
    }

    suspend fun removeAll(toRemove: List<PendingOp>) {
        if (toRemove.isEmpty()) return
        context.pendingOpDataStore.edit { prefs ->
            val remaining = PendingOpCodec.decode(prefs[opsKey]) - toRemove.toSet()
            prefs[opsKey] = PendingOpCodec.encode(remaining)
        }
    }

    suspend fun clear() {
        context.pendingOpDataStore.edit { prefs ->
            prefs.remove(opsKey)
        }
    }
}
