package com.calendarfinance.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calendarfinance.app.ui.navigation.CalendarNavGraph
import com.calendarfinance.app.ui.theme.CalendarFinanceTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d(TAG, "onCreate")

        try {
            setContent {
                var error by remember { mutableStateOf<String?>(null) }
                var errorDetails by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
                        error = throwable.message ?: "Error desconocido"
                        errorDetails = throwable.stackTraceToString()
                        Log.e(TAG, "Uncaught: ${throwable.message}", throwable)
                    }
                }

                CalendarFinanceTheme {
                    if (error != null) {
                        // Error screen - no crash
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "CalendarFinance",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Error al iniciar:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                if (errorDetails != null) {
                                    Text(
                                        text = errorDetails?.take(500) ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        CalendarNavGraph()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en setContent: ${e.message}", e)
        }
    }
}
