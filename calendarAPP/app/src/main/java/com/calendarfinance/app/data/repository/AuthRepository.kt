package com.calendarfinance.app.data.repository

import com.calendarfinance.app.data.model.User
import com.calendarfinance.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val client get() = SupabaseClientProvider.client

    val currentUserId: String?
        get() = client.gotrue.currentSessionOrNull()?.user?.id

    val isLoggedIn: Boolean
        get() = client.gotrue.currentSessionOrNull() != null

    suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            client.gotrue.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userInfo = client.gotrue.retrieveUserForCurrentSession()
            val user = fetchUserProfile(userInfo.id)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithUsername(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val userRow = client.postgrest["users"].select {
                filter { eq("username", username) }
                limit(1L)
            }.decodeSingle<User>()
            login(userRow.email, password)
        } catch (e: Exception) {
            Result.failure(Exception("Usuario no encontrado"))
        }
    }

    suspend fun register(email: String, password: String, username: String, name: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            client.gotrue.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.gotrue.currentSessionOrNull()?.user?.id ?: throw Exception("Error al crear usuario")
            client.postgrest["users"].upsert(mapOf(
                "id" to userId,
                "email" to email,
                "username" to username,
                "name" to name
            ))
            val user = User(id = userId, email = email, username = username, name = name)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recoverPassword(emailOrUsername: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val email = if (emailOrUsername.contains("@")) {
                emailOrUsername
            } else {
                val userRow = client.postgrest["users"].select {
                    filter { eq("username", emailOrUsername) }
                    limit(1L)
                }.decodeSingle<User>()
                userRow.email
            }
            client.gotrue.sendRecoveryEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        client.gotrue.signOut()
    }

    private suspend fun fetchUserProfile(userId: String): User {
        return client.postgrest["users"].select {
            filter { eq("id", userId) }
            limit(1L)
        }.decodeSingle<User>()
    }
}
