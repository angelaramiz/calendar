package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow

/**
 * Tipo canónico de movimiento. Las fuentes usan dos alfabetos
 * ("INCOME"/"EXPENSE" en transacciones y patrones, "ingreso"/"gasto" en
 * movimientos del servidor): este es el único lugar que los mapea.
 * El serializado se conserva tal cual; esto solo centraliza lecturas.
 */
enum class TxKind {
    INCOME,
    EXPENSE;

    val isIncome: Boolean get() = this == INCOME

    companion object {
        /** null = tipo desconocido (se ignora, no se asume gasto). */
        fun of(type: String?): TxKind? =
            when {
                type.equals("INCOME", ignoreCase = true) ||
                    type.equals("ingreso", ignoreCase = true) -> INCOME
                type.equals("EXPENSE", ignoreCase = true) ||
                    type.equals("gasto", ignoreCase = true) -> EXPENSE
                else -> null
            }
    }
}

val TransactionEntity.kind: TxKind? get() = TxKind.of(type)

val MovementRow.kind: TxKind? get() = TxKind.of(type)

val Pattern.kind: TxKind? get() = TxKind.of(type)
