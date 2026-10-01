package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Vinculación registro ↔ evento programado: si un ingreso/gasto ya existe
 * el día de la ocurrencia (±[windowDays]), se ofrece vincularlo en vez de
 * confirmar duplicado (que contaría el dinero dos veces).
 *
 * Señales (lado y fecha siempre obligatorios):
 * - misma categoría (ej. Sueldo), y/o
 * - mismo monto, y/o
 * - traslape de concepto (nombre/descripción del recurrente vs
 *   título/nota/comercio del registro).
 */
data class LinkCandidate(
    /** "mov:<id>" o "tx:<id>". */
    val key: String,
    val title: String,
    val amount: Double,
    val dateIso: String,
    /** "Detectado" (listener) o "Manual/Inicio". */
    val origin: String,
    /** Menor = mejor (0 categoría+monto, 1 uno de los dos, 2 solo concepto). */
    val rank: Int
)

fun findLinkCandidates(
    occurrence: Occurrence,
    movements: List<MovementRow>,
    transactions: List<TransactionEntity>,
    linkedTxIds: Set<String> = emptySet(),
    windowDays: Long = 1
): List<LinkCandidate> {
    val side = occurrence.pattern.kind?.isIncome ?: return emptyList()
    val conceptTokens = conceptTokens(
        occurrence.pattern.name + " " + occurrence.pattern.description
    )
    val out = mutableListOf<LinkCandidate>()
    movements.forEach { mov ->
        if (mov.archived) return@forEach
        if (mov.income_pattern_id != null || mov.expense_pattern_id != null) return@forEach
        if ((mov.kind?.isIncome == true) != side) return@forEach
        val date = runCatching { LocalDate.parse(mov.date) }.getOrNull() ?: return@forEach
        val gap = daysBetween(occurrence.date, date)
        if (gap > windowDays) return@forEach
        val rank = matchRank(
            occurrence, conceptTokens,
            mov.category, mov.confirmed_amount,
            mov.title + " " + mov.description
        ) ?: return@forEach
        out.add(
            LinkCandidate(
                key = "mov:${mov.id}",
                title = mov.title.ifBlank { mov.category },
                amount = mov.confirmed_amount,
                dateIso = mov.date,
                origin = "Manual",
                rank = rank * 10 + gap.toInt()
            )
        )
    }
    transactions.forEach { tx ->
        if (tx.id in linkedTxIds) return@forEach
        if ((tx.kind?.isIncome == true) != side) return@forEach
        val date = tx.timestamp.toLocalDateIn(ZoneId.systemDefault())
        val gap = daysBetween(occurrence.date, date)
        if (gap > windowDays) return@forEach
        val rank = matchRank(
            occurrence, conceptTokens,
            tx.category, tx.amount,
            (tx.merchant ?: "") + " " + tx.description
        ) ?: return@forEach
        out.add(
            LinkCandidate(
                key = "tx:${tx.id}",
                title = (tx.merchant ?: tx.description).ifBlank { tx.category },
                amount = tx.amount,
                dateIso = date.toString(),
                origin = if (tx.source == "AUTO") "Detectado" else "Inicio",
                rank = rank * 10 + gap.toInt()
            )
        )
    }
    return out.sortedWith(compareBy({ it.rank }, { it.dateIso }, { it.key }))
}

/** true si la ocurrencia ya quedó cubierta (FK exacta o cercana, o tx vinculado). */
fun isOccurrenceConfirmed(
    occurrence: Occurrence,
    movements: List<MovementRow>,
    linkedOccKeys: Set<String>,
    windowDays: Long = 1
): Boolean {
    if ("${occurrence.pattern.id}_${occurrence.date}" in linkedOccKeys) return true
    val side = occurrence.pattern.kind?.isIncome ?: return false
    return movements.any { mov ->
        !mov.archived &&
            (mov.income_pattern_id == occurrence.pattern.id ||
                mov.expense_pattern_id == occurrence.pattern.id) &&
            (mov.kind?.isIncome == true) == side &&
            runCatching {
                daysBetween(occurrence.date, LocalDate.parse(mov.date)) <= windowDays
            }.getOrDefault(false)
    }
}

private fun daysBetween(a: LocalDate, b: LocalDate): Long {
    val d = ChronoUnit.DAYS.between(a, b)
    return if (d < 0) -d else d
}

private fun matchRank(
    occurrence: Occurrence,
    conceptTokens: Set<String>,
    category: String,
    amount: Double,
    conceptText: String
): Int? {
    val catOk = category.trim().equals(occurrence.pattern.category.trim(), ignoreCase = true)
    val amtOk = amount == occurrence.amount
    val conOk = conceptTokens.isNotEmpty() && conceptTokens.any { conceptTokens(conceptText).contains(it) }
    return when {
        catOk && amtOk -> 0
        catOk || amtOk -> 1
        conOk -> 2
        else -> null
    }
}

private fun conceptTokens(text: String): Set<String> {
    val normalized = java.text.Normalizer.normalize(
        text.lowercase(), java.text.Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "")
    return normalized.split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 4 && it.any { c -> c.isLetter() } }
        .toSet()
}
