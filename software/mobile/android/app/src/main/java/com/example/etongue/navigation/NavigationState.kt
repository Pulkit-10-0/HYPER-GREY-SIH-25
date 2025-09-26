package com.example.etongue.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * State holder for navigation-related state and actions.
 * This class manages navigation state and provides a clean interface for navigation operations.
 */
@Stable
class NavigationState(
    val navController: NavHostController
) {
    /**
     * Navigation actions for type-safe navigation
     */
    val navigationActions: NavigationActions = NavigationActions(navController)
    
    /**
     * Current destination
     */
    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination
    
    /**
     * Current route as ETongueDestinations
     */
    val currentETongueDestination: ETongueDestinations
        @Composable get() = ETongueDestinations.fromRoute(currentDestination?.route)
    
    /**
     * Check if current destination is the given destination
     */
    @Composable
    fun isCurrentDestination(destination: ETongueDestinations): Boolean {
        return currentDestination?.route == destination.route
    }
    
    /**
     * Check if we should show back button
     */
    @Composable
    fun shouldShowBackButton(): Boolean {
        val current = currentETongueDestination
        return current != ETongueDestinations.Connection && 
               current != ETongueDestinations.Dashboard &&
               navigationActions.canNavigateBack()
    }
    
    /**
     * Get navigation title for current screen
     */
    @Composable
    fun getNavigationTitle(): String {
        return when (currentETongueDestination) {
            ETongueDestinations.Connection -> "Device Connection"
            ETongueDestinations.Dashboard -> "E-Tongue Dashboard"
            ETongueDestinations.DataManagement -> "Data Management"
            ETongueDestinations.Analysis -> "Data Analysis"
        }
    }
}

/**
 * Remember navigation state across recompositions
 */
@Composable
fun rememberNavigationState(
    navController: NavHostController = rememberNavController()
): NavigationState {
    return remember(navController) {
        NavigationState(navController)
    }
}