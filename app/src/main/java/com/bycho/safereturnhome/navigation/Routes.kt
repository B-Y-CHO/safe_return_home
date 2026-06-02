package com.bycho.safereturnhome.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object GuardianSettings : Routes("guardian-settings")
    data object ServerSettings : Routes("server-settings")
    data object Map : Routes("map")
}
