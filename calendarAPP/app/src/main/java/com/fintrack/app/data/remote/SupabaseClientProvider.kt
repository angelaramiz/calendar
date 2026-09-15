package com.fintrack.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient

object SupabaseClientProvider {
    private const val SUPABASE_URL = "https://ugtlxnrwfipoctckuvfd.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_KcdYZchjzzpizgM4nhTw8w_Bd6w6-d1"

    val client: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
            install(Auth) {
                // Sesión persistente: se guarda/carga del almacén y se
                // refresca sola. enableLifecycleCallbacks=false es intencional:
                // con true el refresco se pausa sin foco y el listener en
                // segundo plano se quedaba con token expirado.
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
                enableLifecycleCallbacks = false
            }
            install(io.github.jan.supabase.postgrest.Postgrest)
        }
    }
}

