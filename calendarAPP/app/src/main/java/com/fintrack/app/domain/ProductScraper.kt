package com.fintrack.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Extracción y limpieza de datos de producto para planificar compras.
 * Todo puro y testeable: la red vive en ProductScraperRepository.
 * Contrato del backend: POST /api/scrape/quick {url} ->
 * {success, data:{url, platform, name, price, ...}} o {success:false, error}.
 */
object ProductScraper {

    private val lenient = Json { ignoreUnknownKeys = true }

    data class ProductInfo(
        val name: String,
        val price: Double,
        val store: String,
        val url: String,
        /** El scraper respondió parcial: nombre/precio requieren revisión manual. */
        val needsManualInput: Boolean = false
    )

    sealed interface QuickResult {
        data class Success(val info: ProductInfo) : QuickResult
        /** CAPTCHA de Amazon o respuesta parcial: crear la meta a mano con lo que haya. */
        data class NeedsManual(val message: String, val url: String) : QuickResult
        data class Error(val message: String) : QuickResult
    }

    /** Primera URL http(s) dentro de un texto compartido (o null si no hay). */
    fun extractSharedUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""https?://\S+""").find(text) ?: return null
        // Recorta puntuación final típica de "mira esto: <url>." o "...<url>)".
        return match.value.trimEnd('.', ',', ')', ']', '!', '?', '"', '\'')
    }

    /** Quita sufijos de tienda ("... : Amazon.com.mx ...", "| MercadoLibre", " - $1,234"). */
    fun cleanProductName(raw: String): String {
        var name = raw.trim()
        name = Regex("""\s*:\s*Amazon\.com\.mx.*$""", RegexOption.IGNORE_CASE).replace(name, "")
        name = Regex("""\s*:\s*Amazon\.com.*$""", RegexOption.IGNORE_CASE).replace(name, "")
        name = Regex("""\s*\|\s*MercadoLibre.*$""", RegexOption.IGNORE_CASE).replace(name, "")
        name = Regex("""\s*-\s*\$\s*[\d,.]+\s*$""").replace(name, "")
        return name.trim()
    }

    /** Corrige tienda de 1-2 letras según plataforma o dominio. */
    fun storeFor(platform: String?, url: String, store: String?): String {
        if (!store.isNullOrBlank() && store.trim().length > 2) return store.trim()
        val lower = ((platform ?: "") + " " + url).lowercase()
        return when {
            "amazon" in lower || "a.co" in lower -> "Amazon"
            "mercadolibre" in lower || "mercadolibre" in lower || "mlibre" in lower -> "MercadoLibre"
            else -> store?.trim().orEmpty()
        }
    }

    /** true si el nombre es genérico (página de bloqueo/cookies/login). */
    fun isGenericName(name: String): Boolean {
        if (name.length < 10) return true
        val generic = setOf(
            "mercado libre", "preferencias de cookies", "producto de mercadolibre",
            "producto de amazon", "amazon.com.mx", "sign in"
        )
        return name.lowercase() in generic
    }

    /** Parsea el cuerpo de /api/scrape/quick sin lanzar. */
    fun parseQuickResponse(url: String, body: String): QuickResult {
        val root = runCatching { lenient.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return QuickResult.Error("Respuesta inválida del buscador.")
        // success llega booleano; se compara como texto para no asumir el tipo.
        val success = root["success"]?.let { it.toString() == "true" } == true
        if (!success) {
            val error = (root["error"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val message = (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if ("CAPTCHA" in error.uppercase()) {
                return QuickResult.NeedsManual(
                    "Amazon pidió verificación: revisa el nombre y el precio a mano.",
                    url
                )
            }
            return QuickResult.Error(message.ifBlank { error.ifBlank { "No se pudo leer el producto." } })
        }
        val data = root["data"]?.let { runCatching { it.jsonObject }.getOrNull() }
            ?: return QuickResult.Error("Respuesta sin datos del producto.")
        val name = cleanProductName((data["name"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        val price = (data["price"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        // platform puede venir objeto (con patterns/store) o string según versión.
        val platform = (data["platform"] as? JsonPrimitive)?.contentOrNull
        val store = storeFor(platform, url, (data["store"] as? JsonPrimitive)?.contentOrNull)
        if (isGenericName(name) || price <= 0.0) {
            return QuickResult.NeedsManual(
                if (isGenericName(name)) "La tienda bloqueó la lectura: completa los datos a mano."
                else "Se leyó el producto pero sin precio: confírmalo a mano.",
                url
            ).let {
                // Si hay nombre real, se ofrece precargado aunque pida revisión.
                if (!isGenericName(name)) QuickResult.Success(
                    ProductInfo(name, price, store, url, needsManualInput = true)
                ) else it
            }
        }
        return QuickResult.Success(ProductInfo(name, price, store, url))
    }
}
