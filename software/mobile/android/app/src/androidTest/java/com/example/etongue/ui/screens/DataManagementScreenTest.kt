package com.example.etongue.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.etongue.data.models.DataFile
import com.example.etongue.ui.theme.ETongueTheme
import com.example.etongue.ui.viewmodels.DataManagementViewModel
import com.example.etongue.ui.viewmodels.DataManagementUiState
import com.example.etongue.ui.viewmodels.StorageInfo
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataManagementScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockViewModel: DataManagementViewModel
    private lateinit var uiStateFlow: MutableStateFlow<DataManagementUiState>
    private lateinit var filesFlow: MutableStateFlow<List<DataFile>>
    private lateinit var selectedFileFlow: MutableStateFlow<DataFile?>
    private lateinit var storageInfoFlow: MutableStateFlow<StorageInfo>

    private val sampleFiles = listOf(
        DataFile(
            fileName = "sensor_data_20240101_120000.json",
            filePath = "/data/data/com.example.etongue/files/sensor_data_20240101_120000.json",
            createdAt = 1704110400000L, // 2024-01-01 12:00:00
            fileSize = 1024L,
            dataPointCount = 100,
            sessionId = "session_1",
            deviceId = "ESP32_001"
        ),
        DataFile(
            fileName = "sensor_data_20240102_140000.json",
            filePath = "/data/data/com.example.etongue/files/sensor_data_20240102_140000.json",
            createdAt = 1704204000000L, // 2024-01-02 14:00:00
            fileSize = 2048L,
            dataPointCount = 200,
            sessionId = "session_2",
            deviceId = "ESP32_002"
        )
    )

    @Before
    fun setup() {
        mockViewModel = mockk(relaxed = true)
        
        uiStateFlow = MutableStateFlow(DataManagementUiState())
        filesFlow = MutableStateFlow(emptyList())
        selectedFileFlow = MutableStateFlow(null)
        storageInfoFlow = MutableStateFlow(StorageInfo())

        every { mockViewModel.uiState } returns uiStateFlow
        every { mockViewModel.files } returns filesFlow
        every { mockViewModel.selectedFile } returns selectedFileFlow
        every { mockViewModel.storageInfo } returns storageInfoFlow
        every { mockViewModel.formatTimestamp(any()) } returns "Jan 01, 2024 12:00:00"
    }

    @Test
    fun dataManagementScreen_displaysCorrectTitle() {
        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Data Management").assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_displaysBackButton() {
        var backPressed = false
        
        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = { backPressed = true },
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        
        assert(backPressed)
    }

    @Test
    fun dataManagementScreen_displaysRefreshButton() {
        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Refresh").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        
        verify { mockViewModel.loadFiles() }
    }

    @Test
    fun dataManagementScreen_displaysStorageInfo() {
        storageInfoFlow.value = StorageInfo(
            totalFiles = 5,
            totalSize = 10240L,
            formattedSize = "10 KB"
        )

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("10 KB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Size").assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_displaysEmptyStateWhenNoFiles() {
        filesFlow.value = emptyList()

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No Data Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start collecting sensor data to see files here").assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_displaysFileList() {
        filesFlow.value = sampleFiles

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        // Check that both files are displayed
        composeTestRule.onNodeWithText("sensor_data_20240101_120000.json").assertIsDisplayed()
        composeTestRule.onNodeWithText("sensor_data_20240102_140000.json").assertIsDisplayed()
        
        // Check file details
        composeTestRule.onNodeWithText("100 data points").assertIsDisplayed()
        composeTestRule.onNodeWithText("200 data points").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 KB").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 KB").assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_fileSelection() {
        filesFlow.value = sampleFiles

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        // Click on first file
        composeTestRule.onNodeWithText("sensor_data_20240101_120000.json").performClick()
        
        verify { mockViewModel.selectFile(sampleFiles[0]) }
    }

    @Test
    fun dataManagementScreen_viewFileAction() {
        filesFlow.value = sampleFiles
        var navigatedFileName: String? = null

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = { fileName -> navigatedFileName = fileName }
                )
            }
        }

        // Click view button on first file
        composeTestRule.onAllNodesWithContentDescription("View")[0].performClick()
        
        assert(navigatedFileName == sampleFiles[0].fileName)
    }

    @Test
    fun dataManagementScreen_exportFileAction() {
        filesFlow.value = sampleFiles

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        // Click export button on first file
        composeTestRule.onAllNodesWithContentDescription("Export")[0].performClick()
        
        // Note: Export functionality involves system intents which are hard to test in unit tests
        // This test mainly verifies the button is clickable
        composeTestRule.onAllNodesWithContentDescription("Export")[0].assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_deleteFileAction() {
        filesFlow.value = sampleFiles

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        // Click delete button on first file
        composeTestRule.onAllNodesWithContentDescription("Delete")[0].performClick()
        
        // Check that delete confirmation dialog appears
        composeTestRule.onNodeWithText("Delete File").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to delete 'sensor_data_20240101_120000.json'? This action cannot be undone.").assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_deleteConfirmationDialog() {
        filesFlow.value = sampleFiles

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        // Click delete button and confirm
        composeTestRule.onAllNodesWithContentDescription("Delete")[0].performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        
        verify { mockViewModel.deleteFile(sampleFiles[0].fileName) }
    }

    @Test
    fun dataManagementScreen_deleteConfirmationDialogCancel() {
        filesFlow.value = sampleFiles

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        // Click delete button and cancel
        composeTestRule.onAllNodesWithContentDescription("Delete")[0].performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        
        // Dialog should disappear
        composeTestRule.onNodeWithText("Delete File").assertDoesNotExist()
        
        // Delete should not be called
        verify(exactly = 0) { mockViewModel.deleteFile(any()) }
    }

    @Test
    fun dataManagementScreen_displaysLoadingState() {
        uiStateFlow.value = DataManagementUiState(isLoading = true)

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNode(hasTestTag("loading") or hasContentDescription("Loading")).assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_displaysErrorMessage() {
        uiStateFlow.value = DataManagementUiState(error = "Failed to load files")

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load files").assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_displaysSuccessMessage() {
        uiStateFlow.value = DataManagementUiState(successMessage = "File deleted successfully")

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        composeTestRule.onNodeWithText("File deleted successfully").assertIsDisplayed()
    }

    @Test
    fun dataManagementScreen_fileSelectionHighlight() {
        filesFlow.value = sampleFiles
        selectedFileFlow.value = sampleFiles[0]

        composeTestRule.setContent {
            ETongueTheme {
                DataManagementScreen(
                    viewModel = mockViewModel,
                    onNavigateBack = {},
                    onNavigateToAnalysis = {}
                )
            }
        }

        // The selected file should be visually highlighted
        // This is a visual test that would need to check styling/colors
        // For now, we just verify the file is displayed
        composeTestRule.onNodeWithText("sensor_data_20240101_120000.json").assertIsDisplayed()
    }
}