package com.azimulkabir.actua.ui.automation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.BudgetAutomationDocument
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetScheduleFunding
import com.azimulkabir.actua.model.BudgetTarget
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Dedicated full-page Budget Automation editor (issue #265).
 *
 * Structure matches upstream Actual Budget's mobile editor exactly: an "Automations" list
 * (the 7 contribution types from [contributionTypes]) plus a separate "Options" section
 * (Balance cap / Long-term goal - see [BudgetTarget.Type.isOption]), each with at most one
 * instance per category ([BudgetTarget.Type.isSingleton]). The underlying model/JSON
 * encoding lives in [BudgetTarget] and is unchanged by this screen.
 */
@Composable
fun BudgetAutomationScreen(
    group: BudgetGroup,
    category: BudgetCategory,
    month: String,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
    incomeCategories: List<String> = emptyList(),
    onBack: () -> Unit,
    onSave: (List<BudgetTarget>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (category.hasUnsupportedTarget) {
        UnsupportedAutomationNotice(category = category, onBack = onBack, modifier = modifier)
        return
    }

    var entries by remember(category) { mutableStateOf(category.automations) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var addingType by remember { mutableStateOf<BudgetTarget.Type?>(null) }

    if (editingIndex != null || addingType != null) {
        val index = editingIndex
        val existing = index?.let(entries::get)
        val usedSingletons = entries.withIndex()
            .filter { (i, e) -> i != index && e.type.isSingleton }
            .map { it.value.type }
            .toSet()
        AutomationEntryEditor(
            initial = existing,
            initialType = existing?.type ?: addingType!!,
            month = month,
            hideDecimalPlaces = hideDecimalPlaces,
            scheduleFunding = scheduleFunding,
            incomeCategories = incomeCategories,
            usedSingletonTypes = usedSingletons,
            onBack = { editingIndex = null; addingType = null },
            onDelete = if (existing != null) {
                {
                    entries = entries.toMutableList().also { it.removeAt(requireNotNull(index)) }
                    editingIndex = null; addingType = null
                }
            } else null,
            onSave = { target ->
                entries = when (index) {
                    null -> entries + target
                    else -> entries.toMutableList().also { it[index] = target }
                }
                editingIndex = null; addingType = null
            },
            modifier = modifier,
        )
        return
    }

    val contributionEntries = entries.withIndex().filterNot { it.value.type.isOption }
    val optionEntries = entries.withIndex().filter { it.value.type.isOption }
    val hasLimit = entries.any { it.type == BudgetTarget.Type.LIMIT }
    val hasGoal = entries.any { it.type == BudgetTarget.Type.GOAL }
    val errors = BudgetAutomationDocument.validate(entries)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.automation_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${group.name} · ${category.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(stringResource(R.string.automation_section))
            if (contributionEntries.isEmpty()) EmptyAutomationsNote()
            contributionEntries.forEach { (index, target) ->
                AutomationSummaryCard(target = target, hideDecimalPlaces = hideDecimalPlaces, onClick = { editingIndex = index })
            }
            AddButton(enabled = entries.size < 20, onClick = { addingType = BudgetTarget.Type.FIXED }) { Text(stringResource(R.string.automation_add)) }

            SectionHeader(stringResource(R.string.automation_options), modifier = Modifier.padding(top = 12.dp))
            optionEntries.forEach { (index, target) ->
                AutomationSummaryCard(target = target, hideDecimalPlaces = hideDecimalPlaces, onClick = { editingIndex = index })
            }
            if (!hasLimit) AddButton(onClick = { addingType = BudgetTarget.Type.LIMIT }) { Text(stringResource(R.string.automation_add_balance_cap)) }
            if (!hasGoal) AddButton(onClick = { addingType = BudgetTarget.Type.GOAL }) { Text(stringResource(R.string.automation_add_long_goal)) }

            errors.forEach { Text(localizedAutomationError(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = errors.isEmpty(),
                onClick = { onSave(entries); onBack() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (entries.isEmpty()) R.string.automation_save_remove else R.string.automation_save)) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EmptyAutomationsNote() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.automation_empty_title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.automation_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnsupportedAutomationNotice(category: BudgetCategory, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val types = if (category.unsupportedAutomationTypes.isEmpty()) {
        stringResource(if (category.automationReadOnly) R.string.automation_notes_managed_type else R.string.automation_advanced_type)
    } else {
        category.unsupportedAutomationTypes
            .map { resources.getString(unsupportedAutomationTypeLabelRes(it)) }
            .distinct()
            .joinToString()
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back)) }
            Text(category.name.ifBlank { stringResource(R.string.common_unknown) },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.automation_read_only), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.automation_read_only_body, types),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AutomationSummaryCard(target: BudgetTarget, hideDecimalPlaces: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(target.type.labelRes), fontWeight = FontWeight.SemiBold)
                Text(
                    automationSummary(target, hideDecimalPlaces),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (target.type.hasPriority) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        stringResource(R.string.automation_priority_badge, target.priority),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun automationSummary(target: BudgetTarget, hideDecimalPlaces: Boolean): String = when (target.type) {
    BudgetTarget.Type.FIXED -> {
        val period = periodLabel(target.period, target.everyCount)
        val cadence = if (target.everyCount > 1) stringResource(R.string.automation_period_every_count, target.everyCount, period)
            else stringResource(R.string.automation_period_every, period)
        "${formatMoneyCents(target.amountCents, hideDecimalPlaces)} $cadence"
    }
    BudgetTarget.Type.BY_DATE -> stringResource(R.string.automation_by_date, formatMoneyCents(target.amountCents, hideDecimalPlaces), target.targetMonth.orEmpty())
    BudgetTarget.Type.SCHEDULE -> (target.scheduleName ?: stringResource(R.string.automation_linked_schedule)) + adjustmentSuffix(target)
    BudgetTarget.Type.PERCENTAGE -> stringResource(
        R.string.automation_percent_summary,
        target.percentage,
        stringResource(if (target.percentagePrevious) R.string.automation_last_month else R.string.automation_this_month).lowercase(),
        percentageSourceLabel(target.percentageSource),
    )
    BudgetTarget.Type.HISTORICAL -> when (target.historicalMode) {
        BudgetTarget.HistoricalMode.AVERAGE -> {
            val average = if (target.historicalMonths == 1) {
                stringResource(R.string.automation_average_recent_singular)
            } else {
                pluralStringResource(R.plurals.automation_average_recent_plural, target.historicalMonths, target.historicalMonths)
            }
            average + adjustmentSuffix(target)
        }
        BudgetTarget.HistoricalMode.COPY -> if (target.historicalMonths == 1) {
            stringResource(R.string.automation_copy_ago_singular)
        } else {
            pluralStringResource(R.plurals.automation_copy_ago_plural, target.historicalMonths, target.historicalMonths)
        }
    }
    BudgetTarget.Type.REFILL -> stringResource(R.string.automation_refills_cap)
    BudgetTarget.Type.REMAINDER -> stringResource(R.string.automation_weight_summary, target.weight)
    BudgetTarget.Type.LIMIT -> {
        val cadence = limitPeriodLabel(target.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY)
        "${formatMoneyCents(target.amountCents, hideDecimalPlaces)} · $cadence" +
            " · " + stringResource(if (target.limitHold) R.string.automation_retain_excess else R.string.automation_release_excess)
    }
    BudgetTarget.Type.GOAL -> formatMoneyCents(target.amountCents, hideDecimalPlaces)
}

/** The 7 automation types offered by "+ Add an automation", in upstream `displayTemplateTypes` order. */
private val contributionTypes = listOf(
    BudgetTarget.Type.FIXED, BudgetTarget.Type.SCHEDULE, BudgetTarget.Type.BY_DATE,
    BudgetTarget.Type.PERCENTAGE, BudgetTarget.Type.HISTORICAL, BudgetTarget.Type.REFILL,
    BudgetTarget.Type.REMAINDER,
)

@Composable
private fun AutomationEntryEditor(
    initial: BudgetTarget?,
    initialType: BudgetTarget.Type,
    month: String,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding>,
    incomeCategories: List<String>,
    usedSingletonTypes: Set<BudgetTarget.Type>,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (initialType) {
        BudgetTarget.Type.LIMIT -> LimitEditor(initial, onBack, onDelete, onSave, modifier)
        BudgetTarget.Type.GOAL -> GoalEditor(initial, onBack, onDelete, onSave, modifier)
        else -> ContributionEditor(
            initial, initialType, month, hideDecimalPlaces, scheduleFunding, incomeCategories,
            usedSingletonTypes, onBack, onDelete, onSave, modifier,
        )
    }
}

@Composable
private fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back)) }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (onDelete != null) {
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.automation_remove_description), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.automation_remove_question)) },
            text = { Text(stringResource(R.string.automation_remove_body, title)) },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun NoteField(note: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = note, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.budget_note)) }, minLines = 2, maxLines = 4,
    )
}

