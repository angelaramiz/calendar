package com.fintrack.app.data.remote

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

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

    suspend fun logout() {
        try { client.auth.signOut() } catch (e: Exception) { }
    }
}
