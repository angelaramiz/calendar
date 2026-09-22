package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetErrorsTest {

    @Test
    fun dns_sin_resolver_es_recuperable() {
        assertTrue(
            isRecoverableError(
                Exception("Unable to resolve host \"ugtlxnrwfipoctckuvfd.supabase.co\"")
            )
        )
    }

    @Test
    fun timeout_es_recuperable() {
        assertTrue(isRecoverableError(Exception("socket timeout")))
    }

    @Test
    fun error_de_datos_no_es_recuperable() {
        assertFalse(isRecoverableError(Exception("invalid input syntax for uuid")))
    }

    @Test
    fun friendly_no_expone_url_ni_user_id() {
        val raw = Exception(
            "HTTP request to https://ugtlxnrwfipoctckuvfd.supabase.co/rest/v1/" +
                "fintrack_transactions?user_id=eq.36caa9ba failed: Unable to resolve host"
        )
        val friendly = friendlyErrorMessage(raw)
        assertEquals(
            "Sin conexión: revisa tu internet. Tus movimientos locales siguen aquí.",
            friendly
        )
        assertFalse(friendly.contains("supabase.co"))
        assertFalse(friendly.contains("36caa9ba"))
    }

    @Test
    fun friendly_generico_para_error_de_datos() {
        assertEquals(
            "Algo falló al cargar. Reintenta.",
            friendlyErrorMessage(Exception("column \"x\" does not exist"))
        )
    }
}
