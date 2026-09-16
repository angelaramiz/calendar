package com.fintrack.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Correo + contraseña cifrados en el dispositivo (AndroidKeyStore) para el
 * desbloqueo con huella estilo banco: la huella autoriza usarlos y refrescar
 * el token sin escribir nada a mano. Nunca salen del teléfono.
 */
class CredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "fintrack_creds",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun email(): String? = prefs.getString(KEY_EMAIL, null)

    fun password(): String? = prefs.getString(KEY_PASSWORD, null)

    fun hasCredentials(): Boolean = !email().isNullOrBlank() && !password().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
    }
}
