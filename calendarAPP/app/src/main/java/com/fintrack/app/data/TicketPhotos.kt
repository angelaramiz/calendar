package com.fintrack.app.data

import java.io.File

/**
 * Fotos de tickets (C9): solo almacenamiento interno `filesDir/tickets`.
 *
 * EXCLUIDAS del respaldo por diseño (§B del roadmap: el payload nube es JSON
 * y jamás lleva binarios; `CredentialStore` tampoco viaja): este helper es un
 * objeto plano sobre archivos, NO un DataStore, así que no suma sección a
 * `LocalBackup`/`BackupManager` (regla: sin stores nuevos ⇒ sin secciones de
 * respaldo). Si algún día el payload nube necesitara referenciar fotos, el
 * filtro ya está documentado aquí: solo viajan monto/comercio (texto).
 */
object TicketPhotos {

    const val DIR_NAME = "tickets"

    /** Directorio interno (lo crea si falta). */
    fun dir(filesDir: File): File =
        File(filesDir, DIR_NAME).also { it.mkdirs() }

    /** Archivo nuevo `ticket-<epoch>.jpg` listo para el intent de cámara. */
    fun newFile(filesDir: File): File =
        File(dir(filesDir), "ticket-${System.currentTimeMillis()}.jpg").also {
            runCatching {
                if (it.exists()) it.delete()
                it.createNewFile()
            }
        }

    /** Fotos guardadas, de la más reciente a la más vieja. */
    fun list(filesDir: File): List<File> =
        dir(filesDir).listFiles { f -> f.isFile && f.name.endsWith(".jpg") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)
}
