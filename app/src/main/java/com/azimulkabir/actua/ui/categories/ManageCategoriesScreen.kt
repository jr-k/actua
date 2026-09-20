package com.azimulkabir.actua.ui.categories

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.CategoryDragReorder
import com.azimulkabir.actua.data.budget.CategoryReorderPlanner
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.MoveCategoryDialog
import com.azimulkabir.actua.ui.components.NewCategoryDialog
import com.azimulkabir.actua.ui.components.RenameDialog
import com.azimulkabir.actua.ui.components.dragReorderHandle
import kotlinx.coroutines.launch
import com.azimulkabir.actua.R

private const val ROW_HEIGHT_DP = 56
private const val EDGE_SCROLL_ZONE_PX = 100f
private const val EDGE_SCROLL_SPEED_PX = 14f

private sealed interface ManageRow {
    data class GroupHeader(val group: ActualCategoryGroup) : ManageRow
    data class CategoryItem(val groupId: String, val category: ActualCategory) : ManageRow
}

private fun flatten(groups: List<ActualCategoryGroup>): List<ManageRow> = groups.flatMap { group ->
    listOf(ManageRow.GroupHeader(group)) + group.categories.map { ManageRow.CategoryItem(group.id, it) }
}

@Composable
fun ManageCategoriesScreen(
    groups: List<ActualCategoryGroup>,
    onBack: () -> Unit,
    onReorderGroupsClick: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onSetGroupHidden: (String, Boolean) -> Boolean,
    onCreateCategory: (String, String) -> Unit,
    onRenameCategory: (String, String, String) -> Unit,
    onSetCategoryHidden: (String, String, Boolean) -> Boolean,
    onDeleteCategory: (String, String) -> Boolean,
    onMoveCategory: (CategoryReorderPlanner.CategoryMove) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val orderKey = remember(groups) { groups.joinToString("|") { g -> g.id + ":" + g.categories.joinToString(",") { it.id } } }
    var localGroups by remember { mutableStateOf(groups) }
    var draggingCategoryId by remember { mutableStateOf<String?>(null) }
    var dragStartGroups by remember { mutableStateOf(groups) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    LaunchedEffect(orderKey) {
        if (draggingCategoryId == null) localGroups = groups
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

    var renamingGroup by remember { mutableStateOf<ActualCategoryGroup?>(null) }
    var renamingCategory by remember { mutableStateOf<Pair<ActualCategoryGroup, ActualCategory>?>(null) }
    var addingCategoryTo by remember { mutableStateOf<ActualCategoryGroup?>(null) }
    var addingGroup by remember { mutableStateOf(false) }
    var movingCategory by remember { mutableStateOf<Pair<ActualCategoryGroup, ActualCategory>?>(null) }
    var deletingCategory by remember { mutableStateOf<Pair<ActualCategoryGroup, ActualCategory>?>(null) }

    val rows = remember(localGroups) { flatten(localGroups) }

    fun onDragStart(categoryId: String) {
        draggingCategoryId = categoryId
        dragStartGroups = localGroups
        dragOffsetPx = 0f
    }

    fun autoScrollDirection(categoryId: String): Int {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == "category:$categoryId" } ?: return 0
        val top = item.offset + dragOffsetPx
        val bottom = top + item.size
        return when {
            top < EDGE_SCROLL_ZONE_PX -> -1
            bottom > info.viewportEndOffset - EDGE_SCROLL_ZONE_PX -> 1
            else -> 0
        }
    }

    fun onDrag(categoryId: String, deltaY: Float) {
        dragOffsetPx += deltaY
        while (dragOffsetPx > rowHeightPx / 2) {
            val stepped = CategoryDragReorder.step(localGroups, categoryId, +1)
            if (stepped != null) { localGroups = stepped; dragOffsetPx -= rowHeightPx } else { dragOffsetPx = rowHeightPx / 2; break }
        }
        while (dragOffsetPx < -rowHeightPx / 2) {
            val stepped = CategoryDragReorder.step(localGroups, categoryId, -1)
            if (stepped != null) { localGroups = stepped; dragOffsetPx += rowHeightPx } else { dragOffsetPx = -rowHeightPx / 2; break }
        }
        val direction = autoScrollDirection(categoryId)
        if (direction != 0) scope.launch { listState.scrollBy(direction * EDGE_SCROLL_SPEED_PX) }
    }

    fun onDragEnd(categoryId: String) {
        if (CategoryDragReorder.hasMoved(dragStartGroups, localGroups, categoryId)) {
            val move = CategoryDragReorder.finalMove(localGroups, categoryId)
            if (move != null && !onMoveCategory(move)) localGroups = dragStartGroups
        }
        draggingCategoryId = null
        dragOffsetPx = 0f
    }

    fun stepByButton(categoryId: String, direction: Int) {
        val stepped = CategoryDragReorder.step(localGroups, categoryId, direction) ?: return
        val move = CategoryDragReorder.finalMove(stepped, categoryId) ?: return
        if (onMoveCategory(move)) localGroups = stepped
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.budget_manage_categories), onBack = onBack) {
            IconButton(onClick = onReorderGroupsClick) { Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.categories_reorder_groups)) }
            IconButton(onClick = { addingGroup = true }) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.categories_add_group)) }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(rows.size, key = { index ->
                when (val row = rows[index]) {
                    is ManageRow.GroupHeader -> "group:${row.group.id}"
                    is ManageRow.CategoryItem -> "category:${row.category.id}"
                }
            }) { index ->
                when (val row = rows[index]) {
                    is ManageRow.GroupHeader -> GroupManageRow(
                        group = row.group,
                        onRename = { renamingGroup = row.group },
                        onAddCategory = { addingCategoryTo = row.group },
                        onToggleHidden = { onSetGroupHidden(row.group.name, !row.group.hidden) },
                    )
                    is ManageRow.CategoryItem -> {
                        val group = localGroups.first { it.id == row.groupId }
                        val category = row.category
                        val isDragging = draggingCategoryId == category.id
                        CategoryManageRow(
                            category = category,
                            isFirstInGroup = group.categories.firstOrNull()?.id == category.id,
                            isLastInGroup = group.categories.lastOrNull()?.id == category.id,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT_DP.dp)
                                .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                                .alpha(if (isDragging) 0.85f else 1f),
                            onDragStart = { onDragStart(category.id) },
                            onDrag = { deltaY -> onDrag(category.id, deltaY) },
                            onDragEnd = { onDragEnd(category.id) },
                            onMoveUp = { stepByButton(category.id, -1) },
                            onMoveDown = { stepByButton(category.id, +1) },
                            onRename = { renamingCategory = group to category },
                            onMoveToGroup = { movingCategory = group to category },
                            onToggleHidden = { onSetCategoryHidden(group.name, category.name, !category.hidden) },
                            onDelete = { deletingCategory = group to category },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    renamingGroup?.let { group ->
        RenameDialog(
            title = stringResource(R.string.budget_rename_group),
            currentName = group.name,
            onDismiss = { renamingGroup = null },
            onSave = { name -> onRenameGroup(group.name, name); renamingGroup = null },
        )
    }
    renamingCategory?.let { (group, category) ->
        RenameDialog(
            title = stringResource(R.string.budget_rename_category),
            currentName = category.name,
            onDismiss = { renamingCategory = null },
            onSave = { name -> onRenameCategory(group.name, category.name, name); renamingCategory = null },
        )
    }
    addingCategoryTo?.let { group ->
        NewCategoryDialog(
            groups = listOf(group.name),
            onDismiss = { addingCategoryTo = null },
            onSave = { groupName, name -> onCreateCategory(groupName, name); addingCategoryTo = null },
        )
    }
    if (addingGroup) {
        RenameDialog(
            title = stringResource(R.string.categories_new_group),
            currentName = "",
            onDismiss = { addingGroup = false },
            onSave = { name -> onCreateGroup(name); addingGroup = false },
        )
    }
    movingCategory?.let { (group, category) ->
        MoveCategoryDialog(
            categoryName = category.name,
            groups = localGroups.filterNot { it.isIncome }.map { it.name },
            currentGroup = group.name,
            onDismiss = { movingCategory = null },
            onMove = { targetGroupName ->
                val targetGroup = localGroups.first { it.name == targetGroupName }
                onMoveCategory(CategoryReorderPlanner.CategoryMove(category.id, targetGroup.id, null))
                movingCategory = null
            },
        )
    }
    deletingCategory?.let { (group, category) ->
        val categoryName = category.name.ifBlank { stringResource(R.string.common_unknown) }
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text(stringResource(R.string.categories_delete_question)) },
            text = { Text(stringResource(R.string.categories_delete_warning, categoryName)) },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text(stringResource(R.string.common_cancel)) } },
            confirmButton = {
                TextButton(onClick = { onDeleteCategory(group.name, category.name); deletingCategory = null }) { Text(stringResource(R.string.common_delete)) }
            },
        )
    }
}

