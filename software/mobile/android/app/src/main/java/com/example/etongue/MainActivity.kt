package com.example.etongue

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.etongue.navigation.DeepLinkHandler
import com.example.etongue.navigation.ETongueNavigation
import com.example.etongue.navigation.rememberNavigationState
import com.example.etongue.ui.theme.ETongueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ETongueTheme {
                ETongueApp(intent = intent)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun ETongueApp(intent: Intent? = null) {
    val navigationState = rememberNavigationState()
    
    // Handle deep links
    LaunchedEffect(intent) {
        intent?.let { 
            DeepLinkHandler.handleDeepLink(it, navigationState.navController)
        }
    }
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        ETongueNavigation(
            navController = navigationState.navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}