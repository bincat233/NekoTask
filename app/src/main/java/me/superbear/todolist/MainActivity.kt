package me.superbear.todolist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.launch
import me.superbear.todolist.ui.theme.TodolistTheme

// MainActivity is the main screen of the application.
// It inherits from ComponentActivity, which is a base class for activities that use Jetpack Compose.
class MainActivity : ComponentActivity() {
    // onCreate is the first method called when the Activity is created.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge allows the app to draw behind system bars for a more immersive experience.
        enableEdgeToEdge()
        // setContent is used to define the layout of the Acvtivity using Jetpack Compose.
        setContent {
            // MyApplicationTheme is a custom theme for the application.
            TodolistTheme {
                // LocalContext.current provides the current Android Context, needed for things like accessing assets.
                val context = LocalContext.current
                // 'remember' is a Composable function that stores a value in the Composition.
                // It's used here to create and remember an instance of TodoRepository.
                // The TodoRepository will be a singleton for this Composable's lifecycle unless context changes.
                val todoRepository = remember { TodoRepository(context) }

                // 'mutableStateOf' creates an observable state holder.
                // 'remember' ensures this state survives recompositions.
                // 'allItems' will hold the list of to-do items.
                // 'by' delegate allows direct get/set on allItems as if it were a regular var.
                var allItems by remember { mutableStateOf(emptyList<Task>()) }

                // 'rememberCoroutineScope' gets a CoroutineScope tied to this Composable's lifecycle.
                // This scope is used to launch coroutines for asynchronous operations.
                val coroutineScope = rememberCoroutineScope()

                // 'LaunchedEffect' is a Composable function that runs a side effect (like fetching data)
                // when the Composable enters the Composition or when its keys change.
                // 'key1 = Unit' means this effect runs once when the Composable is first displayed.
                LaunchedEffect(key1 = Unit) {
                    // Fetch the to-do items from the repository. This is a suspend function call.
                    allItems = todoRepository.getTasks("todolist_items.json")
                }

                // Scaffold provides a standard layout structure (app bars, floating action buttons, etc.).
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Filter the list of all items into unfinished and finished items.
                    // This is done based on the 'isCompleted' property of each TodoItem.
                    val unfinishedItems = allItems.filter { it.status == "OPEN" }
                    val finishedItems = allItems.filter { it.status == "DONE" }

                    // Define a lambda function to handle toggling the completion status of a TodoItem.
                    // Explicitly define the type as (TodoItem) -> Unit to match CheckableList's expectation.
                    val onItemToggleLambda: (Task) -> Unit = { toggledItem ->
                        // Create a new TodoItem with the 'isCompleted' status flipped.
                        // It's important to create a new object for state updates to be properly detected by Compose.
                        val updatedItem = toggledItem.copy(status = if (toggledItem.status == "DONE") "OPEN" else "DONE")

                        // Optimistic UI Update: Update the UI immediately for responsiveness.
                        // Create a new list with the updated item to trigger recomposition.
                        allItems = allItems.map {
                            if (it.id == updatedItem.id) updatedItem else it
                        }

                        // Launch a coroutine in the remembered scope to call the simulated API.
                        // This prevents blocking the UI thread during the (simulated) network operation.
                        coroutineScope.launch {
                            // Call the suspend function in the repository to simulate updating the server.
                            val success = todoRepository.updateTaskOnServer(updatedItem)
                            if (success) {
                                // Log success if the simulated server update was successful.
                                Log.d("MainActivity", "Item ${updatedItem.id} updated successfully on server.")
                                // Optionally, you could re-fetch data from the source here or handle success in other ways.
                            } else {
                                // Log failure if the simulated server update failed.
                                Log.e("MainActivity", "Failed to update item ${updatedItem.id} on server.")
                                // Revert UI change if server update fails.
                                // This makes the UI consistent with the (simulated) server state.
                                // We map through allItems again, and if we find the item that failed to update,
                                // we revert it to its original state (before the toggle).
                                allItems = allItems.map {
                                    if (it.id == toggledItem.id) toggledItem else it // Revert to original toggledItem
                                }
                            }
                        }
                    }

                    // Column arranges its children vertically.
                    Column(
                        modifier = Modifier
                            .padding(innerPadding) // Apply padding provided by Scaffold.
                            .fillMaxSize()       // Make the Column take up the full available size.
                    ) {
                        // Display a title for the "Unfinished" items list.
                        Text(
                            text = "Unfinished",
                            style = MaterialTheme.typography.titleMedium, // Use a predefined text style from the theme.
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp) // Add some padding.
                        )
                        // Display the list of unfinished items using the CheckableList composable.
                        CheckableList(
                            items = unfinishedItems,
                            onItemToggle = onItemToggleLambda // Pass the toggle handler.
                        )

                        // Display a title for the "Finished" items list.
                        Text(
                            text = "Finished",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        // Display the list of finished items.
                        CheckableList(
                            items = finishedItems,
                            onItemToggle = onItemToggleLambda // Pass the toggle handler.
                        )
                    }
                }
            }
        }
    }
}

