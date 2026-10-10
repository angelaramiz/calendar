package com.fintrack.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El aviso que trae el concepto del recurrente se reconoce como ese evento. */
class PatternMatcherTest {

    private fun school(
        name: String = "Colegiatura Hybridge",
        description: String = "hybridge",
        active: Boolean = true,
        type: String = "EXPENSE",
        frequency: String = "monthly",
        startDate: LocalDate = LocalDate.of(2026, 1, 5)
    ) = Pattern(
        id = "pat-1",
        name = name,
        type = type,
        baseAmount = 3500.0,
        frequency = frequency,
        startDate = startDate,
        active = active
    ).copy(description = description)

    @Test
    fun aviso_con_concepto_reconoce_el_evento() {
        val today = LocalDate.of(2026, 10, 5)
        val hit = matchPatternByConcept(
            title = "Cargo a tu tarjeta",
            text = "Pagaste $3,500.00 en HYBRIDGE SCHOOL OFICIAL.",
            isIncome = false,
            patterns = listOf(school()),
            today = today
        )
        // Ocurrencia esperada del 5 de octubre.
        assertEquals(LocalDate.of(2026, 10, 5), hit?.date)
        assertEquals("pat-1", hit?.pattern?.id)
    }

    @Test
    fun lado_contrario_no_vincula() {
        val hit = matchPatternByConcept(
            title = "Abono",
            text = "Recibiste $3,500.00 de Hybridge.",
            isIncome = true,
            patterns = listOf(school()),
            today = LocalDate.of(2026, 10, 5)
        )
        assertNull(hit)
    }

    @Test
    fun fuera_de_ventana_no_vincula() {
        val hit = matchPatternByConcept(
            title = "Cargo a tu tarjeta",
            text = "Pagaste $3,500.00 en Hybridge.",
            isIncome = false,
            patterns = listOf(school()),
            today = LocalDate.of(2026, 10, 20)
        )
        assertNull(hit)
    }

    @Test
    fun recurrente_sin_concepto_no_vincula() {
        val hit = matchPatternByConcept(
            title = "Cargo a tu tarjeta",
            text = "Pagaste $3,500.00 en Hybridge.",
            isIncome = false,
            patterns = listOf(school(name = "", description = "")),
            today = LocalDate.of(2026, 10, 5)
        )
        assertNull(hit)
    }

    @Test
    fun recurrente_inactivo_no_vincula() {
        val hit = matchPatternByConcept(
            title = "Cargo a tu tarjeta",
            text = "Pagaste $3,500.00 en Hybridge.",
            isIncome = false,
            patterns = listOf(school(active = false)),
            today = LocalDate.of(2026, 10, 5)
        )
        assertNull(hit)
    }

    @Test
    fun acentos_y_mayusculas_no_importan() {
        val hit = matchPatternByConcept(
            title = "Compra aprobada",
            text = "Pagaste $850.00 en MÚSICA Y MÁS.",
            isIncome = false,
            patterns = listOf(school(name = "Clases de musica", description = "")),
            today = LocalDate.of(2026, 10, 5)
        )
        assertEquals(LocalDate.of(2026, 10, 5), hit?.date)
    }

    @Test
    fun tokens_cortos_no_disparan() {
        // "Luz" (<4 letras) no es concepto válido: no debe vincular.
        val hit = matchPatternByConcept(
            title = "Cargo a tu tarjeta",
            text = "Pagaste $800.00 de luz en CFE.",
            isIncome = false,
            patterns = listOf(school(name = "Luz", description = "")),
            today = LocalDate.of(2026, 10, 5)
        )
        assertNull(hit)
    }

    @Test
    fun elige_la_ocurrencia_mas_cercana() {
        val quincenal = school(frequency = "semimonthly")
        val hit = matchPatternByConcept(
            title = "Cargo a tu tarjeta",
            text = "Pagaste $3,500.00 en Hybridge.",
            isIncome = false,
            patterns = listOf(quincenal),
            // Quincenas de octubre 2026: 15 y 30 (jueves/viernes, sin corrimiento).
            today = LocalDate.of(2026, 10, 16)
        )
        assertEquals(LocalDate.of(2026, 10, 15), hit?.date)
    }
}
