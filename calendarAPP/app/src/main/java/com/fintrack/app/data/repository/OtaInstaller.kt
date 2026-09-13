package com.fintrack.app.data.repository

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fintrack.app.R
import java.io.File

/**
 * OTA con progreso visible e instalación asistida.
 *
 * El instalador ya NO se lanza desde el BroadcastReceiver (Android bloquea
 * arrancar actividades en segundo plano y "no pasaba nada"): al completarse
 * se publica una notificación "Toca para instalar" y la app, si está abierta,
 * dispara el instalador en primer plano con [promptInstall].
 */
object OtaInstaller {

    const val CHANNEL_ID = "fintrack_updates"
    private const val NOTIFICATION_ID = 4401

    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun apkFile(appContext: Context, versionName: String): File =
        File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName(versionName))

    private fun fileName(versionName: String) = "fintrack-$versionName.apk"

    /** Encola la descarga y devuelve su ID. Al completarse publica "Toca para instalar". */
    fun enqueueDownload(appContext: Context, apkUrl: String, versionName: String): Long {
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("FinTrack $versionName")
            setDescription("Descargando actualización...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName(versionName))
        }

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                try { appContext.unregisterReceiver(this) } catch (_: Exception) { }
                val file = apkFile(appContext, versionName)
                if (file.exists()) showInstallNotification(appContext, file, versionName)
            }
        }
        ContextCompat.registerReceiver(
            appContext, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return downloadId
    }

    /**
     * Progreso 0..100, 100 si ya terminó, -1 si falló, null si aún no hay datos.
     */
    fun queryProgress(appContext: Context, downloadId: Long): Int? {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_FAILED) return -1
            if (status == DownloadManager.STATUS_SUCCESSFUL) return 100
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            return if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else null
        }
    }

    fun downloadedFile(appContext: Context, versionName: String): File? =
        apkFile(appContext, versionName).takeIf { it.exists() }

    fun cancelDownload(appContext: Context, downloadId: Long) {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { dm.remove(downloadId) }
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    /** Lanza el instalador del sistema (pide al usuario aceptar). Llamar en primer plano. */
    fun promptInstall(context: Context, apkFile: File) {
        context.startActivity(buildInstallIntent(context, apkFile))
    }

    private fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Respaldo si saliste de la app: tocar la notificación abre el instalador. */
    private fun showInstallNotification(appContext: Context, apkFile: File, versionName: String) {
        ensureChannel(appContext)
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return

        val installPending = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            buildInstallIntent(appContext, apkFile),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_detect)
            .setContentTitle("FinTrack $versionName lista")
            .setContentText("Toca para instalar la actualización.")
            .setContentIntent(installPending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Actualizaciones",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Avisa cuando una actualización está lista para instalar."
                    }
                )
            }
        }
    }
}
