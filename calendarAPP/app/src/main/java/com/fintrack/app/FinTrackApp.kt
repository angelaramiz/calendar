package com.fintrack.app

import android.app.Application
import com.fintrack.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FinTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FinTrackApp)
            modules(appModule)
        }
    }
}
