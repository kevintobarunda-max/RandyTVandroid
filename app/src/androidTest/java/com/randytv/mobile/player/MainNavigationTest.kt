package com.randytv.mobile.player

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.randytv.mobile.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun searchSurvivesActivityRecreationAndBackClosesSearchInsteadOfGoingHome() {
        compose.onNodeWithText("Peliculas").performClick()
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithTag("catalog_search").performTextInput("anillos")
        compose.onNodeWithTag("catalog_search").performImeAction()
        compose.onNodeWithText("PELÍCULAS").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("PELÍCULAS").assertIsDisplayed()
        compose.onNodeWithTag("catalog_search").assertTextContains("anillos")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("catalog_search").assertDoesNotExist()
        compose.onNodeWithText("PELÍCULAS").assertIsDisplayed()
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithTag("catalog_search").performTextInput("otra")
        compose.onNodeWithTag("catalog_search").performImeAction()
        compose.onNodeWithText("PELÍCULAS").assertIsDisplayed()
    }
}
