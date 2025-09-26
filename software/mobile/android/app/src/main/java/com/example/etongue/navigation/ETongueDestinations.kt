package com.example.etongue.navigation

/**
 * Sealed class representing all possible destinations in the E-Tongue app.
 * This provides type-safe navigation and ensures all routes are defined in one place.
 */
sealed class ETongueDestinations(val route: String) {
    /**
     * Connection screen - Entry point for device connection
     */
    object Connection : ETongueDestinations("connection")
    
    /**
     * Dashboard screen - Real-time sensor data visualization
     */
    object Dashboard : ETongueDestinations("dashboard")
    
    /**
     * Data Management screen - File management and data export
     */
    object DataManagement : ETongueDestinations("data_management")
    
    /**
     * Analysis screen - Historical data visualization and analysis
     */
    object Analysis : ETongueDestinations("analysis")
    
    companion object {
        /**
         * List of all destinations for easy iteration
         */
        val allDestinations = listOf(
            Connection,
            Dashboard,
            DataManagement,
            Analysis
        )
        
        /**
         * Find destination by route string
         */
        fun fromRoute(route: String?): ETongueDestinations {
            return allDestinations.find { it.route == route } ?: Connection
        }
    }
}