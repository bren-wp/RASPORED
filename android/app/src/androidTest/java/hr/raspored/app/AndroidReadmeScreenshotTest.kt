package hr.raspored.app

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

/**
 * Captures real API-emulator screenshots of the production Android UI for README/docs.
 * Data visible in these screenshots is generated only through test interactions.
 */
class AndroidReadmeScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("Android emulator screenshot is unavailable")
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "readme")
        check(root.exists() || root.mkdirs()) { "Cannot create README screenshot directory" }
        val target = File(root, "$name.png")
        FileOutputStream(target).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Cannot encode README screenshot $name"
            }
        }
        bitmap.recycle()
        check(target.isFile && target.length() > 0L) { "README screenshot $name is empty" }
    }

    @Test
    fun snimiStvarneEkraneAplikacije() {
        val month = YearMonth.now()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("screen-home").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()
        composeRule.onNodeWithTag("nav-calendar").performClick()
        composeRule.onNodeWithTag("screen-calendar").fetchSemanticsNode()
        composeRule.onNodeWithTag("calendar-day-" + month.atDay(1)).performClick()
        composeRule.onNodeWithTag("calendar-dialog-code-d").performClick()
        composeRule.onNodeWithTag("calendar-day-" + month.atDay(2)).performClick()
        composeRule.onNodeWithTag("calendar-dialog-code-n").performClick()
        composeRule.onNodeWithTag("calendar-day-" + month.atDay(3)).performClick()
        composeRule.onNodeWithTag("calendar-dialog-code-go").performClick()
        capture("android-calendar")

        composeRule.onNodeWithTag("nav-home").performClick()
        composeRule.onNodeWithTag("screen-home").performScrollToNode(hasTestTag("home-hours"))
        composeRule.onNodeWithTag("home-hours").performClick()
        composeRule.onNodeWithTag("screen-hours").fetchSemanticsNode()
        capture("android-evidence")
        composeRule.onNodeWithText("‹ Natrag").performClick()

        composeRule.onNodeWithTag("nav-scan").performClick()
        composeRule.onNodeWithTag("screen-scan").fetchSemanticsNode()
        composeRule.onNodeWithText("Uvezi raspored").fetchSemanticsNode()
        capture("android-scan")
        composeRule.onNodeWithContentDescription("Natrag").performClick()
        composeRule.onNodeWithTag("screen-home").fetchSemanticsNode()

        composeRule.onNodeWithTag("nav-stats").performClick()
        composeRule.onNodeWithTag("screen-stats").fetchSemanticsNode()
        capture("android-statistics")

        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("screen-settings").fetchSemanticsNode()
        capture("android-more")
    }
}
