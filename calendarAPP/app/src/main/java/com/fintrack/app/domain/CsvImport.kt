package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Importar CSV del banco + conciliación (§D7): detector de formato por
 * encabezados (BBVA/Banamex/Santander/Genérico) + parseo de filas a
 * candidatos fecha/monto/concepto. Puro y testeado; la pantalla
 * `ImportScreen` solo concilia (aceptar / vincular / descartar).
 */
enum class BankFormat { BBVA, BANAMEX, SANTANDER, GENERICO, DESCONOCIDO }

data class CsvCandidate(
    val dateIso: String,
    /** Siempre positivo; el lado va en [isIncome]. */
    val amount: Double,
    val isIncome: Boolean,
    val concept: String,
    val rowIndex: Int
)

object CsvImport {

    fun detectFormat(headers: List<String>): BankFormat {
        val h = headers.map { it.normalized() }
        fun has(vararg keys: String) = keys.any { k -> h.any { it.contains(k) } }
        val hasFecha = has("fecha")
        val hasConcepto = has("descrip", "concepto", "movimiento", "detalle")
        val hasMonto = has("monto", "importe", "cantidad", "cargo", "abono", "retiro", "deposito")
        // Distintivos por banco (los tres usan cargo/abono parecidos).
        if (hasFecha && has("saldo") && has("cargo")) return BankFormat.BBVA
        if (hasFecha && has("folio", "sucursal")) return BankFormat.SANTANDER
        if (hasFecha && has("referencia", "autorizacion")) return BankFormat.BANAMEX
        if (hasFecha && hasConcepto && hasMonto) return BankFormat.GENERICO
        return BankFormat.DESCONOCIDO
    }

    /** Detecta formato y parsea todo el contenido (ignora filas malas). */
    fun parse(content: String): List<CsvCandidate> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val headers = splitRow(lines.first())
        val format = detectFormat(headers)
        if (format == BankFormat.DESCONOCIDO) return emptyList()
        return parseRows(format, headers, lines.drop(1))
    }

    fun parseRows(
        format: BankFormat,
        headers: List<String>,
        lines: List<String>
    ): List<CsvCandidate> {
        if (format == BankFormat.DESCONOCIDO) return emptyList()
        val h = headers.map { it.normalized() }
        fun col(vararg keys: String): Int =
            h.indexOfFirst { cell -> keys.any { cell.contains(it) } }
        val fechaIx = col("fecha")
        val conceptoIx = col("descrip", "concepto", "movimiento", "detalle")
        val cargoIx = col("cargo", "retiro", "egreso")
        val abonoIx = col("abono", "deposito", "ingreso")
        val montoIx = col("monto", "importe", "cantidad", "total")
        if (fechaIx < 0 || conceptoIx < 0 || (cargoIx < 0 && abonoIx < 0 && montoIx < 0)) {
            return emptyList()
        }
        return lines.mapIndexedNotNull { i, line ->
            val cells = splitRow(line)
            fun at(ix: Int): String = cells.getOrElse(ix) { "" }.trim()
            val dateIso = parseDate(at(fechaIx)) ?: return@mapIndexedNotNull null
            val concept = at(conceptoIx).ifBlank { return@mapIndexedNotNull null }
            val cargo = parseAmount(at(cargoIx.takeIf { it >= 0 } ?: -1))
            val abono = parseAmount(at(abonoIx.takeIf { it >= 0 } ?: -1))
            val (amount, isIncome) = when {
                cargo != null && cargo > 0 -> cargo to false
                abono != null && abono > 0 -> abono to true
                montoIx >= 0 -> {
                    val m = parseSigned(at(montoIx)) ?: return@mapIndexedNotNull null
                    if (m == 0.0) return@mapIndexedNotNull null
                    (if (m < 0) -m else m) to (m > 0)
                }
                else -> return@mapIndexedNotNull null
            }
            CsvCandidate(
                dateIso = dateIso,
                amount = amount,
                isIncome = isIncome,
                concept = concept,
                rowIndex = i
            )
        }
    }

    /**
     * Ocurrencia sintética para reutilizar [findLinkCandidates]: si el banco
     * ya vive como registro de Inicio, la conciliación ofrece vincular en
     * vez de duplicar.
     */
    fun toOccurrence(candidate: CsvCandidate): Occurrence =
        Occurrence(
            date = LocalDate.parse(candidate.dateIso),
            pattern = Pattern(
                id = "csv-${candidate.rowIndex}",
                name = candidate.concept,
                type = if (candidate.isIncome) "INCOME" else "EXPENSE",
                baseAmount = candidate.amount,
                frequency = "once",
                startDate = LocalDate.parse(candidate.dateIso),
                category = guessCategory(candidate.concept),
                description = candidate.concept
            )
        )

    /** Vínculo exacto: mismo día, mismo monto y mismo lado. */
    fun exactMatch(
        candidate: CsvCandidate,
        existing: List<TransactionEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): TransactionEntity? {
        val day = runCatching { LocalDate.parse(candidate.dateIso) }.getOrNull()
            ?: return null
        return existing.firstOrNull { tx ->
            val txDay = tx.timestamp.toLocalDateIn(zone)
            txDay == day &&
                tx.amount == candidate.amount &&
                (tx.kind?.isIncome == true) == candidate.isIncome
        }
    }

    /** Clave anti-reimport: la misma fila dos veces no crea dos registros. */
    fun dedupKey(candidate: CsvCandidate): String =
        "${candidate.dateIso}|${candidate.amount}|${candidate.isIncome}|${candidate.concept.normalized()}"

    internal fun guessCategory(concept: String): String =
        VoiceExpenseParser.parse(concept).category

    /**
     * Monta "1,500.00", "$ 2 300.50", "(450.00)" o "-$120" a Double.
     * null = celda vacía o no numérica.
     */
    internal fun parseAmount(raw: String): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        var s = t.replace("$", "").replace(" ", "").replace("'", "")
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        if (s.startsWith("-")) {
            negative = true
            s = s.substring(1)
        }
        if (s.startsWith("+")) s = s.substring(1)
        // Miles con coma ("1,500.00") o punto ("1.500,00"): el último
        // separador manda como decimal.
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.')
                else s.replace(",", "")
            lastComma >= 0 ->
                if (Regex(""",\d{2}$""").containsMatchIn(s)) s.replace(",", ".")
                else s.replace(",", "")
            else -> s
        }
        val value = s.toDoubleOrNull() ?: return null
        if (value == 0.0) return value
        return if (negative) -value else value
    }

    internal fun parseSigned(raw: String): Double? = parseAmount(raw)

    /** "15/09/2026", "15-09-2026", "2026-09-15", "15/09/26" -> ISO. */
    internal fun parseDate(raw: String): String? {
        val t = raw.trim().substringBefore(" ").trim()
        val dmy = Regex("""^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})$""").matchEntire(t)
        if (dmy != null) {
            val (d, m, y) = dmy.destructured
            val year = if (y.length == 2) "20$y" else y
            return runCatching {
                LocalDate.of(year.toInt(), m.toInt(), d.toInt()).toString()
            }.getOrNull()
        }
        if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(t)) {
            return runCatching { LocalDate.parse(t).toString() }.getOrNull()
        }
        return null
    }

    /** Divide una fila CSV respetando comillas ("a,\"b,c\",d). */
    internal fun splitRow(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                (c == ',' || c == ';') && !inQuotes -> {
                    out.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString().trim())
        return out.map { it.removeSurrounding("\"").trim() }
    }

    private fun String.normalized(): String =
        java.text.Normalizer.normalize(this.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
