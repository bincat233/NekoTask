package me.superbear.todolist.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.superbear.todolist.data.model.AppDatabase
import me.superbear.todolist.data.model.TaskEntity
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Manages seeding of the Room database from JSON assets.
 * 
 * Ensures that seeding only happens once and tracks completion
 * using SharedPreferences to avoid re-seeding on app restarts.
 */
class SeedManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "seed_prefs", 
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val SEED_DONE_KEY = "seed_done_v1"
        private const val JSON_ASSET_FILE = "todolist_items.json"
    }

    /**
     * JSON data class matching the asset file structure.
     */
    @Serializable
    private data class JsonTask(
        val id: Long,
        val title: String,
        val createdAtIso: String,
        val notes: String? = null,
        val status: String,
        val parentId: Long? = null
    )

    /**
     * Checks if the database needs seeding.
     * 
     * @param database AppDatabase instance
     * @return true if seeding is needed (database empty AND not previously seeded)
     */
    suspend fun needsSeeding(database: AppDatabase): Boolean {
        val seedDone = prefs.getBoolean(SEED_DONE_KEY, false)
        val taskCount = database.taskDao().getTaskCount()
        
        return !seedDone && taskCount == 0
    }

    /**
     * Resets the seeding flag to allow re-seeding.
     * Used when database is deleted via FORCE_DELETE_DB flag.
     */
    fun resetSeedingFlag() {
        prefs.edit().putBoolean(SEED_DONE_KEY, false).apply()
    }

    /**
     * Seeds the database from JSON assets.
     * 
     * Reads the JSON file, converts to TaskEntity objects, and inserts
     * them into the database in a single transaction.
     * 
     * @param database AppDatabase instance
     * @param context Android context for asset access
     */
    suspend fun seedFromAssets(database: AppDatabase, context: Context) {
        try {
            // Read and parse JSON from assets
            val jsonString = context.assets.open(JSON_ASSET_FILE).use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
            
            val jsonTasks = Json.decodeFromString<List<JsonTask>>(jsonString)
            
            // Convert to TaskEntity objects
            val taskEntities = jsonTasks.map { jsonTask ->
                TaskEntity(
                    id = jsonTask.id,
                    title = jsonTask.title,
                    content = jsonTask.notes,
                    status = when (jsonTask.status) {
                        "OPEN" -> TaskStatus.OPEN
                        "DONE" -> TaskStatus.DONE
                        else -> TaskStatus.OPEN // Default fallback
                    },
                    priority = Priority.DEFAULT, // JSON doesn't have priority, use default
                    createdAt = Instant.parse(jsonTask.createdAtIso).toEpochMilliseconds(),
                    updatedAt = null,
                    dueAt = null,
                    parentId = jsonTask.parentId,
                    orderInParent = 0 // JSON doesn't have order, use default
                )
            }
            
            // Seed the database in a transaction
            database.taskDao().seedTasks(taskEntities)
            
            // Mark seeding as complete
            prefs.edit().putBoolean(SEED_DONE_KEY, true).apply()
            
        } catch (e: Exception) {
            // Log error but don't crash the app
            android.util.Log.e("SeedManager", "Failed to seed database from assets", e)
            throw e
        }
    }

    /**
     * Resets the seeding flag for testing purposes.
     * Should only be used in debug/test scenarios.
     */
    fun resetSeedingFlagForTesting() {
        prefs.edit().remove(SEED_DONE_KEY).apply()
    }
}
