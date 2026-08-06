package com.calendarfinance.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calendarfinance.app.ui.auth.LoginScreen
import com.calendarfinance.app.ui.auth.RegisterScreen
import com.calendarfinance.app.ui.balance.BalanceScreen
import com.calendarfinance.app.ui.calendar.CalendarScreen
import com.calendarfinance.app.ui.movement.MovementFormScreen
import com.calendarfinance.app.ui.pattern.PatternFormScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CALENDAR = "calendar"
    const val MOVEMENT_FORM = "movement_form?movementId={movementId}&date={date}"
    const val PATTERN_FORM = "pattern_form?type={type}&patternId={patternId}"
    const val BALANCE = "balance"

    fun movementForm(movementId: String? = null, date: String? = null): String {
        val mid = movementId ?: ""
        val d = date ?: ""
        return "movement_form?movementId=$mid&date=$d"
    }

    fun patternForm(type: String = "income", patternId: String? = null): String {
        val pid = patternId ?: ""
        return "pattern_form?type=$type&patternId=$pid"
    }
}

@Composable
fun CalendarNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Routes.CALENDAR) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Routes.CALENDAR) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(
                onNavigateToMovement = { date -> navController.navigate(Routes.movementForm(date = date)) },
                onNavigateToPattern = { type -> navController.navigate(Routes.patternForm(type = type)) },
                onNavigateToBalance = { navController.navigate(Routes.BALANCE) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(
            route = Routes.MOVEMENT_FORM,
            arguments = listOf(
                navArgument("movementId") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            MovementFormScreen(
                movementId = backStackEntry.arguments?.getString("movementId")?.ifEmpty { null },
                date = backStackEntry.arguments?.getString("date")?.ifEmpty { null },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PATTERN_FORM,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType; defaultValue = "income" },
                navArgument("patternId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            PatternFormScreen(
                patternType = backStackEntry.arguments?.getString("type") ?: "income",
                patternId = backStackEntry.arguments?.getString("patternId")?.ifEmpty { null },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BALANCE) {
            BalanceScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
