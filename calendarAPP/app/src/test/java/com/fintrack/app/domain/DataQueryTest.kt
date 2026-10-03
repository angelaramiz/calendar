package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DataQueryTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 15)

    private fun tx(
        day: String,
        amount: Double,
        type: String = "EXPENSE",
        category: String = "Comida",
        merchant: String? = null,
        description: String = "Compra"
    ): TransactionEntity {
        val ts = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
        return TransactionEntity(
            amount = amount,
            type = type,
            category = category,
            description = description,
            merchant = merchant,
            timestamp = ts,
            source = "MANUAL"
        )
    }

    private fun sample() = listOf(
        tx("2026-03-05", 120.0, merchant = "Oxxo Centro"),
        tx("2026-03-10", 80.0, merchant = "OXXO norte"),
        tx("2026-03-12", 500.0, category = "Compras", merchant = "Walmart"),
        tx("2026-03-15", 15000.0, type = "INCOME", category = "Sueldo", description = "Nómina"),
        tx("2026-04-02", 200.0, merchant = "Oxxo sur"),
        tx("2026-04-20", 3000.0, type = "INCOME", category = "Sueldo", description = "Nómina"),
        tx("2026-08-05", 9000.0, type = "INCOME", category = "Sueldo", description = "Nómina")
    )

    private fun entendido(result: QueryResult): QueryAnswer {
        assertTrue("esperaba Entendido para la pregunta", result is QueryResult.Entendido)
        return (result as QueryResult.Entendido).answer
    }

    @Test
    fun gasto_en_categoria_en_mes() {
        val a = entendido(DataQuery.answer("¿Cuánto gasté en comida en marzo?", sample(), today = today, zone = zone))
        assertEquals(200.0, a.cifra, 0.0)
        assertEquals(2, a.ejemplos.size)
    }

    @Test
    fun gasto_en_comercio_en_mes() {
        val a = entendido(DataQuery.answer("¿Cuánto gasté en oxxo en marzo?", sample(), today = today, zone = zone))
        assertEquals(200.0, a.cifra, 0.0)
    }

    @Test
    fun gasto_total_en_mes() {
        val a = entendido(DataQuery.answer("¿Cuánto gasté en marzo?", sample(), today = today, zone = zone))
        assertEquals(700.0, a.cifra, 0.0)
        assertTrue(a.ejemplos.size <= 3)
    }

    @Test
    fun ingreso_en_mes() {
        val a = entendido(DataQuery.answer("¿Cuánto me entró en agosto?", sample(), today = today, zone = zone))
        assertEquals(9000.0, a.cifra, 0.0)
    }

    @Test
    fun top_comercios_del_mes() {
        val a = entendido(DataQuery.answer("Top comercios en marzo", sample(), today = today, zone = zone))
        assertEquals(500.0, a.cifra, 0.0)
        assertTrue(a.detalle.contains("Walmart"))
    }

    @Test
    fun comparativa_de_meses() {
        val a = entendido(DataQuery.answer("¿Gasté más en marzo o en abril?", sample(), today = today, zone = zone))
        assertEquals(700.0, a.cifra, 0.0)
        assertTrue(a.titulo.contains("Marzo") && a.titulo.contains("Abril"))
        assertTrue(a.detalle.contains("diferencia"))
    }

    @Test
    fun promedio_de_gasto_en_mes() {
        val a = entendido(DataQuery.answer("Promedio de gasto en comida en marzo", sample(), today = today, zone = zone))
        assertEquals(100.0, a.cifra, 0.0)
    }

    @Test
    fun conteo_de_compras_en_comercio() {
        val a = entendido(DataQuery.answer("¿Cuántas veces compré en oxxo en marzo?", sample(), today = today, zone = zone))
        assertEquals(2.0, a.cifra, 0.0)
    }

    @Test
    fun balance_del_mes() {
        val a = entendido(DataQuery.answer("¿Cómo me fue en marzo?", sample(), today = today, zone = zone))
        assertEquals(14300.0, a.cifra, 0.0)
    }

    @Test
    fun meta_cuanto_falta() {
        val goals = listOf(SavingsGoal(id = "g1", name = "Carro", price = 200000.0, aportado = 50000.0))
        val a = entendido(
            DataQuery.answer("¿Cuánto me falta para mi meta carro?", sample(), goals, today, zone)
        )
        assertEquals(150000.0, a.cifra, 0.0)
    }

    @Test
    fun mes_sin_datos_devuelve_cero_explicito() {
        val a = entendido(DataQuery.answer("¿Cuánto gasté en enero?", sample(), today = today, zone = zone))
        assertEquals(0.0, a.cifra, 0.0)
        assertTrue(a.ejemplos.isEmpty())
        assertTrue(a.detalle.contains("Sin gastos"))
    }

    @Test
    fun mes_futuro_apunta_al_anio_pasado() {
        // Hoy es septiembre 2026: "marzo" = marzo 2026 (pasado, mismo año).
        val a = entendido(DataQuery.answer("¿Cuánto gasté en marzo?", sample(), today = today, zone = zone))
        assertEquals(700.0, a.cifra, 0.0)
        // "diciembre" aún no llega en 2026 → diciembre 2025 → sin datos.
        val b = entendido(DataQuery.answer("¿Cuánto gasté en diciembre?", sample(), today = today, zone = zone))
        assertEquals(0.0, b.cifra, 0.0)
    }

    @Test
    fun pregunta_fuera_de_plantilla_devuelve_no_entendi() {
        val r = DataQuery.answer("¿Quién ganó el partido de ayer?", sample(), today = today, zone = zone)
        assertTrue(r is QueryResult.NoEntendido)
    }

    @Test
    fun pregunta_vacia_devuelve_no_entendi() {
        val r = DataQuery.answer("   ", sample(), today = today, zone = zone)
        assertTrue(r is QueryResult.NoEntendido)
    }
}
