package me.superbear.todolist.data.model

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room Database class for the TodoList application.
 * 
 * This abstract class serves as the main database holder and provides
 * access to the DAOs (Data Access Objects) for database operations.
 * 
 * Database configuration:
 * - entities: [TaskEntity] - defines the tables in the database
 * - views: [UnfinishedTasksView] - SQLite VIEWs for common filtering operations
 * - version: 1 - database schema version for migrations
 * - exportSchema: false - disables schema export (no schema location configured)
 * 
 * Views included:
 * - unfinished_tasks: Pre-filtered view of tasks with OPEN status for performance
 * 
 * Usage:
 * - Extend RoomDatabase to leverage Room's code generation
 * - Provide abstract methods for each DAO
 * - Room generates the implementation automatically
 */
@Database(
    entities = [TaskEntity::class], 
    views = [UnfinishedTasksView::class],
    version = 1, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Provides access to task-related database operations.
     * 
     * @return TaskDao instance for performing CRUD operations on tasks
     */
    abstract fun taskDao(): TaskDao
}
