package com.calendarfinance.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.calendarfinance.app.data.model.AppVersionInfo
import com.calendarfinance.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class OtaUpdateRepository {

    private val db get() = SupabaseClientProvider.client
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") pkgInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    suspend fun checkForUpdate(context: Context): AppVersionInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersionCode(context)

            val result = db.postgrest["app_versions"].select {
                filter { eq("clave", "app_version_calendarfinance") }
                limit(1L)
            }.decodeSingle<Map<String, Any>>()

            val valor = result["valor"] as? Map<String, Any> ?: return@withContext null
            val latestVersion = (valor["versionCode"] as? Number)?.toInt() ?: return@withContext null
            val versionName = valor["versionName"] as? String ?: "1.0.0"
            val apkUrl = valor["apkUrl"] as? String ?: return@withContext null

            if (latestVersion > currentVersion && apkUrl.isNotEmpty()) {
                AppVersionInfo(latestVersion, versionName, apkUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(context: Context, apkUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val urlWithTimestamp = if (apkUrl.contains("?")) "$apkUrl&t=${System.currentTimeMillis()}"
            else "$apkUrl?t=${System.currentTimeMillis()}"

            val request = Request.Builder().url(urlWithTimestamp).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val bytes = response.body?.bytes() ?: return@withContext null

            // Clean old APKs
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir?.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }

            // Save new APK
            val file = File(dir, "calendarfinance_update.apk")
            file.writeBytes(bytes)
            file
        } catch (e: Exception) {
            null
        }
    }

    fun installApk(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
