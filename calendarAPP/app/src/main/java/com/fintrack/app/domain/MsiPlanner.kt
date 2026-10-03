package com.fintrack.app.domain

import com.fintrack.app.data.MsiPlan
import java.time.LocalDate
import java.time.YearMonth

/**
 * Rastreador MSI (§A). Puro y testeable en JVM.
 *
 * Los planes son capa de lectura sobre cargos tagueados: cada plan proyecta
 * sus cuotas sobre los cortes de su tarjeta y cada cuota se marca cubierta
 * cuando en su periodo hay cargos tagueados >= parcial.
 */
object MsiPlanner {

    /** Meses que ofrecen los bancos (editable). */
    val MESES_VALIDOS = setOf(3, 6, 9, 12, 18, 24)

    /** Tolerancia de centavos al comparar cargos contra el parcial. */
    const val TOLERANCIA = 0.01

    private fun atDay(month: YearMonth, day: Int): LocalDate {
        val safe = day.coerceIn(1, month.lengthOfMonth())
        return LocalDate.of(month.year, month.month, safe)
    }

    /** Último corte en o antes de hoy (mismo criterio que CreditCardPlanner). */
    fun lastCutoff(cutoffDay: Int, today: LocalDate): LocalDate {
        val thisMonth = atDay(YearMonth.from(today), cutoffDay)
        return if (!thisMonth.isAfter(today)) thisMonth
        else atDay(YearMonth.from(today).minusMonths(1), cutoffDay)
    }

    /**
     * Parciales del plan: mensualidad truncada al centavo con ajuste en la
     * última cuota para que la suma sea exactamente el total.
     */
    fun parciales(plan: MsiPlan): List<Double> {
        require(plan.meses > 0) { "Meses debe ser > 0" }
        val base = kotlin.math.floor(plan.montoTotal * 100.0 / plan.meses) / 100.0
        val last = redondea2(plan.montoTotal - base * (plan.meses - 1))
        return List(plan.meses - 1) { base } + last
    }

    /** Mensualidad base (la última cuota puede variar por el ajuste). */
    fun mensualidad(plan: MsiPlan): Double = parciales(plan).first()

    data class MsiCuota(
        val numero: Int,
        val total: Int,
        /** Corte al que cae esta cuota. */
        val corteIso: LocalDate,
        val parcial: Double
    )

    /**
     * Cuotas del plan sobre los cortes de su tarjeta. Vacío si está
     * liquidado (liquidar cierra la proyección). Cuotas futuras = marcadores
     * de calendario (no crean movements).
     */
    fun cuotas(plan: MsiPlan, cutoffDay: Int): List<MsiCuota> {
        if (plan.liquidado) return emptyList()
        val primero = runCatching { LocalDate.parse(plan.primerCorteIso) }.getOrNull()
            ?: return emptyList()
        val montos = parciales(plan)
        // Alinea el primer corte al día de corte real de la tarjeta.
        var month = YearMonth.from(primero)
        // Si el primer corte dado es anterior al día de corte de ese mes, ese
        // mismo mes aplica; si no, se respeta tal cual el mes indicado.
        return montos.mapIndexed { index, parcial ->
            val corte = atDay(month.plusMonths(index.toLong()), cutoffDay)
            MsiCuota(index + 1, montos.size, corte, parcial)
        }
    }

    /**
     * Cuota cubierta si en su periodo [prev, corte) hay cargos tagueados
     * >= parcial − tolerancia. Si el cargo original pierde el tag, la cuota
     * queda pendiente (el plan no se borra).
     */
    fun cuotaCubierta(
        cuota: MsiCuota,
        cutoffDay: Int,
        cargosTagueados: List<Pair<LocalDate, Double>>
    ): Boolean {
        val prev = lastCutoff(cutoffDay, cuota.corteIso.minusDays(1))
        val suma = cargosTagueados
            .filter { (date, _) -> !date.isBefore(prev) && date.isBefore(cuota.corteIso) }
            .sumOf { (_, amount) -> amount }
        return suma >= cuota.parcial - TOLERANCIA
    }

    /** Liquidación anticipada: cierra la proyección. */
    fun liquidar(plan: MsiPlan): MsiPlan = plan.copy(liquidado = true)

    /**
     * Progreso n/N: cuotas con corte en o antes del corte abierto actual.
     * (ej. "4/12").
     */
    fun progreso(plan: MsiPlan, cutoffDay: Int, today: LocalDate): Pair<Int, Int> {
        val hechas = cuotas(plan, cutoffDay).count { !it.corteIso.isAfter(lastCutoff(cutoffDay, today)) }
        return hechas.coerceAtMost(plan.meses) to plan.meses
    }

    /**
     * Total MSI del corte dado (informativo "incluye $X de MSI"): suma los
     * parciales de planes activos cuya cuota cae en [corteIso].
     */
    fun msiEnCorte(planes: List<MsiPlan>, cutoffDay: Int, corteIso: LocalDate): Double =
        planes.filter { !it.liquidado }
            .sumOf { plan ->
                cuotas(plan, cutoffDay)
                    .firstOrNull { it.corteIso == corteIso }
                    ?.parcial ?: 0.0
            }

    /**
     * Saldo pendiente del plan: suma de parciales con corte en o después del
     * corte abierto actual (lo que falta por pagar). 0 si liquidado.
     */
    fun saldoPendiente(plan: MsiPlan, cutoffDay: Int, today: LocalDate): Double {
        if (plan.liquidado) return 0.0
        val abierto = lastCutoff(cutoffDay, today)
        return cuotas(plan, cutoffDay)
            .filter { !it.corteIso.isBefore(abierto) }
            .sumOf { it.parcial }
    }

    private fun redondea2(value: Double): Double =
        kotlin.math.round(value * 100.0) / 100.0
}
