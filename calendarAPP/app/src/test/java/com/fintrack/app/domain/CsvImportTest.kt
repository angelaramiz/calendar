package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CsvImportTest {

    private val zone = ZoneId.systemDefault()

    @Test
    fun deteccion_bbva_por_encabezado_con_saldo() {
        val headers = listOf("Fecha", "Descripción", "Cargo", "Abono", "Saldo")
        assertEquals(BankFormat.BBVA, CsvImport.detectFormat(headers))
    }

    @Test
    fun deteccion_santander_por_folio() {
        val headers = listOf("Fecha", "Folio", "Descripción", "Cargos", "Abonos")
        assertEquals(BankFormat.SANTANDER, CsvImport.detectFormat(headers))
    }

    @Test
    fun deteccion_banamex_por_referencia() {
        val headers = listOf("Fecha", "Descripción", "Referencia", "Cargos", "Abonos")
        assertEquals(BankFormat.BANAMEX, CsvImport.detectFormat(headers))
    }

    @Test
    fun deteccion_generica_y_desconocida() {
        assertEquals(
            BankFormat.GENERICO,
            CsvImport.detectFormat(listOf("Fecha", "Concepto", "Monto"))
        )
        assertEquals(
            BankFormat.DESCONOCIDO,
            CsvImport.detectFormat(listOf("Nombre", "Edad", "Ciudad"))
        )
    }

    @Test
    fun parseo_bbva_cargo_y_abono_con_comas() {
        // Los bancos entrecomillan los miles con coma ("1,500.00").
        val content = "Fecha,Descripción,Cargo,Abono,Saldo\n" +
            "15/09/2026,\"OXXO TACOS\",\"1,500.00\",,\"10,000.00\"\n" +
            "16/09/2026,NOMINA QUINCENA,,8000.00,18000.00"
        val rows = CsvImport.parse(content)
        assertEquals(2, rows.size)
        assertEquals("2026-09-15", rows[0].dateIso)
        assertEquals(1500.0, rows[0].amount, 0.001)
        assertFalse(rows[0].isIncome)
        assertTrue(rows[1].isIncome)
        assertEquals(8000.0, rows[1].amount, 0.001)
    }

    @Test
    fun parseo_montos_con_signo_y_parentesis() {
        assertEquals(-450.0, CsvImport.parseAmount("(450.00)")!!, 0.001)
        assertEquals(-120.0, CsvImport.parseAmount("-\$120")!!, 0.001)
        assertEquals(2300.50, CsvImport.parseAmount("\$ 2,300.50")!!, 0.001)
        assertEquals(1500.0, CsvImport.parseAmount("1.500,00")!!, 0.001)
    }

    @Test
    fun filas_malas_se_ignoran() {
        val content = "Fecha,Concepto,Monto\n" +
            "15/09/2026,Tacos,200\n" +
            "sin-fecha,Tacos,200\n" +
            "16/09/2026,,300\n" +
            "17/09/2026,Cine,no-numérico"
        val rows = CsvImport.parse(content)
        assertEquals(1, rows.size)
        assertEquals("2026-09-15", rows[0].dateIso)
    }

    private fun tx(
        day: LocalDate,
        amount: Double,
        type: String = "EXPENSE",
        id: String = "tx-1"
    ) = TransactionEntity(
        id = id,
        amount = amount,
        type = type,
        category = "Comida",
        description = "OXXO TACOS",
        timestamp = day.atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3600 * 1000
    )

    @Test
    fun vinculo_exacto_mismo_dia_monto_y_lado() {
        val candidate = CsvCandidate("2026-09-15", 1500.0, false, "OXXO TACOS", 0)
        val saved = tx(LocalDate.of(2026, 9, 15), 1500.0)
        assertNotNull(CsvImport.exactMatch(candidate, listOf(saved), zone))
        // La ocurrencia sintética también lo propone para vincular.
        val links = findLinkCandidates(
            CsvImport.toOccurrence(candidate), emptyList(), listOf(saved)
        )
        assertTrue(links.any { it.key == "tx:${saved.id}" && it.rank < 20 })
    }

    @Test
    fun sin_vinculo_si_cambia_monto_o_lado() {
        val candidate = CsvCandidate("2026-09-15", 1500.0, false, "OXXO TACOS", 0)
        val otroMonto = tx(LocalDate.of(2026, 9, 15), 1600.0)
        val otroLado = tx(LocalDate.of(2026, 9, 15), 1500.0, type = "INCOME")
        val otroDia = tx(LocalDate.of(2026, 9, 16), 1500.0)
        assertNull(CsvImport.exactMatch(candidate, listOf(otroMonto), zone))
        assertNull(CsvImport.exactMatch(candidate, listOf(otroLado), zone))
        assertNull(CsvImport.exactMatch(candidate, listOf(otroDia), zone))
    }
}
