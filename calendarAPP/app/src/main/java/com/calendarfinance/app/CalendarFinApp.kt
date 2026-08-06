package com.calendarfinance.app

import android.app.Application
import com.calendarfinance.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CalendarFinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CalendarFinApp)
            modules(appModule)
        }
    }
}
