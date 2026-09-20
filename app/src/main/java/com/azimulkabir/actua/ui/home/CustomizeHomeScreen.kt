package com.azimulkabir.actua.ui.home

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
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeLayoutPlanner
import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.dragReorderHandle
import kotlinx.coroutines.delay
import com.azimulkabir.actua.R

private const val ROW_HEIGHT_DP = 64
private const val EDGE_SCROLL_ZONE_PX = 100f
private const val EDGE_SCROLL_SPEED_PX = 14f
private const val EDGE_SCROLL_POLL_DELAY_MS = 16L

/**
 * Show/hide and reorder the optional Home sections. Ready to Budget is pinned first and always
 * visible (see [HomeLayoutPlanner]), so it renders without a drag handle or visibility switch.
 *
 * Drag mutates [localOrder] in memory only; [onLayoutChange] fires once per committed change
 * (drag end, a switch flip, or Restore Defaults) rather than on every row crossed during a drag.
 */
@Composable
fun CustomizeHomeScreen(
    layout: HomeLayout,
    onBack: () -> Unit,
    onLayoutChange: (HomeLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sanitized = remember(layout) { HomeLayoutPlanner.sanitize(layout) }
    var localOrder by remember(sanitized) { mutableStateOf(sanitized.order) }
    var localHidden by remember(sanitized) { mutableStateOf(sanitized.hidden) }
    var draggingSection by remember { mutableStateOf<HomeSection?>(null) }
    var dragStartOrder by remember { mutableStateOf(sanitized.order) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    LaunchedEffect(sanitized) {
        if (draggingSection == null) {
            localOrder = sanitized.order
            localHidden = sanitized.hidden
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

    fun autoScrollDirection(section: HomeSection): Int {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == section.name } ?: return 0
        val top = item.offset + dragOffsetPx
        val bottom = top + item.size
        return when {
            top < EDGE_SCROLL_ZONE_PX -> -1
            bottom > info.viewportEndOffset - EDGE_SCROLL_ZONE_PX -> 1
            else -> 0
        }
    }

    fun checkSwaps(section: HomeSection) {
        while (dragOffsetPx > rowHeightPx / 2) {
            val stepped = HomeLayoutPlanner.moveSectionDown(localOrder, section)
            if (stepped != null) { localOrder = stepped; dragOffsetPx -= rowHeightPx } else { dragOffsetPx = rowHeightPx / 2; break }
        }
        while (dragOffsetPx < -rowHeightPx / 2) {
            val stepped = HomeLayoutPlanner.moveSectionUp(localOrder, section)
            if (stepped != null) { localOrder = stepped; dragOffsetPx += rowHeightPx } else { dragOffsetPx = -rowHeightPx / 2; break }
        }
    }

    fun onDragStart(section: HomeSection) {
        draggingSection = section
        dragStartOrder = localOrder
        dragOffsetPx = 0f
    }

    fun onDrag(section: HomeSection, deltaY: Float) {
        dragOffsetPx += deltaY
        checkSwaps(section)
    }

    fun onDragEnd(section: HomeSection) {
        if (HomeLayoutPlanner.hasMoved(dragStartOrder, localOrder, section)) {
            onLayoutChange(HomeLayout(localOrder, localHidden))
        }
        draggingSection = null
        dragOffsetPx = 0f
    }

    // A single continuous loop (rather than a scrollBy launched per drag delta, which would flood
    // the dispatcher with concurrent coroutines) so the list keeps scrolling and the dragged row
    // keeps swapping with its new neighbors even while the finger holds still at the edge. Feeding
    // the actual scrolled amount back into dragOffsetPx keeps the row glued to the finger instead
    // of drifting as the list's item offsets shift underneath it.
    LaunchedEffect(draggingSection) {
        val section = draggingSection ?: return@LaunchedEffect
        while (true) {
            val direction = autoScrollDirection(section)
            if (direction != 0) {
                val scrolled = listState.scrollBy(direction * EDGE_SCROLL_SPEED_PX)
                dragOffsetPx += scrolled
                checkSwaps(section)
            }
            delay(EDGE_SCROLL_POLL_DELAY_MS)
        }
    }

    fun stepByButton(section: HomeSection, direction: Int) {
        val stepped = if (direction < 0) HomeLayoutPlanner.moveSectionUp(localOrder, section)
            else HomeLayoutPlanner.moveSectionDown(localOrder, section)
        if (stepped == null) return
        localOrder = stepped
        onLayoutChange(HomeLayout(stepped, localHidden))
    }

    fun setHidden(section: HomeSection, hidden: Boolean) {
        localHidden = if (hidden) localHidden + section else localHidden - section
        onLayoutChange(HomeLayout(localOrder, localHidden))
    }

    val reorderable = remember(localOrder) { localOrder.filterNot { it == HomeSection.READY_TO_BUDGET } }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.home_customize), onBack = onBack) {
            IconButton(onClick = {
                val defaults = HomeLayout.default()
                localOrder = defaults.order
                localHidden = defaults.hidden
                onLayoutChange(defaults)
            }) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.home_restore_layout))
            }
        }
        Text(
            stringResource(R.string.home_customize_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(localOrder, key = { it.name }) { section ->
                val isDragging = draggingSection == section
                val reorderIndex = reorderable.indexOf(section)
                HomeSectionRow(
                    section = section,
                    hidden = section in localHidden,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT_DP.dp)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                        .alpha(if (isDragging) 0.85f else 1f),
                    onDragStart = { onDragStart(section) },
                    onDrag = { deltaY -> onDrag(section, deltaY) },
                    onDragEnd = { onDragEnd(section) },
                    onMoveUp = { stepByButton(section, -1) },
                    onMoveDown = { stepByButton(section, +1) },
                    onHiddenChange = { hidden -> setHidden(section, hidden) },
                    canMoveUp = reorderIndex > 0,
                    canMoveDown = reorderIndex in 0 until reorderable.size - 1,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    val pinned = section == HomeSection.READY_TO_BUDGET
    val title = stringResource(section.titleRes)
    val moveUpDescription = stringResource(R.string.home_move_up, title)
    val moveDownDescription = stringResource(R.string.home_move_down, title)
    val showDescription = stringResource(R.string.home_show_section, title)
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!pinned) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = stringResource(R.string.home_drag_reorder, title),
                modifier = Modifier
                    .size(44.dp)
                    .padding(8.dp)
                    .dragReorderHandle(
                        key = section,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                    ),
            )
        } else {
            Box(Modifier.size(44.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (!pinned) {
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
            Switch(
                checked = !hidden,
                onCheckedChange = { checked -> onHiddenChange(!checked) },
                modifier = Modifier.semantics { contentDescription = showDescription },
            )
        } else {
            Text(stringResource(R.string.home_required), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
