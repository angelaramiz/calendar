package com.fintrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
fun FinTrackNavGraph(
    navController: NavHostController,
    openQuickEntryOnStart: Boolean = false
) {
    if (openQuickEntryOnStart) {
        LaunchedEffect(Unit) {
            navController.navigate(Routes.QUICK_ENTRY)
        }
    }
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            val parentEntry = remember(it) { navController.getBackStackEntry(Routes.DASHBOARD) }
            val dashboardViewModel: DashboardViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
            DashboardScreen(
                onNavigateToQuickEntry = { navController.navigate(Routes.QUICK_ENTRY) },
                onNavigateToPermissions = { navController.navigate(Routes.PERMISSIONS) },
                viewModel = dashboardViewModel
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QUICK_ENTRY) {
            val parentEntry = remember { navController.getBackStackEntry(Routes.DASHBOARD) }
            val dashboardViewModel: DashboardViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
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