// A simple Composable function to display a greeting.
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

// A Composable function annotated with @Preview to show a preview in Android Studio.
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TodolistTheme {
        Greeting("Android")
        // Example of how you might set up a preview for CheckableList with mock TodoItems:
        // To make this preview work, you'd need to define a mock list of TodoItem objects here.
        // For example:
        // val previewItems = listOf(
        //     Task(id = 1, title = "Preview Item 1", createdAtIso = "2024-01-01T10:00:00Z", status = "OPEN"),
        //     Task(id = 2, title = "Preview Item 2", createdAtIso = "2024-01-01T11:00:00Z", status = "DONE")
        // )
        // Column {
        //     Text("Unfinished Preview")
        //     CheckableList(items = previewItems.filter { !it.isCompleted }, onItemToggle = { /* Mock toggle */ })
        //     Text("Finished Preview")
        //     CheckableList(items = previewItems.filter { it.isCompleted }, onItemToggle = { /* Mock toggle */ })
        // }
    }
}

// A Composable function to display a list of to-do items with checkboxes.
@Composable
fun CheckableList(
    items: List<Task>,                      // The list of items to display.
    onItemToggle: (Task) -> Unit,         // A lambda function to call when an item is toggled.
    modifier: Modifier = Modifier             // An optional Modifier for customizing the layout.
) {
    // LazyColumn is a vertically scrolling list that only composes and lays out the currently visible items.
    // It's efficient for long lists.
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        // 'items' is a helper function for LazyColumn to display a list of items.
        // The 'key' parameter helps LazyColumn identify items uniquely, improving performance
        // and preserving state when items are reordered, added, or removed.
        items(items, key = { it.id }) { item ->
            // Row arranges its children horizontally.
            Row(
                modifier = Modifier
                    .fillMaxWidth() // Make the Row take up the full width.
                    .clickable { onItemToggle(item) } // Make the entire row clickable to toggle the item.
                    .padding(horizontal = 16.dp, vertical = 8.dp), // Add padding within the row.
                verticalAlignment = Alignment.CenterVertically // Align children vertically to the center.
            ) {
                // Checkbox to show the completion status.
                Checkbox(
                    checked = item.status == "DONE",                // The checked state of the checkbox.
                    onCheckedChange = { onItemToggle(item) } // Lambda called when the checkbox state changes.
                    // We could pass null here if the Row's clickable is enough,
                    // but having both is common for accessibility.
                )
                // Text to display the to-do item's text.
                Text(
                    text = item.title,
                    modifier = Modifier.padding(start = 8.dp), // Add padding to the left of the text.
                    // Change text color and decoration based on whether the item is completed.
                    color = if (item.status == "DONE") Color.Gray else Color.Unspecified,
                    textDecoration = if (item.status == "DONE") TextDecoration.LineThrough else null
                )
            }
        }
    }
}
