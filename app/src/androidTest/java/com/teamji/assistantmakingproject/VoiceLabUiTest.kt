package com.teamji.assistantmakingproject

import android.Manifest
import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

class VoiceLabUiTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun readingControlsKeepCustomTextAndStopRemainsReachable() {
        compose.onNodeWithTag("readMode").assertIsSelected()
        compose.onNodeWithTag("english").performScrollTo().performClick()
        compose.onNodeWithTag("male").assertIsSelected()
        compose.onNodeWithTag("female").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("speed1.2").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("text").performScrollTo().performTextReplacement("Hello. This text must remain when the language changes.")
        compose.onNodeWithTag("urdu").performScrollTo().performClick()
        compose.onNodeWithTag("text").performScrollTo().assertTextContains("Hello. This text must remain when the language changes.")
        compose.onNodeWithTag("english").performScrollTo().performClick()
        compose.onNodeWithTag("text").performScrollTo().performTextReplacement("This is a continuous reading test. ".repeat(30))
        compose.onNodeWithTag("speak").assertIsDisplayed().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("stop").fetchSemanticsNodes().any {
                it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
            }
        }
        compose.onNodeWithTag("stop").assertIsDisplayed().performClick()
        compose.onNodeWithTag("speak").assertIsEnabled()
        compose.onNodeWithTag("text").performScrollTo().performTextReplacement("Hello. Reading works after Stop.")
        compose.onNodeWithTag("speak").performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag("speak").fetchSemanticsNodes().any {
                it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
            }
        }
        compose.onNodeWithTag("readMode").performScrollTo()
        screenshot("01-reading")
    }

    @Test fun recordFinishesIntoAPlayableSampleWithoutDoubleStart() {
        compose.onNodeWithTag("cloneMode").performClick()
        compose.onNodeWithTag("record").performScrollTo().performClick()
        compose.onNodeWithTag("import").assertIsNotEnabled()
        compose.onNodeWithTag("readMode").assertIsNotEnabled()
        compose.onNodeWithTag("stop").assertIsDisplayed().assertIsEnabled()
        Thread.sleep(1200)
        compose.onNodeWithTag("stop").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("record").fetchSemanticsNodes().any {
                it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
            }
        }
        compose.onNodeWithText("اپنی ریکارڈنگ سنیں").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("record").fetchSemanticsNodes().any {
                it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
            }
        }
        compose.onNodeWithTag("cloneMode").performScrollTo()
        screenshot("02-recording")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/voice-lab-review")
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        checkNotNull(context.contentResolver.openOutputStream(uri)).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
