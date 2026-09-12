package com.fintrack.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.fintrack.app.ui.auth.AuthScreen
import com.fintrack.app.ui.budget.BudgetScreen
import com.fintrack.app.ui.calendar.CalendarScreen
import com.fintrack.app.ui.dashboard.DashboardScreen
import com.fintrack.app.ui.flows.FlowsScreen
import com.fintrack.app.ui.permissions.PermissionsScreen
import com.fintrack.app.ui.quickentry.QuickEntryScreen
import org.koin.androidx.compose.koinViewModel
import com.fintrack.app.ui.dashboard.DashboardViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val QUICK_ENTRY = "quick_entry"
    const val PERMISSIONS = "permissions"
    const val AUTH = "auth"
    const val CALENDAR = "calendar"
    const val FLOWS = "flows"
    const val BUDGET = "budget"
}

private fun androidx.navigation.NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD)
        launchSingleTop = true
    }
}

@Composable
fun FinTrackNavGraph(
    navController: NavHostController,
    openQuickEntryOnStart: Boolean = false
) {
    // Un solo DashboardViewModel por actividad: sobrevive a pops del backstack
    // (tabs, Tile, QuickEntry) sin el crash de getBackStackEntry().
    val activity = LocalContext.current as ComponentActivity

    if (openQuickEntryOnStart) {
        LaunchedEffect(Unit) {
            navController.navigate(Routes.QUICK_ENTRY)
        }
    }
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            val dashboardViewModel: DashboardViewModel =
                koinViewModel(viewModelStoreOwner = activity)
            DashboardScreen(
                onNavigateToQuickEntry = { navController.navigate(Routes.QUICK_ENTRY) },
                onNavigateToPermissions = { navController.navigate(Routes.PERMISSIONS) },
                onNavigateToAuth = { navController.navigate(Routes.AUTH) },
                onNavigateToCalendar = { navController.navigateToTab(Routes.CALENDAR) },
                onNavigateToFlows = { navController.navigateToTab(Routes.FLOWS) },
                onNavigateToBudget = { navController.navigateToTab(Routes.BUDGET) },
                viewModel = dashboardViewModel
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(
                onNavigateToDashboard = { navController.navigateToTab(Routes.DASHBOARD) },
                onNavigateToAuth = { navController.navigate(Routes.AUTH) },
                onNavigateToFlows = { navController.navigateToTab(Routes.FLOWS) },
                onNavigateToBudget = { navController.navigateToTab(Routes.BUDGET) }
            )
        }
        composable(Routes.FLOWS) {
            FlowsScreen(
                onNavigateToAuth = { navController.navigate(Routes.AUTH) },
                onNavigateToDashboard = { navController.navigateToTab(Routes.DASHBOARD) },
                onNavigateToCalendar = { navController.navigateToTab(Routes.CALENDAR) },
                onNavigateToBudget = { navController.navigateToTab(Routes.BUDGET) }
            )
        }
        composable(Routes.BUDGET) {
            BudgetScreen(
                onNavigateToAuth = { navController.navigate(Routes.AUTH) },
                onNavigateToDashboard = { navController.navigateToTab(Routes.DASHBOARD) },
                onNavigateToCalendar = { navController.navigateToTab(Routes.CALENDAR) },
                onNavigateToFlows = { navController.navigateToTab(Routes.FLOWS) }
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AUTH) {
            val dashboardViewModel: DashboardViewModel =
                koinViewModel(viewModelStoreOwner = activity)
            AuthScreen(
                onLoggedIn = {
                    dashboardViewModel.loadDashboard()
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.QUICK_ENTRY) {
            val dashboardViewModel: DashboardViewModel =
                koinViewModel(viewModelStoreOwner = activity)
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
