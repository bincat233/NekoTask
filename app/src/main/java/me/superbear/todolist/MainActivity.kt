package me.superbear.todolist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import me.superbear.todolist.ui.main.MainScreen
import me.superbear.todolist.ui.main.UiEvent
import me.superbear.todolist.ui.main.UiState
import me.superbear.todolist.ui.theme.TodolistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TodolistTheme {
                val context = LocalContext.current
                val todoRepository = remember { TodoRepository(context) }
                var allItems by remember { mutableStateOf(emptyList<Task>()) }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(key1 = Unit) {
                    allItems = todoRepository.getTasks("todolist_items.json")
                }

                var bubbleStack by remember { mutableStateOf(listOf("I can help with that! Just tell me what you want to do.")) }
                var manualMode by remember { mutableStateOf(false) }
                var manualTitle by remember { mutableStateOf("") }
                var manualDesc by remember { mutableStateOf("") }

                val imeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
                var fabWidth by remember { mutableStateOf(0.dp) }

                val state = UiState(
                    items = allItems,
                    manualMode = manualMode,
                    manualTitle = manualTitle,
                    manualDesc = manualDesc,
                    bubbleStack = bubbleStack,
                    fabWidthDp = fabWidth,
                    imeVisible = imeVisible
                )

                val onEvent: (UiEvent) -> Unit = { event ->
                    when (event) {
                        is UiEvent.ToggleTask -> {
                            val toggledItem = event.task
                            val updatedItem = toggledItem.copy(status = if (toggledItem.status == "DONE") "OPEN" else "DONE")
                            allItems = allItems.map {
                                if (it.id == updatedItem.id) updatedItem else it
                            }
                            coroutineScope.launch {
                                val success = todoRepository.updateTaskOnServer(updatedItem)
                                if (success) {
                                    Log.d("MainActivity", "Item ${updatedItem.id} updated successfully on server.")
                                } else {
                                    Log.e("MainActivity", "Failed to update item ${updatedItem.id} on server.")
                                    allItems = allItems.map {
                                        if (it.id == toggledItem.id) toggledItem else it
                                    }
                                }
                            }
                        }
                        is UiEvent.OpenManual -> manualMode = true
                        is UiEvent.CloseManual -> manualMode = false
                        is UiEvent.ChangeTitle -> manualTitle = event.value
                        is UiEvent.ChangeDesc -> manualDesc = event.value
                        is UiEvent.SendManual -> {
                            manualTitle = ""
                            manualDesc = ""
                            manualMode = false
                        }
                        is UiEvent.FabMeasured -> fabWidth = event.widthDp
                    }
                }

                MainScreen(state = state, onEvent = onEvent)

                BackHandler(enabled = manualMode) { onEvent(UiEvent.CloseManual) }
            }
        }
    }
}
