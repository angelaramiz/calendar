package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fintrack.app.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pendingTxDataStore by preferencesDataStore("pending_tx")

/**
 * Cola local de detecciones que no pudieron subirse (sin sesión o sin red).
 * El listener encola aquí y la app las sincroniza al recuperar sesión:
 * ninguna detección se pierde por token expirado.
 */
class PendingTxStore(private val context: Context) {

    private val queueKey = stringPreferencesKey("pending_tx_json")

    val pending: Flow<List<PendingTx>> = context.pendingTxDataStore.data.map { prefs ->
        PendingTxCodec.decode(prefs[queueKey])
    }

    suspend fun snapshot(): List<PendingTx> = pending.first()

    suspend fun count(): Int = snapshot().size

    /** Encola y devuelve el total en cola. */
    suspend fun enqueue(tx: TransactionEntity): Int {
        var total = 0
        context.pendingTxDataStore.edit { prefs ->
            val updated = PendingTxCodec.enqueue(
                PendingTxCodec.decode(prefs[queueKey]),
                PendingTx(tx)
            )
            prefs[queueKey] = PendingTxCodec.encode(updated)
            total = updated.size
        }
        return total
    }

    suspend fun removeAll(toRemove: List<PendingTx>) {
        if (toRemove.isEmpty()) return
        context.pendingTxDataStore.edit { prefs ->
            val remaining = PendingTxCodec.decode(prefs[queueKey]) - toRemove.toSet()
            prefs[queueKey] = PendingTxCodec.encode(remaining)
        }
    }

    suspend fun clear() {
        context.pendingTxDataStore.edit { prefs ->
            prefs.remove(queueKey)
        }
    }
}
