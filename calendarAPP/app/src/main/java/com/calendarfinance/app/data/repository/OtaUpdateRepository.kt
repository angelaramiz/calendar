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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class OtaUpdateRepository {

    private val tag = "OtaUpdate"
    private val db get() = SupabaseClientProvider.client
    private val client = OkHttpClient()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

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

            val result = db.from("app_versions").select {
                filter { eq("clave", "app_version_calendarfinance") }
                limit(1)
            }.decodeSingle<Map<String, Any>>()

            Log.d(tag, "Supabase response: $result")

            val valorRaw = result["valor"]
            val valor: Map<String, String> = when (valorRaw) {
                is Map<*, *> -> valorRaw.mapKeys { it.key?.toString() ?: "" }.mapValues { it.value?.toString() ?: "" }
                is String -> {
                    Log.d(tag, "valor es String, parseando...")
                    val cleaned = valorRaw.removeSurrounding("{", "}").trim()
                    val pairs = mutableListOf<Pair<String, String>>()
                    var i = 0
                    while (i < cleaned.length) {
                        val keyStart = cleaned.indexOf("\"", i) + 1
                        val keyEnd = cleaned.indexOf("\"", keyStart)
                        if (keyEnd == -1) break
                        val key = cleaned.substring(keyStart, keyEnd)
                        val colonIdx = cleaned.indexOf(":", keyEnd + 1)
                        if (colonIdx == -1) break
                        val valueStart = cleaned.indexOf("\"", colonIdx + 1) + 1
                        val valueEnd = cleaned.indexOf("\"", valueStart)
                        val value = if (valueEnd == -1) cleaned.substring(valueStart).trim()
                                   else cleaned.substring(valueStart, valueEnd)
                        pairs.add(key to value)
                        i = if (valueEnd == -1) cleaned.length else valueEnd + 1
                    }
                    pairs.toMap()
                }
                else -> {
                    Log.e(tag, "valor tipo desconocido: ${valorRaw?.javaClass}")
                    return@withContext null
                }
            }

            Log.d(tag, "valor parseado: $valor")

            val latestVersion = valor["versionCode"]?.toIntOrNull() ?: return@withContext null
            val versionName = valor["versionName"] ?: "1.0.0"
            val apkUrl = valor["apkUrl"] ?: return@withContext null

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