@Composable
private fun GroupManageRow(
    group: ActualCategoryGroup,
    onRename: () -> Unit,
    onAddCategory: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayName = group.name.ifBlank { stringResource(R.string.common_unknown) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().height(ROW_HEIGHT_DP.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (group.hidden) Icon(Icons.Outlined.VisibilityOff, stringResource(R.string.categories_hidden_group), modifier = Modifier.size(18.dp).padding(end = 8.dp))
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.categories_group_options, displayName)) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.common_rename)) }, onClick = { menuExpanded = false; onRename() })
                    if (!group.isIncome) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.categories_add_category)) }, onClick = { menuExpanded = false; onAddCategory() })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (group.hidden) R.string.budget_unhide_group else R.string.budget_hide_group)) },
                            onClick = { menuExpanded = false; onToggleHidden() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryManageRow(
    category: ActualCategory,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onMoveToGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayName = category.name.ifBlank { stringResource(R.string.common_unknown) }
    Row(modifier.padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = stringResource(R.string.categories_drag_category, displayName),
            modifier = Modifier
                .size(44.dp)
                .padding(8.dp)
                .dragReorderHandle(
                    key = category.id,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                ),
        )
        Text(
            displayName,
            style = MaterialTheme.typography.bodyLarge,
            overflow = TextOverflow.Ellipsis,
            color = if (category.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (category.hidden) Icon(Icons.Outlined.VisibilityOff, stringResource(R.string.categories_hidden_category), modifier = Modifier.size(16.dp).padding(end = 8.dp))
        Box {
            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.categories_category_options, displayName)) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.common_rename)) }, onClick = { menuExpanded = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.categories_move_group)) }, onClick = { menuExpanded = false; onMoveToGroup() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.categories_move_up)) },
                    enabled = !isFirstInGroup,
                    onClick = { menuExpanded = false; onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.categories_move_down)) },
                    enabled = !isLastInGroup,
                    onClick = { menuExpanded = false; onMoveDown() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (category.hidden) R.string.categories_unhide else R.string.categories_hide)) },
                    leadingIcon = { Icon(if (category.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null) },
                    onClick = { menuExpanded = false; onToggleHidden() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}
