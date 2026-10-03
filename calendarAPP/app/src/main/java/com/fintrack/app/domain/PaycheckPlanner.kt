package com.fintrack.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * D3 Reparto de quincena: ritual de un toque al llegar la nómina.
 * Orden: fijos (servicios del periodo) → deuda (mínimos + excedente) →
 * ahorro a meta (D4) → libre. La regla del excedente es PROPIA y
 * autocontenida: un % del resto tras fijos/mínimos/ahorro, repartido
 * entre tarjetas en proporción a sus mínimos (a más deuda, más extra).
 */
object PaycheckPlanner {

    data class PaycheckInput(
        val ingreso: Double,
        val serviciosTotal: Double,
        /** cardId → mínimo a pagar (restante del estado abierto). */
        val minimosTarjetas: Map<String, Double> = emptyMap(),
        /** Apartado de ahorro de la quincena (va a la meta D4). */
        val ahorroMeta: Double = 0.0,
        /** % del resto que se adelanta a deuda (0..1). */
        val extraRate: Double = 0.20,
        val periodStart: LocalDate,
        val periodEnd: LocalDate
    )

    data class PaycheckPlan(
        val fijos: Double,
        val minimosTotal: Double,
        /** cardId → extra asignado además del mínimo. */
        val extraDeuda: Map<String, Double>,
        val ahorro: Double,
        val libre: Double,
        /** Marcadores del periodo (qué se apartó y cuándo). */
        val markers: List<String>
    ) {
        val extraTotal: Double get() = extraDeuda.values.sum()
    }

    /** Quincena en curso: días 1..15 o 16..fin (igual que el límite diario). */
    fun periodFor(today: LocalDate): Pair<LocalDate, LocalDate> {
        val month = YearMonth.from(today)
        return if (today.dayOfMonth <= 15) {
            month.atDay(1) to month.atDay(15)
        } else {
            month.atDay(16) to month.atEndOfMonth()
        }
    }

    fun plan(input: PaycheckInput): PaycheckPlan {
        val minimos = input.minimosTarjetas.values.sumOf { it.coerceAtLeast(0.0) }
        val fijos = input.serviciosTotal.coerceAtLeast(0.0) + minimos
        val ahorro = input.ahorroMeta.coerceAtLeast(0.0)
        val resto = input.ingreso - fijos - ahorro
        if (resto <= 0.0) {
            return PaycheckPlan(
                fijos = fijos,
                minimosTotal = minimos,
                extraDeuda = emptyMap(),
                ahorro = ahorro,
                libre = 0.0,
                markers = markersFor(input, fijos, minimos, emptyMap(), ahorro, 0.0)
            )
        }
        val rate = input.extraRate.coerceIn(0.0, 1.0)
        val extraTotal = if (minimos > 0.0) resto * rate else 0.0
        val extra = if (extraTotal > 0.0) {
            input.minimosTarjetas.mapValues { (_, minimo) ->
                extraTotal * (minimo.coerceAtLeast(0.0) / minimos)
            }
        } else {
            emptyMap()
        }
        val libre = (resto - extraTotal).coerceAtLeast(0.0)
        return PaycheckPlan(
            fijos = fijos,
            minimosTotal = minimos,
            extraDeuda = extra,
            ahorro = ahorro,
            libre = libre,
            markers = markersFor(input, fijos, minimos, extra, ahorro, libre)
        )
    }

    private fun markersFor(
        input: PaycheckInput,
        fijos: Double,
        minimos: Double,
        extra: Map<String, Double>,
        ahorro: Double,
        libre: Double
    ): List<String> {
        val rango = "${input.periodStart.dayOfMonth}–${input.periodEnd.dayOfMonth}/${input.periodEnd.monthValue}"
        val out = mutableListOf(
            "Quincena $rango: aparta fijos ${money(fijos)}",
            "Quincena $rango: mínimos de tarjetas ${money(minimos)}"
        )
        if (extra.values.sum() > 0.0) {
            out.add("Quincena $rango: extra a deuda ${money(extra.values.sum())}")
        }
        if (ahorro > 0.0) out.add("Quincena $rango: ahorro a meta ${money(ahorro)}")
        out.add("Quincena $rango: libre ${money(libre)}")
        return out
    }

    private fun money(value: Double): String =
        "$" + "%,.0f".format(value).replace(',', '.')
}
