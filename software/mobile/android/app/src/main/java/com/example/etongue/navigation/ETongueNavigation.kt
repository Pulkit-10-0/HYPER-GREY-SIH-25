package com.example.etongue.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavBackStackEntry
import com.example.etongue.ui.screens.AnalysisScreen
import com.example.etongue.ui.screens.ConnectionScreen
import com.example.etongue.ui.screens.DashboardScreen
import com.example.etongue.ui.screens.DataManagementScreen
import com.example.etongue.ui.viewmodels.ViewModelFactory
import com.example.etongue.ui.viewmodels.ConnectionViewModel
import com.example.etongue.ui.viewmodels.DataManagementViewModel

/**
 * Main navigation composable for the E-Tongue app.
 * Handles all screen navigation and state management.
 */
@Composable
fun ETongueNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModelFactory = ViewModelFactory(context)
    
    NavHost(
        navController = navController,
        startDestination = ETongueDestinations.Connection.route,
        modifier = modifier
    ) {
        // Connection Screen - Device discovery and connection
        composable(
            route = ETongueDestinations.Connection.route
        ) {
            val connectionViewModel = viewModel<ConnectionViewModel>(
                factory = viewModelFactory
            )
            
            ConnectionScreen(
                viewModel = connectionViewModel,
                onNavigateToDashboard = {
                    navController.navigate(ETongueDestinations.Dashboard.route) {
                        // Clear back stack when successfully connected
                        popUpTo(ETongueDestinations.Connection.route) {
                            inclusive = false
                        }
                    }
                }
            )
        }
        
        // Dashboard Screen - Real-time sensor data visualization
        composable(
            route = ETongueDestinations.Dashboard.route
        ) {
            DashboardScreen(
                onNavigateToDataManagement = {
                    navController.navigate(ETongueDestinations.DataManagement.route)
                },
                onNavigateToAnalysis = {
                    navController.navigate(ETongueDestinations.Analysis.route)
                },
                onNavigateToConnection = {
                    navController.navigate(ETongueDestinations.Connection.route) {
                        // Clear back stack when disconnecting
                        popUpTo(ETongueDestinations.Connection.route) {
                            inclusive = true
                        }
                    }
                },
                viewModelFactory = viewModelFactory
            )
        }
        
        // Data Management Screen - File management and data export
        composable(
            route = ETongueDestinations.DataManagement.route
        ) {
            val dataManagementViewModel = viewModel<DataManagementViewModel>(
                factory = viewModelFactory
            )
            
            DataManagementScreen(
                viewModel = dataManagementViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAnalysis = { fileName ->
                    navController.navigate("${ETongueDestinations.Analysis.route}?fileName=$fileName")
                }
            )
        }
        
        // Analysis Screen - Historical data visualization and analysis
        composable(
            route = "${ETongueDestinations.Analysis.route}?fileName={fileName}"
        ) { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName")
            
            AnalysisScreen(
                fileName = fileName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Alternative Analysis Screen route without parameters
        composable(
            route = ETongueDestinations.Analysis.route
        ) {
            AnalysisScreen(
                fileName = null,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}