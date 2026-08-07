package com.calendarfinance.app.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    private const val TAG = "SupabaseClient"

    const val SUPABASE_URL = "https://ugtlxnrwfipoctckuvfd.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_KcdYZchjzzpizgM4nhTw8w_Bd6w6-d1"

    val client: SupabaseClient by lazy {
        try {
            Log.d(TAG, "Inicializando Supabase client...")
            val c = createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
                install(Auth)
                install(Postgrest)
            }
            Log.d(TAG, "Supabase client OK")
            c
        } catch (e: Exception) {
            Log.e(TAG, "Error creando Supabase client: ${e.message}", e)
            throw e
        }
    }
}
