package me.superbear.todolist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
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

                var aiReply by remember { mutableStateOf("I can help with that! Just tell me what you want to do.") }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            ChatInputBar(modifier = Modifier.imePadding())
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        val unfinishedItems = allItems.filter { it.status == "OPEN" }
                        val finishedItems = allItems.filter { it.status == "DONE" }

                        val onItemToggleLambda: (Task) -> Unit = { toggledItem ->
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

                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        ) {
                            Text(
                                text = "Unfinished",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            CheckableList(
                                items = unfinishedItems,
                                onItemToggle = onItemToggleLambda
                            )
                            Text(
                                text = "Finished",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            CheckableList(
                                items = finishedItems,
                                onItemToggle = onItemToggleLambda
                            )
                        }
                    }
                    SpeechBubble(
                        text = aiReply,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 80.dp) // Position above the ChatInputBar
                            .imePadding()
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TodolistTheme {
        Greeting("Android")
    }
}

@Composable
fun CheckableList(
    items: List<Task>,
    onItemToggle: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items, key = { it.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemToggle(item) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.status == "DONE",
                    onCheckedChange = { onItemToggle(item) }
                )
                Text(
                    text = item.title,
                    modifier = Modifier.padding(start = 8.dp),
                    color = if (item.status == "DONE") Color.Gray else Color.Unspecified,
                    textDecoration = if (item.status == "DONE") TextDecoration.LineThrough else null
                )
            }
        }
    }
}
