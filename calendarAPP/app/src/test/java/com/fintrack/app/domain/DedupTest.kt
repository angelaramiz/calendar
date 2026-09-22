package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.data.repository.PatternRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DedupTest {

    private fun tx(
        id: String = "",
        amount: Double = 130.0,
        type: String = "EXPENSE",
        timestamp: Long = 1_700_000_000_000L,
        category: String = "Comida",
        description: String = "Prueba"
    ) = TransactionEntity(
        id = id, amount = amount, type = type, timestamp = timestamp,
        category = category, description = description
    )

    @Test
    fun tx_reintento_mismo_ms_lo_encuentra() {
        val saved = tx(id = "srv-1")
        assertEquals(saved, findDuplicateTx(tx(), listOf(saved)))
    }

    @Test
    fun tx_distinto_ms_no_duplica() {
        val saved = tx(id = "srv-1", timestamp = 1_700_000_000_000L)
        assertNull(findDuplicateTx(tx(timestamp = 1_700_000_000_001L), listOf(saved)))
    }

    @Test
    fun tx_distinto_monto_o_tipo_no_duplica() {
        val saved = tx(id = "srv-1")
        assertNull(findDuplicateTx(tx(amount = 131.0), listOf(saved)))
        assertNull(findDuplicateTx(tx(type = "INCOME"), listOf(saved)))
    }

    private fun mov(
        id: String = "m1",
        date: String = "2026-09-20",
        type: String = "gasto",
        title: String = "Renta",
        amount: Double = 7500.0,
        patternId: String? = null
    ) = MovementRow(
        id = id, date = date, type = type, title = title,
        description = "", category = "Casa", confirmed_amount = amount,
        expense_pattern_id = patternId
    )

    @Test
    fun mov_manual_duplicado_lo_encuentra() {
        val saved = mov()
        val found = findDuplicateMovement(
            "2026-09-20", "gasto", "Renta", "", "Casa", 7500.0, null, listOf(saved)
        )
        assertEquals(saved, found)
    }

    @Test
    fun mov_distinto_titulo_o_monto_no_duplica() {
        val saved = mov()
        assertNull(
            findDuplicateMovement("2026-09-20", "gasto", "Luz", "", "Casa", 7500.0, null, listOf(saved))
        )
        assertNull(
            findDuplicateMovement("2026-09-20", "gasto", "Renta", "", "Casa", 650.0, null, listOf(saved))
        )
    }

    @Test
    fun confirm_mismo_patron_y_fecha_lo_encuentra() {
        val saved = mov(patternId = "pat-1")
        val found = findDuplicateMovement(
            "2026-09-20", "gasto", "Renta", "", "Casa", 7500.0, "pat-1", listOf(saved)
        )
        assertEquals(saved, found)
        assertNull(
            findDuplicateMovement(
                "2026-09-21", "gasto", "Renta", "", "Casa", 7500.0, "pat-1", listOf(saved)
            )
        )
    }

    private fun pat(
        id: String = "p1",
        name: String = "Renta",
        start: String = "2026-09-01"
    ) = PatternRow(
        id = id, name = name, description = "", category = "Casa",
        base_amount = 7500.0, frequency = "monthly", start_date = start
    )

    @Test
    fun pattern_duplicado_lo_encuentra() {
        val saved = pat()
        val found = findDuplicatePattern(
            "Renta", "", "Casa", 7500.0, "monthly", "2026-09-01", listOf(saved)
        )
        assertEquals(saved, found)
        assertNull(
            findDuplicatePattern(
                "Luz", "", "Casa", 7500.0, "monthly", "2026-09-01", listOf(saved)
            )
        )
    }
}
