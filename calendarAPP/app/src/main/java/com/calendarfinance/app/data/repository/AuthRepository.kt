package com.calendarfinance.app.data.repository

import android.util.Log
import com.calendarfinance.app.data.model.User
import com.calendarfinance.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val tag = "AuthRepository"
    private val client get() = SupabaseClientProvider.client

    val currentUserId: String?
        get() = try { client.auth.currentSessionOrNull()?.user?.id } catch (e: Exception) { Log.e(tag, "currentUserId error", e); null }

    val isLoggedIn: Boolean
        get() = try { client.auth.currentSessionOrNull() != null } catch (e: Exception) { Log.e(tag, "isLoggedIn error", e); false }

    suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "login email=$email")
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Log.d(tag, "signInWith OK")
            val userInfo = client.auth.retrieveUserForCurrentSession()
            Log.d(tag, "retrieveUser OK: ${userInfo.id}")
            val user = fetchUserProfile(userInfo.id)
            Log.d(tag, "fetchUserProfile OK: ${user.username}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "login error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun loginWithUsername(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "login username=$username")
            val userRow = client.from("users").select {
                filter { eq("username", username) }
                limit(1)
            }.decodeSingle<User>()
            Log.d(tag, "found user: ${userRow.email}")
            login(userRow.email, password)
        } catch (e: Exception) {
            Log.e(tag, "loginWithUsername error: ${e.message}", e)
            Result.failure(Exception("Usuario no encontrado"))
        }
    }

    suspend fun register(email: String, password: String, username: String, name: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "register email=$email, username=$username")
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentSessionOrNull()?.user?.id ?: throw Exception("Error al crear usuario")
            Log.d(tag, "signUp OK: userId=$userId")
            client.from("users").upsert(mapOf(
                "id" to userId,
                "email" to email,
                "username" to username,
                "name" to name
            ))
            val user = User(id = userId, email = email, username = username, name = name)
            Log.d(tag, "register OK")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "register error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun recoverPassword(emailOrUsername: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "recoverPassword: $emailOrUsername")
            val email = if (emailOrUsername.contains("@")) {
                emailOrUsername
            } else {
                val userRow = client.from("users").select {
                    filter { eq("username", emailOrUsername) }
                    limit(1)
                }.decodeSingle<User>()
                userRow.email
            }
            client.auth.resetPasswordForEmail(email)
            Log.d(tag, "resetPassword OK")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "recoverPassword error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            client.auth.signOut()
            Log.d(tag, "logout OK")
        } catch (e: Exception) {
            Log.e(tag, "logout error: ${e.message}", e)
        }
    }

    private suspend fun fetchUserProfile(userId: String): User {
        return try {
            client.from("users").select {
                filter { eq("id", userId) }
                limit(1)
            }.decodeSingle<User>()
        } catch (e: Exception) {
            Log.w(tag, "fetchUserProfile: perfil no existe, creando uno nuevo")
            val userInfo = client.auth.retrieveUserForCurrentSession()
            val email = userInfo.email ?: "unknown@unknown.com"
            val user = User(id = userId, email = email, username = email.substringBefore("@"), name = email.substringBefore("@"))
            client.from("users").upsert(mapOf(
                "id" to userId,
                "email" to email,
                "username" to user.username,
                "name" to user.name
            ))
            user
        }
    }
}
