package com.bycho.safereturnhome.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Map : Routes("map")
    data object Trip : Routes("trip")
}
