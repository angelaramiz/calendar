package com.fintrack.app.data

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTxCodecTest {

    private fun tx(amount: Double, merchant: String? = "Pay trans urbani") = TransactionEntity(
        amount = amount,
        type = "EXPENSE",
        category = "Compras",
        description = "Pago detectado",
        merchant = merchant,
        timestamp = 1_700_000_000_000L,
        source = "com.nu.production"
    )

    @Test
    fun roundtrip_preservaCampos() {
        val original = listOf(PendingTx(tx(40.0), detectedAt = 1_700_000_001_000L))
        val decoded = PendingTxCodec.decode(PendingTxCodec.encode(original))
        assertEquals(1, decoded.size)
        assertEquals(40.0, decoded[0].tx.amount, 0.0)
        assertEquals("Pay trans urbani", decoded[0].tx.merchant)
        assertEquals(1_700_000_001_000L, decoded[0].detectedAt)
    }

    @Test
    fun decode_nuloVacioCorrupto_devuelveVacia() {
        assertTrue(PendingTxCodec.decode(null).isEmpty())
        assertTrue(PendingTxCodec.decode("").isEmpty())
        assertTrue(PendingTxCodec.decode("no-es-json{{{").isEmpty())
    }

    @Test
    fun enqueue_agregaNuevo() {
        val result = PendingTxCodec.enqueue(emptyList(), PendingTx(tx(40.0)))
        assertEquals(1, result.size)
    }

    @Test
    fun enqueue_duplicadoMismoMinuto_noDuplica() {
        val first = PendingTx(tx(40.0), detectedAt = 60_000L)
        val dup = PendingTx(tx(40.0), detectedAt = 89_000L)
        val result = PendingTxCodec.enqueue(listOf(first), dup)
        assertEquals(1, result.size)
    }

    @Test
    fun enqueue_distintoMinuto_siAgrega() {
        val first = PendingTx(tx(40.0), detectedAt = 60_000L)
        val later = PendingTx(tx(40.0), detectedAt = 120_000L)
        val result = PendingTxCodec.enqueue(listOf(first), later)
        assertEquals(2, result.size)
    }

    @Test
    fun enqueue_distintoMonto_siAgrega() {
        val first = PendingTx(tx(40.0), detectedAt = 60_000L)
        val other = PendingTx(tx(55.0), detectedAt = 61_000L)
        val result = PendingTxCodec.enqueue(listOf(first), other)
        assertEquals(2, result.size)
    }

    @Test
    fun enqueue_tope_sacaLoMasViejo() {
        var list = emptyList<PendingTx>()
        // Minutos distintos para que el dedup no los fusione.
        for (i in 0 until PendingTxCodec.MAX_PENDING + 5) {
            list = PendingTxCodec.enqueue(
                list,
                PendingTx(tx(i.toDouble()), detectedAt = i * 60_000L)
            )
        }
        assertEquals(PendingTxCodec.MAX_PENDING, list.size)
        // Los 5 más viejos (montos 0..4) salieron.
        assertEquals(5.0, list.first().tx.amount, 0.0)
    }

    @Test
    fun roundtrip_merchantNulo() {
        val original = listOf(PendingTx(tx(10.0, merchant = null)))
        val decoded = PendingTxCodec.decode(PendingTxCodec.encode(original))
        assertEquals(1, decoded.size)
        assertEquals(null, decoded[0].tx.merchant)
    }
}
