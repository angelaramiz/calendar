package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudBackupDataStore by preferencesDataStore("cloud_backup")

/**
 * Marca local del respaldo en nube: epoch-millis del último sync exitoso
 * (subida o bajada). Es el "reloj local" que [com.fintrack.app.domain.CloudBackupPlanner]
 * compara contra el `updated_at` remoto (last-write-wins; igual = nada).
 */
class CloudBackupStore(private val context: Context) {

    private val lastSyncKey = longPreferencesKey("last_sync_millis")

    /** null = nunca se sincronizó en este teléfono. */
    suspend fun lastSync(): Long? =
        context.cloudBackupDataStore.data.map { it[lastSyncKey] }.first()

    suspend fun markSynced(atMillis: Long = System.currentTimeMillis()) {
        context.cloudBackupDataStore.edit { it[lastSyncKey] = atMillis }
    }

    suspend fun clear() {
        context.cloudBackupDataStore.edit { it.remove(lastSyncKey) }
    }
}
