package com.example.etongue.navigation

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController

/**
 * Handles deep linking for the E-Tongue app.
 * Supports navigation to specific screens via URI schemes.
 */
object DeepLinkHandler {
    
    private const val SCHEME = "etongue"
    private const val HOST = "app"
    
    /**
     * Deep link URIs for each screen
     */
    object DeepLinks {
        const val CONNECTION = "$SCHEME://$HOST/connection"
        const val DASHBOARD = "$SCHEME://$HOST/dashboard"
        const val DATA_MANAGEMENT = "$SCHEME://$HOST/data"
        const val ANALYSIS = "$SCHEME://$HOST/analysis"
        const val ANALYSIS_WITH_FILE = "$SCHEME://$HOST/analysis?fileName={fileName}"
    }
    
    /**
     * Handle incoming deep link intent
     */
    fun handleDeepLink(intent: Intent, navController: NavController): Boolean {
        val uri = intent.data ?: return false
        
        return when {
            uri.toString().startsWith(DeepLinks.CONNECTION) -> {
                navController.navigate(ETongueDestinations.Connection.route)
                true
            }
            uri.toString().startsWith(DeepLinks.DASHBOARD) -> {
                navController.navigate(ETongueDestinations.Dashboard.route)
                true
            }
            uri.toString().startsWith(DeepLinks.DATA_MANAGEMENT) -> {
                navController.navigate(ETongueDestinations.DataManagement.route)
                true
            }
            uri.toString().startsWith("$SCHEME://$HOST/analysis") -> {
                val fileName = uri.getQueryParameter("fileName")
                val route = if (fileName != null) {
                    "${ETongueDestinations.Analysis.route}?fileName=$fileName"
                } else {
                    ETongueDestinations.Analysis.route
                }
                navController.navigate(route)
                true
            }
            else -> false
        }
    }
    
    /**
     * Create deep link URI for a destination
     */
    fun createDeepLink(destination: ETongueDestinations, parameters: Map<String, String> = emptyMap()): Uri {
        val baseUri = when (destination) {
            ETongueDestinations.Connection -> DeepLinks.CONNECTION
            ETongueDestinations.Dashboard -> DeepLinks.DASHBOARD
            ETongueDestinations.DataManagement -> DeepLinks.DATA_MANAGEMENT
            ETongueDestinations.Analysis -> DeepLinks.ANALYSIS
        }
        
        return if (parameters.isEmpty()) {
            Uri.parse(baseUri)
        } else {
            val uriBuilder = Uri.parse(baseUri).buildUpon()
            parameters.forEach { (key, value) ->
                uriBuilder.appendQueryParameter(key, value)
            }
            uriBuilder.build()
        }
    }
    
    /**
     * Check if URI is a valid deep link for this app
     */
    fun isValidDeepLink(uri: Uri): Boolean {
        return uri.scheme == SCHEME && uri.host == HOST
    }
}