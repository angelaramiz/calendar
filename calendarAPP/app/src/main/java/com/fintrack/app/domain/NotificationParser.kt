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

    // IDs verificados en Google Play (sep 2026): Nu com.nu.production,
    // Plata dif.tech.plata, DiDi com.didiglobal.passenger, Azteca
    // mx.com.bancoazteca.bazdigitalmovil, Spin com.pagopopmobile,
    // Google Wallet com.google.android.apps.walletnfcrel.
    // Banamex com.citibanamex.banamexmobile (fuente secundaria).
    // El resto sigue pendiente de confirmación en dispositivo real.
    val DEFAULT_PACKAGES = setOf(
        "com.bbva.bbvacontigo",
        "com.bancomer.mbanking",
        "com.citibanamex.banamexmobile",
        "com.santander.santandermexico",
        "com.banorte.movil",
        "com.hsbc.hsbcmexico",
        "com.scotiabank.mobile",
        "com.inbursa.bancamovil",
        "mx.com.bancoazteca.bazdigitalmovil",
        "com.mercadopago.wallet",
        "com.paypal.android.p2pmobile",
        "com.nu.production",
        "dif.tech.plata",
        "com.didiglobal.passenger",
        "com.pagopopmobile",
        "com.google.android.apps.walletnfcrel"
    )

    // Raíces (stems) normalizadas sin acentos: matchean conjugaciones
    // ("debitamos"~"debito", "ingresaste"~"ingres", "pagaste"~"pag").
    private val expenseKeywords = listOf(
        "carg", "compr", "pag", "retir", "transfer",
        "debit", "recarg", "comisi", "enviaste"
    )

    private val incomeKeywords = listOf(
        "abon", "deposit", "nomin", "ingres", "reembols", "recibid",
        "recibi", "te envia", "te envio"
    )

    // Pagos que NUNCA movieron dinero: rechazados/fallidos e instrucciones
    // de fondeo ("Ingresa $X para realizar el pago"). Corren antes de las
    // keywords porque "ingresa" activa la raíz de ingreso "ingres".
    private val rejectionStems = listOf(
        "rechaz", "fallid", "no se pudo", "no pudimos", "declinad"
    )
    private val fundingInstruction = Regex("""\bingresa\b.*\bpara\b""")
    // Corren ANTES de buscar montos: "Terminal MINI por solo $99" no es un gasto.
    private val promoExclusions = listOf(
        "%", "anual", "invert", "promoc", "referid", "publicidad",
        "felicidades", "ganaste", "ganar", "descuento", "cupon",
        "conoce", "descubre", "nuevo beneficio", "te regalamos",
        "tiempo limitado", "solo por", "por solo", "oferta", "aprovecha",
        "llevate", "obten", "vence", "paquete", "terminal",
        "ultimos dias", "ultimas horas", "fin de semana"
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
        // 2b. Rechazos: el dinero nunca se movió (ej. "Rechazamos tu pago").
        val combinedLower = "$title $text".normalized()
        if (rejectionStems.any { combinedLower.contains(it) } ||
            fundingInstruction.containsMatchIn(combinedLower)
        ) {
            return ParseResult.Rejected("movimiento_rechazado")
        }
        // 3. Keywords financieras (título + texto: "Recibiste $200" trae todo arriba)
        val combined = "$title $text"
        val normalized = combined.normalized()
        val hasExpense = expenseKeywords.any { normalized.contains(it) }
        val hasIncome = incomeKeywords.any { normalized.contains(it) }
        if (!hasExpense && !hasIncome) {
            return ParseResult.Rejected("sin_keywords")
        }
        // 4. Monto valido (también puede venir solo en el título)
        val amount = extractAmount(combined) ?: return ParseResult.Rejected("sin_monto")
        if (amount <= 0) return ParseResult.Rejected("monto_invalido")

        // 5. Tipo (ingreso gana si hay ambas), comercio y categoria
        val type = if (hasIncome) "INCOME" else "EXPENSE"
        val merchant = extractMerchant(title, text)
        val category = categorize("$title $merchant $text", type == "INCOME")

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
        // "Bautista Gonzalez ... te envió dinero" -> remitente (sobre texto normalizado)
        val senderPattern = Regex("""([a-z\s]+?)\s+te\s+envio\b""")
        // "Compra en Starbucks por $85.00." -> "Starbucks" (para en $, dígitos o "por")
        val textPattern = Regex("""en\s+([A-Za-z][A-Za-z\s]*?)(?=\s+por\b|\s*\d|\$|\.|$)""")
        val normalizedText = text.normalized()
        return titlePattern.find(title)?.groupValues?.get(1)?.trim()
            ?: senderPattern.find(normalizedText)?.groupValues?.get(1)?.trim()
                ?.split(" ")?.map { it.replaceFirstChar(Char::uppercase) }?.joinToString(" ")
                ?.takeIf { it.isNotEmpty() }
            ?: textPattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun categorize(text: String, isIncome: Boolean): String {
        val lower = text.normalized()
        fun has(vararg keywords: String) = keywords.any { lower.contains(it.normalized()) }
        if (isIncome) {
            // Nómina/sueldo y devoluciones; el resto de ingresos queda en Otros.
            if (has("nomin", "sueldo", "salario", "quincen", "aguinaldo")) return "Sueldo"
            if (has("reembols", "reembolso", "devolucion")) return "Reembolso"
            return "Otros"
        }
        // Orden intencional: lo específico (comercio) antes que lo genérico
        // (Finanzas al final: "tarjeta de credito" no debe ganarle a Liverpool).
        return when {
            has("restaurante", "cafeteria", "cafe", "comida", "restaurant",
                "starbucks", "mcdonald", "burger", "pizza", "tacos", "kfc",
                "dominos", "sushi", "panaderia", "comedor") -> "Comida"
            has("uber", "didi", "taxi", "cabify", "gasolina", "gasolinera",
                "pemex", "estacionamiento", "metro", "metrobus", "peaje",
                "caseta", "autobus", "vuelo", "aeropuerto", "volaris",
                "vivaaerobus", "aeromexico") -> "Transporte"
            has("cajero", "atm", "retiro") -> "Efectivo"
            has("cfe", "luz", "agua", " de gas", "internet", "telefono",
                "telefonia", "telcel", "telmex", "att", "movistar", "izzi",
                "totalplay", "megacable", "sky", "predial") -> "Servicios"
            has("farmacia", "benavides", "similar", "doctor", "hospital",
                "dentista", "clinica", "laboratorio", "chopo",
                "salud digna") -> "Salud"
            has("netflix", "spotify", "disney", "prime video", "hbo", "max ",
                "youtube", "cinema", "cine", "cinepolis", "cinemex", "juego",
                "steam", "xbox", "playstation", "concierto",
                "ticketmaster") -> "Ocio"
            has("oxxo", "seven", "7-eleven", "walmart", "soriana", "chedraui",
                "costco", "sams", "liverpool", "palacio de hierro", "amazon",
                "mercado libre", "mercadolibre", "shein", "aliexpress", "coppel",
                "elektra", "zara", "ropa", "tienda") -> "Compras"
            has("renta", "alquiler", "hipoteca", "infonavit",
                "condominio") -> "Vivienda"
            has("escuela", "colegio", "universidad", "colegiatura", "curso",
                "udemy", "coursera", "utiles") -> "Educación"
            has("transferencia", "spei", "dimo", "codi", "traspaso") -> "Transferencias"
            // "pago (de tu/la/mi) tarjeta": fraseo variable, se detecta por
            // co-ocurrencia en vez de frase exacta.
            has("prestamo", "credito", "interes",
                "comision", "anualidad", "seguro de", "seguros", "tu seguro",
                "poliza", "afore") || (has("tarjeta") && has("pago")) -> "Finanzas"
            else -> "Otros"
        }
    }
}
