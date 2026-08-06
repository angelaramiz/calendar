package com.calendarfinance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.calendarfinance.app.ui.navigation.CalendarNavGraph
import com.calendarfinance.app.ui.theme.CalendarFinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarFinanceTheme {
                CalendarNavGraph()
            }
        }
    }
}
