package com.fintrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBackupTest {

    private fun full() = LocalBackup(
        wallets = listOf(WalletRow("efectivo", "Efectivo"), WalletRow("w1", "Mercado Pago", custom = true, last4 = "1234")),
        hiddenWallets = listOf("azteca"),
        walletOverrides = mapOf("tx:abc" to "w1"),
        cards = listOf(CreditCardRow("c1", "Plata", 15, 0, "4567", graceDays = 30)),
        charges = mapOf("tx:abc" to "c1"),
        payments = listOf(CardPayment("pay-1", "c1", 500.0, "2026-09-20", "2026-09-15")),
        bills = listOf(ServiceBillRow("b1", "Luz", 650.0, 10, 1, "bimonthly")),
        caps = mapOf("Comida" to 3000.0),
        links = mapOf("pat-1" to PatternLink("credit", "c1")),
        flowJson = """{"name":"Sueldo"}""",
        allowedPackages = listOf("com.nu.production"),
        msiPlans = listOf(MsiPlan("m1", "c1", "Celular", 12000.0, 12, "2026-09-03")),
        personDebts = listOf(
            PersonDebt("p1", "Juan", 500.0, false, "2026-09-01", listOf(PersonDebtAbono("a1", 200.0, "2026-09-02")))
        ),
        rules = mapOf("oxxo" to "Compras"),
        discreto = true,
        allowanceIncome = 12000.0,
        allowanceSavings = 1000.0,
        paycheck = PaycheckSnapshot("2026-10-01", 12000.0, 5000.0, 2000.0, 1000.0, 1000.0, 3000.0),
        dismissedAnomalies = setOf("dup_1"),
        streaks = mapOf("Comida" to 3),
        streaksLastPeriod = "2026-Q4",
        hormigaFreq = "semanal",
        hormigaLastSent = "2026-W40",
        draft = EntryDraft("EXPENSE", "77", "Comida", "", null, null, 2)
    )

    @Test
    fun roundtrip_conserva_todas_las_secciones() {
        val original = full()
        val decoded = decodeBackup(encodeBackup(original))
        assertEquals(original, decoded)
    }

    @Test
    fun texto_corrupto_o_vacio_devuelve_null() {
        assertNull(decodeBackup(null))
        assertNull(decodeBackup(""))
        assertNull(decodeBackup("no-es-json"))
        assertNull(decodeBackup("""{"version":1,"wallets":"roto"}"""))
    }

    @Test
    fun version_futura_se_rechaza() {
        val raw = encodeBackup(full().copy(version = 99))
        assertNull(decodeBackup(raw))
    }

    @Test
    fun respaldo_minimo_solo_version_es_valido() {
        val decoded = decodeBackup("""{"version":1}""")
        assertEquals(1, decoded?.version)
        assertEquals(null, decoded?.wallets)
    }

    @Test
    fun respaldo_vacio_roundtrip() {
        assertEquals(LocalBackup(), decodeBackup(encodeBackup(LocalBackup())))
    }
}
