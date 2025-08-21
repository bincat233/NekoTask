package me.superbear.todolist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

                var uiState by remember { mutableStateOf(UiState()) }

                LaunchedEffect(allItems) {
                    uiState = uiState.copy(items = allItems)
                }

                val imeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
                LaunchedEffect(imeVisible) {
                    uiState = uiState.copy(imeVisible = imeVisible)
                }

                MainScreen(
                    state = uiState,
                    onEvent = { event ->
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
                            is UiEvent.OpenManual -> uiState = uiState.copy(manualMode = true)
                            is UiEvent.CloseManual -> uiState = uiState.copy(manualMode = false)
                            is UiEvent.ChangeTitle -> uiState = uiState.copy(manualTitle = event.value)
                            is UiEvent.ChangeDesc -> uiState = uiState.copy(manualDesc = event.value)
                            is UiEvent.SendManual -> {
                                // UI-only: clear and close
                                uiState = uiState.copy(
                                    manualTitle = "",
                                    manualDesc = "",
                                    manualMode = false
                                )
                            }
                            is UiEvent.FabMeasured -> uiState = uiState.copy(fabWidthDp = event.widthDp)
                        }
                    }
                )
            }
        }
    }
}
