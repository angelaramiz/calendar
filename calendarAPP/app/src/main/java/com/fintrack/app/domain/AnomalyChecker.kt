package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity

enum class AnomalyKind { DUPLICATE, PRICE_HIKE }

data class Anomaly(
    val kind: AnomalyKind,
    /** Comercio normalizado para mostrar. */
    val merchant: String,
    val amount: Double,
    /** Epoch del cargo que dispara el aviso. */
    val timestamp: Long,
    val detail: String
) {
    /** Id estable para dedup (`anom_`) y descarte de un toque. */
    val stableId: String get() = when (kind) {
        AnomalyKind.DUPLICATE -> "dup_${merchant.lowercase().hashCode()}_${timestamp}"
        AnomalyKind.PRICE_HIKE -> "hike_${merchant.lowercase().hashCode()}_${amount.toInt()}"
    }
}

/**
 * Vigilante (D5): duplicados y subidas de precio. Puro y testeable.
 * Solo mira gastos; los ingresos repetidos (nomina quincenal) no son anomalia.
 */
object AnomalyChecker {

    const val DUPLICATE_WINDOW_MS = 72L * 3600L * 1000L
    const val MIN_HISTORY_MONTHS = 3

    /** Tolerancia de centavos: lo mismo que [SubscriptionDetector]. */
    fun hikeTolerance(base: Double): Double = maxOf(1.0, base * 0.01)

    private fun TransactionEntity.isExpense(): Boolean = kind == TxKind.EXPENSE

    fun labelOf(tx: TransactionEntity): String? {
        val label = tx.merchant?.trim()?.takeIf { it.isNotBlank() }
            ?: tx.description.trim().takeIf { it.isNotBlank() }
            ?: return null
        return label
    }

    /**
     * Mismo comercio + mismo monto en <72h: avisa sobre el segundo cobro.
     * Cada cargo genera como maximo un aviso (contra el previo mas cercano).
     */
    fun findDuplicates(transactions: List<TransactionEntity>): List<Anomaly> {
        val ordered = transactions
            .filter { it.isExpense() }
            .mapNotNull { tx -> labelOf(tx)?.let { tx to it.lowercase() } }
            .sortedBy { (tx, _) -> tx.timestamp }
        val out = mutableListOf<Anomaly>()
        ordered.forEachIndexed { index, (tx, norm) ->
            val prev = (index - 1 downTo 0).firstNotNullOfOrNull { j ->
                val (cand, candNorm) = ordered[j]
                if (candNorm == norm && cand.amount == tx.amount &&
                    tx.timestamp - cand.timestamp < DUPLICATE_WINDOW_MS
                ) cand else null
            } ?: return@forEachIndexed
            val label = labelOf(tx) ?: return@forEachIndexed
            val hours = (tx.timestamp - prev.timestamp) / 3600000L
            out.add(
                Anomaly(
                    kind = AnomalyKind.DUPLICATE,
                    merchant = label,
                    amount = tx.amount,
                    timestamp = tx.timestamp,
                    detail = "Dos cobros de $${tx.amount.toInt()} en $label con ${hours}h de diferencia. Revisa si fue doble?"
                )
            )
        }
        return out
    }

    /**
     * Cargo repetido de suscripcion con monto mayor al historico:
     * 3+ meses con el mismo monto base y el cargo mas reciente por encima de
     * base + tolerancia. Centavos dentro de tolerancia no son subida.
     */
    fun findPriceHikes(transactions: List<TransactionEntity>): List<Anomaly> {
        val groups = mutableMapOf<String, MutableList<TransactionEntity>>()
        transactions.filter { it.isExpense() }.forEach { tx ->
            val label = labelOf(tx) ?: return@forEach
            groups.getOrPut(label.lowercase()) { mutableListOf() }.add(tx)
        }
        return groups.mapNotNull { (_, items) ->
            val ordered = items.sortedBy { it.timestamp }
            if (ordered.size < MIN_HISTORY_MONTHS + 1) return@mapNotNull null
            val history = ordered.dropLast(1)
            val base = history.groupingBy { it.amount }.eachCount()
                .maxBy { it.value }.key
            val baseMonths = history.filter { it.amount == base }
                .map {
                    java.time.YearMonth.from(
                        java.time.Instant.ofEpochMilli(it.timestamp)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    )
                }.distinct().size
            if (baseMonths < MIN_HISTORY_MONTHS) return@mapNotNull null
            val latest = ordered.last()
            if (latest.amount <= base + hikeTolerance(base)) return@mapNotNull null
            val label = labelOf(latest) ?: return@mapNotNull null
            Anomaly(
                kind = AnomalyKind.PRICE_HIKE,
                merchant = label,
                amount = latest.amount,
                timestamp = latest.timestamp,
                detail = "$label subio de $${base.toInt()} a $${latest.amount.toInt()}. La conservas?"
            )
        }
    }

    fun check(transactions: List<TransactionEntity>): List<Anomaly> =
        findDuplicates(transactions) + findPriceHikes(transactions)
}
