package com.fintrack.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FinTrackBottomBar(
    selected: String,
    onDashboard: () -> Unit,
    onCalendar: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == Routes.DASHBOARD,
            onClick = onDashboard,
            icon = { Icon(Icons.Default.Home, "Inicio") },
            label = { Text("Inicio") }
        )
        NavigationBarItem(
            selected = selected == Routes.CALENDAR,
            onClick = onCalendar,
            icon = { Icon(Icons.Default.CalendarMonth, "Calendario") },
            label = { Text("Calendario") }
        )
    }
}
