package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.schedules.BillCalendarItem
import com.azimulkabir.actua.data.schedules.BillFilter
import com.azimulkabir.actua.data.schedules.BillsCalendarEngine
import com.azimulkabir.actua.data.schedules.BillsTabMode
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.success
import java.time.Month
import java.time.format.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BillsCalendarScreen(
    loadItems: (Int, Int, Boolean) -> List<BillCalendarItem>,
    refreshKey: Int,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onAddSchedule: () -> Unit,
    onConfigureCards: () -> Unit,
    onEditSchedule: (String) -> Unit,
    onPost: (String, Boolean) -> Unit,
    onSkip: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { DayDate.today() }
    var month by remember { mutableStateOf(DayDate(today.year, today.month, 1)) }
    var mode by remember { mutableStateOf(BillsTabMode.RECURRING) }
    var filter by remember { mutableStateOf(BillFilter.ALL) }
    var selectedDate by remember { mutableStateOf<DayDate?>(null) }
    var actionItem by remember { mutableStateOf<BillCalendarItem?>(null) }
    var pendingDelete by remember { mutableStateOf<BillCalendarItem?>(null) }
    val loadedItems by produceState<List<BillCalendarItem>?>(null, month, mode, refreshKey) {
        value = null
        value = withContext(Dispatchers.IO) {
            loadItems(month.year, month.month, mode == BillsTabMode.CARD_BILLS)
        }
    }
    val items = loadedItems.orEmpty()
    val summary = BillsCalendarEngine.summarize(items)
    val visible = BillsCalendarEngine.filter(items, filter, selectedDate)

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_bills), onBack = onBack) {
            IconButton(onClick = if (mode == BillsTabMode.RECURRING) onAddSchedule else onConfigureCards) {
                Icon(Icons.Outlined.Add, stringResource(if (mode == BillsTabMode.RECURRING) R.string.fs_add_schedule else R.string.fs_configure_cards))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BillsTabMode.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = mode == tab,
                        onClick = { mode = tab; selectedDate = null },
                        shape = SegmentedButtonDefaults.itemShape(index, BillsTabMode.entries.size),
                    ) { Text(stringResource(if (tab == BillsTabMode.RECURRING) R.string.fs_recurring else R.string.fs_card_bills)) }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.addingMonths(-1); selectedDate = null }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.fs_previous_month))
                }
                Text(
                    "${Month.of(month.month).getDisplayName(TextStyle.FULL, locale)} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (summary.totalCount > 0) Text(
                    stringResource(R.string.fs_paid_count, summary.clearedCount, summary.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { month = month.addingMonths(1); selectedDate = null }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.fs_next_month))
                }
            }

            BillsCalendarGrid(
                year = month.year,
                month = month.month,
                today = today,
                selectedDate = selectedDate,
                items = items,
                onSelect = { selectedDate = if (selectedDate == it) null else it },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(stringResource(R.string.fs_status_upcoming), summary.upcomingCents, MaterialTheme.colorScheme.primary,
                    hideDecimalPlaces, Modifier.weight(1f))
                SummaryMetric(stringResource(R.string.fs_overdue), summary.overdueCents, MaterialTheme.colorScheme.error,
                    hideDecimalPlaces, Modifier.weight(1f))
                SummaryMetric(stringResource(R.string.fs_status_paid), summary.paidCents, MaterialTheme.colorScheme.success,
                    hideDecimalPlaces, Modifier.weight(1f))
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BillFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(when (option) {
                            BillFilter.ALL -> R.string.fs_filter_all
                            BillFilter.UPCOMING -> R.string.fs_filter_upcoming
                            BillFilter.OVERDUE -> R.string.fs_filter_overdue
                            BillFilter.PAID -> R.string.fs_filter_paid
                        })) },
                    )
                }
                selectedDate?.let { day ->
                    FilterChip(selected = true, onClick = { selectedDate = null }, label = { Text(stringResource(R.string.fs_day_filter, day.day)) })
                }
            }

            if (loadedItems == null) {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (visible.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.EventAvailable, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(if (mode == BillsTabMode.RECURRING) R.string.fs_no_schedules_due else R.string.fs_no_card_bills_due),
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(if (selectedDate != null) R.string.fs_nothing_scheduled_date
                            else R.string.fs_no_filter_matches),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else visible.forEach { item ->
                BillCard(
                    item = item,
                    today = today,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onClick = {
                        if (item.isCreditCard) onConfigureCards()
                        else item.scheduleId?.let(onEditSchedule)
                    },
                    onLongClick = { actionItem = item },
                )
            }
        }
    }

    actionItem?.let { item ->
        BillActionsSheet(
            item = item,
            onDismiss = { actionItem = null },
            onEdit = {
                actionItem = null
                if (item.isCreditCard) onConfigureCards() else item.scheduleId?.let(onEditSchedule)
            },
            onPost = { todayOnly -> actionItem = null; item.scheduleId?.let { onPost(it, todayOnly) } },
            onSkip = { actionItem = null; item.scheduleId?.let(onSkip) },
            onDelete = { actionItem = null; pendingDelete = item },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.fs_delete_schedule_short_question)) },
            text = { Text(stringResource(R.string.fs_delete_schedule_named, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    item.scheduleId?.let(onDelete)
                }) { Text(stringResource(R.string.fs_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.fs_cancel)) } },
        )
    }
}

