package com.fintrack.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FinTrackBottomBar(
    selected: String,
    onDashboard: () -> Unit,
    onCalendar: () -> Unit,
    onFlows: () -> Unit,
    onBudget: () -> Unit
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
        NavigationBarItem(
            selected = selected == Routes.FLOWS,
            onClick = onFlows,
            icon = { Icon(Icons.Default.AccountTree, "Flujos") },
            label = { Text("Flujos") }
        )
        NavigationBarItem(
            selected = selected == Routes.BUDGET,
            onClick = onBudget,
            icon = { Icon(Icons.Default.Savings, "Presupuesto") },
            label = { Text("Presupuesto") }
        )
    }
}
