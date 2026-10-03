package com.fintrack.app.domain

/** Versión del esquema del respaldo en nube (columna `schema_v`). */
const val CLOUD_BACKUP_SCHEMA_V = 1

/**
 * Decisión pura de sincronización: last-write-wins por `updated_at`.
 * `null` = "nunca" (sin fila remota o sin marca local). Timestamps iguales
 * = nada que hacer.
 */
object CloudBackupPlanner {

    fun shouldUpload(localUpdatedAt: Long?, remoteUpdatedAt: Long?): Boolean {
        if (localUpdatedAt == null) return false
        if (remoteUpdatedAt == null) return true
        return localUpdatedAt > remoteUpdatedAt
    }

    fun shouldDownload(localUpdatedAt: Long?, remoteUpdatedAt: Long?): Boolean {
        if (remoteUpdatedAt == null) return false
        if (localUpdatedAt == null) return true
        return remoteUpdatedAt > localUpdatedAt
    }

    /**
     * Un `schema_v` mayor al conocido NO se aplica: se avisa al usuario
     * (actualizar la app) en vez de romper el restore.
     */
    fun isCompatible(schemaV: Int): Boolean = schemaV <= CLOUD_BACKUP_SCHEMA_V
}

/**
 * Filtro documentado del payload nube (§B + C9): las fotos/tickets binarios
 * futuros NUNCA viajan (solo JSON, como hoy emite `BackupManager`).
 * Hoy no hay fotos, así que el JSON pasa tal cual; cuando exista OCR (C9),
 * aquí se eliminan los campos binarios antes de cifrar.
 */
fun sanitizeCloudJson(plainJson: String): String = plainJson
