package com.fintrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOpCodecTest {

    private fun op(id: String, kind: String = PendingOpKind.TX_DELETE) =
        PendingOp(id = id, kind = kind, payload = PendingOpCodec.json.encodeToString(
            TxIdPayload.serializer(), TxIdPayload(id)
        ))

    @Test
    fun roundtrip_preserva_ops_y_payloads() {
        val original = listOf(op("a"), op("b", PendingOpKind.MOV_INSERT))
        val decoded = PendingOpCodec.decode(PendingOpCodec.encode(original))
        assertEquals(2, decoded.size)
        assertEquals(PendingOpKind.TX_DELETE, decoded[0].kind)
        assertEquals("a", PendingOpCodec.payload<TxIdPayload>(decoded[0])?.id)
    }

    @Test
    fun decode_nuloVacioCorrupto_devuelveVacia() {
        assertTrue(PendingOpCodec.decode(null).isEmpty())
        assertTrue(PendingOpCodec.decode("").isEmpty())
        assertTrue(PendingOpCodec.decode("{{{no-json").isEmpty())
    }

    @Test
    fun payload_corrupto_devuelve_nulo() {
        val bad = PendingOp(id = "x", kind = "?", payload = "no-json")
        assertEquals(null, PendingOpCodec.payload<TxIdPayload>(bad))
    }

    @Test
    fun enqueue_respeta_tope_sacando_lo_viejo() {
        var list = emptyList<PendingOp>()
        for (i in 0 until PendingOpCodec.MAX_OPS + 10) {
            list = PendingOpCodec.enqueue(list, op("op-$i"))
        }
        assertEquals(PendingOpCodec.MAX_OPS, list.size)
        assertEquals("op-10", list.first().id)
    }

    @Test
    fun payloads_tipados_serializan() {
        val insert = TxInsertPayload(
            tx = com.fintrack.app.data.model.TransactionEntity(amount = 5.0),
            walletId = "nu"
        )
        val raw = PendingOpCodec.json.encodeToString(TxInsertPayload.serializer(), insert)
        val back = PendingOpCodec.json.decodeFromString(TxInsertPayload.serializer(), raw)
        assertEquals(5.0, back.tx.amount, 0.0)
        assertEquals("nu", back.walletId)
    }
}
