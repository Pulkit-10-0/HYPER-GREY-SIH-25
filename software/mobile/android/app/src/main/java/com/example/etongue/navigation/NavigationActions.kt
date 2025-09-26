package com.example.etongue.navigation

import androidx.navigation.NavHostController

/**
 * Navigation actions class that encapsulates all navigation operations.
 * This provides a clean interface for navigation and makes testing easier.
 */
class NavigationActions(private val navController: NavHostController) {
    
    /**
     * Navigate to Connection screen
     */
    fun navigateToConnection() {
        navController.navigate(ETongueDestinations.Connection.route) {
            // Clear entire back stack when going to connection
            popUpTo(0) {
                inclusive = true
            }
        }
    }
    
    /**
     * Navigate to Dashboard screen after successful connection
     */
    fun navigateToDashboard() {
        navController.navigate(ETongueDestinations.Dashboard.route) {
            // Keep connection in back stack but don't allow multiple dashboard instances
            popUpTo(ETongueDestinations.Dashboard.route) {
                inclusive = true
            }
        }
    }
    
    /**
     * Navigate to Data Management screen
     */
    fun navigateToDataManagement() {
        navController.navigate(ETongueDestinations.DataManagement.route)
    }
    
    /**
     * Navigate to Analysis screen
     */
    fun navigateToAnalysis(fileName: String? = null) {
        val route = if (fileName != null) {
            "${ETongueDestinations.Analysis.route}?fileName=$fileName"
        } else {
            ETongueDestinations.Analysis.route
        }
        navController.navigate(route)
    }
    
    /**
     * Navigate back to previous screen
     */
    fun navigateBack(): Boolean {
        return navController.popBackStack()
    }
    
    /**
     * Navigate back to a specific destination
     */
    fun navigateBackTo(destination: ETongueDestinations, inclusive: Boolean = false): Boolean {
        return navController.popBackStack(destination.route, inclusive)
    }
    
    /**
     * Get current destination route
     */
    fun getCurrentRoute(): String? {
        return navController.currentDestination?.route
    }
    
    /**
     * Check if we can navigate back
     */
    fun canNavigateBack(): Boolean {
        return navController.previousBackStackEntry != null
    }
    
    /**
     * Clear entire navigation stack and go to destination
     */
    fun navigateAndClearStack(destination: ETongueDestinations) {
        navController.navigate(destination.route) {
            popUpTo(0) {
                inclusive = true
            }
        }
    }
}