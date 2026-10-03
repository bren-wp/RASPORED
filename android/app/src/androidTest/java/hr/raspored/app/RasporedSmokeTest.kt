package hr.raspored.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class RasporedSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun glavneNavigacijeOtvarajuProdukcijskeEkrane() {
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()

        composeRule.onNodeWithTag("nav-calendar").performClick()
        composeRule.onNodeWithTag("screen-calendar").fetchSemanticsNode()
        val currentMonth=YearMonth.now()
        composeRule.onNodeWithTag("calendar-day-"+currentMonth.atDay(1)).performClick()
        composeRule.onNodeWithTag("calendar-dialog-code-d").performClick()
        composeRule.onNodeWithTag("calendar-day-"+currentMonth.atDay(2)).performClick()
        composeRule.onNodeWithTag("calendar-dialog-code-n").performClick()
        // Make the neutral-state assertion deterministic even when another instrumented
        // test has left persisted schedule data for this date.
        composeRule.onNodeWithTag("calendar-day-"+currentMonth.atDay(3)).performClick()
        composeRule.onNodeWithTag("calendar-dialog-code-d").performClick()
        composeRule.onNodeWithTag("calendar-day-"+currentMonth.atDay(3)).performClick()
        composeRule.onNodeWithText("Očisti dan").performClick()
        composeRule.onNodeWithTag("calendar-day-"+currentMonth.atDay(3)).performClick()
        composeRule.onNodeWithTag("calendar-dialog-current-state").assertTextEquals("Nije označeno")
        composeRule.onNodeWithText("Zatvori").performClick()

        composeRule.onNodeWithTag("nav-home").performClick()
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()
        composeRule.onNodeWithTag("screen-home").performScrollToNode(hasTestTag("home-hours"))
        composeRule.onNodeWithTag("home-hours").performClick()
        composeRule.onNodeWithTag("screen-hours").fetchSemanticsNode()
        composeRule.onNodeWithText("Mjesečni fond").fetchSemanticsNode()
        composeRule.onNodeWithText("Iznad fonda").fetchSemanticsNode()
        composeRule.onNodeWithText("Obračunski sati").fetchSemanticsNode()
        composeRule.onNodeWithText("‹ Natrag").performClick()
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()

        composeRule.onNodeWithTag("nav-calendar").performClick()
        composeRule.onNodeWithTag("screen-calendar").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Sljedeći mjesec").performClick()
        composeRule.onNodeWithContentDescription("Prethodni mjesec").performClick()
        composeRule.onNodeWithTag("calendar-month-picker").performClick()
        composeRule.onNodeWithText("Odaberi mjesec").fetchSemanticsNode()
        composeRule.onNodeWithText("Danas").performClick()

        composeRule.onNodeWithTag("nav-scan").performClick()
        composeRule.onNodeWithTag("screen-scan").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Pomoć za skeniranje").performClick()
        composeRule.onNodeWithText("Kako dobiti dobar rezultat").fetchSemanticsNode()
        composeRule.onNodeWithText("Prazna kućica ostaje „Nije označeno”", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("U redu").performClick()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()

        composeRule.onNodeWithTag("nav-stats").performClick()
        composeRule.onNodeWithTag("screen-stats").fetchSemanticsNode()
        composeRule.onNodeWithTag("stats-payroll-preview").performClick()
        composeRule.onNodeWithTag("screen-payroll").fetchSemanticsNode()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("screen-stats").fetchSemanticsNode()

        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("screen-settings").fetchSemanticsNode()
        composeRule.onNodeWithTag("screen-settings").performScrollToNode(hasTestTag("settings-about"))
        composeRule.onNodeWithText("Verzija " + BuildConfig.VERSION_NAME).fetchSemanticsNode()
        composeRule.onNodeWithText("Izradio Brendigo").fetchSemanticsNode()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()
    }
}
