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
import androidx.navigation.navDeepLink
import com.bycho.safereturnhome.ui.screen.auth.AuthRoute
import com.bycho.safereturnhome.ui.screen.guardian.GuardianSettingsRoute
import com.bycho.safereturnhome.ui.screen.home.HomeRoute
import com.bycho.safereturnhome.ui.screen.map.MapRoute
import com.bycho.safereturnhome.ui.screen.server.ServerSettingsRoute
import com.bycho.safereturnhome.ui.screen.trip.TripRoute
import com.bycho.safereturnhome.ui.viewmodel.AuthViewModel
import com.bycho.safereturnhome.ui.viewmodel.MapViewModel
import com.bycho.safereturnhome.ui.viewmodel.SignalPollingViewModel

@Composable
fun SafeReturnHomeNavGraph(
    authViewModel: AuthViewModel = viewModel(),
    signalPollingViewModel: SignalPollingViewModel = viewModel()
) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewModel: MapViewModel = viewModel()
    val authUiState by authViewModel.uiState.collectAsState()
    val signalPollingUiState by signalPollingViewModel.uiState.collectAsState()

    LaunchedEffect(lifecycleOwner, signalPollingViewModel, authUiState.isSignedIn) {
        if (authUiState.isSignedIn) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                signalPollingViewModel.pollSignals()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Auth.route
    ) {
        composable(Routes.Auth.route) {
            AuthRoute(
                viewModel = authViewModel,
                onAuthenticated = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Auth.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.Home.route) {
            HomeRoute(
                onStartTripClick = { navController.navigate(Routes.Map.route) },
                onSosClick = { navController.navigate(Routes.Trip.route) },
                onGuardianSettingsClick = {
                    navController.navigate(Routes.GuardianSettings.route)
                },
                onServerSettingsClick = {
                    navController.navigate(Routes.ServerSettings.route)
                },
                onLogoutClick = {
                    mapViewModel.stopNavigation()
                    authViewModel.signOut()
                    navController.navigate(Routes.Auth.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                signalPollingUiState = signalPollingUiState,
                mapViewModel = mapViewModel
            )
        }
        composable(Routes.GuardianSettings.route) {
            if (authUiState.isSignedIn) {
                GuardianSettingsRoute(onBackClick = { navController.popBackStack() })
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Auth.route) {
                        popUpTo(Routes.GuardianSettings.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
        composable(Routes.ServerSettings.route) {
            if (authUiState.isSignedIn) {
                ServerSettingsRoute(onBackClick = { navController.popBackStack() })
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Auth.route) {
                        popUpTo(Routes.ServerSettings.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
        composable(
            route = Routes.Map.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = Routes.Map.deepLinkUri }
            )
        ) {
            if (authUiState.isSignedIn) {
                MapRoute(
                    dangerZones = signalPollingUiState.dangerZones,
                    dangerZone = signalPollingUiState.latestDangerZone,
                    dangerZoneEventVersion = signalPollingUiState.dangerZoneEventVersion,
                    signalPollingUiState = signalPollingUiState,
                    onUavEscortRequested = signalPollingViewModel::requestUavEscort,
                    onBackClick = { navController.popBackStack() },
                    onNavigationFinished = {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Home.route) { inclusive = true }
                        }
                    },
                    viewModel = mapViewModel
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Auth.route) {
                        popUpTo(Routes.Map.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
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
