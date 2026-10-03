package com.fintrack.app.domain

/**
 * Registro por voz (§C8): interpreta el texto del reconocedor del sistema
 * (`RECOGNIZE_SPEECH`, sin permiso extra) y sugiere monto + categoría para
 * pre-llenar `QuickEntryDialog`. Puro y testeado; la UI solo confirma.
 */
data class VoiceExpenseResult(
    /** null = el dictado no trae monto (la UI lo pide a mano). */
    val amount: Double?,
    /** Categoría tentativa de [TransactionCategories]. */
    val category: String,
    val isIncome: Boolean,
    val text: String
)

object VoiceExpenseParser {

    private val incomeClues = listOf(
        "me pagaron", "me depositaron", "me transfirieron",
        "recibi", "cobre", "ingreso", "nomina", "sueldo",
        "quincena", "reembolso", "aguinaldo"
    )

    /** Palabra normalizada -> categoría de gasto. */
    private val categoryKeywords = listOf(
        listOf("taco", "comida", "restaurante", "cena", "desayuno", "almuerzo", "cafe", "fonda", "pizza", "hamburguesa", "oxxo", "super", "despensa", "mercado", "antojo", "chel", "cerveza") to "Comida",
        listOf("gasolina", "uber", "didi", "taxi", "metro", "camion", "autobus", "transporte", "estacionamiento", "peaje", "verificacion", "tenencia") to "Transporte",
        listOf("retiro", "cajero", "efectivo") to "Efectivo",
        listOf("luz", "agua", "internet", "telefono", "celular", "servicio", "gas ", "renta del") to "Servicios",
        listOf("farmacia", "doctor", "medico", "medicina", "hospital", "salud", "consulta") to "Salud",
        listOf("cine", "fiesta", "juego", "netflix", "spotify", "ocio", "concierto", "partido") to "Ocio",
        listOf("ropa", "zapatos", "amazon", "liverpool", "compra", "tienda", "mercado libre", "sams", "costco") to "Compras",
        listOf("renta", "hipoteca", "casa", "vivienda", "predial") to "Vivienda",
        listOf("escuela", "curso", "libro", "universidad", "educacion", "colegiatura") to "Educación",
        listOf("transferencia", "envie", "envio") to "Transferencias",
        listOf("banco", "comision", "interes", "tarjeta", "seguro", "anualidad") to "Finanzas"
    )

    fun parse(raw: String): VoiceExpenseResult {
        val lower = raw.normalized()
        val isIncome = incomeClues.any { lower.contains(it) }
        val amount = extractAmount(raw)
        val category = when {
            isIncome && lower.contains("reembolso") -> "Reembolso"
            isIncome -> "Sueldo"
            else -> categoryKeywords.firstOrNull { (keys, _) ->
                keys.any { lower.contains(it) }
            }?.second ?: "Otros"
        }
        return VoiceExpenseResult(
            amount = amount,
            category = category,
            isIncome = isIncome,
            text = raw.trim()
        )
    }

    /**
     * Primer número del dictado. Acepta miles con coma/punto ("1,500",
     * "1.500") y decimales con punto o coma ("200.50", "200,50"): cuando
     * hay ambos separadores, el último manda como decimal.
     */
    internal fun extractAmount(text: String): Double? {
        val pattern = Regex("""\d[\d.,]*\d|\d""")
        pattern.findAll(text).forEach { m ->
            normalizeNumber(m.value)?.let { return it }
        }
        return null
    }

    internal fun normalizeNumber(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val hasComma = ',' in s
        val hasDot = '.' in s
        s = when {
            hasComma && hasDot -> {
                val lastComma = s.lastIndexOf(',')
                val lastDot = s.lastIndexOf('.')
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.')
                else s.replace(",", "")
            }
            hasComma -> {
                if (Regex(""",\d{2}$""").containsMatchIn(s)) s.replace(",", ".")
                else s.replace(",", "")
            }
            hasDot -> {
                if (Regex("""\.\d{2}$""").containsMatchIn(s)) s
                else if (Regex("""^\d{1,3}(\.\d{3})+$""").matches(s)) s.replace(".", "")
                else s
            }
            else -> s
        }
        val value = s.toDoubleOrNull() ?: return null
        return if (value > 0) value else null
    }

    private fun String.normalized(): String =
        java.text.Normalizer.normalize(this.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
