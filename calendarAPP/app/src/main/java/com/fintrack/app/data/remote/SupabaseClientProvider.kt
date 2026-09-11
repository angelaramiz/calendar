package com.fintrack.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient

object SupabaseClientProvider {
    private const val SUPABASE_URL = "https://ugtlxnrwfipoctckuvfd.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_KcdYZchjzzpizgM4nhTw8w_Bd6w6-d1"

    val client: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
            install(io.github.jan.supabase.auth.Auth)
            install(io.github.jan.supabase.postgrest.Postgrest)
        }
    }
}

