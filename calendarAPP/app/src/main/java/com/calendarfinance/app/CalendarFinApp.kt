package com.calendarfinance.app

import android.app.Application
import android.util.Log
import com.calendarfinance.app.di.appModule
import org.koin.android.ext.android.startKoin

class CalendarFinApp : Application() {

    companion object {
        const val TAG = "CalendarFinApp"
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "CRASH en hilo ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            startKoin {
                androidContext(this@CalendarFinApp)
                modules(appModule)
            }
            Log.d(TAG, "Koin inicializado OK")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Koin: ${e.message}", e)
        }
    }
}
