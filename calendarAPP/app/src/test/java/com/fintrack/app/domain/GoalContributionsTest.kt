package com.fintrack.app.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalContributionsTest {

    private fun goal(price: Double = 20_000.0) = SavingsGoal(
        id = "g1", name = "Carro", price = price
    )

    @Test
    fun aportar_suma_y_registra_historial() {
        val out = GoalContributions.aportar(goal(), 5_000.0, "2026-10-03")

        assertEquals(5_000.0, out.aportado, 0.001)
        assertEquals(1, out.aportaciones.size)
        assertEquals("2026-10-03", out.aportaciones[0].fechaIso)
        assertEquals(5_000.0, out.aportaciones[0].monto, 0.001)
    }

    @Test
    fun aportar_acumula_varias_veces() {
        var g = goal()
        g = GoalContributions.aportar(g, 5_000.0, "2026-10-03")
        g = GoalContributions.aportar(g, 3_000.0, "2026-10-18")

        assertEquals(8_000.0, g.aportado, 0.001)
        assertEquals(2, g.aportaciones.size)
    }

    @Test
    fun aportar_ignora_monto_no_positivo() {
        val g = goal()
        assertEquals(g, GoalContributions.aportar(g, 0.0, "2026-10-03"))
        assertEquals(g, GoalContributions.aportar(g, -100.0, "2026-10-03"))
    }

    @Test
    fun retirar_resta_y_deja_historial_negativo() {
        var g = GoalContributions.aportar(goal(), 5_000.0, "2026-10-03")
        g = GoalContributions.retirar(g, 2_000.0, "2026-10-04")

        assertEquals(3_000.0, g.aportado, 0.001)
        assertEquals(2, g.aportaciones.size)
        assertEquals(-2_000.0, g.aportaciones[1].monto, 0.001)
    }

    @Test
    fun retirar_no_deja_juntado_negativo() {
        var g = GoalContributions.aportar(goal(), 1_000.0, "2026-10-03")
        g = GoalContributions.retirar(g, 5_000.0, "2026-10-04")

        assertEquals(0.0, g.aportado, 0.001)
        assertEquals(-1_000.0, g.aportaciones[1].monto, 0.001)
    }

    @Test
    fun retirar_sin_fondo_no_cambia_nada() {
        val g = goal()
        assertEquals(g, GoalContributions.retirar(g, 500.0, "2026-10-04"))
        assertEquals(g, GoalContributions.retirar(g, 0.0, "2026-10-04"))
    }

    @Test
    fun restante_y_quincenas() {
        var g = goal(price = 20_000.0)
        g = GoalContributions.aportar(g, 5_000.0, "2026-10-03")

        assertEquals(15_000.0, GoalContributions.restante(g), 0.001)
        assertEquals(3, GoalContributions.quincenasRestantes(g, 5_000.0))
        assertNull(GoalContributions.quincenasRestantes(g, 0.0))
    }

    @Test
    fun quincenas_cero_si_meta_juntada() {
        var g = goal(price = 5_000.0)
        g = GoalContributions.aportar(g, 5_000.0, "2026-10-03")

        assertEquals(0.0, GoalContributions.restante(g), 0.001)
        assertEquals(0, GoalContributions.quincenasRestantes(g, 1_000.0))
        assertEquals(1f, GoalContributions.progreso(g), 0.001f)
    }

    @Test
    fun migracion_json_viejo_sin_aportado_no_rompe() {
        val viejo = """[{"id":"g1","name":"Carro","price":20000.0}]"""
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val metas = json.decodeFromString(ListSerializer(SavingsGoal.serializer()), viejo)

        assertEquals(1, metas.size)
        assertEquals(0.0, metas[0].aportado, 0.001)
        assertTrue(metas[0].aportaciones.isEmpty())
    }

    @Test
    fun roundtrip_json_con_historial() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        var g = goal()
        g = GoalContributions.aportar(g, 2_000.0, "2026-10-03")
        val raw = json.encodeToString(ListSerializer(SavingsGoal.serializer()), listOf(g))
        val back = json.decodeFromString(ListSerializer(SavingsGoal.serializer()), raw)

        assertEquals(2_000.0, back[0].aportado, 0.001)
        assertEquals(1, back[0].aportaciones.size)
    }
}
