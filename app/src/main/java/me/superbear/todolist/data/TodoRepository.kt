package me.superbear.todolist.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import me.superbear.todolist.domain.entities.Task
import org.json.JSONArray
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Repository for handling Task data operations.
 * This class abstracts the data source (currently a JSON file in assets)
 * from the rest of the application, like the UI (MainActivity).
 */
class TodoRepository(private val context: Context) {

    /**
     * Loads and parses Tasks from a JSON file in the assets folder.
     * @param fileName The name of the JSON file in the assets folder (e.g., "todolist_items.json").
     * @return A list of Task objects.
     * Returns an empty list if there's an error during file reading or parsing.
     */
    fun getTasks(fileName: String): List<Task> {
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
            // Map each JSONObject in the JSONArray to a Task object.
            List(jsonArray.length()) { i ->
                val itemJson = jsonArray.getJSONObject(i)
                Task(
                    id = itemJson.getLong("id"),
                    title = itemJson.getString("title"),
                    createdAt = Instant.parse(itemJson.getString("createdAtIso")),
                    notes = itemJson.optString("notes").takeIf { it.isNotEmpty() },
                    status = itemJson.getString("status")
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
     * Simulates an API call to update a Task on a server.
     * In a real application, this function would make an actual network request.
     * @param item The Task to be updated.
     * @return Boolean indicating whether the simulated update was successful.
     */
    suspend fun updateTaskOnServer(item: Task): Boolean {
        // Simulate network delay to mimic a real API call.
        delay(500) // 0.5 second delay

        // Log the simulated action.
        // In a real app, you would have network request code here (e.g., using Retrofit or Ktor).
        Log.d("TodoRepository", "Simulating update for item: ${item.id}, title: '${item.title}', status: ${item.status} on server.")

        // For now, always simulate success.
        return true
    }
}
