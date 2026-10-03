package com.fintrack.app.domain

import kotlin.math.ceil

/**
 * D4 Aportaciones a metas: lógica pura sobre [SavingsGoal].
 * Aportar suma (ignora montos <= 0); retirar resta sin dejar el juntado
 * en negativo (el historial guarda el retiro como monto negativo).
 */
object GoalContributions {

    fun aportar(goal: SavingsGoal, monto: Double, fechaIso: String): SavingsGoal {
        if (monto <= 0.0) return goal
        return goal.copy(
            aportado = goal.aportado + monto,
            aportaciones = goal.aportaciones + Aportacion(fechaIso = fechaIso, monto = monto)
        )
    }

    fun retirar(goal: SavingsGoal, monto: Double, fechaIso: String): SavingsGoal {
        if (monto <= 0.0) return goal
        val real = minOf(monto, goal.aportado.coerceAtLeast(0.0))
        if (real <= 0.0) return goal
        return goal.copy(
            aportado = (goal.aportado - real).coerceAtLeast(0.0),
            aportaciones = goal.aportaciones + Aportacion(fechaIso = fechaIso, monto = -real)
        )
    }

    /** Lo que falta para llegar a la meta (nunca negativo). */
    fun restante(goal: SavingsGoal): Double =
        (goal.price - goal.aportado).coerceAtLeast(0.0)

    /** Progreso 0..1 para la barra juntado/meta. */
    fun progreso(goal: SavingsGoal): Float =
        if (goal.price <= 0.0) 0f
        else (goal.aportado / goal.price).toFloat().coerceIn(0f, 1f)

    /**
     * Quincenas que faltan si apartas [montoQuincenal] por quincena.
     * null = sin apartado (no se inventa fecha); 0 = meta ya juntada.
     */
    fun quincenasRestantes(goal: SavingsGoal, montoQuincenal: Double): Int? {
        val falta = restante(goal)
        if (falta <= 0.0) return 0
        if (montoQuincenal <= 0.0) return null
        return ceil(falta / montoQuincenal).toInt().coerceAtLeast(1)
    }
}
