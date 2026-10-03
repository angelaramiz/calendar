package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupPlannerTest {

    @Test
    fun remoto_mas_nuevo_pide_bajar_y_no_subir() {
        assertTrue(CloudBackupPlanner.shouldDownload(localUpdatedAt = 1000L, remoteUpdatedAt = 2000L))
        assertFalse(CloudBackupPlanner.shouldUpload(localUpdatedAt = 1000L, remoteUpdatedAt = 2000L))
    }

    @Test
    fun local_mas_nuevo_pide_subir_y_no_bajar() {
        assertTrue(CloudBackupPlanner.shouldUpload(localUpdatedAt = 3000L, remoteUpdatedAt = 2000L))
        assertFalse(CloudBackupPlanner.shouldDownload(localUpdatedAt = 3000L, remoteUpdatedAt = 2000L))
    }

    @Test
    fun timestamps_iguales_no_hace_nada() {
        assertFalse(CloudBackupPlanner.shouldUpload(localUpdatedAt = 2000L, remoteUpdatedAt = 2000L))
        assertFalse(CloudBackupPlanner.shouldDownload(localUpdatedAt = 2000L, remoteUpdatedAt = 2000L))
    }

    @Test
    fun sin_remoto_pide_subir_si_hay_local() {
        assertTrue(CloudBackupPlanner.shouldUpload(localUpdatedAt = 1000L, remoteUpdatedAt = null))
        assertFalse(CloudBackupPlanner.shouldDownload(localUpdatedAt = 1000L, remoteUpdatedAt = null))
    }

    @Test
    fun sin_local_pide_bajar_si_hay_remoto() {
        assertTrue(CloudBackupPlanner.shouldDownload(localUpdatedAt = null, remoteUpdatedAt = 1000L))
        assertFalse(CloudBackupPlanner.shouldUpload(localUpdatedAt = null, remoteUpdatedAt = 1000L))
    }

    @Test
    fun sin_ninguno_no_hace_nada() {
        assertFalse(CloudBackupPlanner.shouldUpload(localUpdatedAt = null, remoteUpdatedAt = null))
        assertFalse(CloudBackupPlanner.shouldDownload(localUpdatedAt = null, remoteUpdatedAt = null))
    }

    @Test
    fun schema_actual_es_compatible() {
        assertTrue(CloudBackupPlanner.isCompatible(CLOUD_BACKUP_SCHEMA_V))
        assertTrue(CloudBackupPlanner.isCompatible(CLOUD_BACKUP_SCHEMA_V - 1))
    }

    @Test
    fun schema_futuro_no_es_compatible_no_se_aplica() {
        assertFalse(CloudBackupPlanner.isCompatible(CLOUD_BACKUP_SCHEMA_V + 1))
        assertEquals(1, CLOUD_BACKUP_SCHEMA_V)
    }

    @Test
    fun sanitize_pasa_json_sin_binarios() {
        // Filtro documentado §B/C9: las fotos futuras NUNCA entran al payload
        // nube. Hoy BackupManager solo emite JSON, así que pasa tal cual.
        val json = """{"version":1,"caps":{"Comida":3000.0}}"""
        assertEquals(json, sanitizeCloudJson(json))
    }
}
