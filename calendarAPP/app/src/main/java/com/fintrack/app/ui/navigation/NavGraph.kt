package com.fintrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.fintrack.app.ui.dashboard.DashboardScreen
import com.fintrack.app.ui.permissions.PermissionsScreen
import com.fintrack.app.ui.quickentry.QuickEntryScreen
import org.koin.androidx.compose.koinViewModel
import com.fintrack.app.ui.dashboard.DashboardViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val QUICK_ENTRY = "quick_entry"
    const val PERMISSIONS = "permissions"
}

@Composable
fun FinTrackNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToQuickEntry = { navController.navigate(Routes.QUICK_ENTRY) },
                onNavigateToPermissions = { navController.navigate(Routes.PERMISSIONS) }
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QUICK_ENTRY) {
            val dashboardViewModel: DashboardViewModel = koinViewModel()
            QuickEntryScreen(
                onSave = { transaction ->
                    dashboardViewModel.addTransaction(transaction)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}
