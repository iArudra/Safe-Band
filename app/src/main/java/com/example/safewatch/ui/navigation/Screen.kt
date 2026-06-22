package com.example.safewatch.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object ProfileSetup : Screen("profile_setup")
    object Home : Screen("home")
    object Alerts : Screen("alerts")
    object GeoFence : Screen("geofence")
    object Settings : Screen("settings")
    object SOSAlert : Screen("sos_alert")
}
