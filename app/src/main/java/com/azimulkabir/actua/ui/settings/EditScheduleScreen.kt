package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.schedules.*
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.CalculatorAmountSheet
import com.azimulkabir.actua.ui.components.centsToInput
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.formatDate as formatDisplayDate
import com.azimulkabir.actua.ui.transactions.PickerTextField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleScreen(
    item: ScheduleListItem?,
    accounts: List<Account>,
    payeeOptions: List<String>,
    hideDecimalPlaces: Boolean,
    conventionalAmountEntry: Boolean,
    linkedTransactions: List<ScheduleLinkedTransaction> = emptyList(),
    onBack: () -> Unit,
    onSave: (ScheduleFormFields, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onUnlinkTransaction: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val schedule = item?.schedule
    val originalRecurrence = (schedule?.dateCondition as? ScheduleDateCondition.Recurring)?.config
    val originalDate = (schedule?.dateCondition as? ScheduleDateCondition.Fixed)?.day
        ?: schedule?.nextDate ?: DayDate.today()
    val editorKey = schedule?.id ?: "new"
    var name by remember(editorKey) { mutableStateOf(schedule?.name.orEmpty()) }
    var payeeName by remember(editorKey) { mutableStateOf(item?.payeeName.orEmpty()) }
    var accountName by remember(editorKey) {
        mutableStateOf(item?.accountName ?: accounts.firstOrNull { !it.closed }?.name.orEmpty())
    }
    var income by remember(editorKey) { mutableStateOf((schedule?.postAmount ?: -1L) > 0) }
    var amountOp by remember(editorKey) { mutableStateOf(schedule?.amountOp ?: ScheduleAmountOp.APPROXIMATE) }
    val originalRange = schedule?.amount as? ScheduledAmount.Range
    var amountLow by remember(editorKey) {
        mutableStateOf(minOf(abs(originalRange?.first ?: schedule?.postAmount ?: 0L), abs(originalRange?.second ?: schedule?.postAmount ?: 0L)))
    }
    var amountHigh by remember(editorKey) {
        mutableStateOf(maxOf(abs(originalRange?.first ?: schedule?.postAmount ?: 0L), abs(originalRange?.second ?: schedule?.postAmount ?: 0L)))
    }
    var calculatorTarget by remember { mutableStateOf<Int?>(null) }
    var repeats by remember(editorKey) { mutableStateOf(originalRecurrence != null) }
    var oneOffDate by remember(editorKey) { mutableStateOf(originalDate) }
    var recurrence by remember(editorKey) {
        mutableStateOf(
            originalRecurrence ?: RecurConfig(
                frequency = RecurConfig.Frequency.MONTHLY,
                interval = 1,
                start = schedule?.nextDate ?: DayDate.today(),
            ),
        )
    }
    var datePickerTarget by remember { mutableStateOf<DateTarget?>(null) }
    var showRepeatEditor by remember(editorKey) { mutableStateOf(false) }
    var automaticallyAdd by remember(editorKey) { mutableStateOf(schedule?.postsTransaction ?: false) }
    var upcomingValue by remember(editorKey) { mutableStateOf(schedule?.customUpcomingLength) }
    val localizedUpcomingOptions = upcomingOptions()
    var showDelete by remember { mutableStateOf(false) }
    val unreadableDate = schedule?.dateCondition == ScheduleDateCondition.Unsupported && schedule.dateOp != null
    val accountId = accounts.firstOrNull { it.name == accountName && !it.closed }?.id
    val amountValid = amountLow > 0 && (amountOp != ScheduleAmountOp.BETWEEN || amountHigh > 0)
    val canSave = accountId != null && amountValid && !unreadableDate

    fun fields(): ScheduleFormFields {
        val sign = if (income) 1L else -1L
        val amount = if (amountOp == ScheduleAmountOp.BETWEEN) {
            val first = sign * amountLow
            val second = sign * amountHigh
            ScheduledAmount.Range(minOf(first, second), maxOf(first, second))
        } else {
            ScheduledAmount.Fixed(sign * amountLow)
        }
        return ScheduleFormFields(
            name = name,
            accountId = accountId,
            amount = amount,
            amountOp = amountOp,
            date = if (repeats) ScheduleDateCondition.Recurring(recurrence)
                else ScheduleDateCondition.Fixed(oneOffDate),
            postsTransaction = automaticallyAdd,
            customUpcomingLength = upcomingValue,
        )
    }

    if (showRepeatEditor) {
        RepeatEditorScreen(
            recurrence = recurrence,
            onChange = { recurrence = it },
            onBack = { showRepeatEditor = false },
            modifier = modifier,
        )
        return
    }

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(
            title = stringResource(if (schedule == null) R.string.fs_new_schedule else R.string.fs_edit_schedule_title),
            onBack = onBack,
        ) {
            TextButton(
                enabled = canSave,
                onClick = { onSave(fields(), payeeName) },
            ) { Text(stringResource(R.string.fs_save)) }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (unreadableDate) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        stringResource(R.string.fs_unreadable_repeat),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else if (schedule?.isCustom == true) {
                Text(
                    stringResource(R.string.fs_custom_rule_preserved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(stringResource(R.string.fs_details))
            OutlinedTextField(
                name, { name = it }, label = { Text(stringResource(R.string.fs_schedule_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            PickerTextField(
                label = stringResource(R.string.fs_payee_optional),
                value = payeeName,
                options = payeeOptions,
                onValueChange = { payeeName = it },
                allowCustom = true,
            )
            PickerTextField(
                label = stringResource(R.string.fs_account),
                value = accountName,
                options = accounts.filterNot { it.closed }.map { it.name },
                onValueChange = { accountName = it },
            )

            SectionTitle(stringResource(R.string.fs_amount))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !income,
                    onClick = { income = false },
                    label = { Text(stringResource(R.string.fs_expense)) },
                    leadingIcon = if (!income) {
                        { Icon(Icons.Outlined.Check, null) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = income,
                    onClick = { income = true },
                    label = { Text(stringResource(R.string.fs_income)) },
                    leadingIcon = if (income) {
                        { Icon(Icons.Outlined.Check, null) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleAmountOp.entries.forEach { op ->
                    FilterChip(
                        selected = amountOp == op,
                        onClick = { amountOp = op },
                        label = {
                            Text(
                                when (op) {
                                    ScheduleAmountOp.EXACT -> stringResource(R.string.fs_exact)
                                    ScheduleAmountOp.APPROXIMATE -> stringResource(R.string.fs_approximate)
                                    ScheduleAmountOp.BETWEEN -> stringResource(R.string.fs_between)
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AmountField(
                label = stringResource(if (amountOp == ScheduleAmountOp.BETWEEN) R.string.fs_from else R.string.fs_amount),
                cents = amountLow,
            ) { calculatorTarget = 0 }
            if (amountOp == ScheduleAmountOp.BETWEEN) {
                AmountField(stringResource(R.string.fs_to), amountHigh) { calculatorTarget = 1 }
            }

            SectionTitle(stringResource(R.string.fs_date))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.fs_repeats), modifier = Modifier.weight(1f))
                Switch(repeats, { repeats = it })
            }
            if (repeats) {
                Row(
                    Modifier.fillMaxWidth().clickable { showRepeatEditor = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.fs_repeat), modifier = Modifier.weight(1f))
                    Text(
                        recurrenceSummary(recurrence),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.fs_edit_repeat_pattern),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    nextDateSummary(recurrence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DateField(stringResource(R.string.fs_date), oneOffDate) { datePickerTarget = DateTarget.ONE_OFF }
            }

            SectionTitle(stringResource(R.string.fs_options))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.fs_automatically_add_transaction))
                    Text(
                        stringResource(R.string.fs_automatically_add_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(automaticallyAdd, { automaticallyAdd = it })
            }
            PickerTextField(
                label = stringResource(R.string.fs_upcoming_window),
                value = localizedUpcomingOptions.firstOrNull { it.second == upcomingValue }?.first
                    ?: localizedUpcomingOptions.first().first,
                options = localizedUpcomingOptions.map { it.first },
                onValueChange = { label -> upcomingValue = localizedUpcomingOptions.first { it.first == label }.second },
            )

            if (schedule != null) {
                SectionTitle(stringResource(R.string.fs_linked_transactions))
                if (linkedTransactions.isEmpty()) {
                    Text(
                        stringResource(R.string.fs_no_linked_transactions),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column {
                            linkedTransactions.forEachIndexed { index, transaction ->
                                LinkedTransactionRow(
                                    transaction = transaction,
                                    hideDecimalPlaces = hideDecimalPlaces,
                                    onUnlink = onUnlinkTransaction?.let { unlink ->
                                        { unlink(transaction.id) }
                                    },
                                )
                                if (index != linkedTransactions.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }

            if (onDelete != null) {
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.fs_delete_schedule_title), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    calculatorTarget?.let { target ->
        CalculatorAmountSheet(
            title = stringResource(if (target == 0) R.string.fs_schedule_amount else R.string.fs_upper_amount),
            initialCents = if (target == 0) amountLow else amountHigh,
            conventionalAmountEntry = conventionalAmountEntry,
            onDismiss = { calculatorTarget = null },
            onApply = {
                if (target == 0) amountLow = it else amountHigh = it
            },
        )
    }

    datePickerTarget?.let { target ->
        val selected = when (target) {
            DateTarget.ONE_OFF -> oneOffDate
        }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val day = DayDate.from(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                        when (target) {
                            DateTarget.ONE_OFF -> oneOffDate = day
                        }
                    }
                    datePickerTarget = null
                }) { Text(stringResource(R.string.fs_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text(stringResource(R.string.fs_cancel)) }
            },
        ) { DatePicker(picker) }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.fs_delete_schedule_question)) },
            text = { Text(stringResource(R.string.fs_delete_schedule_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete?.invoke()
                }) { Text(stringResource(R.string.fs_delete_schedule), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.fs_cancel)) }
            },
        )
    }
}

@Composable
private fun LinkedTransactionRow(
    transaction: ScheduleLinkedTransaction,
    hideDecimalPlaces: Boolean,
    onUnlink: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(transaction.payeeName.ifBlank { stringResource(R.string.fs_unknown_payee) }, maxLines = 1)
            Text(
                listOfNotNull(
                    transaction.date?.let { formatDisplayDate(it.toLocalDate()) },
                    transaction.accountName.ifBlank { stringResource(R.string.fs_unknown_account) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            formatMoneyCents(transaction.amountCents, hideDecimalPlaces),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (onUnlink != null) {
            TextButton(onClick = onUnlink) { Text(stringResource(R.string.fs_unlink)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatEditorScreen(
    recurrence: RecurConfig,
    onChange: (RecurConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateTarget by remember { mutableStateOf<RepeatDateTarget?>(null) }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_repeat), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(stringResource(R.string.fs_repeats))
            ChoiceField(
                label = stringResource(R.string.fs_frequency),
                value = frequencyLabel(recurrence.frequency),
                choices = RecurConfig.Frequency.entries.map {
                    frequencyLabel(it) to it
                },
            ) { frequency ->
                onChange(recurrence.copy(
                    frequency = frequency,
                    patterns = if (frequency == RecurConfig.Frequency.MONTHLY) {
                        recurrence.patterns
                    } else emptyList(),
                ))
            }
            NumberStepper(
                label = stringResource(R.string.fs_every),
                value = recurrence.interval,
                valueLabel = intervalLabel(recurrence),
                range = 1..365,
            ) { onChange(recurrence.copy(interval = it)) }
            DateField(stringResource(R.string.fs_starting), recurrence.start) { dateTarget = RepeatDateTarget.START }

            if (recurrence.frequency == RecurConfig.Frequency.MONTHLY) {
                SectionTitle(stringResource(R.string.fs_on_these_days))
                recurrence.patterns.forEachIndexed { index, pattern ->
                    MonthlyPatternRow(
                        pattern = pattern,
                        onChange = { replacement ->
                            onChange(recurrence.copy(patterns = recurrence.patterns.toMutableList().also {
                                it[index] = replacement
                            }))
                        },
                        onDelete = {
                            onChange(recurrence.copy(patterns = recurrence.patterns.filterIndexed { i, _ -> i != index }))
                        },
                    )
                }
                TextButton(onClick = {
                    onChange(recurrence.copy(
                        patterns = recurrence.patterns + RecurConfig.Pattern("day", recurrence.start.day),
                    ))
                }) {
                    Icon(Icons.Outlined.AddCircleOutline, null)
                    Text(stringResource(R.string.fs_add_day), Modifier.padding(start = 8.dp))
                }
                Text(
                    monthlyPatternSummary(recurrence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(stringResource(R.string.fs_ends))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                repeatEndOptions().forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = recurrence.endMode == option.first,
                        onClick = {
                            onChange(recurrence.copy(
                                endMode = option.first,
                                endOccurrences = if (option.first == "after_n_occurrences") {
                                    recurrence.endOccurrences ?: 1
                                } else null,
                                endDate = if (option.first == "on_date") {
                                    recurrence.endDate ?: recurrence.start
                                } else null,
                            ))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, repeatEndOptions().size),
                    ) { Text(option.second) }
                }
            }
            if (recurrence.endMode == "after_n_occurrences") {
                NumberStepper(
                    label = stringResource(R.string.fs_occurrences),
                    value = recurrence.endOccurrences ?: 1,
                    valueLabel = (recurrence.endOccurrences ?: 1).toString(),
                    range = 1..999,
                ) { onChange(recurrence.copy(endOccurrences = it)) }
            }
            if (recurrence.endMode == "on_date") {
                DateField(stringResource(R.string.fs_end_date), recurrence.endDate ?: recurrence.start) {
                    dateTarget = RepeatDateTarget.END
                }
            }

            SectionTitle(stringResource(R.string.fs_weekend_handling))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.fs_skip_weekends))
                    Text(
                        stringResource(R.string.fs_skip_weekends_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(recurrence.skipWeekend, {
                    onChange(recurrence.copy(skipWeekend = it))
                })
            }
            if (recurrence.skipWeekend) {
                ChoiceField(
                    label = stringResource(R.string.fs_move_to),
                    value = stringResource(if (recurrence.weekendSolveMode == "before") R.string.fs_friday_before else R.string.fs_monday_after),
                    choices = listOf(stringResource(R.string.fs_friday_before) to "before",
                        stringResource(R.string.fs_monday_after) to "after"),
                ) { onChange(recurrence.copy(weekendSolveMode = it)) }
            }

            SectionTitle(stringResource(R.string.fs_next_dates))
            val preview = ScheduleRecurrence.upcomingDates(recurrence, 4, DayDate.today())
            if (preview.isEmpty()) {
                Text(stringResource(R.string.fs_no_upcoming_pattern_dates), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else preview.forEach { day ->
                Row(Modifier.fillMaxWidth()) {
                    Text(formatDisplayDate(day.toLocalDate()), modifier = Modifier.weight(1f))
                    Text(weekdayLabel(day.weekday), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    dateTarget?.let { target ->
        val selected = if (target == RepeatDateTarget.START) recurrence.start
            else recurrence.endDate ?: recurrence.start
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val day = DayDate.from(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                        onChange(if (target == RepeatDateTarget.START) recurrence.copy(start = day)
                            else recurrence.copy(endDate = day))
                    }
                    dateTarget = null
                }) { Text(stringResource(R.string.fs_ok)) }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text(stringResource(R.string.fs_cancel)) } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun MonthlyPatternRow(
    pattern: RecurConfig.Pattern,
    onChange: (RecurConfig.Pattern) -> Unit,
    onDelete: () -> Unit,
) {
    val max = if (pattern.type == "day") 31 else 5
    val boundedValue = if (pattern.value == -1) -1 else pattern.value.coerceIn(1, max)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceField(
            label = stringResource(R.string.fs_which),
            value = ordinal(boundedValue),
            choices = listOf(stringResource(R.string.fs_last) to -1) + (1..max).map { ordinal(it) to it },
            modifier = Modifier.weight(1f),
        ) { onChange(pattern.copy(value = it)) }
        ChoiceField(
            label = stringResource(R.string.fs_day),
            value = patternTypeLabel(pattern.type),
            choices = patternTypes(),
            modifier = Modifier.weight(1.35f),
        ) { type ->
            onChange(pattern.copy(type = type, value = if (type != "day" && pattern.value > 5) 5 else pattern.value))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.fs_remove_pattern))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, T>>,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (text, choice) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { expanded = false; onSelect(choice) },
                )
            }
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    valueLabel: String,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 14.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun AmountField(label: String, cents: Long, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = centsToInput(cents),
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.Calculate, null) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun DateField(label: String, date: DayDate, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = formatDisplayDate(date.toLocalDate()),
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.DateRange, null) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    )
}

private enum class DateTarget { ONE_OFF }
private enum class RepeatDateTarget { START, END }

@Composable
private fun repeatEndOptions() = listOf(
    "never" to stringResource(R.string.fs_never),
    "after_n_occurrences" to stringResource(R.string.fs_after),
    "on_date" to stringResource(R.string.fs_on_date),
)

@Composable
private fun patternTypes() = listOf(
    stringResource(R.string.fs_day) to "day",
    stringResource(R.string.fs_sunday) to "SU",
    stringResource(R.string.fs_monday) to "MO",
    stringResource(R.string.fs_tuesday) to "TU",
    stringResource(R.string.fs_wednesday) to "WE",
    stringResource(R.string.fs_thursday) to "TH",
    stringResource(R.string.fs_friday) to "FR",
    stringResource(R.string.fs_saturday) to "SA",
)

@Composable
private fun weekdayLabel(day: Int) = stringResource(when (day) {
    1 -> R.string.fs_sunday
    2 -> R.string.fs_monday
    3 -> R.string.fs_tuesday
    4 -> R.string.fs_wednesday
    5 -> R.string.fs_thursday
    6 -> R.string.fs_friday
    else -> R.string.fs_saturday
})

@Composable
private fun patternTypeLabel(type: String): String {
    for ((label, token) in patternTypes()) if (token == type) return label
    return stringResource(R.string.fs_day)
}

@Composable
private fun ordinal(value: Int): String {
    if (value == -1) return stringResource(R.string.fs_last)
    if (value == 1) return stringResource(R.string.fs_ordinal_first)
    return stringResource(
        if (value % 100 in 11..13) R.string.fs_ordinal_other else when (value % 10) {
            1 -> R.string.fs_ordinal_st
            2 -> R.string.fs_ordinal_nd
            3 -> R.string.fs_ordinal_rd
            else -> R.string.fs_ordinal_other
        },
        value,
    )
}

@Composable
private fun frequencyLabel(frequency: RecurConfig.Frequency) = stringResource(when (frequency) {
    RecurConfig.Frequency.DAILY -> R.string.fs_frequency_daily
    RecurConfig.Frequency.WEEKLY -> R.string.fs_frequency_weekly
    RecurConfig.Frequency.MONTHLY -> R.string.fs_frequency_monthly
    RecurConfig.Frequency.YEARLY -> R.string.fs_frequency_yearly
})

@Composable
private fun intervalLabel(config: RecurConfig): String = pluralStringResource(
    when (config.frequency) {
        RecurConfig.Frequency.DAILY -> R.plurals.fs_interval_days
        RecurConfig.Frequency.WEEKLY -> R.plurals.fs_interval_weeks
        RecurConfig.Frequency.MONTHLY -> R.plurals.fs_interval_months
        RecurConfig.Frequency.YEARLY -> R.plurals.fs_interval_years
    },
    config.interval,
    config.interval,
)

@Composable
private fun recurrenceSummary(config: RecurConfig): String = when {
    config.interval == 1 -> frequencyLabel(config.frequency)
    else -> stringResource(R.string.fs_every_interval, intervalLabel(config))
}

@Composable
private fun monthlyPatternSummary(config: RecurConfig): String {
    if (config.patterns.isEmpty()) {
        return stringResource(R.string.fs_monthly_default_pattern, config.start.day)
    }
    val labels = mutableListOf<String>()
    for (pattern in config.patterns) {
        labels += if (pattern.type == "day") stringResource(R.string.fs_pattern_day, ordinal(pattern.value))
        else stringResource(R.string.fs_pattern_weekday, ordinal(pattern.value), patternTypeLabel(pattern.type))
    }
    return stringResource(R.string.fs_repeats_on, labels.joinToString(", "))
}

@Composable
private fun nextDateSummary(config: RecurConfig): String {
    val next = ScheduleRecurrence.upcomingDates(config, 1, DayDate.today()).firstOrNull()
    return if (next == null) stringResource(R.string.fs_no_upcoming_dates)
    else stringResource(R.string.fs_next_date, formatDisplayDate(next.toLocalDate()))
}

@Composable
private fun upcomingOptions() = listOf(
    stringResource(R.string.fs_budget_default) to null,
    stringResource(R.string.fs_one_day) to "1",
    stringResource(R.string.fs_one_week) to "7",
    stringResource(R.string.fs_two_weeks) to "14",
    stringResource(R.string.fs_one_month) to "oneMonth",
    stringResource(R.string.fs_rest_of_month) to "currentMonth",
)

private fun DayDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)
