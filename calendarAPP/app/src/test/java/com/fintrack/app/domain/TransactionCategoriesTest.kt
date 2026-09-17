package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCategoriesTest {

    @Test
    fun gastos_traen_las_12_del_detector() {
        val expense = TransactionCategories.forType(false)
        assertEquals(12, expense.size)
        assertTrue(expense.containsAll(listOf("Comida", "Vivienda", "Finanzas", "Otros")))
        assertTrue("Sueldo" !in expense)
    }

    @Test
    fun ingresos_son_solo_sueldo_reembolso_otros() {
        assertEquals(
            listOf("Sueldo", "Reembolso", "Otros"),
            TransactionCategories.forType(true)
        )
    }

    @Test
    fun defaults_son_sensatos() {
        assertEquals("Comida", TransactionCategories.defaultFor(false))
        assertEquals("Sueldo", TransactionCategories.defaultFor(true))
    }
}
