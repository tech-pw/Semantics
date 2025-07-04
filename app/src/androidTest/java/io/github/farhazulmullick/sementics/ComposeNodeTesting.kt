package io.github.farhazulmullick.sementics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.techpw.sementics.App
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ComposeNodeTesting {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        composeTestRule.setContent {
            App() // Your composable with Modifier.testTag("myTag")
        }
    }

    @Test
    fun test1() {
        composeTestRule.onNodeWithText("hello world 2").assertIsDisplayed()
    }

    @Test
    fun text2() {
        composeTestRule.onNodeWithTag(testTag = "auto_login_MainActivity_Column_PWCText_1").assertIsDisplayed()
    }

    @Test
    fun test3() {
        composeTestRule.onNodeWithTag(testTag = "auto_login_MainActivity_Column_Text_1").assertIsDisplayed()
    }

    @Test
    fun test4() {
        composeTestRule.onNodeWithTag(testTag = "auto_login_MainActivity_Column_MyText_1").assertIsDisplayed()
    }

    @Test
    fun test5() {
        composeTestRule.onNodeWithTag(testTag = "auto_login_MainActivity_Column_PWCButton_1").assertIsDisplayed()
    }

}