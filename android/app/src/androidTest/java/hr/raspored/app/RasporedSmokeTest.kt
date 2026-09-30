package hr.raspored.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class RasporedSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun homeSePokreceBezRusenja() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dobar dan! 👋").fetchSemanticsNode()
        composeRule.onNodeWithText("Današnja smjena").fetchSemanticsNode()
    }

    @Test fun kalendarNavigacijaRadi() {
        composeRule.onNodeWithText("Kalendar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sažetak za mjesec").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Sljedeći mjesec").performClick()
        composeRule.onNodeWithContentDescription("Prethodni mjesec").performClick()
    }

    @Test fun skeniranjeIPomocRade() {
        composeRule.onNodeWithText("Skeniraj").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skeniraj raspored").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Pomoć za skeniranje").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Kako dobiti dobar rezultat").fetchSemanticsNode()
        composeRule.onNodeWithText("U redu").performClick()
        composeRule.onNodeWithContentDescription("Natrag").performClick()
    }

    @Test fun statistikaNavigacijaRadi() {
        composeRule.onNodeWithText("Statistika").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ukupno odrađeno sati").fetchSemanticsNode()
    }

    @Test fun postavkeNavigacijaRadi() {
        composeRule.onNodeWithText("Postavke").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Izgled i pristupačnost").fetchSemanticsNode()
    }
}
