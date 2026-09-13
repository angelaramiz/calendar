package com.fintrack.app.data.remote

import android.util.Log
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

/**
 * Resultado del registro: con "Confirm email" activado en Supabase no hay
 * sesión hasta confirmar el correo, así que el éxito NO exige sesión.
 */
sealed interface RegisterOutcome {
    data class LoggedIn(val userId: String) : RegisterOutcome
    data class NeedsConfirmation(val email: String) : RegisterOutcome
}

class AuthRepository {

    private val client get() = SupabaseClientProvider.client

    val currentUserId: String?
        get() = try { client.auth.currentSessionOrNull()?.user?.id } catch (e: Exception) { null }

    val isLoggedIn: Boolean
        get() = try { client.auth.currentSessionOrNull() != null } catch (e: Exception) { false }

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentSessionOrNull()?.user?.id ?: throw Exception("No user")
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String): Result<RegisterOutcome> {
        return try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentSessionOrNull()?.user?.id
                ?: runCatching { client.auth.currentUserOrNull()?.id }.getOrNull()
            if (userId != null) Result.success(RegisterOutcome.LoggedIn(userId))
            else Result.success(RegisterOutcome.NeedsConfirmation(email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reenvía el correo de confirmación de registro. */
    suspend fun resendConfirmation(email: String): Result<Unit> {
        return try {
            client.auth.resendEmail(OtpType.Email.SIGNUP, email)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("AuthRepository", "resendConfirmation failed", e)
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try { client.auth.signOut() } catch (e: Exception) { }
    }
}
