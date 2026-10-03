package com.fintrack.app.domain

import com.fintrack.app.data.PersonDebt

/**
 * Cálculo de saldos de deudas personales (D8). Puro y testeable en JVM:
 * saldo vivo = monto − abonos (nunca negativo; de más se considera a favor).
 */
object PersonDebtPlanner {

    /** Saldo pendiente de una deuda (0 si ya se liquidó). */
    fun saldo(deuda: PersonDebt): Double =
        (deuda.monto - deuda.abonos.sumOf { it.amount }).coerceAtLeast(0.0)

    /** Total abonado a una deuda. */
    fun abonado(deuda: PersonDebt): Double = deuda.abonos.sumOf { it.amount }

    /** true si la deuda quedó en ceros. */
    fun estaLiquidada(deuda: PersonDebt): Boolean = saldo(deuda) <= 0.0

    data class Totales(
        /** Me deben (suma de saldos con esDeudaMia = false). */
        val porCobrar: Double,
        /** Yo debo (suma de saldos con esDeudaMia = true). */
        val porPagar: Double
    ) {
        /** Neto a mi favor: positivo = me deben más de lo que debo. */
        val neto: Double get() = porCobrar - porPagar
    }

    /** Agrega saldos vivos por dirección (liquidadas suman 0). */
    fun totales(deudas: List<PersonDebt>): Totales {
        var porCobrar = 0.0
        var porPagar = 0.0
        deudas.forEach { deuda ->
            val vivo = saldo(deuda)
            if (deuda.esDeudaMia) porPagar += vivo else porCobrar += vivo
        }
        return Totales(porCobrar, porPagar)
    }
}
