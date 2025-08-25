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

    /**
     * Seeds the database with hardcoded sample data (bypassing JSON assets).
     * This is useful for quick local testing without maintaining asset files.
     */
    suspend fun seedWithSampleData(database: AppDatabase) {
        // Use a fixed base timestamp for deterministic ordering
        val base = Instant.parse("2024-01-01T00:00:00Z").toEpochMilliseconds()

        val dao = database.taskDao()

        // Root-level tasks
        val inboxId = dao.insert(
            TaskEntity(
                title = "Have Breakfast",
                content = "Egg and toast",
                status = TaskStatus.OPEN,
                priority = Priority.DEFAULT,
                createdAt = base + 0,
                updatedAt = null,
                dueAt = null,
                parentId = null,
                orderInParent = 0
            )
        )

        val readingId = dao.insert(
            TaskEntity(
                title = "Reading time",
                content = null,
                status = TaskStatus.OPEN,
                priority = Priority.DEFAULT,
                createdAt = base + 1,
                updatedAt = null,
                dueAt = null,
                parentId = null,
                orderInParent = 1
            )
        )

        // Children of "读书"
        val kotlinInActionId = dao.insert(
            TaskEntity(
                title = "Read Kotlin in Action",
                content = null,
                status = TaskStatus.OPEN,
                priority = Priority.DEFAULT,
                createdAt = base + 2,
                updatedAt = null,
                dueAt = base + 2 + 24 * 60 * 60 * 1000,
                parentId = readingId,
                orderInParent = 0
            )
        )

        dao.insert(
            TaskEntity(
                title = "Write a blog post",
                content = null,
                status = TaskStatus.OPEN,
                priority = Priority.DEFAULT,
                createdAt = base + 3,
                updatedAt = null,
                dueAt = null,
                parentId = readingId,
                orderInParent = 1
            )
        )

        dao.insert(
            TaskEntity(
                title = "Sleep",
                content = null,
                status = TaskStatus.DONE,
                priority = Priority.DEFAULT,
                createdAt = base - 24 * 60 * 60 * 1000,
                updatedAt = null,
                dueAt = null,
                parentId = null,
                orderInParent = 2
            )
        )


        prefs.edit().putBoolean(SEED_DONE_KEY, true).apply()
    }

    /**
     * Resets the seeding flag for testing purposes.
     * Should only be used in debug/test scenarios.
     */
    fun resetSeedingFlagForTesting() {
        prefs.edit().remove(SEED_DONE_KEY).apply()
    }
}
