package hr.raspored.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class RasporedSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun glavneNavigacijeOtvarajuProdukcijskeEkrane() {
        composeRule.onNodeWithTag("screen-calendar").fetchSemanticsNode()
        composeRule.onNodeWithTag("calendar-set-d").performScrollTo().performClick()

        composeRule.onNodeWithTag("nav-home").performClick()
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()

        composeRule.onNodeWithTag("nav-calendar").performClick()
        composeRule.onNodeWithTag("screen-calendar").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Sljedeći mjesec").performClick()
        composeRule.onNodeWithContentDescription("Prethodni mjesec").performClick()
        composeRule.onNodeWithTag("calendar-month-picker").performClick()
        composeRule.onNodeWithText("Odaberi mjesec").fetchSemanticsNode()
        composeRule.onNodeWithText("Zatvori").performClick()

        composeRule.onNodeWithTag("nav-scan").performClick()
        composeRule.onNodeWithTag("screen-scan").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Pomoć za skeniranje").performClick()
        composeRule.onNodeWithText("Kako dobiti dobar rezultat").fetchSemanticsNode()
        composeRule.onNodeWithText("U redu").performClick()
        composeRule.onNodeWithContentDescription("Natrag").performClick()

        composeRule.onNodeWithTag("nav-stats").performClick()
        composeRule.onNodeWithTag("screen-stats").fetchSemanticsNode()
        composeRule.onNodeWithText("Izračunaj okvirnu plaću ›").performClick()
        composeRule.onNodeWithTag("screen-payroll").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Natrag").performClick()
        composeRule.onNodeWithTag("screen-stats").fetchSemanticsNode()

        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("screen-settings").fetchSemanticsNode()
    }
}
