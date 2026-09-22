package com.fintrack.app.data.repository

import com.fintrack.app.BuildConfig
import com.fintrack.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class AppVersionRow(
    val clave: String,
    val valor: String
)

@Serializable
private data class OtaPayload(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    /** SHA-256 hex del APK (minúsculas). "" = fila vieja sin firma. */
    val apkSha256: String = ""
)

data class OtaUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String = ""
)

class OtaUpdateRepository {

    private val db get() = SupabaseClientProvider.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(): OtaUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val row = db.from("app_versions").select {
                filter { eq("clave", "app_version_calendarfinance") }
                limit(1)
            }.decodeSingle<AppVersionRow>()

            val payload = parsePayload(row.valor) ?: return@withContext null
            if (payload.apkUrl.isBlank()) return@withContext null

            if (payload.versionCode > BuildConfig.VERSION_CODE) {
                OtaUpdateInfo(payload.versionCode, payload.versionName, payload.apkUrl, payload.apkSha256)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePayload(raw: String): OtaPayload? {
        // `valor` llega doblemente codificado (un JSON dentro de un string JSON),
        // se intenta parseo directo primero y luego con doble parseo.
        return runCatching { json.decodeFromString<OtaPayload>(raw) }.getOrNull()
            ?: runCatching {
                val inner = json.decodeFromString<String>(raw.trim().trim('"'))
                json.decodeFromString<OtaPayload>(inner)
            }.getOrNull()
            ?: runCatching {
                json.decodeFromString<OtaPayload>(json.decodeFromString<String>(raw))
            }.getOrNull()
    }
}
