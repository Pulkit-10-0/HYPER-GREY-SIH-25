package com.example.etongue.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.ui.viewmodels.ConnectionUiState
import com.example.etongue.ui.viewmodels.ConnectionViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockViewModel: ConnectionViewModel
    private lateinit var mockOnNavigateToDashboard: () -> Unit
    private lateinit var uiStateFlow: MutableStateFlow<ConnectionUiState>
    private lateinit var connectionStatusFlow: MutableStateFlow<ConnectionStatus>

    @Before
    fun setup() {
        mockViewModel = mockk(relaxed = true)
        mockOnNavigateToDashboard = mockk(relaxed = true)
        
        uiStateFlow = MutableStateFlow(ConnectionUiState())
        connectionStatusFlow = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        
        every { mockViewModel.uiState } returns uiStateFlow
        every { mockViewModel.connectionStatus } returns connectionStatusFlow
        every { mockViewModel.getFilteredDevices() } returns emptyList()
    }

    @Test
    fun connectionScreen_displaysHeaderCorrectly() {
        composeTestRule.setContent {
            ConnectionScreen(
                viewModel = mockViewModel,
                onNavigateToDashboard = mockOnNavigateToDashboard
            )
        }

        composeTestRule
            .onNodeWithText("Device Connection")
            .assertIsDisplayed()
    }

    @Test
    fun connectionScreen_displaysConnectionStatusCard() {
        composeTestRule.setContent {
            ConnectionScreen(
                viewModel = mockViewModel,
                onNavigateToDashboard = mockOnNavigateToDashboard
            )
        }

        composeTestRule
            .onNodeWithText("Connection Status")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Disconnected")
            .assertIsDisplayed()
    }

    @Test
    fun connectionScreen_displaysScanButton() {
        composeTestRule.setContent {
            ConnectionScreen(
                viewModel = mockViewModel,
                onNavigateToDashboard = mockOnNavigateToDashboard
            )
        }

        composeTestRule
            .onNodeWithText("Scan")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun connectionScreen_displaysNoDevicesMessage_whenDeviceListIsEmpty() {
        composeTestRule.setContent {
            ConnectionScreen(
                viewModel = mockViewModel,
                onNavigateToDashboard = mockOnNavigateToDashboard
            )
        }

        composeTestRule
            .onNodeWithText("No devices found")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Tap 'Scan' to search for ESP32 devices")
            .assertIsDisplayed()
    }

    @Test
    fun connectionScreen_callsStartScanning_whenScanButtonClicked() {
        composeTestRule.setContent {
            ConnectionScreen(
                viewModel = mockViewModel,
                onNavigateToDashboard = mockOnNavigateToDashboard
            )
        }

        composeTestRule
            .onNodeWithText("Scan")
            .performClick()

        verify { mockViewModel.startScanning() }
    }
}