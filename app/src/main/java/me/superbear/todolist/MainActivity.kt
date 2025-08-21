package me.superbear.todolist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
                var manualMode by remember { mutableStateOf(false) }
                var manualTitle by remember { mutableStateOf("") }
                var manualDesc by remember { mutableStateOf("") }


                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            if (!manualMode) {
                                ChatInputBar(modifier = Modifier.imePadding())
                            }
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
                    if (!manualMode) {
                        ManualAddFab(
                            onClick = { manualMode = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 96.dp) // sits above ChatInputBar
                        )
                    }

                    if (manualMode) {
                        Scrim(onDismiss = { manualMode = false }, modifier = Modifier.fillMaxSize())
                        ManualAddCard(
                            title = manualTitle,
                            onTitleChange = { manualTitle = it },
                            description = manualDesc,
                            onDescriptionChange = { manualDesc = it },
                            onSend = {
                                // UI-only: clear and close
                                manualTitle = ""
                                manualDesc = ""
                                manualMode = false
                            },
                            onCancel = { manualMode = false },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    BackHandler(enabled = manualMode) { manualMode = false }
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

@Composable
fun ManualAddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = "Add Task") },
        text = { Text("Add") }
    )
}

@Composable
fun ManualAddCard(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Manual Add Card (TEST)")
                TextField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = { Text("What would you like to do?") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(8.dp))
                TextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Event, contentDescription = "Due date")
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Flag, contentDescription = "Priority")
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Folder, contentDescription = "Project")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(onClick = onSend, enabled = title.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
fun Scrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize()
        ) {}
    }
}
