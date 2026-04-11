package com.bycho.safereturnhome.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bycho.safereturnhome.ui.screen.home.HomeRoute
import com.bycho.safereturnhome.ui.screen.map.MapRoute
import com.bycho.safereturnhome.ui.screen.trip.TripRoute

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
                onSosClick = { navController.navigate(Routes.Trip.route) }
            )
        }
        composable(Routes.Map.route) {
            MapRoute(
                onBackClick = { navController.popBackStack() },
                onConfirmRouteClick = { navController.navigate(Routes.Trip.route) }
            )
        }
        composable(Routes.Trip.route) {
            TripRoute(
                onBackClick = { navController.popBackStack() },
                onEndTripClick = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
