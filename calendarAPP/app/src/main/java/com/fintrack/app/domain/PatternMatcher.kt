package com.fintrack.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Auto-vínculo aviso → evento recurrente: si el texto del aviso trae el
 * concepto del recurrente (nombre/descripción que puso el usuario, ej.
 * "hybridge" para la colegiatura), el movimiento detectado se reconoce
 * como ese evento y se marca solo (vínculo local, igual que Vincular
 * manual: excluye del balance y oculta la proyección, con 🔗).
 *
 * Dinámico por diseño: no hay lista de palabras clave, el concepto sale
 * de tus recurrentes. Reglas:
 * - lado (ingreso/gasto) siempre obligatorio;
 * - el concepto debe aportar ≥1 token (misma convención que
 *   [findLinkCandidates]: ≥4 letras, normalizado sin acentos);
 * - debe haber una ocurrencia esperada a ±[windowDays] (default 2) del aviso;
 * - el monto NO se exige (colegiaturas con recargos varían).
 */
fun matchPatternByConcept(
    title: String,
    text: String,
    isIncome: Boolean,
    patterns: List<Pattern>,
    today: LocalDate,
    windowDays: Long = 2
): Occurrence? {
    val hayTokens = conceptTokens("$title $text")
    if (hayTokens.isEmpty()) return null
    var best: Occurrence? = null
    var bestGap = Long.MAX_VALUE
    var bestId = ""
    patterns.forEach { pattern ->
        if (!pattern.active) return@forEach
        if (pattern.kind?.isIncome != isIncome) return@forEach
        val concept = conceptTokens(pattern.name + " " + pattern.description)
        if (concept.isEmpty()) return@forEach
        if (concept.none { it in hayTokens }) return@forEach
        val from = today.minusDays(windowDays)
        val to = today.plusDays(windowDays)
        PatternExpander.expand(pattern, from, to).forEach { occ ->
            val gap = ChronoUnit.DAYS.between(today, occ.date).let { if (it < 0) -it else it }
            if (gap < bestGap || (gap == bestGap && pattern.id < bestId)) {
                best = occ
                bestGap = gap
                bestId = pattern.id
            }
        }
    }
    return best
}

private fun conceptTokens(text: String): Set<String> {
    val normalized = java.text.Normalizer.normalize(
        text.lowercase(), java.text.Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "")
    return normalized.split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 4 && it.any { c -> c.isLetter() } }
        .toSet()
}
