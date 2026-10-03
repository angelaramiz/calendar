package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pregunta a tus datos (D9): plantillas en español sobre tus registros,
 * sin LLM ni red. Si la pregunta no matchea ninguna plantilla responde
 * [QueryResult.NoEntendido] explícito: cero alucinaciones por diseño.
 *
 * Fuentes: transacciones (gasto/ingreso/top/comparativa/promedio/conteo/
 * balance) y metas (cuánto falta/avance). Patrones y MSI quedan fuera a
 * propósito: `BudgetViewModel` no los expone en su estado (ver snippet de
 * integración en el reporte) y aquí solo se responde con lo visible.
 *
 * Meses: nombre en español ("en marzo"), "este mes", "mes pasado/anterior".
 * Sin año = el más reciente no futuro (si hoy es febrero y pides "marzo",
 * es marzo del año pasado). Con "de 2025" se respeta el año.
 */
data class QueryExample(
    /** Fecha ISO del movimiento (yyyy-MM-dd). */
    val fecha: String,
    val concepto: String,
    val monto: Double
)

data class QueryAnswer(
    val titulo: String,
    val cifra: Double,
    val detalle: String,
    val ejemplos: List<QueryExample>
)

sealed interface QueryResult {
    data class Entendido(val answer: QueryAnswer) : QueryResult
    data class NoEntendido(val pregunta: String) : QueryResult
}

object DataQuery {

    private val Spanish = Locale("es")

    /** Ejemplos para mostrar junto al "no entendí". */
    val SUGERENCIAS = listOf(
        "¿Cuánto gasté en comida en marzo?",
        "¿Cuánto me entró en agosto?",
        "Top comercios en marzo",
        "¿Gasté más en marzo o en abril?",
        "¿Cómo me fue en marzo?",
        "¿Cuánto me falta para mi meta carro?"
    )

