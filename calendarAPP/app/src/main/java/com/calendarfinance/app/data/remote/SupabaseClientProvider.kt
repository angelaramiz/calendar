package com.calendarfinance.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    const val SUPABASE_URL = "https://ugtlxnrwfipoctckuvfd.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_KcdYZchjzzpizgM4nhTw8w_Bd6w6-d1"

    val client: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
        }
    }
}
