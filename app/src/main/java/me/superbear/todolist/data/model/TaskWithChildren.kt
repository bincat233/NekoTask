package me.superbear.todolist.data.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room POJO for fetching a parent task with all its children in a single query.
 * 
 * Uses Room's @Embedded and @Relation annotations to automatically handle
 * the parent-child relationship mapping. This is more efficient than separate
 * queries for parent and children.
 * 
 * @property parent The parent task entity (embedded)
 * @property children List of child tasks related to the parent
 */
data class TaskWithChildren(
    @Embedded 
    val parent: TaskEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "parent_id"
    )
    val children: List<TaskEntity>
) {
    /**
     * Counts the number of completed children.
     * 
     * @return Number of children with DONE status
     */
    fun getDoneChildrenCount(): Int {
        return children.count { it.status == me.superbear.todolist.domain.entities.TaskStatus.DONE }
    }
    
    /**
     * Gets the total number of children.
     * 
     * @return Total children count
     */
    fun getTotalChildrenCount(): Int {
        return children.size
    }
    
    /**
     * Gets the progress as a pair (done, total).
     * 
     * @return Pair of (completed children, total children)
     */
    fun getProgress(): Pair<Int, Int> {
        return getDoneChildrenCount() to getTotalChildrenCount()
    }
    
    /**
     * Checks if all children are completed.
     * 
     * @return true if all children are DONE, false otherwise
     */
    fun areAllChildrenDone(): Boolean {
        return children.isNotEmpty() && children.all { 
            it.status == me.superbear.todolist.domain.entities.TaskStatus.DONE 
        }
    }
    
    /**
     * Gets children ordered by their order_in_parent, then created_at.
     * 
     * @return Children sorted by hierarchical order
     */
    fun getOrderedChildren(): List<TaskEntity> {
        return children.sortedWith(
            compareBy<TaskEntity> { it.orderInParent }.thenBy { it.createdAt }
        )
    }
}
