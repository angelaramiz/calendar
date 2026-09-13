package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PatternValidatorTest {

    private val start = LocalDate.of(2026, 9, 1)

    @Test
    fun patron_valido_no_devuelve_error() {
        assertNull(
            PatternValidator.validate(
                name = "Sueldo",
                baseAmount = 10_000.0,
                frequency = "biweekly",
                startDate = start,
                endDate = null
            )
        )
    }

    @Test
    fun nombre_vacio_es_rechazado() {
        assertEquals(
            "Ponle un nombre al recurrente (ej. Sueldo, Renta).",
            PatternValidator.validate(
                name = "   ",
                baseAmount = 10_000.0,
                frequency = "monthly",
                startDate = start,
                endDate = null
            )
        )
    }

    @Test
    fun monto_cero_o_negativo_es_rechazado() {
        assertEquals(
            "El monto debe ser mayor que cero.",
            PatternValidator.validate(
                name = "Renta",
                baseAmount = 0.0,
                frequency = "monthly",
                startDate = start,
                endDate = null
            )
        )
    }

    @Test
    fun frecuencia_desconocida_es_rechazada() {
        assertEquals(
            "Frecuencia no válida.",
            PatternValidator.validate(
                name = "Renta",
                baseAmount = 5_000.0,
                frequency = "quincenal",
                startDate = start,
                endDate = null
            )
        )
    }

    @Test
    fun sin_fecha_de_inicio_es_rechazado() {
        assertEquals(
            "Elige la fecha del primer cobro o pago.",
            PatternValidator.validate(
                name = "Renta",
                baseAmount = 5_000.0,
                frequency = "monthly",
                startDate = null,
                endDate = null
            )
        )
    }

    @Test
    fun fin_anterior_al_inicio_es_rechazado() {
        assertEquals(
            "La fecha de fin no puede ser anterior a la de inicio.",
            PatternValidator.validate(
                name = "Préstamo",
                baseAmount = 2_000.0,
                frequency = "monthly",
                startDate = start,
                endDate = start.minusDays(1)
            )
        )
    }

    @Test
    fun fin_igual_al_inicio_es_valido() {
        assertNull(
            PatternValidator.validate(
                name = "Préstamo",
                baseAmount = 2_000.0,
                frequency = "monthly",
                startDate = start,
                endDate = start
            )
        )
    }
}
