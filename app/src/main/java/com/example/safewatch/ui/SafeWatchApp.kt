package com.example.safewatch.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.safewatch.ui.navigation.MainNavigation
import com.example.safewatch.ui.navigation.Screen
import com.example.safewatch.ui.theme.BackgroundDark
import com.example.safewatch.ui.theme.CardBackground
import com.example.safewatch.ui.theme.PrimaryBlue
import com.example.safewatch.ui.theme.TextSecondary

sealed class NavItem(val screen: Screen, val icon: ImageVector, val label: String) {
    object Home : NavItem(Screen.Home, Icons.Default.Home, "Home")
    object Alerts : NavItem(Screen.Alerts, Icons.Default.Notifications, "Alerts")
    object GeoFence : NavItem(Screen.GeoFence, Icons.Default.LocationOn, "Fence")
    object Settings : NavItem(Screen.Settings, Icons.Default.Settings, "Settings")
}

val navItems = listOf(
    NavItem.Home,
    NavItem.Alerts,
    NavItem.GeoFence,
    NavItem.Settings
)

@Composable
fun SafeWatchApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val showBottomBar = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.Alerts.route,
        Screen.GeoFence.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CardBackground,
                    contentColor = PrimaryBlue,
                ) {
                    navItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding),
            color = BackgroundDark
        ) {
            MainNavigation(navController = navController)
        }
    }
}
