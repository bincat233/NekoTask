package me.superbear.todolist

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.superbear.todolist.assistant.FakeChatAgent
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.ui.main.MainScreen
import me.superbear.todolist.ui.main.MainViewModel
import me.superbear.todolist.ui.theme.TodolistTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Demonstrates the ChatAgent seam: MainViewModel takes its AI backend as a constructor
 * dependency, so UI tests can inject a deterministic FakeChatAgent instead of calling a
 * real LLM.
 */
@RunWith(AndroidJUnit4::class)
class ChatWithFakeAgentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sendingAMessage_showsTheFakeAgentsReply() {
        val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val todoRepository = TodoRepository(application)
        val longTermMemoryRepository =
            LongTermMemoryRepository(todoRepository.database.longTermMemoryDao())
        val fakeAgent = FakeChatAgent(reply = "Fake agent reply")
        val viewModel = MainViewModel(application, todoRepository, longTermMemoryRepository, fakeAgent)

        composeTestRule.setContent {
            TodolistTheme {
                MainScreen(viewModel = viewModel)
            }
        }

        val placeholder = composeTestRule.activity.getString(R.string.ai_input_placeholder)
        val sendDescription = composeTestRule.activity.getString(R.string.send_message)

        composeTestRule.onNodeWithText(placeholder).performTextInput("Remind me to buy milk")
        composeTestRule.onNodeWithContentDescription(sendDescription).performClick()

        composeTestRule.onNodeWithText("Fake agent reply").assertExists()
        assertEquals("Remind me to buy milk", fakeAgent.lastUserMessage)
    }
}
