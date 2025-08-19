package me.superbear.todolist

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.io.InputStream
import java.nio.charset.StandardCharsets

// TodoItem data class is defined in its own file: TodoItem.kt

/**
 * Repository for handling TodoItem data operations.
 * This class abstracts the data source (currently a JSON file in assets)
 * from the rest of the application, like the UI (MainActivity).
 */
class TodoRepository(private val context: Context) {

    /**
     * Loads and parses TodoItems from a JSON file in the assets folder.
     * @param fileName The name of the JSON file in the assets folder (e.g., "todolist_items.json").
     * @return A list of TodoItem objects.
     * Returns an empty list if there's an error during file reading or parsing.
     */
    fun getTodoItems(fileName: String): List<TodoItem> {
        return try {
            // Open an InputStream to the asset file.
            val inputStream: InputStream = context.assets.open(fileName)
            // Get the size of the file.
            val size: Int = inputStream.available()
            // Create a ByteArray to hold the file data.
            val buffer = ByteArray(size)
            // Read the file data into the buffer.
            inputStream.read(buffer)
            // Close the InputStream to free up resources.
            inputStream.close()
            // Convert the ByteArray to a String using UTF-8 encoding.
            val jsonString = String(buffer, StandardCharsets.UTF_8)
            // Parse the JSON string into a JSONArray.
            val jsonArray = JSONArray(jsonString)
            // Map each JSONObject in the JSONArray to a TodoItem object.
            List(jsonArray.length()) { i ->
                val itemJson = jsonArray.getJSONObject(i)
                TodoItem(
                    id = itemJson.getString("id"),
                    text = itemJson.getString("text"),
                    isCompleted = itemJson.getBoolean("isCompleted"),
                    notes = itemJson.optString("notes").takeIf { it.isNotEmpty() }
                    // subItems parsing would go here if we decide to add them later.
                )
            }
        } catch (e: Exception) {
            // Log any errors that occur during file reading or JSON parsing.
            // In a production app, you might use a more sophisticated logging framework.
            Log.e("TodoRepository", "Error loading items from JSON: ${e.message}", e)
            e.printStackTrace() // Print stack trace for debugging.
            // Return an empty list to prevent the app from crashing.
            emptyList()
        }
    }

    /**
     * Simulates an API call to update a TodoItem on a server.
     * In a real application, this function would make an actual network request.
     * @param item The TodoItem to be updated.
     * @return Boolean indicating whether the simulated update was successful.
     */
    suspend fun updateTodoItemOnServer(item: TodoItem): Boolean {
        // Simulate network delay to mimic a real API call.
        delay(500) // 0.5 second delay

        // Log the simulated action.
        // In a real app, you would have network request code here (e.g., using Retrofit or Ktor).
        Log.d("TodoRepository", "Simulating update for item: ${item.id}, text: '${item.text}', isCompleted: ${item.isCompleted} on server.")

        // Example of how you might simulate a chance of failure:
        // return if (Math.random() > 0.1) { // 90% success rate
        //     Log.d("TodoRepository", "Update successful for item: ${item.id}")
        //     true
        // } else {
        //     Log.e("TodoRepository", "Simulated network error for item: ${item.id}")
        //     false
        // }

        // For now, always simulate success.
        return true
    }
}
