package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PatternExpanderTest {

    private fun pattern(
        frequency: String,
        interval: Int = 1,
        start: LocalDate = LocalDate.of(2026, 1, 5),
        end: LocalDate? = null
    ) = Pattern(
        id = "p1",
        name = "Test",
        type = "EXPENSE",
        baseAmount = 100.0,
        frequency = frequency,
        interval = interval,
        startDate = start,
        endDate = end,
        active = true
    )

    @Test
    fun weekly_generatesEvery7Days() {
        val occ = PatternExpander.expand(
            pattern("weekly"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31)
        ).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 19),
                LocalDate.of(2026, 1, 26)
            ),
            occ
        )
    }

    @Test
    fun biweekly_generatesEvery14Days() {
        val occ = PatternExpander.expand(
            pattern("biweekly"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 28)
        ).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 19),
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 16)
            ),
            occ
        )
    }

    @Test
    fun weekly_withInterval2_skipsWeeks() {
        val occ = PatternExpander.expand(
            pattern("weekly", interval = 2),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31)
        ).map { it.date }

        assertEquals(
            listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19)),
            occ
        )
    }

    @Test
    fun monthly_keepsDayOfMonth() {
        val occ = PatternExpander.expand(
            pattern("monthly", start = LocalDate.of(2026, 1, 15)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 30)
        ).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 15)
            ),
            occ
        )
    }

    @Test
    fun monthly_day31_clampsToLastDayOfFeb() {
        val occ = PatternExpander.expand(
            pattern("monthly", start = LocalDate.of(2026, 1, 31)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31)
        ).map { it.date }

        // Paridad web: deriva al último día (31 ene -> 28 feb -> 28 mar)
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 28)
            ),
            occ
        )
    }

    @Test
    fun yearly_generatesSameDateEachYear() {
        val occ = PatternExpander.expand(
            pattern("yearly", start = LocalDate.of(2024, 5, 10)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2028, 12, 31)
        ).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2027, 5, 10),
                LocalDate.of(2028, 5, 10)
            ),
            occ
        )
    }

    @Test
    fun endDate_limitsOccurrences() {
        val occ = PatternExpander.expand(
            pattern("weekly", end = LocalDate.of(2026, 1, 12)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31)
        ).map { it.date }

        assertEquals(
            listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12)),
            occ
        )
    }

    @Test
    fun occurrencesBeforeRangeStart_areExcluded() {
        val occ = PatternExpander.expand(
            pattern("weekly"),
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 28)
        ).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 23)
            ),
            occ
        )
    }

    @Test
    fun bimonthly_generatesEvery2Months() {
        val occ = PatternExpander.expand(
            pattern("bimonthly", start = LocalDate.of(2026, 1, 15)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 7, 31)
        ).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 7, 15)
            ),
            occ
        )
    }

    @Test
    fun occurrence_carriesPatternReference() {
        val p = pattern("weekly")
        val occ = PatternExpander.expand(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))

        assertTrue(occ.isNotEmpty())
        assertTrue(occ.all { it.pattern.id == "p1" && it.amount == 100.0 })
    }

    @Test
    fun semimonthly_caso_usuario_15_y_fin_de_mes() {
        // Quincena desde el 15 oct 2026: 15 y 30 (el 31 cae sábado -> viernes).
        val occ = PatternExpander.expand(
            pattern("semimonthly", start = LocalDate.of(2026, 10, 15)),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31)
        ).map { it.date }

        assertEquals(
            listOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 30)),
            occ
        )
    }

    @Test
    fun semimonthly_fin_de_semana_se_recorre_al_viernes() {
        // Nov 2026: el 15 es domingo -> viernes 13; el 30 es lunes -> 30.
        val nov = PatternExpander.expand(
            pattern("semimonthly", start = LocalDate.of(2026, 10, 15)),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 30)
        ).map { it.date }
        assertEquals(
            listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30)),
            nov
        )
        // Feb 2027: el 15 es lunes -> 15; el 28 es domingo -> viernes 26.
        val feb = PatternExpander.expand(
            pattern("semimonthly", start = LocalDate.of(2026, 10, 15)),
            LocalDate.of(2027, 2, 1),
            LocalDate.of(2027, 2, 28)
        ).map { it.date }
        assertEquals(
            listOf(LocalDate.of(2027, 2, 15), LocalDate.of(2027, 2, 26)),
            feb
        )
    }

    @Test
    fun semimonthly_respeta_inicio_y_fin() {
        // Inicio el 16: el 15 de ese mes ya pasó.
        val occ = PatternExpander.expand(
            pattern("semimonthly", start = LocalDate.of(2026, 10, 16)),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31)
        ).map { it.date }
        assertEquals(listOf(LocalDate.of(2026, 10, 30)), occ)
        // Fin el 20: solo alcanza el 15.
        val occ2 = PatternExpander.expand(
            pattern(
                "semimonthly",
                start = LocalDate.of(2026, 10, 15),
                end = LocalDate.of(2026, 10, 20)
            ),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31)
        ).map { it.date }
        assertEquals(listOf(LocalDate.of(2026, 10, 15)), occ2)
    }
}
