package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.data.repository.PatternRow

/**
 * Anti-duplicados sin DDL: cuando una respuesta se pierde (el servidor sí
 * guardó pero el teléfono no lo vio), el reintento reenvía el mismo payload.
 * El reintento conserva el timestamp al milisegundo, así que la igualdad
 * exacta distingue "mismo envío" de "otra compra igual".
 */

private fun TransactionEntity.isIncome(): Boolean = kind?.isIncome == true

/** Reenvío del mismo registro de Inicio: devuelve la fila ya guardada. */
fun findDuplicateTx(
    candidate: TransactionEntity,
    existing: List<TransactionEntity>
): TransactionEntity? = existing.firstOrNull {
    it.id != candidate.id &&
        it.amount == candidate.amount &&
        it.timestamp == candidate.timestamp &&
        it.isIncome() == candidate.isIncome() &&
        it.category == candidate.category &&
        it.description == candidate.description
}

/** Reenvío del mismo movimiento (manual o confirmación): fila ya guardada. */
fun findDuplicateMovement(
    dateIso: String,
    type: String,
    title: String,
    description: String,
    category: String,
    amount: Double,
    patternId: String?,
    existing: List<MovementRow>
): MovementRow? = existing.firstOrNull {
    it.date == dateIso &&
        it.type.equals(type, ignoreCase = true) &&
        it.title == title &&
        it.description == description &&
        it.category == category &&
        it.confirmed_amount == amount &&
        (it.income_pattern_id ?: it.expense_pattern_id) == patternId
}

/** Reenvío del mismo recurrente: patrón ya guardado. */
fun findDuplicatePattern(
    name: String,
    description: String,
    category: String,
    baseAmount: Double,
    frequency: String,
    startDateIso: String,
    existing: List<PatternRow>
): PatternRow? = existing.firstOrNull {
    it.name == name &&
        it.description == description &&
        it.category == category &&
        it.base_amount == baseAmount &&
        it.frequency == frequency &&
        it.start_date == startDateIso
}
