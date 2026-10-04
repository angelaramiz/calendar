package com.fintrack.app.domain

/**
 * OCR de tickets (C9): texto plano de ML Kit → monto + comercio sugeridos.
 *
 * Puro y testeable: la cámara (intent del sistema) y ML Kit viven en
 * `ui/ocr/OcrCaptureButton`; aquí solo entra el String reconocido.
 *
 * Filosofía de montos heredada de `NotificationParser.extractAmount`:
 * prefiere el candidato con `$`, luego el que trae decimales, luego el
 * primero. Encima va UNA regla de tickets: la línea de TOTAL gana
 * (excluyendo "subtotal"/"sub total"), porque el ticket trae precios
 * unitarios, subtotal, IVA y cambio que no son el gasto real.
 *
 * Formatos mexicanos: `$1,250.50`, `850,50`, `1500`, `TOTAL: $116.00`.
 */
data class TicketResult(
    val amount: Double?,
    val merchant: String?
)

object TicketOcr {

    private val totalKeys = listOf("total", "importe", "a pagar", "gran total", "monto total")
    private val notTotalKeys = listOf(
        "subtotal", "sub total",
        // "TOTAL ARTICULOS 5" es conteo, no el gasto.
        "articulo", "cantidad", "pieza", "producto"
    )

    /** Líneas que jamás son el comercio (datos del ticket, no la tienda). */
    private val merchantBan = listOf(
        "total", "subtotal", "cambio", "efectivo", "tarjeta", "gracias",
        "folio", "ticket", "factura", "pago", "venta", "cliente", "caja",
        "articulo", "cantidad", "precio", "iva", "propina",
        // Fiscales, dirección y medio de pago: nunca la tienda.
        "rfc", "regimen", "expedido", "avenida", "av ", "calle", "colonia",
        "col ", "codigo postal", "telefono", "sucursal", "visa", "mastercard",
        "amex", "debito", "credito"
    )

    /** Tarjeta enmascarada (****1234): sus dígitos no son dinero. */
    private val maskedCard = Regex("""\*+\s*[\d\s]+""")

    /** Marcas de dólar americano: el monto se sugiere igual pero se avisa. */
    private val usdKeys = listOf("usd", "dll", "dolar", "dollar")

    private val moneyPattern = Regex("""([$])?\s*(\d[\d.,]*\d|\d)""")

    private data class Candidate(val score: Int, val order: Int, val value: Double)

    fun parse(fullText: String): TicketResult {
        if (fullText.isBlank()) return TicketResult(null, null)
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return TicketResult(null, null)
        val totalIdx = lines.indexOfFirst { it.isTotalLine() }
        val amount = if (totalIdx >= 0) {
            // En la línea de total vale hasta el entero pelón ("TOTAL 1500").
            // Si el TOTAL viene solo ("TOTAL\n$116"), se mira hasta 2 líneas abajo.
            val scope = listOf(lines[totalIdx]) +
                lines.drop(totalIdx + 1).take(2)
            scope.flatMapIndexed { i, line ->
                line.candidates()
                    // En las líneas de abajo solo valen $ o decimales (un
                    // folio suelto no es el gasto); en la de TOTAL todo vale.
                    .filter { c -> i == 0 || c.score >= 1 }
                    .map { c -> c.copy(order = i * 1000 + c.order) }
            }.maxWithOrNull(compareBy({ it.score }, { -it.order }))?.value
        } else {
            // Sin línea de total solo valen candidatos con $ o decimales:
            // un "FOLIO 123" o "NOTA 2026" sueltos no son el gasto. La propina
            // no gana si hay otro monto (el gasto real la incluye).
            var order = 0
            val all = lines.flatMap { line ->
                line.candidates().map { c ->
                    order += 1
                    Triple(line, order, c)
                }
            }.filter { (_, _, c) -> c.score >= 1 }
            val rest = all.filterNot { (line, _, _) -> "propina" in line.normalized() }
            (if (rest.isNotEmpty()) rest else all)
                .maxWithOrNull(compareBy({ (_, _, c) -> c.score }, { (_, order, _) -> -order }))
                ?.let { (_, _, c) -> c.value }
        }
        val usd = lines.any { line -> usdKeys.any { it in line.normalized() } }
        val merchant = extractMerchant(lines)?.let { if (usd) "$it (USD)" else it }
        return TicketResult(amount, merchant)
    }

    private fun String.normalized(): String =
        java.text.Normalizer.normalize(this.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun String.isTotalLine(): Boolean {
        val norm = this.normalized()
        if (notTotalKeys.any { it in norm }) return false
        return totalKeys.any { it in norm }
    }

    private fun String.candidates(): List<Candidate> =
        maskedCard.replace(this, " ").let { clean ->
            moneyPattern.findAll(clean).mapNotNull { m ->
                val value = parseMoney(m.groupValues[2]) ?: return@mapNotNull null
                val score = when {
                    m.groupValues[1].isNotEmpty() -> 2
                    m.groupValues[2].contains(".") || m.groupValues[2].contains(",") -> 1
                    else -> 0
                }
                Candidate(score, m.range.first, value)
            }.toList()
        }

    /**
     * Normaliza un token numérico a Double. El último separador seguido de
     * exactamente 2 dígitos es el decimal (`1,250.50`→1250.50,
     * `850,50`→850.50, `1.250,50`→1250.50); sin decimal claro se asume
     * entero (`1,250`→1250). null si no es número.
     */
    fun parseMoney(raw: String): Double? {
        var clean = raw.replace("$", "").replace(" ", "").replace("MXN", "")
        if (clean.isEmpty()) return null
        if (clean.any { !it.isDigit() && it != '.' && it != ',' }) return null
        val lastSep = maxOf(clean.lastIndexOf('.'), clean.lastIndexOf(','))
        return if (lastSep >= 0 && clean.length - lastSep - 1 == 2) {
            val intPart = clean.substring(0, lastSep).filter { it.isDigit() }
            val decPart = clean.substring(lastSep + 1)
            if (intPart.isEmpty()) return null
            "$intPart.$decPart".toDoubleOrNull()
        } else {
            clean.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        }
    }

    /**
     * Comercio = primera línea en MAYÚSCULAS (lo usual en tickets MX).
     * Fallback honesto: primera línea con letras que no sea dato del ticket
     * (sirve para "Walmart México" en Title Case). null si no hay nada útil.
     */
    fun extractMerchant(lines: List<String>): String? {
        val withLetters = lines.filter { line ->
            val norm = line.normalized()
            line.any { it.isLetter() } && merchantBan.none { it in norm }
        }
        withLetters.firstOrNull { line ->
            val letters = line.filter { it.isLetter() }
            val upper = letters.count { it.isUpperCase() }
            letters.length >= 3 && upper >= 3 && upper.toDouble() / letters.length >= 0.6
        }?.let { return it.take(40).trim() }
        return withLetters.firstOrNull { it.filter(Char::isLetter).length >= 3 }
            ?.take(40)?.trim()
    }
}
