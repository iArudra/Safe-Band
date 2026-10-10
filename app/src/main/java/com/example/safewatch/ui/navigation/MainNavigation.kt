package com.example.safewatch.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.safewatch.ui.screens.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun MainNavigation(navController: NavHostController) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val startDestination = if (currentUser != null) Screen.Home.route else Screen.Splash.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onNextScreen = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onNavigateToLogin = {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.ProfileSetup.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }
        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(onSetupComplete = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAlerts = { filter ->
                    navController.navigate("alerts?filter=$filter")
                },
                onNavigateToGeoFence = {
                    navController.navigate(Screen.GeoFence.route)
                }
            )
        }
        composable(
            route = "alerts?filter={filter}",
            arguments = listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    defaultValue = "All"
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val filter = backStackEntry.arguments?.getString("filter") ?: "All"
            AlertsScreen(initialFilter = filter)
        }
        composable(Screen.Alerts.route) {
            AlertsScreen(initialFilter = "All")
        }
        composable(Screen.GeoFence.route) {
            GeoFenceScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.SOSAlert.route) {
            SOSAlertScreen(onAcknowledge = {
                navController.popBackStack()
            })
        }
    }
}
