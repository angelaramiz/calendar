package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class CloudBackupCryptoTest {

    @Test
    fun sellar_y_abrir_roundtrip() {
        val plain = """{"version":1,"caps":{"Comida":3000.0}}"""
        val sealed = CloudBackupCrypto.seal(plain, "secreto-fuerte-123")
        assertEquals(plain, CloudBackupCrypto.open(sealed, "secreto-fuerte-123"))
    }

    @Test
    fun contrasena_incorrecta_falla() {
        val sealed = CloudBackupCrypto.seal("""{"version":1}""", "correcta-123")
        try {
            CloudBackupCrypto.open(sealed, "otra-contrasena-456")
            fail("Abrir con contraseña incorrecta debe fallar")
        } catch (e: IllegalArgumentException) {
            // Esperado: GCM no autentica con otra llave.
        }
    }

    @Test
    fun payload_corrupto_falla() {
        val sealed = CloudBackupCrypto.seal("""{"version":1}""", "clave-123")
        val corrupto = sealed.copy(cipherB64 = JvmB64.encode(ByteArray(32) { it.toByte() }))
        try {
            CloudBackupCrypto.open(corrupto, "clave-123")
            fail("Payload corrupto debe fallar")
        } catch (e: IllegalArgumentException) {
            // Esperado.
        }
    }

    @Test
    fun iv_manipulado_falla() {
        val sealed = CloudBackupCrypto.seal("""{"version":1}""", "clave-123")
        val ivBytes = JvmB64.decode(sealed.ivB64)
        ivBytes[0] = (ivBytes[0] + 1).toByte()
        val manipulado = sealed.copy(ivB64 = JvmB64.encode(ivBytes))
        try {
            CloudBackupCrypto.open(manipulado, "clave-123")
            fail("IV manipulado debe fallar (GCM autentica el IV)")
        } catch (e: IllegalArgumentException) {
            // Esperado.
        }
    }

    @Test
    fun sal_aleatoria_cada_sello_difiere() {
        val a = CloudBackupCrypto.seal("""{"version":1}""", "clave-123")
        val b = CloudBackupCrypto.seal("""{"version":1}""", "clave-123")
        assertNotEquals("El salt aleatorio debe diferir", a.saltB64, b.saltB64)
        assertNotEquals("El IV aleatorio debe diferir", a.ivB64, b.ivB64)
        assertEquals(a.cipherB64.length, b.cipherB64.length)
    }

    @Test
    fun contrasena_vacia_se_rechaza() {
        try {
            CloudBackupCrypto.seal("""{}""", "")
            fail("Sellar con contraseña vacía debe fallar")
        } catch (e: IllegalArgumentException) {
            // Esperado.
        }
    }
}
