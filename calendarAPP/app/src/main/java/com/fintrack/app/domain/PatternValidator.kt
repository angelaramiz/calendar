package com.fintrack.app.domain

import java.time.LocalDate

/**
 * Validación pura del formulario "Nuevo recurrente" del calendario.
 * Devuelve el mensaje de error en español, o null si el patrón es válido.
 * Las frecuencias aceptadas son las que entiende [PatternExpander].
 */
object PatternValidator {

    val FREQUENCIES = listOf("weekly", "biweekly", "monthly", "bimonthly", "yearly")

    fun validate(
        name: String,
        baseAmount: Double,
        frequency: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): String? {
        if (name.isBlank()) return "Ponle un nombre al recurrente (ej. Sueldo, Renta)."
        if (baseAmount <= 0.0) return "El monto debe ser mayor que cero."
        if (frequency !in FREQUENCIES) return "Frecuencia no válida."
        if (startDate == null) return "Elige la fecha del primer cobro o pago."
        if (endDate != null && endDate.isBefore(startDate)) {
            return "La fecha de fin no puede ser anterior a la de inicio."
        }
        return null
    }
}
