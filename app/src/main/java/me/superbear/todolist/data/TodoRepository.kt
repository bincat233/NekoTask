package me.superbear.todolist.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import org.json.JSONArray
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Repository for handling Task data operations.
 * This class manages task state in memory with reactive StateFlow,
 * initially loading from JSON assets and supporting real-time updates.
 */
class TodoRepository(private val context: Context) {

    // Private mutable state for tasks
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    // Public read-only state for UI observation
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    init {
        // Load initial data from JSON on repository creation
        loadInitialData()
    }

    /**
     * Load initial task data from JSON assets into memory state
     */
    private fun loadInitialData() {
        _tasks.value = getTasksFromJson("todolist_items.json")
    }

    /**
     * Loads and parses Tasks from a JSON file in the assets folder.
     * This is now a private helper function used only for initial data loading.
     * @param fileName The name of the JSON file in the assets folder (e.g., "todolist_items.json").
     * @return A list of Task objects.
     * Returns an empty list if there's an error during file reading or parsing.
     */
    private fun getTasksFromJson(fileName: String): List<Task> {
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
                    status = when (itemJson.getString("status")) {
                        "DONE" -> TaskStatus.DONE
                        "OPEN" -> TaskStatus.OPEN
                        else -> TaskStatus.OPEN // 默认为 OPEN
                    }
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

    /**
     * Get all child tasks for a given parent task ID
     */
    fun getChildren(parentId: Long): List<Task> {
        return _tasks.value.filter { it.parentId == parentId }
    }

    /**
     * Get progress information (done count, total count) for a parent task
     */
    fun getParentProgress(parentId: Long): Pair<Int, Int> {
        val children = getChildren(parentId)
        val doneCount = children.count { it.status == TaskStatus.DONE }
        return Pair(doneCount, children.size)
    }

    /**
     * Add a new task (parent or child) to the task list
     * @param title The title of the new task
     * @param parentId Optional parent ID for creating subtasks
     */
    fun addTask(title: String, parentId: Long? = null) {
        val currentTasks = _tasks.value
        val newId = (currentTasks.maxOfOrNull { it.id } ?: 0) + 1
        val now = Clock.System.now()
        val newTask = Task(
            id = newId,
            title = title,
            status = TaskStatus.OPEN,
            parentId = parentId,
            dueAt = null,
            createdAt = now,
            updatedAt = now
        )
        // Update the StateFlow with new task list
        _tasks.value = currentTasks + newTask
    }

    /**
     * Toggle the completion status of a task
     * @param id The ID of the task to toggle
     * @param done Whether the task should be marked as done
     */
    fun toggleTaskStatus(id: Long, done: Boolean) {
        val currentTasks = _tasks.value.toMutableList()
        val taskIndex = currentTasks.indexOfFirst { it.id == id }
        
        if (taskIndex != -1) {
            val updatedTask = currentTasks[taskIndex].copy(
                status = if (done) TaskStatus.DONE else TaskStatus.OPEN,
                updatedAt = Clock.System.now()
            )
            currentTasks[taskIndex] = updatedTask
            _tasks.value = currentTasks
        }
    }
}
