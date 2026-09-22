package com.fintrack.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OtaVerifyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun hash_correcto_pasa() {
        val file = tmp.newFile("app.apk").apply { writeText("fintrack-payload") }
        val expected = sha256Hex("fintrack-payload".toByteArray())
        assertTrue(OtaInstaller.verifySha256(file, expected))
    }

    @Test
    fun mayusculas_tambien_pasan() {
        val file = tmp.newFile("app.apk").apply { writeText("fintrack-payload") }
        val expected = sha256Hex("fintrack-payload".toByteArray()).uppercase()
        assertTrue(OtaInstaller.verifySha256(file, expected))
    }

    @Test
    fun archivo_tamperado_no_pasa() {
        val file = tmp.newFile("app.apk").apply { writeText("fintrack-payload") }
        val expected = sha256Hex("fintrack-payload".toByteArray())
        file.appendText("malware")
        assertFalse(OtaInstaller.verifySha256(file, expected))
    }

    @Test
    fun hash_vacio_o_malformado_no_pasa() {
        val file = tmp.newFile("app.apk").apply { writeText("fintrack-payload") }
        assertFalse(OtaInstaller.verifySha256(file, ""))
        assertFalse(OtaInstaller.verifySha256(file, "zzz"))
        assertFalse(OtaInstaller.verifySha256(file, "ab12"))
    }

    @Test
    fun archivo_inexistente_no_pasa() {
        val missing = java.io.File(tmp.root, "no-existe.apk")
        assertFalse(
            OtaInstaller.verifySha256(
                missing,
                sha256Hex("x".toByteArray())
            )
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
