package hr.raspored.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class RasporedSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun glavneNavigacijeOtvarajuReferentneEkrane() {
        composeRule.onNodeWithText("Dobar dan! 👋").assertExists()

        composeRule.onNodeWithText("Kalendar").performClick()
        composeRule.onNodeWithText("Sažetak za mjesec").assertExists()
        composeRule.onNodeWithContentDescription("Sljedeći mjesec").performClick()
        composeRule.onNodeWithContentDescription("Prethodni mjesec").performClick()

        composeRule.onNodeWithText("Skeniraj").performClick()
        composeRule.onNodeWithText("Skeniraj raspored").assertExists()
        composeRule.onNodeWithContentDescription("Pomoć za skeniranje").performClick()
        composeRule.onNodeWithText("Kako dobiti dobar rezultat").assertExists()
        composeRule.onNodeWithText("U redu").performClick()
        composeRule.onNodeWithContentDescription("Natrag").performClick()

        composeRule.onNodeWithText("Statistika").performClick()
        composeRule.onNodeWithText("Ukupno odrađeno sati").assertExists()

        composeRule.onNodeWithText("Postavke").performClick()
        composeRule.onNodeWithText("Izgled i pristupačnost").assertExists()
    }
}
