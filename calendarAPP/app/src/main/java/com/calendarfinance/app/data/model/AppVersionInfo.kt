package com.calendarfinance.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppVersionInfo(
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val apkUrl: String = ""
)
