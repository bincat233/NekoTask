package me.superbear.todolist.ui.main.sections.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.superbear.todolist.domain.entities.Task

// Row model to flatten headers and parent cards at the same LazyColumn level
// Header = section title (e.g., "Unfinished"/"Finished")
// Parent = a single parent task card row
private sealed class RowItem {
    data class Header(val title: String) : RowItem()
    data class Parent(val task: Task) : RowItem()
}

/**
 * Task section composable - displays task list with parent/child relationships
 * This is the entry point used by MainScreen to render the task list area.
 */
@Composable
fun TaskSection(
    state: TaskState,
    onToggleTask: (Task) -> Unit,
    onAddSubtask: (parentId: Long, title: String) -> Unit,
    onToggleSubtask: (childId: Long, done: Boolean) -> Unit,
    getChildren: (parentId: Long) -> List<Task>,
    getParentProgress: (parentId: Long) -> Pair<Int, Int>,
    modifier: Modifier = Modifier
) {
    // Build a flat list of header and parent rows to feed the LazyColumn
    val rows = remember(state.items) {
        buildList<RowItem> {
            add(RowItem.Header("Unfinished"))
            state.openParents.forEach { add(RowItem.Parent(it)) }
            add(RowItem.Header("Finished"))
            state.doneParents.forEach { add(RowItem.Parent(it)) }
        }
    }

    // LazyColumn = the scrollable list container for the whole task section
    LazyColumn(modifier = modifier) {
        items(
            items = rows,
            key = { item ->
                when (item) {
                    is RowItem.Header -> "header_${item.title}"
                    is RowItem.Parent -> item.task.id
                }
            }
        ) { item ->
            when (item) {
                // Header row (section title)
                is RowItem.Header -> {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                // Parent task card row
                is RowItem.Parent -> {
                    val parent = item.task
                    ParentTaskCard(
                        task = parent,
                        onToggleParent = { onToggleTask(parent) },
                        onAddSubtask = onAddSubtask,
                        onToggleSubtask = onToggleSubtask,
                        getChildren = getChildren,
                        getParentProgress = getParentProgress
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentTaskCard(
    task: Task,
    onToggleParent: () -> Unit,
    onAddSubtask: (parentId: Long, title: String) -> Unit,
    onToggleSubtask: (childId: Long, done: Boolean) -> Unit,
    getChildren: (parentId: Long) -> List<Task>,
    getParentProgress: (parentId: Long) -> Pair<Int, Int>
) {
    // Local UI state: expansion of children and add-subtask dialog visibility
    var expanded by remember(task.id) { mutableStateOf(false) }
    var showAddDialog by remember(task.id) { mutableStateOf(false) }
    val children = remember(task.id) { getChildren(task.id) }
    val (doneCount, totalCount) = remember(task.id) { getParentProgress(task.id) }

    // Auto-expand when first subtask created
    LaunchedEffect(totalCount) {
        if (totalCount == 1) expanded = true
    }

    // Column = the card body (header row + expandable subtasks area)
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row of the parent task card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.status == "DONE",
                onCheckedChange = { onToggleParent() }
            )
            // Title + progress badge + expand/collapse button
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (task.status == "DONE") Color.Gray else Color.Unspecified,
                        textDecoration = if (task.status == "DONE") TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                    // Progress badge X/Y
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "$doneCount/$totalCount",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }
                // Secondary line: due-today indicator (optional)
                val dueToday = remember(task.dueAt) { isDueToday(task) }
                if (dueToday) {
                    Text(
                        text = "Due today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Expandable subtasks area (children list + add button)
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                children.forEach { child ->
                    // One subtask row: checkbox + title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = child.status == "DONE",
                            onCheckedChange = { checked -> onToggleSubtask(child.id, checked) }
                        )
                        Text(
                            text = child.title,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (child.status == "DONE") Color.Gray else Color.Unspecified,
                            textDecoration = if (child.status == "DONE") TextDecoration.LineThrough else null
                        )
                    }
                }

                // Add-subtask action
                TextButton(onClick = { showAddDialog = true }, modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = "+ Subtask")
                }
            }
        }
    }

    // Dialog for creating a new subtask under this parent task
    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        onAddSubtask(task.id, title)
                        title = ""
                        showAddDialog = false
                        expanded = true
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
            title = { Text("New subtask") },
            text = {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    placeholder = { Text("Subtask title") }
                )
            }
        )
    }
}

private fun isDueToday(task: Task): Boolean {
    val due = task.dueAt ?: return false
    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dueDate = due.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return dueDate == today
}
