package me.superbear.todolist.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.superbear.todolist.domain.entities.TaskStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TaskStatusCascadeTest {

    private var repository: TodoRepository? = null

    @Before
    fun setUp() {
        runBlocking {
            val context = RepositoryTestContext(InstrumentationRegistry.getInstrumentation().targetContext)
            context.deleteDatabase(DATABASE_NAME)
            repository = TodoRepository(context)
            repository.require().database.taskDao().deleteAll()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            repository?.database?.taskDao()?.deleteAll()
        }
    }

    @Test
    fun completingParentCompletesDescendants_butReopeningDoesNotReopenThem() {
        runBlocking {
            val repository = repository.require()
            val parentId = repository.addTask(title = "Parent").getOrThrow()
            val childId = repository.addTask(title = "Child", parentId = parentId).getOrThrow()
            val grandchildId = repository.addTask(title = "Grandchild", parentId = childId).getOrThrow()
            val siblingId = repository.addTask(title = "Sibling", parentId = parentId).getOrThrow()

            assertTrue(repository.toggleTaskStatus(parentId, done = true).getOrThrow())

            assertEquals(TaskStatus.DONE, repository.getTaskById(parentId)?.status)
            assertEquals(TaskStatus.DONE, repository.getTaskById(childId)?.status)
            assertEquals(TaskStatus.DONE, repository.getTaskById(grandchildId)?.status)
            assertEquals(TaskStatus.DONE, repository.getTaskById(siblingId)?.status)

            assertTrue(repository.toggleTaskStatus(parentId, done = false).getOrThrow())

            assertEquals(TaskStatus.OPEN, repository.getTaskById(parentId)?.status)
            assertEquals(TaskStatus.DONE, repository.getTaskById(childId)?.status)
            assertEquals(TaskStatus.DONE, repository.getTaskById(grandchildId)?.status)
            assertEquals(TaskStatus.DONE, repository.getTaskById(siblingId)?.status)
        }
    }

    private fun TodoRepository?.require(): TodoRepository {
        return requireNotNull(this) { "Repository was not initialized" }
    }

    private class RepositoryTestContext(base: Context) : ContextWrapper(base) {

        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File {
            val databaseDir = File(cacheDir, "task-status-cascade-databases")
            databaseDir.mkdirs()
            return File(databaseDir, name)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            val databasePath = getDatabasePath(name)
            databasePath.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(databasePath, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            val databasePath = getDatabasePath(name)
            databasePath.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(databasePath.path, factory, errorHandler)
        }

        override fun deleteDatabase(name: String): Boolean {
            val databasePath = getDatabasePath(name)
            val deletedMain = databasePath.delete()
            val deletedWal = File("${databasePath.path}-wal").delete()
            val deletedShm = File("${databasePath.path}-shm").delete()
            return deletedMain || deletedWal || deletedShm
        }
    }

    private companion object {
        const val DATABASE_NAME = "todolist_database"
    }
}
