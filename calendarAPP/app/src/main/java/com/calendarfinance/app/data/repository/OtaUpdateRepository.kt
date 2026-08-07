package com.calendarfinance.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.calendarfinance.app.data.model.AppVersionInfo
import com.calendarfinance.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AppVersionRow(
    val clave: String = "",
    val valor: String = ""
)

@Serializable
data class AppVersionData(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = ""
)

class OtaUpdateRepository {

    private val tag = "OtaUpdate"
    private val db get() = SupabaseClientProvider.client
    private val client = OkHttpClient()
    private val jsonParser = Json { ignoreUnknownKeys = true }

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
            Log.d(tag, "Version actual: $currentVersion")

            val row = db.from("app_versions").select {
                filter { eq("clave", "app_version_calendarfinance") }
                limit(1)
            }.decodeSingle<AppVersionRow>()

            Log.d(tag, "Row: clave=${row.clave}, valor=${row.valor}")

            // valor viene como JSONB, Supabase lo decodifica como String
            val valorElement = try {
                jsonParser.parseToJsonElement(row.valor)
            } catch (e: Exception) {
                Log.e(tag, "Error parseando valor: ${e.message}")
                return@withContext null
            }

            val valor = valorElement.jsonObject
            val latestVersion = valor["versionCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@withContext null
            val versionName = valor["versionName"]?.jsonPrimitive?.content ?: "1.0.0"
            val apkUrl = valor["apkUrl"]?.jsonPrimitive?.content ?: return@withContext null

            Log.d(tag, "Latest: $latestVersion, name: $versionName, url: $apkUrl")

            if (latestVersion > currentVersion && apkUrl.isNotEmpty()) {
                Log.d(tag, "Actualizacion disponible: v$versionName")
                AppVersionInfo(latestVersion, versionName, apkUrl)
            } else {
                Log.d(tag, "No hay actualizacion (latest=$latestVersion, current=$currentVersion)")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "checkForUpdate error: ${e.message}", e)
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

            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir?.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }

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
