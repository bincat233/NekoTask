package me.superbear.todolist.data.model

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY order_in_parent ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parent_id = :parentId ORDER BY order_in_parent ASC")
    fun observeChildren(parentId: Long?): Flow<List<TaskEntity>>

    @Query("SELECT MAX(order_in_parent) FROM tasks WHERE parent_id = :parentId")
    fun getMaxOrderInParent(parentId: Long?): Int?
}
