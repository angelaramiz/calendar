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
        "debit", "recarg", "comisi", "enviaste",
        // Fallback genérico (ej. Wallet: "Transacción aprobada en OXXO").
        // Exige monto igual que el resto, así que un aviso sin cantidad
        // ("No reconoces este movimiento") sigue rechazado.
        "transaccion", "movimiento", "aprobad", "paid"
    )

    private val incomeKeywords = listOf(
        "abon", "deposit", "nomin", "ingres", "reembols", "recibid",
        "recibi", "te envia", "te envio", "received"
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

    /**
     * Señales bancarias FUERTES (stems, contains): jerga de dinero real y
     * marcas. Solo para el gate de sugerencia, NO para parse (la allowlist
     * ya cubre a los bancos conocidos y no queremos endurecerla).
     */
    private val bankSignalStems = listOf(
        "banco", "banca", "bancaria", "tarjeta", "cuenta", "saldo",
        "transferencia", "traspaso", "retiro", "deposito", "fonde",
        "credito", "debito", "prestamo", "adeudo", "comision",
        "anualidad", "corte", "vencimiento", "nomin", "sueldo",
        "salario", "quincen", "aguinaldo", "cajero", "afore",
        "cetes", "terminacion", "terminada",
        "pago de", "pago con", "pago a", "cargo a", "abono a",
        "bbva", "banamex", "bancomer", "santander", "banorte",
        "hsbc", "scotia", "inbursa", "azteca", "mercadopago",
        "mercado pago", "paypal", "fondeadora", "stori", "konfio",
        "nubank", "wise", "dolarapp", "uala", "kubo"
    )

    /**
     * Marcas/términos cortos que como substring darían falsos positivos
     * ("nu" en "nuevo"/"anual", "hey" en "hey!" casual): solo valen como
     * palabra completa. Texto ya normalizado (minúsculas sin acentos).
     */
    private val bankSignalTokens = Regex(
        """\b(nu|hey|klar|albo|clip|spin|spei|codi|dimo|nip|atm|clabe|afore|oxxo spin|kushki)\b"""
    )

    /** Tarjeta enmascarada ("****1234", "**** 1234"): casi siempre banco. */
    private val maskedCard = Regex("""\*{2,}\s?\d{2,4}""")

    /**
     * ¿Parece aviso bancario aunque el paquete no esté en la allowlist?
     * Gate PROPIO (más estricto que parse): exige monto + señal bancaria
     * FUERTE (marca, producto o jerga de dinero real). Los verbos genéricos
     * solos ("pagaste", "te envió 3 fotos", "paid") NO bastan: así WhatsApp,
     * juegos o YouTube dejan de sugerirse como bancos.
     */
    fun looksLikeBankActivity(packageName: String, title: String, text: String): Boolean {
        val combined = "$title $text"
        val normalized = combined.normalized()
        // Promos y rechazos nunca son bancos ("ganaste 500 monedas", "pago rechazado").
        if (isPromo(combined)) return false
        if (rejectionStems.any { normalized.contains(it) } ||
            fundingInstruction.containsMatchIn(normalized)
        ) {
            return false
        }
        // Sin monto no hay movimiento que sugerir.
        if (extractAmount(combined) == null) return false
        if (bankSignalStems.any { normalized.contains(it) }) return true
        if (bankSignalTokens.containsMatchIn(normalized)) return true
        // Tarjeta enmascarada ("****1234", "terminación 5678"): casi siempre banco.
        return maskedCard.containsMatchIn(combined)
    }

    /**
     * Normaliza una llave merchant→categoría para el match exacto
     * (minúsculas sin acentos, igual que las keywords).
     */
    fun normalizeKey(raw: String): String = raw.normalized()

    fun parse(
        packageName: String,
        title: String,
        text: String,
        allowedPackages: Set<String> = DEFAULT_PACKAGES,
        /** Reglas merchant→categoría que aprenden de tus correcciones: ganan a la heurística. */
        rules: Map<String, String> = emptyMap()
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

        // 5. Tipo (ingreso gana si hay ambas), comercio y categoria.
        // La regla aprendida (match exacto con ambos lados normalizados)
        // va ANTES de la heurística: si corregiste "Urbani" a Comida, la
        // próxima llega así aunque la llave venga en otras mayúsculas.
        val type = if (hasIncome) "INCOME" else "EXPENSE"
        val merchant = extractMerchant(title, text)
        val merchantKey = merchant?.let { normalizeKey(it) }
        val ruleHit = merchantKey?.let { key ->
            rules.entries.firstOrNull { normalizeKey(it.key) == key }?.value
        }
        val category = ruleHit
            ?: categorize("$title $merchant $text", type == "INCOME")

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

    /**
     * Mejor candidato a monto: prefiere el que trae $ (ej. "•1234 por $85"),
     * luego el que trae centavos, luego el primero. Así "1500 pesos" no se
     * trunca a 150 como hacía el patrón anterior de 1-3 dígitos.
     */
    private fun extractAmount(text: String): Double? {
        val pattern = Regex("""(\$)?\s?(\d[\d,]*(?:\.\d{1,2})?)""")
        return pattern.findAll(text)
            .mapNotNull { m ->
                val value = m.groupValues[2].replace(",", "").toDoubleOrNull()
                    ?: return@mapNotNull null
                val score = when {
                    m.groupValues[1].isNotEmpty() -> 2
                    m.groupValues[2].contains(".") -> 1
                    else -> 0
                }
                Triple(score, m.range.first, value)
            }
            .maxWithOrNull(compareBy({ it.first }, { -it.second }))
            ?.third
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
