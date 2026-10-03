package com.fintrack.app.domain

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Base64 aislado detrás de una interfaz inyectable: `android.util.Base64`
 * no existe en los tests JVM (solo stubs), así que el cripto puro nunca lo
 * toca directo. En producción y en tests se usa [JvmB64].
 */
interface CloudB64 {
    fun encode(bytes: ByteArray): String
    fun decode(s: String): ByteArray
}

/**
 * Base64 con `java.util.Base64`: corre igual en JVM (tests) y en Android
 * (API 26+, el minSdk del proyecto), sin dependencias nuevas.
 */
object JvmB64 : CloudB64 {
    private val encoder = java.util.Base64.getEncoder()
    private val decoder = java.util.Base64.getDecoder()
    override fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)
    override fun decode(s: String): ByteArray = decoder.decode(s)
}

/** JSON cifrado listo para la columna `payload` de `fintrack_backups`. */
@Serializable
data class SealedPayload(
    val saltB64: String,
    val ivB64: String,
    val cipherB64: String
)

/**
 * Cifrado del respaldo en nube (§B): PBKDF2-HMAC-SHA256 (salt aleatorio de
 * 16 B por usuario, 210k iteraciones) → AES-256-GCM.
 *
 * 100% puro y testeable en JVM (solo `javax.crypto` del JDK, sin
 * dependencias nuevas). La llave deriva de la contraseña de FinTrack, leída
 * en solo-lectura desde `CredentialStore`: la contraseña y el JSON en claro
 * JAMÁS se loguean ni viajan al servidor.
 */
object CloudBackupCrypto {

    const val ITERATIONS = 210_000
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun seal(
        plainJson: String,
        password: String,
        b64: CloudB64 = JvmB64,
        random: SecureRandom = SecureRandom()
    ): SealedPayload {
        require(password.isNotEmpty()) { "Contraseña vacía" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipherBytes = newCipher(Cipher.ENCRYPT_MODE, password, salt, iv)
            .doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return SealedPayload(
            saltB64 = b64.encode(salt),
            ivB64 = b64.encode(iv),
            cipherB64 = b64.encode(cipherBytes)
        )
    }

    /**
     * Lanza [IllegalArgumentException] si la contraseña es incorrecta o el
     * payload está corrupto/manipulado (GCM autentica datos e IV).
     */
    fun open(
        sealed: SealedPayload,
        password: String,
        b64: CloudB64 = JvmB64
    ): String {
        require(password.isNotEmpty()) { "Contraseña vacía" }
        try {
            val salt = b64.decode(sealed.saltB64)
            val iv = b64.decode(sealed.ivB64)
            val cipherBytes = b64.decode(sealed.cipherB64)
            require(salt.size == SALT_BYTES) { "Salt inválido" }
            require(iv.size == IV_BYTES) { "IV inválido" }
            return newCipher(Cipher.DECRYPT_MODE, password, salt, iv)
                .doFinal(cipherBytes).toString(Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "No se pudo descifrar: contraseña incorrecta o respaldo corrupto", e
            )
        }
    }

    private fun newCipher(mode: Int, password: String, salt: ByteArray, iv: ByteArray): Cipher {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS))
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, javax.crypto.spec.SecretKeySpec(key.encoded, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
    }
}
