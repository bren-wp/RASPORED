package hr.raspored.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class RasporedSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun glavneNavigacijeOtvarajuReferentneEkrane() {
        composeRule.onNodeWithText("Četvrtak, 16.10.2026.").assertIsDisplayed()

        composeRule.onNodeWithText("Kalendar").performClick()
        composeRule.onNodeWithText("Sažetak za mjesec").assertIsDisplayed()

        composeRule.onNodeWithText("Skeniraj").performClick()
        composeRule.onNodeWithText("Skeniraj raspored").assertIsDisplayed()

        composeRule.onNodeWithText("Statistika").performClick()
        composeRule.onNodeWithText("Ukupno odrađeno sati").assertIsDisplayed()

        composeRule.onNodeWithText("Postavke").performClick()
        composeRule.onNodeWithText("Izgled i pristupačnost").assertIsDisplayed()
    }
}
