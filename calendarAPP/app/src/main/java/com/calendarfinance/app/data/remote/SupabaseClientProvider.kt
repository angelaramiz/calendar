package com.calendarfinance.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    const val SUPABASE_URL = "https://ugtlxnrwfipoctckuvfd.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVndGx4bnJ3Zmlwb2N0Y2t1dmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzU4Mzk4MDQsImV4cCI6MjA1MTQxNTgwNH0.A5W4rRxYDxyPqFh7a4FX_ejniQl1nBNf1hMQuf7vjm4"

    val client: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
            install(GoTrue)
            install(Postgrest)
        }
    }
}