    private val monthNames = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12
    )

    private fun String.normalized(): String =
        Normalizer.normalize(this.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun monthLabel(month: YearMonth): String {
        val name = month.month.getDisplayName(TextStyle.FULL, Spanish)
            .replaceFirstChar { it.uppercase() }
        return "$name ${month.year}"
    }

    private data class MonthRef(val raw: String, val month: YearMonth)

    private fun resolveMonth(monthNumber: Int, year: Int?, today: LocalDate): YearMonth {
        if (year != null) return YearMonth.of(year, monthNumber)
        var candidate = YearMonth.of(today.year, monthNumber)
        if (candidate.isAfter(YearMonth.from(today))) {
            candidate = candidate.minusYears(1)
        }
        return candidate
    }

    /** Todas las referencias a meses en la pregunta, en orden de aparición. */
    private fun findMonths(q: String, today: LocalDate): List<MonthRef> {
        val found = mutableListOf<Pair<Int, MonthRef>>()
        if (q.contains("este mes")) {
            found += q.indexOf("este mes") to
                MonthRef("este mes", YearMonth.from(today))
        }
        val pastIdx = listOf("mes pasado", "mes anterior", "el mes anterior")
            .firstOrNull { it in q }?.let { q.indexOf(it) }
        if (pastIdx != null) {
            found += pastIdx to
                MonthRef("mes pasado", YearMonth.from(today).minusMonths(1))
        }
        val yearPattern = Regex("""(de|del)\s+(\d{4})""")
        monthNames.forEach { (name, number) ->
            var idx = q.indexOf(name)
            while (idx >= 0) {
                val after = q.substring(idx + name.length).trim()
                val year = yearPattern.find(after)
                    ?.takeIf { it.range.first <= 8 }
                    ?.groupValues?.get(2)?.toIntOrNull()
                found += idx to MonthRef(name, resolveMonth(number, year, today))
                idx = q.indexOf(name, idx + 1)
            }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun txDate(tx: TransactionEntity, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()

    private fun inMonth(tx: TransactionEntity, month: YearMonth, zone: ZoneId): Boolean =
        YearMonth.from(txDate(tx, zone)) == month

    private fun isExpense(tx: TransactionEntity): Boolean = tx.kind == TxKind.EXPENSE

    private fun isIncome(tx: TransactionEntity): Boolean = tx.kind == TxKind.INCOME

    private fun haystackOf(tx: TransactionEntity): String =
        "${tx.category} ${tx.merchant ?: ""} ${tx.description}".normalized()

    /** El filtro matchea si TODAS sus palabras aparecen en categoría/comercio/nota. */
    private fun matchesFilter(tx: TransactionEntity, filtro: String): Boolean {
        val words = filtro.normalized().split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return true
        val hay = haystackOf(tx)
        return words.all { it in hay }
    }

    private fun conceptoOf(tx: TransactionEntity): String {
        val base = tx.merchant?.trim()?.takeIf { it.isNotBlank() }
            ?: tx.description.trim().takeIf { it.isNotBlank() }
            ?: tx.category
        return base.take(40)
    }

    private fun ejemplosDe(txs: List<TransactionEntity>, zone: ZoneId): List<QueryExample> =
        txs.sortedByDescending { it.timestamp }.take(3).map { tx ->
            QueryExample(
                fecha = txDate(tx, zone).toString(),
                concepto = conceptoOf(tx),
                monto = tx.amount
            )
        }

    /**
     * Filtro = lo que está entre el verbo y el mes:
     * "cuanto gaste EN tacos EN marzo" → "tacos".
     * Sin filtro (solo mes) → suma total del mes.
     */
    private fun extractFiltro(q: String, months: List<MonthRef>): String {
        val segments = q.split(" en ").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size < 2) return ""
        val body = segments.drop(1)
        val monthRaws = months.map { it.raw }.toSet()
        return body.filter { seg -> monthRaws.none { it in seg } }.joinToString(" ").trim()
    }

    fun answer(
        question: String,
        transactions: List<TransactionEntity>,
        goals: List<SavingsGoal> = emptyList(),
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): QueryResult {
        val q = question.normalized().replace("?", " ").replace("¿", " ").trim()
        if (q.isBlank()) return QueryResult.NoEntendido(question)
        val months = findMonths(q, today)
        val month = months.firstOrNull()?.month
        val current = YearMonth.from(today)

        // 1. Comparativa de meses: "gasté más en marzo o en abril".
        if (months.size >= 2 && (" vs " in q || " o " in q || "compar" in q || "versus" in q)) {
            val m1 = months[0].month
            val m2 = months[1].month
            val t1 = transactions.filter { isExpense(it) && inMonth(it, m1, zone) }
            val t2 = transactions.filter { isExpense(it) && inMonth(it, m2, zone) }
            val s1 = t1.sumOf { it.amount }
            val s2 = t2.sumOf { it.amount }
            val (ganador, cifra) = if (s2 >= s1) monthLabel(m2) to s2 else monthLabel(m1) to s1
            val delta = kotlin.math.abs(s2 - s1)
            val ejemplos = (t1 + t2).sortedByDescending { it.amount }
                .take(2).map { tx ->
                    QueryExample(txDate(tx, zone).toString(), conceptoOf(tx), tx.amount)
                }
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = "${monthLabel(m1)} vs ${monthLabel(m2)}: más en $ganador",
                    cifra = cifra,
                    detalle = "${monthLabel(m1)}: ${s1.toMoney()} · " +
                        "${monthLabel(m2)}: ${s2.toMoney()} · " +
                        "diferencia ${delta.toMoney()}",
                    ejemplos = ejemplos
                )
            )
        }

        // 2. Top comercios: "top comercios en marzo" / "dónde gasté más".
        if ("top" in q || (("donde" in q || "dónde" in q) && "mas" in q) ||
            ("en que comercio" in q) || ("en que gasto mas" in q)
        ) {
            val target = month ?: current
            val top = transactions.filter { isExpense(it) && inMonth(it, target, zone) }
                .groupBy { conceptoOf(it) }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(3)
            if (top.isEmpty()) {
                return QueryResult.Entendido(
                    QueryAnswer(
                        titulo = "Top comercios · ${monthLabel(target)}",
                        cifra = 0.0,
                        detalle = "Sin gastos en ${monthLabel(target)}.",
                        ejemplos = emptyList()
                    )
                )
            }
            val detalle = top.mapIndexed { i, e -> "${i + 1}. ${e.key}: ${e.value.toMoney()}" }
                .joinToString(" · ")
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = "Top comercios · ${monthLabel(target)}",
                    cifra = top.first().value,
                    detalle = detalle,
                    ejemplos = ejemplosDe(
                        transactions.filter { isExpense(it) && inMonth(it, target, zone) },
                        zone
                    )
                )
            )
        }

        // 3. Metas: "cuánto me falta para <nombre>" / "cómo va mi meta".
        if ("meta" in q || "objetivo" in q) {
            val goal = goals.firstOrNull { it.name.normalized() in q }
                ?: goals.singleOrNull()
            if (goal == null) {
                val detalle = if (goals.isEmpty()) {
                    "Aún no tienes metas. Agrega una en Presupuesto → Objetivos."
                } else {
                    "Tienes ${goals.size} metas: " +
                        goals.joinToString(", ") { it.name } +
                        ". Pregunta por una, ej. «¿cuánto me falta para ${goals.first().name}?»"
                }
                return QueryResult.Entendido(
                    QueryAnswer(
                        titulo = "Tu meta",
                        cifra = 0.0,
                        detalle = detalle,
                        ejemplos = emptyList()
                    )
                )
            }
            val falta = (goal.price - goal.aportado).coerceAtLeast(0.0)
            val ejemplos = goal.aportaciones.takeLast(3).reversed().map { a ->
                QueryExample(
                    fecha = a.fechaIso,
                    concepto = if (a.monto >= 0) "Aportación" else "Retiro",
                    monto = kotlin.math.abs(a.monto)
                )
            }
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = "Meta ${goal.name}: te faltan ${falta.toMoney()}",
                    cifra = falta,
                    detalle = "Juntado ${goal.aportado.toMoney()} de ${goal.price.toMoney()}" +
                        if (falta <= 0.0) " · ¡Meta juntada!" else "",
                    ejemplos = ejemplos
                )
            )
        }

        // 4. Conteo: "cuántas veces compré en oxxo en marzo".
        if ("cuantas veces" in q || "cuantos " in q && "veces" in q || "numero de" in q || "número de" in q) {
            val target = month ?: current
            val filtro = extractFiltro(q, months)
            val hit = transactions.filter {
                isExpense(it) && inMonth(it, target, zone) && matchesFilter(it, filtro)
            }
            val donde = if (filtro.isBlank()) monthLabel(target) else "«$filtro» en ${monthLabel(target)}"
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = "${hit.size} compras $donde",
                    cifra = hit.size.toDouble(),
                    detalle = if (hit.isEmpty()) {
                        "Sin compras $donde."
                    } else {
                        "${hit.size} compras por ${hit.sumOf { it.amount }.toMoney()} $donde."
                    },
                    ejemplos = ejemplosDe(hit, zone)
                )
            )
        }

        // 5. Promedio: "promedio de gasto en comida en marzo".
        if ("promedio" in q) {
            val target = month ?: current
            val filtro = extractFiltro(q, months)
            val hit = transactions.filter {
                isExpense(it) && inMonth(it, target, zone) && matchesFilter(it, filtro)
            }
            val avg = if (hit.isEmpty()) 0.0 else hit.sumOf { it.amount } / hit.size
            val donde = if (filtro.isBlank()) monthLabel(target) else "«$filtro» en ${monthLabel(target)}"
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = "Ticket promedio $donde",
                    cifra = avg,
                    detalle = if (hit.isEmpty()) {
                        "Sin compras $donde."
                    } else {
                        "Promedio ${avg.toMoney()} en ${hit.size} compras $donde."
                    },
                    ejemplos = ejemplosDe(hit, zone)
                )
            )
        }

        // 6. Ingresos: "cuánto me entró / ingresó / gané / recibí en agosto".
        // "gane" (no el stem "gan": "ganó el partido" no es un ingreso).
        if ("entr" in q || "ingres" in q || "gane" in q || "ganancia" in q || "recib" in q) {
            val target = month ?: current
            val hit = transactions.filter { isIncome(it) && inMonth(it, target, zone) }
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = "Ingresos · ${monthLabel(target)}",
                    cifra = hit.sumOf { it.amount },
                    detalle = if (hit.isEmpty()) {
                        "Sin ingresos en ${monthLabel(target)}."
                    } else {
                        "${hit.size} ingresos por ${hit.sumOf { it.amount }.toMoney()} " +
                            "en ${monthLabel(target)}."
                    },
                    ejemplos = ejemplosDe(hit, zone)
                )
            )
        }

        // 7. Balance: "cómo me fue / balance / cuánto ahorré en marzo".
        if ("balance" in q || "como me fue" in q || "ahorr" in q) {
            val target = month ?: current
            val ing = transactions.filter { isIncome(it) && inMonth(it, target, zone) }
                .sumOf { it.amount }
            val gas = transactions.filter { isExpense(it) && inMonth(it, target, zone) }
                .sumOf { it.amount }
            val neto = ing - gas
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = "Balance · ${monthLabel(target)}: ${neto.toMoney()}",
                    cifra = neto,
                    detalle = "Entró ${ing.toMoney()} y salió ${gas.toMoney()} " +
                        "en ${monthLabel(target)}.",
                    ejemplos = ejemplosDe(
                        transactions.filter { inMonth(it, target, zone) },
                        zone
                    )
                )
            )
        }

        // 8. Gasto: "cuánto gasté [en <filtro>] [en <mes>]".
        if ("gast" in q || "compr" in q || "pag" in q) {
            val target = month ?: current
            val filtro = extractFiltro(q, months)
            val hit = transactions.filter {
                isExpense(it) && inMonth(it, target, zone) && matchesFilter(it, filtro)
            }
            val titulo = if (filtro.isBlank()) {
                "Gasto total · ${monthLabel(target)}"
            } else {
                "Gasto en «$filtro» · ${monthLabel(target)}"
            }
            return QueryResult.Entendido(
                QueryAnswer(
                    titulo = titulo,
                    cifra = hit.sumOf { it.amount },
                    detalle = if (hit.isEmpty()) {
                        if (filtro.isBlank()) {
                            "Sin gastos en ${monthLabel(target)}."
                        } else {
                            "Nada en «$filtro» en ${monthLabel(target)}."
                        }
                    } else {
                        "${hit.size} gastos por ${hit.sumOf { it.amount }.toMoney()} " +
                            "en ${monthLabel(target)}."
                    },
                    ejemplos = ejemplosDe(hit, zone)
                )
            )
        }

        return QueryResult.NoEntendido(question)
    }

    private fun Double.toMoney(): String =
        "$" + "%,.0f".format(this).replace(',', '.')
}