@Composable
private fun BillsCalendarGrid(
    year: Int,
    month: Int,
    today: DayDate,
    selectedDate: DayDate?,
    items: List<BillCalendarItem>,
    onSelect: (DayDate) -> Unit,
) {
    val byDate = items.groupBy(BillCalendarItem::date)
    val cells = List<DayDate?>(BillsCalendarEngine.leadingEmptyDays(year, month)) { null } +
        BillsCalendarEngine.daysInMonth(year, month)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                stringResource(R.string.fs_weekdays_narrow).split(',').forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (week + List(7 - week.size) { null }).forEach { day ->
                        if (day == null) Spacer(Modifier.weight(1f).height(44.dp))
                        else CalendarDay(day, day == today, day == selectedDate, byDate[day].orEmpty(),
                            { onSelect(day) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarDay(
    day: DayDate,
    isToday: Boolean,
    isSelected: Boolean,
    items: List<BillCalendarItem>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.height(44.dp).combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(28.dp).background(
                when { isSelected -> MaterialTheme.colorScheme.primary; isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent }, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.day.toString(), style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            items.take(2).forEach { item -> Spacer(Modifier.size(5.dp).background(statusColor(item.status), CircleShape)) }
        }
    }
}

@Composable
private fun SummaryMetric(title: String, cents: Long, color: Color, hideDecimals: Boolean, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Spacer(Modifier.size(7.dp).background(color, CircleShape))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatMoneyCents(cents, hideDecimals), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BillCard(
    item: BillCalendarItem,
    today: DayDate,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (item.isCreditCard) Icons.Outlined.CreditCard else Icons.Outlined.Repeat, null,
                        tint = statusColor(item.status))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(formatMoneyCents(item.amountCents, hideDecimalPlaces),
                        color = if (item.amountCents > 0) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurface)
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(relativeDueText(item, today), style = MaterialTheme.typography.bodySmall,
                        color = statusColor(item.status))
                }
                Text(listOfNotNull(item.categoryName, item.accountName).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.date.day.toString().padStart(2, '0'), fontWeight = FontWeight.Bold)
                    Text(Month.of(item.date.month).getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillActionsSheet(
    item: BillCalendarItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onPost: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (item.scheduleId != null && item.isCurrentOccurrence && item.status != ScheduleStatus.COMPLETED) {
            ActionRow(Icons.Outlined.Add, stringResource(R.string.fs_post_transaction), onClick = { onPost(false) })
            ActionRow(Icons.Outlined.EventAvailable, stringResource(R.string.fs_post_transaction_today), onClick = { onPost(true) })
            if (item.isRecurring) ActionRow(Icons.Outlined.SkipNext, stringResource(R.string.fs_skip_next_date), onSkip)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        }
        ActionRow(Icons.Outlined.Edit, stringResource(if (item.isCreditCard) R.string.fs_configure_credit_cards else R.string.fs_edit_schedule_description), onEdit)
        if (item.scheduleId != null) {
            ActionRow(Icons.Outlined.DeleteOutline, stringResource(R.string.fs_delete_schedule), onDelete, MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = tint)
            Text(label, color = tint)
        }
    }
}

@Composable
private fun statusColor(status: ScheduleStatus): Color = when (status) {
    ScheduleStatus.MISSED -> MaterialTheme.colorScheme.error
    ScheduleStatus.PAID, ScheduleStatus.COMPLETED -> MaterialTheme.colorScheme.success
    ScheduleStatus.DUE -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun relativeDueText(item: BillCalendarItem, today: DayDate): String {
    if (item.status == ScheduleStatus.PAID) return stringResource(R.string.fs_status_paid)
    if (item.status == ScheduleStatus.COMPLETED) return stringResource(R.string.fs_status_completed)
    val days = today.daysUntil(item.date)
    return when {
        days < 0 -> pluralStringResource(R.plurals.fs_overdue_by_days, -days, -days)
        days == 0 -> stringResource(R.string.fs_due_today)
        days == 1 -> stringResource(R.string.fs_due_tomorrow)
        else -> pluralStringResource(R.plurals.fs_due_in_days, days, days)
    }
}
