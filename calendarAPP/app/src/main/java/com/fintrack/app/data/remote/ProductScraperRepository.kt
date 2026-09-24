package com.fintrack.app.data.remote

import com.fintrack.app.domain.ProductScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente del buscador de productos (Fly.io + Selenium, mismo que la web).
 * HttpURLConnection directo: sin dependencias nuevas. Timeouts amplios porque
 * el backend despierta la máquina (min 0) y el scraper tarda ~15-60 s.
 */
class ProductScraperRepository {

    companion object {
        const val SCRAPER_URL = "https://calendar-backend-ed6u5g.fly.dev"
        private const val TAG = "ProductScraper"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 120_000
    }

    suspend fun scrape(url: String): ProductScraper.QuickResult = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$SCRAPER_URL/api/scrape/quick").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }
            val payload = """{"url":"${url.replace("\\", "\\\\").replace("\"", "\\\"")}"}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (body.isBlank()) {
                return@withContext ProductScraper.QuickResult.Error(
                    "El buscador no respondió. Revisa tu conexión e intenta de nuevo."
                )
            }
            ProductScraper.parseQuickResponse(url, body)
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.w(TAG, "scrape timeout ($url)", e)
            ProductScraper.QuickResult.Error(
                "El buscador tardó demasiado (despertando el servidor). Intenta de nuevo en un minuto."
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "scrape failed ($url): ${e.javaClass.simpleName} ${e.message}", e)
            ProductScraper.QuickResult.Error(
                "Sin conexión con el buscador. Puedes crear la meta a mano."
            )
        }
    }
}
