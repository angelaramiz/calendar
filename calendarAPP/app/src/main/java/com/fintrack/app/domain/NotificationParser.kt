package com.fintrack.app.domain

/**
 * Capa procedural de procesamiento de notificaciones (sin IA, sin red).
 * Orden de etapas (intencional, no reordenar):
 * 1. Allowlist de apps -> 2. Anti-promos -> 3. Keywords -> 4. Monto -> 5. Tipo/comercio/categoria.
 */
data class ParsedTransaction(
    val amount: Double,
    val type: String, // "INCOME" | "EXPENSE"
    val category: String,
    val description: String,
    val merchant: String? = null,
    val source: String = "AUTO"
)

sealed interface ParseResult {
    data class Accepted(val tx: ParsedTransaction) : ParseResult
    data class Rejected(val reason: String) : ParseResult
}

object NotificationParser {

    val DEFAULT_PACKAGES = setOf(
        "com.bbva.bbvacontigo",
        "com.bancomer.mbanking",
        "com.santander.santandermexico",
        "com.banorte.movil",
        "com.hsbc.hsbcmexico",
        "com.scotiabank.mobile",
        "com.inbursa.bancamovil",
        "com.mercadopago.wallet",
        "com.paypal.android.p2pmobile"
    )

    // Raíces (stems) normalizadas sin acentos: matchean conjugaciones
    // ("debitamos"~"debito", "ingresaste"~"ingres", "pagaste"~"pag").
    private val expenseKeywords = listOf(
        "carg", "compr", "pag", "retir", "transfer",
        "debit", "recarg", "comisi"
    )

    private val incomeKeywords = listOf(
        "abon", "deposit", "nomin", "ingres", "reembols", "recibid"
    )

    // Promociones y avisos que NUNCA son movimientos (ej: "gana 12% anual")
    private val promoExclusions = listOf(
        "%", "anual", "invert", "promoc", "referid", "publicidad",
        "felicidades", "ganaste", "ganar", "descuento", "cupon",
        "conoce", "descubre", "nuevo beneficio", "te regalamos"
    )

    fun parse(
        packageName: String,
        title: String,
        text: String,
        allowedPackages: Set<String> = DEFAULT_PACKAGES
    ): ParseResult {
        // 1. Allowlist de apps
        if (allowedPackages.none { it.equals(packageName, ignoreCase = true) }) {
            return ParseResult.Rejected("app_no_permitida")
        }
        // 2. Anti-promos (antes de buscar montos: "12% anual" no es movimiento)
        if (isPromo("$title $text")) {
            return ParseResult.Rejected("promocion")
        }
        // 3. Keywords financieras
        val normalized = text.normalized()
        val hasExpense = expenseKeywords.any { normalized.contains(it) }
        val hasIncome = incomeKeywords.any { normalized.contains(it) }
        if (!hasExpense && !hasIncome) {
            return ParseResult.Rejected("sin_keywords")
        }
        // 4. Monto valido
        val amount = extractAmount(text) ?: return ParseResult.Rejected("sin_monto")
        if (amount <= 0) return ParseResult.Rejected("monto_invalido")

        // 5. Tipo (ingreso gana si hay ambas), comercio y categoria
        val type = if (hasIncome) "INCOME" else "EXPENSE"
        val merchant = extractMerchant(title, text)
        val category = categorize("$title $merchant $text")

        return ParseResult.Accepted(
            ParsedTransaction(
                amount = amount,
                type = type,
                category = category,
                description = text.take(100),
                merchant = merchant
            )
        )
    }

    private fun String.normalized(): String =
        java.text.Normalizer.normalize(this.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun isPromo(text: String): Boolean {
        val lower = text.normalized()
        return promoExclusions.any { lower.contains(it) }
    }

    private fun extractAmount(text: String): Double? {
        val pattern = Regex("""\$?\s?(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)""")
        return pattern.find(text)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()
    }

    private fun extractMerchant(title: String, text: String): String? {
        val titlePattern = Regex("""(?i)(?:pagaste a|pago a|compra en|pago en)\s+([A-Za-z0-9\s]+)""")
        // "Compra en Starbucks por $85.00." -> "Starbucks" (para en $, dígitos o "por")
        val textPattern = Regex("""en\s+([A-Za-z][A-Za-z\s]*?)(?=\s+por\b|\s*\d|\$|\.|$)""")
        return titlePattern.find(title)?.groupValues?.get(1)?.trim()
            ?: textPattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun categorize(text: String): String {
        val lower = text.normalized()
        fun has(vararg keywords: String) = keywords.any { lower.contains(it.normalized()) }
        return when {
            has("restaurante", "cafe", "comida", "restaurant", "starbucks", "mcdonald") -> "Comida"
            has("uber", "taxi", "gasolina", "estacionamiento", "metro") -> "Transporte"
            has("luz", "agua", "gas", "internet", "telefono", "sky", "telcel") -> "Servicios"
            has("netflix", "spotify", "cinema", "cine", "juego") -> "Ocio"
            else -> "Otros"
        }
    }
}
