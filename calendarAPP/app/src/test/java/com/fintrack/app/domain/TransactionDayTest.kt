package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TransactionDayTest {

    private fun tx(day: LocalDate, hour: Int = 12, amount: Double = 100.0): TransactionEntity =
        TransactionEntity(
            amount = amount,
            timestamp = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        )

    @Test
    fun filtra_solo_el_dia_pedido() {
        val day = LocalDate.of(2026, 9, 15)
        val all = listOf(
            tx(day, amount = 10.0),
            tx(day.minusDays(1), amount = 20.0),
            tx(day.plusDays(1), amount = 30.0)
        )

        val result = all.onDayUtc(day)

        assertEquals(1, result.size)
        assertEquals(10.0, result[0].amount, 0.001)
    }

    @Test
    fun borde_de_dia_utc_no_se_mueve() {
        val day = LocalDate.of(2026, 9, 15)
        val ultimoMs = day.plusDays(1).atStartOfDay()
            .toInstant(ZoneOffset.UTC).toEpochMilli() - 1
        val primerMs = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        assertEquals(day, ultimoMs.toLocalDateUtc())
        assertEquals(day, primerMs.toLocalDateUtc())
        assertEquals(day.plusDays(1), (ultimoMs + 1).toLocalDateUtc())
    }

    @Test
    fun dia_sin_movimientos_devuelve_vacio() {
        assertTrue(emptyList<TransactionEntity>().onDayUtc(LocalDate.of(2026, 9, 15)).isEmpty())
    }

    @Test
    fun noche_local_cae_en_hoy_local_aunque_utc_sea_manana() {
        // 19 sep 18:30 en México = 20 sep 00:30 UTC: antes Inicio se vaciaba.
        val mexico = java.time.ZoneId.of("America/Mexico_City")
        val ts = java.time.LocalDateTime.of(2026, 9, 20, 0, 30)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val all = listOf(
            TransactionEntity(amount = 130.0, timestamp = ts)
        )
        assertEquals(1, all.onDay(LocalDate.of(2026, 9, 19), mexico).size)
        assertTrue(all.onDayUtc(LocalDate.of(2026, 9, 19)).isEmpty())
    }
}