@Composable
private fun LimitEditor(
    initial: BudgetTarget?,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by remember { mutableStateOf(initial?.amountCents?.let(::plainAmount) ?: "") }
    var period by remember { mutableStateOf(initial?.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY) }
    var startDate by remember { mutableStateOf(initial?.limitStartDate ?: LocalDate.now().toString()) }
    var hold by remember { mutableStateOf(initial?.limitHold ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var datePickerOpen by remember { mutableStateOf(false) }
    val amountCents = runCatching { java.math.BigDecimal(amount).movePointRight(2).longValueExact() }.getOrNull()
    val validStart = period != BudgetTarget.LimitPeriod.WEEKLY || runCatching { LocalDate.parse(startDate) }.isSuccess
    val canSave = amountCents != null && amountCents > 0L && validStart

    EditorScaffold(title = stringResource(BudgetTarget.Type.LIMIT.labelRes), onBack = onBack, onDelete = onDelete, modifier = modifier) {
        Text(stringResource(BudgetTarget.Type.LIMIT.explanationRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle(stringResource(R.string.automation_configuration))
        OutlinedTextField(
            value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.budget_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(stringResource(R.string.automation_every), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetTarget.LimitPeriod.entries.forEach { option ->
                FilledTonalButton(
                    onClick = { period = option }, modifier = Modifier.weight(1f),
                    colors = if (period == option) ButtonDefaults.filledTonalButtonColors()
                        else ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) { Text(limitPeriodLabel(option)) }
            }
        }
        Text(
            stringResource(R.string.automation_cap_scale),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (period == BudgetTarget.LimitPeriod.WEEKLY) {
            DateField(stringResource(R.string.automation_weekly_start), startDate) { datePickerOpen = true }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.automation_retain_over_cap))
                Text(
                    stringResource(if (hold) R.string.automation_excess_stays else R.string.automation_excess_released),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = hold, onCheckedChange = { hold = it })
        }
        NoteField(note) { note = it }
        Button(
            onClick = {
                onSave(
                    BudgetTarget(
                        BudgetTarget.Type.LIMIT, amountCents = amountCents ?: 0L,
                        limitPeriod = period, limitStartDate = if (period == BudgetTarget.LimitPeriod.WEEKLY) startDate else null,
                        limitHold = hold, note = note.trim().ifBlank { null },
                    ),
                )
            },
            enabled = canSave, modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (initial == null) R.string.automation_add_cap else R.string.automation_update_cap)) }
        Spacer(Modifier.height(20.dp))
    }
    simpleDatePicker(datePickerOpen, startDate, { datePickerOpen = false }) { startDate = it }
}

@Composable
private fun GoalEditor(
    initial: BudgetTarget?,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by remember { mutableStateOf(initial?.amountCents?.let(::plainAmount) ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val amountCents = runCatching { java.math.BigDecimal(amount).movePointRight(2).longValueExact() }.getOrNull()

    EditorScaffold(title = stringResource(BudgetTarget.Type.GOAL.labelRes), onBack = onBack, onDelete = onDelete, modifier = modifier) {
        Text(stringResource(BudgetTarget.Type.GOAL.explanationRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle(stringResource(R.string.automation_configuration))
        OutlinedTextField(
            value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.automation_target_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        NoteField(note) { note = it }
        Button(
            onClick = { onSave(BudgetTarget(BudgetTarget.Type.GOAL, amountCents = amountCents ?: 0L, note = note.trim().ifBlank { null })) },
            enabled = amountCents != null && amountCents > 0L, modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (initial == null) R.string.automation_add_goal else R.string.automation_update_goal)) }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributionEditor(
    initial: BudgetTarget?,
    initialType: BudgetTarget.Type,
    month: String,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding>,
    incomeCategories: List<String>,
    usedSingletonTypes: Set<BudgetTarget.Type>,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by remember { mutableStateOf(initialType) }
    var amount by remember { mutableStateOf(initial?.amountCents?.let(::plainAmount) ?: "") }
    var priority by remember { mutableStateOf(initial?.priority?.takeIf { it > 0 } ?: 1) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    // FIXED
    var period by remember { mutableStateOf(initial?.period ?: BudgetTarget.Period.MONTH) }
    var everyCount by remember { mutableStateOf(initial?.everyCount ?: 1) }
    var startingDate by remember { mutableStateOf(initial?.startingDate ?: "$month-01") }

    // BY_DATE
    var targetMonth by remember { mutableStateOf(initial?.targetMonth ?: month) }
    var repeats by remember { mutableStateOf(initial?.repeats ?: false) }
    var repeatEvery by remember { mutableStateOf(initial?.repeatEvery ?: 1) }
    var repeatAnnual by remember { mutableStateOf(initial?.repeatAnnual ?: false) }
    var allowEarlySpending by remember { mutableStateOf(initial?.allowEarlySpending ?: false) }
    var spendFromMonth by remember { mutableStateOf(initial?.spendFromMonth ?: month) }

    // SCHEDULE
    var scheduleId by remember { mutableStateOf(initial?.scheduleId) }
    var scheduleName by remember { mutableStateOf(initial?.scheduleName) }
    var scheduleFull by remember { mutableStateOf(initial?.scheduleFull ?: false) }
    var scheduleMenu by remember { mutableStateOf(false) }
    var savingsModeMenu by remember { mutableStateOf(false) }

    // HISTORICAL
    var historicalMode by remember { mutableStateOf(initial?.historicalMode ?: BudgetTarget.HistoricalMode.AVERAGE) }
    var historicalMonths by remember { mutableStateOf(initial?.historicalMonths ?: 3) }
    var historicalModeMenu by remember { mutableStateOf(false) }

    // PERCENTAGE
    var percentage by remember { mutableStateOf(initial?.percentage ?: 10) }
    var percentageSource by remember { mutableStateOf(initial?.percentageSource ?: "available funds") }
    var percentagePrevious by remember { mutableStateOf(initial?.percentagePrevious ?: false) }
    var percentageOfMenu by remember { mutableStateOf(false) }
    var percentageSourceMenu by remember { mutableStateOf(false) }

    // Adjustment ("increase"/"decrease" modifier) - SCHEDULE and HISTORICAL(AVERAGE) only
    var adjustmentEnabled by remember { mutableStateOf(initial?.adjustmentType != null) }
    var adjustmentType by remember { mutableStateOf(initial?.adjustmentType ?: BudgetTarget.AdjustmentType.PERCENT) }
    var adjustmentIncrease by remember {
        mutableStateOf(
            when (initial?.adjustmentType) {
                BudgetTarget.AdjustmentType.PERCENT -> (initial.adjustmentPercent ?: 0.0) >= 0.0
                BudgetTarget.AdjustmentType.FIXED -> (initial.adjustmentAmountCents ?: 0L) >= 0L
                null -> true
            },
        )
    }
    var adjustmentMagnitude by remember {
        mutableStateOf(
            when (initial?.adjustmentType) {
                BudgetTarget.AdjustmentType.PERCENT -> initial.adjustmentPercent?.let { kotlin.math.abs(it) }
                    ?.let { java.math.BigDecimal(it).stripTrailingZeros().toPlainString() } ?: ""
                BudgetTarget.AdjustmentType.FIXED -> initial.adjustmentAmountCents?.let { plainAmount(kotlin.math.abs(it)) } ?: ""
                null -> ""
            },
        )
    }

    // REMAINDER
    var weight by remember { mutableStateOf(initial?.weight ?: 1) }
    var limitPeriod by remember { mutableStateOf(initial?.limitPeriod) }
    var limitAmount by remember { mutableStateOf(initial?.limitAmountCents?.let(::plainAmount) ?: "") }
    var limitStartDate by remember { mutableStateOf(initial?.limitStartDate ?: "$month-01") }
    var limitHold by remember { mutableStateOf(initial?.limitHold ?: false) }

    var datePickerFor by remember { mutableStateOf<DateTarget?>(null) }
    var periodMenu by remember { mutableStateOf(false) }

    val amountCents = runCatching { java.math.BigDecimal(amount).movePointRight(2).longValueExact() }.getOrNull()
    val limitAmountCents = runCatching { java.math.BigDecimal(limitAmount).movePointRight(2).longValueExact() }.getOrNull()
    val needsStartingDate = type == BudgetTarget.Type.FIXED && (period == BudgetTarget.Period.WEEK || period == BudgetTarget.Period.DAY)
    val validStartingDate = !needsStartingDate || runCatching { LocalDate.parse(startingDate) }.isSuccess
    val validLimitStartDate = limitPeriod != BudgetTarget.LimitPeriod.WEEKLY ||
        runCatching { LocalDate.parse(limitStartDate) }.isSuccess
    val canSave = when (type) {
        BudgetTarget.Type.FIXED -> amountCents?.let { it > 0L } == true
        BudgetTarget.Type.BY_DATE -> amountCents?.let { it > 0L } == true &&
            runCatching { java.time.YearMonth.parse(targetMonth.take(7)) }.isSuccess
        BudgetTarget.Type.SCHEDULE -> !scheduleId.isNullOrBlank() || !scheduleName.isNullOrBlank()
        BudgetTarget.Type.PERCENTAGE -> percentage in 1..100
        BudgetTarget.Type.HISTORICAL -> historicalMonths in 1..24
        BudgetTarget.Type.REFILL -> true
        BudgetTarget.Type.REMAINDER -> weight >= 1 &&
            (limitPeriod == null || limitAmountCents?.let { it > 0L } == true)
        else -> false
    } && validStartingDate && validLimitStartDate

    val adjustmentApplicable = type == BudgetTarget.Type.SCHEDULE ||
        (type == BudgetTarget.Type.HISTORICAL && historicalMode == BudgetTarget.HistoricalMode.AVERAGE)
    val adjustmentMagnitudeValue = adjustmentMagnitude.toDoubleOrNull()?.takeIf { it > 0.0 }
    val adjustmentActive = adjustmentApplicable && adjustmentEnabled && adjustmentMagnitudeValue != null

    fun buildTarget(): BudgetTarget = BudgetTarget(
        type = type,
        amountCents = amountCents ?: 0L,
        priority = priority.coerceAtLeast(1),
        note = note.trim().ifBlank { null },
        period = period, everyCount = everyCount.coerceAtLeast(1),
        startingDate = if (type == BudgetTarget.Type.FIXED) startingDate else null,
        targetMonth = if (type == BudgetTarget.Type.BY_DATE) targetMonth.take(7) else null,
        repeats = repeats, repeatEvery = repeatEvery.coerceAtLeast(1), repeatAnnual = repeatAnnual,
        allowEarlySpending = allowEarlySpending,
        spendFromMonth = if (allowEarlySpending) spendFromMonth.take(7) else null,
        scheduleId = if (type == BudgetTarget.Type.SCHEDULE) scheduleId else null,
        scheduleName = if (type == BudgetTarget.Type.SCHEDULE) scheduleName else null,
        scheduleFull = scheduleFull,
        historicalMode = historicalMode, historicalMonths = historicalMonths.coerceIn(1, 24),
        percentage = percentage.coerceIn(1, 100), percentageSource = percentageSource, percentagePrevious = percentagePrevious,
        adjustmentType = if (adjustmentActive) adjustmentType else null,
        adjustmentPercent = if (adjustmentActive && adjustmentType == BudgetTarget.AdjustmentType.PERCENT) {
            (if (adjustmentIncrease) 1.0 else -1.0) * (adjustmentMagnitudeValue ?: 0.0)
        } else null,
        adjustmentAmountCents = if (adjustmentActive && adjustmentType == BudgetTarget.AdjustmentType.FIXED) {
            val magnitudeCents = runCatching {
                java.math.BigDecimal(adjustmentMagnitude).movePointRight(2).longValueExact()
            }.getOrDefault(0L)
            (if (adjustmentIncrease) 1L else -1L) * magnitudeCents
        } else null,
        weight = weight.coerceAtLeast(1),
        limitPeriod = if (type == BudgetTarget.Type.REMAINDER) limitPeriod else null,
        limitAmountCents = if (type == BudgetTarget.Type.REMAINDER) limitAmountCents else null,
        limitStartDate = if (type == BudgetTarget.Type.REMAINDER && limitPeriod == BudgetTarget.LimitPeriod.WEEKLY) limitStartDate else null,
        limitHold = if (type == BudgetTarget.Type.REMAINDER) limitHold else false,
    )

    EditorScaffold(title = stringResource(type.labelRes), onBack = onBack, onDelete = onDelete, modifier = modifier) {
        SectionTitle(stringResource(R.string.automation_type))
        ContributionTypeGrid(selected = type, disabled = usedSingletonTypes) { type = it }

        SectionTitle(stringResource(R.string.automation_configuration))
        when (type) {
            BudgetTarget.Type.FIXED -> {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.budget_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                NumberStepper(stringResource(R.string.automation_every), everyCount, 1..365) { everyCount = it }
                Box {
                    ChoiceField(stringResource(R.string.automation_period), periodLabel(period, 2)) { periodMenu = true }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        BudgetTarget.Period.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(periodLabel(option, 2)) },
                                onClick = { period = option; periodMenu = false },
                            )
                        }
                    }
                }
                DateField(stringResource(R.string.automation_starting), startingDate) { datePickerFor = DateTarget.STARTING_DATE }
            }
            BudgetTarget.Type.BY_DATE -> {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.automation_total_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                DateField(stringResource(R.string.automation_target_month), targetMonth.take(7) + "-01") { datePickerFor = DateTarget.TARGET_MONTH }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.automation_repeats), Modifier.weight(1f))
                    Switch(checked = repeats, onCheckedChange = { repeats = it })
                }
                if (repeats) {
                    NumberStepper(stringResource(R.string.automation_repeat_every), repeatEvery, 1..50) { repeatEvery = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to stringResource(R.string.automation_months), true to stringResource(R.string.automation_years)).forEach { (annual, label) ->
                            FilledTonalButton(
                                onClick = { repeatAnnual = annual }, modifier = Modifier.weight(1f),
                                colors = if (repeatAnnual == annual) ButtonDefaults.filledTonalButtonColors()
                                    else ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) { Text(label) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.automation_allow_early))
                        Text(
                            stringResource(R.string.automation_allow_early_body),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = allowEarlySpending, onCheckedChange = { allowEarlySpending = it })
                }
                if (allowEarlySpending) {
                    DateField(stringResource(R.string.automation_start_spending), spendFromMonth.take(7) + "-01") { datePickerFor = DateTarget.SPEND_FROM }
                }
            }
            BudgetTarget.Type.SCHEDULE -> {
                if (scheduleFunding.isEmpty()) {
                    Text(
                        stringResource(R.string.automation_no_schedules),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box {
                        ChoiceField(stringResource(R.string.automation_schedule), scheduleName ?: stringResource(R.string.automation_select_schedule)) { scheduleMenu = true }
                        DropdownMenu(expanded = scheduleMenu, onDismissRequest = { scheduleMenu = false }) {
                            scheduleFunding.forEach { schedule ->
                                DropdownMenuItem(text = {
                                    Column {
                                        Text(schedule.name ?: stringResource(R.string.automation_unnamed_schedule))
                                        Text(formatMoneyCents(schedule.amountCents, hideDecimalPlaces),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }, onClick = { scheduleId = schedule.id; scheduleName = schedule.name; scheduleMenu = false })
                            }
                        }
                    }
                }
                Box {
                    ChoiceField(
                        stringResource(R.string.automation_savings_mode),
                        stringResource(if (scheduleFull) R.string.automation_cover_occurrence else R.string.automation_save_next),
                    ) { savingsModeMenu = true }
                    DropdownMenu(expanded = savingsModeMenu, onDismissRequest = { savingsModeMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.automation_save_next)) }, onClick = { scheduleFull = false; savingsModeMenu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.automation_cover_occurrence)) }, onClick = { scheduleFull = true; savingsModeMenu = false })
                    }
                }
            }
            BudgetTarget.Type.PERCENTAGE -> {
                NumberStepper(stringResource(R.string.automation_percentage), percentage, 1..100) { percentage = it }
                Box {
                    ChoiceField(stringResource(R.string.automation_percentage_of), stringResource(if (percentagePrevious) R.string.automation_last_month else R.string.automation_this_month)) { percentageOfMenu = true }
                    DropdownMenu(expanded = percentageOfMenu, onDismissRequest = { percentageOfMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.automation_this_month)) }, onClick = { percentagePrevious = false; percentageOfMenu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.automation_last_month)) }, onClick = { percentagePrevious = true; percentageOfMenu = false })
                    }
                }
                Box {
                    ChoiceField(stringResource(R.string.automation_income_source), percentageSourceLabel(percentageSource)) { percentageSourceMenu = true }
                    DropdownMenu(expanded = percentageSourceMenu, onDismissRequest = { percentageSourceMenu = false }) {
                        (listOf("available funds", "all income") + incomeCategories).distinct().forEach { source ->
                            DropdownMenuItem(
                                text = { Text(percentageSourceLabel(source)) },
                                onClick = { percentageSource = source; percentageSourceMenu = false },
                            )
                        }
                    }
                }
            }
            BudgetTarget.Type.HISTORICAL -> {
                Box {
                    ChoiceField(
                        stringResource(R.string.automation_mode),
                        stringResource(if (historicalMode == BudgetTarget.HistoricalMode.COPY) R.string.automation_copy_previous else R.string.automation_average_previous),
                    ) { historicalModeMenu = true }
                    DropdownMenu(expanded = historicalModeMenu, onDismissRequest = { historicalModeMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_average_previous)) },
                            onClick = { historicalMode = BudgetTarget.HistoricalMode.AVERAGE; historicalModeMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_copy_previous)) },
                            onClick = { historicalMode = BudgetTarget.HistoricalMode.COPY; historicalModeMenu = false },
                        )
                    }
                }
                NumberStepper(stringResource(R.string.automation_months_back), historicalMonths, 1..24) { historicalMonths = it }
            }
            BudgetTarget.Type.REFILL -> {
                if (BudgetTarget.Type.LIMIT !in usedSingletonTypes) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                stringResource(R.string.automation_refill_needs_cap),
                                modifier = Modifier.padding(start = 10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.automation_refill_body),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BudgetTarget.Type.REMAINDER -> {
                NumberStepper(stringResource(R.string.automation_weight), weight, 1..20) { weight = it }
                Text(
                    stringResource(R.string.automation_weight_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionTitle(stringResource(R.string.automation_optional_cap))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = limitAmount, onValueChange = { limitAmount = it }, modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.automation_limit_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    TextButton(onClick = {
                        limitPeriod = when (limitPeriod) {
                            null -> BudgetTarget.LimitPeriod.MONTHLY
                            BudgetTarget.LimitPeriod.MONTHLY -> BudgetTarget.LimitPeriod.WEEKLY
                            BudgetTarget.LimitPeriod.WEEKLY -> BudgetTarget.LimitPeriod.DAILY
                            BudgetTarget.LimitPeriod.DAILY -> null
                        }
                    }) { Text(limitPeriod?.let { limitPeriodLabel(it) } ?: stringResource(R.string.automation_no_cap)) }
                }
                if (limitPeriod == BudgetTarget.LimitPeriod.WEEKLY) {
                    DateField(stringResource(R.string.automation_weekly_start), limitStartDate) { datePickerFor = DateTarget.LIMIT_START }
                }
                if (limitPeriod != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.automation_hold_excess), Modifier.weight(1f))
                        Switch(checked = limitHold, onCheckedChange = { limitHold = it })
                    }
                }
            }
            else -> Unit
        }

        if (adjustmentApplicable) {
            SectionTitle(stringResource(R.string.automation_adjustment))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.automation_adjustment_body))
                    Text(
                        stringResource(R.string.automation_adjustment_actual),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = adjustmentEnabled, onCheckedChange = { adjustmentEnabled = it })
            }
            if (adjustmentEnabled) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to stringResource(R.string.automation_increase), false to stringResource(R.string.automation_decrease)).forEach { (increase, label) ->
                        FilledTonalButton(
                            onClick = { adjustmentIncrease = increase }, modifier = Modifier.weight(1f),
                            colors = if (adjustmentIncrease == increase) ButtonDefaults.filledTonalButtonColors()
                                else ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) { Text(label) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = adjustmentMagnitude, onValueChange = { adjustmentMagnitude = it }, modifier = Modifier.weight(1f),
                        label = { Text(stringResource(if (adjustmentType == BudgetTarget.AdjustmentType.PERCENT) R.string.automation_percent else R.string.budget_amount)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    TextButton(onClick = {
                        adjustmentType = if (adjustmentType == BudgetTarget.AdjustmentType.PERCENT) {
                            BudgetTarget.AdjustmentType.FIXED
                        } else {
                            BudgetTarget.AdjustmentType.PERCENT
                        }
                    }) { Text(if (adjustmentType == BudgetTarget.AdjustmentType.PERCENT) "%" else stringResource(R.string.automation_fixed)) }
                }
            }
        }

        if (type.hasPriority) NumberStepper(stringResource(R.string.automation_priority), priority, 1..30) { priority = it }
        NoteField(note) { note = it }
        Text(stringResource(type.explanationRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(onClick = { onSave(buildTarget()) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (initial == null) R.string.automation_add else R.string.automation_update).removePrefix("+ "))
        }
        Spacer(Modifier.height(20.dp))
    }

    datePickerFor?.let { target ->
        val initialDate = when (target) {
            DateTarget.STARTING_DATE -> runCatching { LocalDate.parse(startingDate) }.getOrDefault(LocalDate.now())
            DateTarget.TARGET_MONTH -> runCatching { LocalDate.parse(targetMonth.take(7) + "-01") }.getOrDefault(LocalDate.now())
            DateTarget.SPEND_FROM -> runCatching { LocalDate.parse(spendFromMonth.take(7) + "-01") }.getOrDefault(LocalDate.now())
            DateTarget.LIMIT_START -> runCatching { LocalDate.parse(limitStartDate) }.getOrDefault(LocalDate.now())
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        when (target) {
                            DateTarget.STARTING_DATE -> startingDate = picked.toString()
                            DateTarget.TARGET_MONTH -> targetMonth = picked.toString().take(7)
                            DateTarget.SPEND_FROM -> spendFromMonth = picked.toString().take(7)
                            DateTarget.LIMIT_START -> limitStartDate = picked.toString()
                        }
                    }
                    datePickerFor = null
                }) { Text(androidx.compose.ui.res.stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { datePickerFor = null }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(picker) }
    }
}

