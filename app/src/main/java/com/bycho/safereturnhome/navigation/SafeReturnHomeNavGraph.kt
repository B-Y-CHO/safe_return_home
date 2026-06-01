package com.bycho.safereturnhome.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bycho.safereturnhome.ui.screen.home.HomeRoute
import com.bycho.safereturnhome.ui.screen.guardian.GuardianSettingsRoute
import com.bycho.safereturnhome.ui.screen.map.MapRoute

@Composable
fun SafeReturnHomeNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Home.route
    ) {
        composable(Routes.Home.route) {
            HomeRoute(
                onStartTripClick = { navController.navigate(Routes.Map.route) },
                onGuardianSettingsClick = { navController.navigate(Routes.GuardianSettings.route) }
            )
        }
        composable(Routes.GuardianSettings.route) {
            GuardianSettingsRoute(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.Map.route) {
            MapRoute(
                onBackClick = { navController.popBackStack() },
                onNavigationFinished = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
