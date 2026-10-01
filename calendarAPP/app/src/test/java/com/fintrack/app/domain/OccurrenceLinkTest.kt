package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OccurrenceLinkTest {

    private val zone = ZoneId.systemDefault()

    private fun payrollOccurrence(date: LocalDate = LocalDate.of(2026, 9, 15)) = Occurrence(
        date = date,
        pattern = Pattern(
            id = "p-nomina",
            name = "Nómina quincenal",
            type = "INCOME",
            baseAmount = 8000.0,
            frequency = "monthly",
            startDate = LocalDate.of(2026, 1, 15),
            category = "Sueldo",
            description = "Sueldo quincena"
        )
    )

    private fun mov(
        id: String,
        date: String,
        amount: Double,
        category: String = "Sueldo",
        title: String = "Nómina",
        type: String = "ingreso",
        fk: String? = null
    ) = MovementRow(
        id = id,
        type = type,
        title = title,
        category = category,
        date = date,
        confirmed_amount = amount,
        income_pattern_id = fk
    )

    private fun tx(
        id: String,
        date: LocalDate,
        amount: Double,
        category: String = "Sueldo",
        source: String = "AUTO",
        type: String = "INCOME",
        merchant: String? = null,
        description: String = "Nómina"
    ) = TransactionEntity(
        id = id,
        amount = amount,
        type = type,
        category = category,
        description = description,
        merchant = merchant,
        timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3600 * 1000,
        source = source
    )

    @Test
    fun movimiento_mismo_dia_misma_categoria_es_candidato() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            listOf(mov("m1", "2026-09-15", 7500.0)),
            emptyList()
        )
        assertEquals(1, found.size)
        assertEquals("mov:m1", found[0].key)
    }

    @Test
    fun movimiento_un_dia_despues_mismo_monto_es_candidato() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            listOf(mov("m1", "2026-09-16", 8000.0, category = "Otros")),
            emptyList()
        )
        assertEquals(1, found.size)
    }

    @Test
    fun movimiento_dos_dias_despues_se_excluye() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            listOf(mov("m1", "2026-09-17", 8000.0, category = "Sueldo")),
            emptyList()
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun movimiento_lado_contrario_se_excluye() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            listOf(mov("m1", "2026-09-15", 8000.0, category = "Sueldo", type = "gasto")),
            emptyList()
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun movimiento_ya_vinculado_se_excluye() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            listOf(mov("m1", "2026-09-15", 8000.0, fk = "p-otra")),
            emptyList()
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun tx_detectada_sueldo_es_candidata_con_origen() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            emptyList(),
            listOf(tx("t1", LocalDate.of(2026, 9, 15), 8000.0))
        )
        assertEquals(1, found.size)
        assertEquals("tx:t1", found[0].key)
        assertEquals("Detectado", found[0].origin)
    }

    @Test
    fun tx_ya_vinculada_se_excluye() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            emptyList(),
            listOf(tx("t1", LocalDate.of(2026, 9, 15), 8000.0)),
            linkedTxIds = setOf("t1")
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun concepto_solapa_aunque_categoria_y_monto_difieran() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            // Sin categoría ni monto iguales, pero el título trae "nómina".
            listOf(mov("m1", "2026-09-15", 100.0, category = "Otros", title = "Pago de nómina")),
            emptyList()
        )
        assertEquals(1, found.size)
        assertEquals(2, found[0].rank / 10)
    }

    @Test
    fun sin_senal_no_hay_candidato() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            listOf(mov("m1", "2026-09-15", 100.0, category = "Otros", title = "Cena")),
            listOf(tx("t1", LocalDate.of(2026, 9, 15), 50.0, category = "Otros", merchant = "Oxxo", description = "Compra", source = "MANUAL")),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun orden_categoria_monto_antes_que_solo_concepto() {
        val found = findLinkCandidates(
            payrollOccurrence(),
            listOf(
                mov("m1", "2026-09-15", 100.0, category = "Otros", title = "Pago de nómina"),
                mov("m2", "2026-09-15", 8000.0, category = "Sueldo", title = "X")
            ),
            emptyList()
        )
        assertEquals(listOf("mov:m2", "mov:m1"), found.map { it.key })
    }

    @Test
    fun patron_sin_tipo_no_propone_nada() {
        val occ = payrollOccurrence().copy(pattern = payrollOccurrence().pattern.copy(type = "???"))
        val found = findLinkCandidates(
            occ,
            listOf(mov("m1", "2026-09-15", 8000.0)),
            emptyList()
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun confirmada_exacta_fk_y_fecha() {
        val occ = payrollOccurrence()
        assertTrue(
            isOccurrenceConfirmed(
                occ,
                listOf(mov("m1", "2026-09-15", 8000.0, fk = "p-nomina")),
                emptySet()
            )
        )
    }

    @Test
    fun confirmada_fk_un_dia_antes() {
        val occ = payrollOccurrence()
        assertTrue(
            isOccurrenceConfirmed(
                occ,
                listOf(mov("m1", "2026-09-14", 8000.0, fk = "p-nomina")),
                emptySet()
            )
        )
    }

    @Test
    fun no_confirmada_fk_lejana() {
        val occ = payrollOccurrence()
        assertTrue(
            !isOccurrenceConfirmed(
                occ,
                listOf(mov("m1", "2026-09-10", 8000.0, fk = "p-nomina")),
                emptySet()
            )
        )
    }

    @Test
    fun confirmada_por_tx_vinculado() {
        val occ = payrollOccurrence()
        assertTrue(
            isOccurrenceConfirmed(occ, emptyList(), setOf("p-nomina_2026-09-15"))
        )
    }

    @Test
    fun no_confirmada_sin_nada() {
        val occ = payrollOccurrence()
        assertTrue(!isOccurrenceConfirmed(occ, emptyList(), emptySet()))
    }
}
