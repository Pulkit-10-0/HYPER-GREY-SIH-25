package com.example.etongue.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.etongue.ui.theme.ETongueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun analysisScreen_displaysCorrectTitle_whenFileNameProvided() {
        // Given
        val fileName = "test_data.json"
        var navigateBackCalled = false
        
        // When
        composeTestRule.setContent {
            ETongueTheme {
                AnalysisScreen(
                    fileName = fileName,
                    onNavigateBack = { navigateBackCalled = true }
                )
            }
        }
        
        // Then
        composeTestRule
            .onNodeWithText("Analysis: $fileName")
            .assertIsDisplayed()
    }
    
    @Test
    fun analysisScreen_displaysGenericTitle_whenNoFileName() {
        // Given
        var navigateBackCalled = false
        
        // When
        composeTestRule.setContent {
            ETongueTheme {
                AnalysisScreen(
                    fileName = null,
                    onNavigateBack = { navigateBackCalled = true }
                )
            }
        }
        
        // Then
        composeTestRule
            .onNodeWithText("Data Analysis")
            .assertIsDisplayed()
    }
    
    @Test
    fun analysisScreen_hasBackButton() {
        // Given
        var navigateBackCalled = false
        
        // When
        composeTestRule.setContent {
            ETongueTheme {
                AnalysisScreen(
                    fileName = null,
                    onNavigateBack = { navigateBackCalled = true }
                )
            }
        }
        
        // Then
        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
    
    @Test
    fun analysisScreen_backButton_triggersNavigation() {
        // Given
        var navigateBackCalled = false
        
        composeTestRule.setContent {
            ETongueTheme {
                AnalysisScreen(
                    fileName = null,
                    onNavigateBack = { navigateBackCalled = true }
                )
            }
        }
        
        // When
        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()
        
        // Then
        assert(navigateBackCalled)
    }
    
    @Test
    fun analysisScreen_hasRefreshButton() {
        // Given
        var navigateBackCalled = false
        
        // When
        composeTestRule.setContent {
            ETongueTheme {
                AnalysisScreen(
                    fileName = null,
                    onNavigateBack = { navigateBackCalled = true }
                )
            }
        }
        
        // Then
        composeTestRule
            .onNodeWithContentDescription("Refresh")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
    
    @Test
    fun analysisScreen_showsLoadingIndicator_initially() {
        // Given
        var navigateBackCalled = false
        
        // When
        composeTestRule.setContent {
            ETongueTheme {
                AnalysisScreen(
                    fileName = "test.json",
                    onNavigateBack = { navigateBackCalled = true }
                )
            }
        }
        
        // Then
        composeTestRule
            .onNodeWithText("Loading data...")
            .assertIsDisplayed()
    }
    
    @Test
    fun analysisScreen_showsFileSelection_whenNoFileProvided() {
        // Given
        var navigateBackCalled = false
        
        // When
        composeTestRule.setContent {
            ETongueTheme {
                AnalysisScreen(
                    fileName = null,
                    onNavigateBack = { navigateBackCalled = true }
                )
            }
        }
        
        // Wait for loading to complete and check for file selection or empty state
        composeTestRule.waitForIdle()
        
        // Then - should show either file selection or empty state
        // This will depend on the actual data available, so we check for one of the expected states
        try {
            composeTestRule
                .onNodeWithText("Select a file to analyze:")
                .assertIsDisplayed()
        } catch (e: AssertionError) {
            // If no files available, should show empty state
            composeTestRule
                .onNodeWithText("No data files available")
                .assertIsDisplayed()
        }
    }
}