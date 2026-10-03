package com.fintrack.app.domain

import java.time.YearMonth

/**
 * Estrategia de deudas (§D2): bola de nieve vs avalancha. Puro y testeable
 * en JVM. Agrega TODO lo que debes (tarjetas vía `CreditCardPlanner.summarize`
 * con cargos reales + MSI activos + deudas D8) y calcula tu fecha de libertad
 * con un pago mensual total dado.
 *
 * Interés mensual = CAT/12/100 × saldo. MSI y personales entran con CAT 0
 * (sin intereses). Cada mes el pago se reparte en el orden de la estrategia:
 * se liquida una deuda por completo antes de pasar a la siguiente.
 */
object DebtPlanner {

    /** CAT anual por defecto cuando la tarjeta no define uno (60%). */
    const val DEFAULT_CAT = 60.0

    /** Tope de simulación: 50 años; más allá se declara inviable. */
    const val MAX_MESES = 600

    /** Menor saldo primero (nieve) vs mayor interés primero (avalancha). */
    enum class Estrategia { NIEVE, AVALANCHA }

    data class Deuda(
        val id: String,
        val nombre: String,
        /** Saldo actual a pagar. */
        val saldo: Double,
        /** CAT anual en % (ej. 60.0). 0 = sin intereses. */
        val catAnual: Double = DEFAULT_CAT
    ) {
        /** Tasa mensual en tanto por uno. */
        val tasaMensual: Double get() = catAnual / 100.0 / 12.0
    }

    data class FilaMes(
        val mes: YearMonth,
        /** Pagado a cada deuda ese mes (id → monto). */
        val pagos: Map<String, Double>,
        val interesDelMes: Double,
        /** Saldo total restante al cierre del mes. */
        val saldoRestante: Double
    )

    data class Resultado(
        /** Orden en que se liquidan (ids). */
        val orden: List<String>,
        val filas: List<FilaMes>,
        val interesTotal: Double,
        /** null = con ese pago nunca terminas. */
        val fechaLibertad: YearMonth?,
        /** Total inicial agregado. */
        val deudaInicial: Double
    ) {
        /** Meses hasta la libertad (null si inviable). */
        val meses: Int? get() = fechaLibertad?.let { filas.size }
    }

    /**
     * Junta todas las fuentes en una sola lista (tarjetas + MSI + personales).
     * Listas vacías o saldos <= 0 se ignoran.
     */
    fun agregar(
        tarjetas: List<Deuda> = emptyList(),
        msi: List<Deuda> = emptyList(),
        personales: List<Deuda> = emptyList()
    ): List<Deuda> = (tarjetas + msi + personales).filter { it.saldo > 0.0 }

    fun total(deudas: List<Deuda>): Double = deudas.sumOf { it.saldo }

    /**
     * Simula el pago mes a mes con [pagoMensual] fijo repartido en el orden de
     * la [estrategia]. Nieve = menor saldo primero (gana rápido);
     * avalancha = mayor CAT primero (paga menos interés).
     */
    fun simular(
        deudas: List<Deuda>,
        pagoMensual: Double,
        desde: YearMonth,
        estrategia: Estrategia
    ): Resultado {
        val vivas = deudas.filter { it.saldo > 0.0 }
        val inicial = total(vivas)
        if (vivas.isEmpty() || pagoMensual <= 0.0) {
            return Resultado(emptyList(), emptyList(), 0.0, null, inicial)
        }
        val ordenadas = when (estrategia) {
            Estrategia.NIEVE -> vivas.sortedWith(compareBy({ it.saldo }, { it.id }))
            Estrategia.AVALANCHA -> vivas.sortedWith(compareByDescending<Deuda> { it.catAnual }.thenBy { it.saldo }.thenBy { it.id })
        }
        // El pago debe superar el interés del primer mes o nunca baja.
        val interesPrimerMes = vivas.sumOf { it.saldo * it.tasaMensual }
        if (pagoMensual <= interesPrimerMes) {
            return Resultado(ordenadas.map { it.id }, emptyList(), 0.0, null, inicial)
        }
        val saldos = ordenadas.associate { it.id to it.saldo }.toMutableMap()
        val filas = mutableListOf<FilaMes>()
        val ordenLiquidacion = mutableListOf<String>()
        var interesTotal = 0.0
        var mes = desde
        var guard = 0
        while (saldos.values.any { it > 0.0 } && guard < MAX_MESES) {
            var interesMes = 0.0
            ordenadas.forEach { deuda ->
                val vivo = saldos.getValue(deuda.id)
                if (vivo > 0.0) {
                    val interes = vivo * deuda.tasaMensual
                    interesMes += interes
                    saldos[deuda.id] = vivo + interes
                }
            }
            interesTotal += interesMes
            var resto = pagoMensual
            val pagos = mutableMapOf<String, Double>()
            for (deuda in ordenadas) {
                if (resto <= 0.0) break
                val vivo = saldos.getValue(deuda.id)
                if (vivo <= 0.0) continue
                val pago = minOf(vivo, resto)
                saldos[deuda.id] = redondea2(vivo - pago)
                if (saldos.getValue(deuda.id) < 0.005) {
                    saldos[deuda.id] = 0.0
                    if (!ordenLiquidacion.contains(deuda.id)) ordenLiquidacion.add(deuda.id)
                }
                pagos[deuda.id] = redondea2(pago)
                resto = redondea2(resto - pago)
            }
            val restante = redondea2(saldos.values.sum())
            filas.add(FilaMes(mes, pagos, redondea2(interesMes), restante))
            if (restante <= 0.0) break
            mes = mes.plusMonths(1)
            guard++
        }
        val libre = saldos.values.all { it <= 0.0 }
        return Resultado(
            orden = ordenLiquidacion,
            filas = filas,
            interesTotal = redondea2(interesTotal),
            fechaLibertad = if (libre && filas.isNotEmpty()) filas.last().mes else null,
            deudaInicial = redondea2(inicial)
        )
    }

    private fun redondea2(value: Double): Double =
        kotlin.math.round(value * 100.0) / 100.0
}
