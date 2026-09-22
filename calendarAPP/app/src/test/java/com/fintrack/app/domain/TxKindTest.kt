package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxKindTest {

    @Test
    fun of_mapea_los_dos_alfabetos() {
        assertEquals(TxKind.INCOME, TxKind.of("INCOME"))
        assertEquals(TxKind.INCOME, TxKind.of("ingreso"))
        assertEquals(TxKind.INCOME, TxKind.of("Income"))
        assertEquals(TxKind.EXPENSE, TxKind.of("EXPENSE"))
        assertEquals(TxKind.EXPENSE, TxKind.of("gasto"))
    }

    @Test
    fun desconocido_o_nulo_es_null() {
        assertEquals(null, TxKind.of("otro"))
        assertEquals(null, TxKind.of(null))
    }

    @Test
    fun isIncome_coherente() {
        assertTrue(TxKind.INCOME.isIncome)
        assertFalse(TxKind.EXPENSE.isIncome)
    }
}
