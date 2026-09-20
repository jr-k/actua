package com.azimulkabir.actua.ui.categories

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.CategoryReorderPlanner
import com.azimulkabir.actua.data.budget.GroupDragReorder
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.dragReorderHandle
import kotlinx.coroutines.launch
import com.azimulkabir.actua.R

private const val ROW_HEIGHT_DP = 56
private const val EDGE_SCROLL_ZONE_PX = 100f
private const val EDGE_SCROLL_SPEED_PX = 14f

/** Dedicated group-only ordering screen: category editing lives in Manage Categories instead. */
@Composable
fun ReorderGroupsScreen(
    groups: List<ActualCategoryGroup>,
    onBack: () -> Unit,
    onMoveGroup: (CategoryReorderPlanner.GroupMove) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val orderKey = remember(groups) { groups.joinToString("|") { it.id } }
    var localGroups by remember { mutableStateOf(groups) }
    var draggingGroupId by remember { mutableStateOf<String?>(null) }
    var dragStartGroups by remember { mutableStateOf(groups) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    LaunchedEffect(orderKey) {
        if (draggingGroupId == null) localGroups = groups
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

    fun autoScrollDirection(groupId: String): Int {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == "group:$groupId" } ?: return 0
        val top = item.offset + dragOffsetPx
        val bottom = top + item.size
        return when {
            top < EDGE_SCROLL_ZONE_PX -> -1
            bottom > info.viewportEndOffset - EDGE_SCROLL_ZONE_PX -> 1
            else -> 0
        }
    }

    fun onDragStart(groupId: String) {
        draggingGroupId = groupId
        dragStartGroups = localGroups
        dragOffsetPx = 0f
    }

    fun onDrag(groupId: String, deltaY: Float) {
        dragOffsetPx += deltaY
        while (dragOffsetPx > rowHeightPx / 2) {
            val stepped = CategoryReorderPlanner.moveGroupDown(localGroups, groupId)?.first
            if (stepped != null) { localGroups = stepped; dragOffsetPx -= rowHeightPx } else { dragOffsetPx = rowHeightPx / 2; break }
        }
        while (dragOffsetPx < -rowHeightPx / 2) {
            val stepped = CategoryReorderPlanner.moveGroupUp(localGroups, groupId)?.first
            if (stepped != null) { localGroups = stepped; dragOffsetPx += rowHeightPx } else { dragOffsetPx = -rowHeightPx / 2; break }
        }
        val direction = autoScrollDirection(groupId)
        if (direction != 0) scope.launch { listState.scrollBy(direction * EDGE_SCROLL_SPEED_PX) }
    }

    fun onDragEnd(groupId: String) {
        if (GroupDragReorder.hasMoved(dragStartGroups, localGroups, groupId)) {
            val move = GroupDragReorder.finalMove(localGroups, groupId)
            if (move != null && !onMoveGroup(move)) localGroups = dragStartGroups
        }
        draggingGroupId = null
        dragOffsetPx = 0f
    }

    fun stepByButton(groupId: String, direction: Int) {
        val stepped = if (direction < 0) CategoryReorderPlanner.moveGroupUp(localGroups, groupId)?.first
            else CategoryReorderPlanner.moveGroupDown(localGroups, groupId)?.first
        if (stepped == null) return
        val move = GroupDragReorder.finalMove(stepped, groupId) ?: return
        if (onMoveGroup(move)) localGroups = stepped
    }

    val reorderable = remember(localGroups) { localGroups.filterNot { it.isIncome } }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.categories_reorder_groups), onBack = onBack)
        Text(
            stringResource(R.string.categories_reorder_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(localGroups.size, key = { index -> "group:${localGroups[index].id}" }) { index ->
                val group = localGroups[index]
                val isDragging = draggingGroupId == group.id
                val reorderIndex = reorderable.indexOfFirst { it.id == group.id }
                GroupReorderRow(
                    group = group,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT_DP.dp)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                        .alpha(if (isDragging) 0.85f else 1f),
                    onDragStart = { onDragStart(group.id) },
                    onDrag = { deltaY -> onDrag(group.id, deltaY) },
                    onDragEnd = { onDragEnd(group.id) },
                    onMoveUp = { stepByButton(group.id, -1) },
                    onMoveDown = { stepByButton(group.id, +1) },
                    canMoveUp = reorderIndex > 0,
                    canMoveDown = reorderIndex in 0 until reorderable.size - 1,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun GroupReorderRow(
    group: ActualCategoryGroup,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    val displayName = group.name.ifBlank { stringResource(R.string.common_unknown) }
    val moveUpDescription = stringResource(R.string.categories_move_named_group_up, displayName)
    val moveDownDescription = stringResource(R.string.categories_move_named_group_down, displayName)
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!group.isIncome) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = stringResource(R.string.categories_drag_group, displayName),
                modifier = Modifier
                    .size(44.dp)
                    .padding(8.dp)
                    .dragReorderHandle(
                        key = group.id,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                    ),
            )
        } else {
            Box(Modifier.size(44.dp))
        }
        Text(
            displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (group.hidden) Icon(Icons.Outlined.VisibilityOff, stringResource(R.string.categories_hidden_group), modifier = Modifier.size(18.dp).padding(end = 8.dp))
        if (!group.isIncome) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.semantics { contentDescription = moveUpDescription },
            ) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.semantics { contentDescription = moveDownDescription },
            ) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
        }
    }
}
