package com.bycho.safereturnhome.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bycho.safereturnhome.ui.screen.home.HomeRoute
import com.bycho.safereturnhome.ui.screen.guardian.GuardianSettingsRoute
import com.bycho.safereturnhome.ui.screen.map.MapRoute
import com.bycho.safereturnhome.ui.screen.server.ServerSettingsRoute
import com.bycho.safereturnhome.ui.viewmodel.SignalPollingViewModel

@Composable
fun SafeReturnHomeNavGraph(
    signalPollingViewModel: SignalPollingViewModel = viewModel()
) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val signalPollingUiState by signalPollingViewModel.uiState.collectAsState()

    LaunchedEffect(lifecycleOwner, signalPollingViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            signalPollingViewModel.pollSignals()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Home.route
    ) {
        composable(Routes.Home.route) {
            HomeRoute(
                onStartTripClick = { navController.navigate(Routes.Map.route) },
                onGuardianSettingsClick = { navController.navigate(Routes.GuardianSettings.route) },
                onServerSettingsClick = { navController.navigate(Routes.ServerSettings.route) },
                signalPollingUiState = signalPollingUiState
            )
        }
        composable(Routes.GuardianSettings.route) {
            GuardianSettingsRoute(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.ServerSettings.route) {
            ServerSettingsRoute(
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
