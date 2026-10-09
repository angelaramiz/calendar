package com.fintrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El borrador del Registro rápido debe sobrevivir intacto al disco. */
class DraftCodecTest {

    @Test
    fun borrador_completo_hace_roundtrip() {
        val original = EntryDraft(
            type = "INCOME",
            amount = "1500",
            category = "Sueldo",
            description = "Quincena",
            walletId = "bbva-nomina",
            cardId = null,
            step = 3
        )
        val raw = PendingOpCodec.json.encodeToString(EntryDraft.serializer(), original)
        val decoded = PendingOpCodec.json.decodeFromString(EntryDraft.serializer(), raw)
        assertEquals(original, decoded)
    }

    @Test
    fun borrador_vacio_usa_defaults() {
        val decoded = PendingOpCodec.json.decodeFromString(
            EntryDraft.serializer(), "{}"
        )
        assertEquals(EntryDraft(), decoded)
    }

    @Test
    fun json_corrupto_se_rechaza() {
        val decoded = runCatching {
            PendingOpCodec.json.decodeFromString(EntryDraft.serializer(), "{no json")
        }.getOrNull()
        assertNull(decoded)
    }
}