private enum class DateTarget { STARTING_DATE, TARGET_MONTH, SPEND_FROM, LIMIT_START }

@Composable
private fun ContributionTypeGrid(selected: BudgetTarget.Type, disabled: Set<BudgetTarget.Type>, onSelect: (BudgetTarget.Type) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        contributionTypes.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    TypeCard(
                        type = option, selected = option == selected, enabled = option !in disabled,
                        onClick = { onSelect(option) }, modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypeCard(type: BudgetTarget.Type, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(104.dp),
        shape = MaterialTheme.shapes.large,
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(type.labelRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.height(18.dp).width(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (!enabled) stringResource(R.string.automation_only_one) else stringResource(type.explanationRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun AddButton(enabled: Boolean = true, onClick: () -> Unit, label: @Composable () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick, modifier = Modifier.fillMaxWidth()) { label() }
}

@Composable
private fun NumberStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 14.dp))
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.automation_choose, label))
        }
    }
}

@Composable
private fun DateField(label: String, isoDate: String, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    runCatching {
                        LocalDate.parse(isoDate).format(
                            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                                .withLocale(locale),
                        )
                    }.getOrDefault(isoDate),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(Icons.Outlined.DateRange, contentDescription = stringResource(R.string.automation_choose, label))
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SectionHeader(value: String, modifier: Modifier = Modifier) {
    Text(
        value.uppercase(), modifier = modifier,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun simpleDatePicker(open: Boolean, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    if (!open) return
    val initialDate = runCatching { LocalDate.parse(current) }.getOrDefault(LocalDate.now())
    val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                onDismiss()
            }) { Text(androidx.compose.ui.res.stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) { DatePicker(picker) }
}

private fun plainAmount(cents: Long): String = java.math.BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

private fun adjustmentSuffix(target: BudgetTarget): String = when (target.adjustmentType) {
    BudgetTarget.AdjustmentType.PERCENT -> target.adjustmentPercent?.let {
        " · ${if (it >= 0) "+" else ""}${java.math.BigDecimal(it).stripTrailingZeros().toPlainString()}%"
    } ?: ""
    BudgetTarget.AdjustmentType.FIXED -> target.adjustmentAmountCents?.let {
        " · ${if (it >= 0) "+" else ""}${plainAmount(it)}"
    } ?: ""
    null -> ""
}

@Composable
private fun localizedAutomationError(error: String): String {
    val direct = when (error) {
        "A category can have at most 20 automations" -> R.string.automation_error_max
        "Only one refill automation is allowed" -> R.string.automation_error_one_refill
        "Only one balance cap automation is allowed" -> R.string.automation_error_one_cap
        "Only one long-term goal automation is allowed" -> R.string.automation_error_one_goal
        "Only one remainder automation is allowed" -> R.string.automation_error_one_remainder
        "Refill to cap needs a balance cap automation" -> R.string.automation_error_refill_cap
        "Remainder weight must be at least 1" -> R.string.automation_error_weight
        "Remainder limits must be positive" -> R.string.automation_error_positive_limit
        "Remainder limits need both a period and amount" -> R.string.automation_error_limit_fields
        "Weekly remainder limits need a valid start date" -> R.string.automation_error_remainder_date
        "Weekly balance caps need a valid start date" -> R.string.automation_error_cap_date
        "Only weekly balance caps can have a start date" -> R.string.automation_error_weekly_start_only
        "Automation priority cannot be negative" -> R.string.automation_error_priority
        "Balance cap needs a positive amount" -> R.string.automation_error_positive_cap
        "Percentage automations must be between 1 and 100" -> R.string.automation_error_percentage
        "From-history automations must look back 1 to 24 months" -> R.string.automation_error_history
        "Percentage automations need an income source" -> R.string.automation_error_income_source
        "Adjustments need a value" -> R.string.automation_error_adjustment
        "Schedule automations need a schedule ID or name" -> R.string.automation_error_schedule
        "Schedule and date automations must use the same priority" -> R.string.automation_error_shared_priority
        "Date targets must use the same priority" -> R.string.automation_error_date_priority
        else -> null
    }
    if (direct != null) return stringResource(direct)
    val index = Regex("""Automation (\d+) needs""").find(error)?.groupValues?.get(1)?.toIntOrNull()
    return when {
        index != null && error.endsWith("a positive amount") ->
            stringResource(R.string.automation_error_positive_amount, index)
        index != null && error.endsWith("a valid target month") ->
            stringResource(R.string.automation_error_target_month, index)
        index != null && error.endsWith("a valid starting date") ->
            stringResource(R.string.automation_error_start_date, index)
        else -> stringResource(R.string.automation_error_unknown)
    }
}

@StringRes
private fun unsupportedAutomationTypeLabelRes(type: String): Int = when (type) {
    BudgetAutomationDocument.UNSUPPORTED_UNKNOWN -> R.string.automation_unsupported_type_unknown
    BudgetAutomationDocument.UNSUPPORTED_INVALID_DEFINITION -> R.string.automation_unsupported_invalid_definition
    BudgetAutomationDocument.UNSUPPORTED_BY_PRIORITIES -> R.string.automation_unsupported_by_priorities
    BudgetAutomationDocument.UNSUPPORTED_INVALID_SUPPORTED_DEFINITION ->
        R.string.automation_unsupported_invalid_supported_definition
    "periodic" -> BudgetTarget.Type.FIXED.labelRes
    "by", "spend" -> BudgetTarget.Type.BY_DATE.labelRes
    "schedule" -> BudgetTarget.Type.SCHEDULE.labelRes
    "percentage" -> BudgetTarget.Type.PERCENTAGE.labelRes
    "average", "copy" -> BudgetTarget.Type.HISTORICAL.labelRes
    "refill" -> BudgetTarget.Type.REFILL.labelRes
    "remainder" -> BudgetTarget.Type.REMAINDER.labelRes
    "limit" -> BudgetTarget.Type.LIMIT.labelRes
    "goal" -> BudgetTarget.Type.GOAL.labelRes
    else -> R.string.automation_unsupported_type_unknown
}

@Composable
private fun percentageSourceLabel(source: String): String = when (source.lowercase()) {
    "available funds" -> stringResource(R.string.automation_available_funds)
    "all income" -> stringResource(R.string.automation_all_income)
    else -> source
}

@Composable
private fun periodLabel(period: BudgetTarget.Period, quantity: Int): String = stringResource(
    when (period) {
        BudgetTarget.Period.DAY -> if (quantity == 1) R.string.automation_period_day_singular else R.string.automation_period_day_plural
        BudgetTarget.Period.WEEK -> if (quantity == 1) R.string.automation_period_week_singular else R.string.automation_period_week_plural
        BudgetTarget.Period.MONTH -> if (quantity == 1) R.string.automation_period_month_singular else R.string.automation_period_month_plural
        BudgetTarget.Period.YEAR -> if (quantity == 1) R.string.automation_period_year_singular else R.string.automation_period_year_plural
    },
)

@Composable
private fun limitPeriodLabel(period: BudgetTarget.LimitPeriod): String = stringResource(
    when (period) {
        BudgetTarget.LimitPeriod.DAILY -> R.string.automation_limit_daily
        BudgetTarget.LimitPeriod.WEEKLY -> R.string.automation_limit_weekly
        BudgetTarget.LimitPeriod.MONTHLY -> R.string.automation_limit_monthly
    },
)
