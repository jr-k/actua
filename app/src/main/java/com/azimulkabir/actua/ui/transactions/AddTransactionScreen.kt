package com.azimulkabir.actua.ui.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.ActiveTagRepository
import com.azimulkabir.actua.data.location.ForegroundLocationPermission
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.ui.components.centsToInput
import com.azimulkabir.actua.ui.components.currencyInputPrefix
import com.azimulkabir.actua.ui.components.CalculatorAmountSheet
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.parseStoredDate
import com.azimulkabir.actua.ui.components.storageDate
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    editing: Transaction?,
    onBack: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit = {},
    modifier: Modifier = Modifier,
    accountOptions: List<String> = listOf("Everyday account", "Cash", "Credit card"),
    offBudgetAccountOptions: Set<String> = emptySet(),
    categoryOptions: List<String> = listOf("Groceries", "Dining", "Transport", "Rent"),
    payeeOptions: List<String> = emptyList(),
    accountBalanceLabels: Map<String, String> = emptyMap(),
    defaultAccount: String? = null,
    defaultCategory: String? = null,
    defaultType: Type = Type.EXPENSE,
    hideDecimalPlaces: Boolean = false,
    conventionalAmountEntry: Boolean = false,
    onPreviewRules: (Transaction) -> Transaction = { it },
    onFindNearbyPayees: (suspend () -> NearbyPayeeSearchResult)? = null,
    onSavePayeeLocation: (suspend (String) -> PayeeLocationSaveResult)? = null,
    onForgetPayeeLocation: (suspend (String) -> Boolean)? = null,
) {
    var amountCents by remember(editing) { mutableStateOf(abs(editing?.amountCents ?: 0L)) }
    var showCalculator by remember { mutableStateOf(false) }
    var amountExpression by remember(editing) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(editing) { mutableStateOf(false) }
    var payee by remember(editing) { mutableStateOf(editing?.payee ?: "") }
    var category by remember(editing, defaultCategory) {
        mutableStateOf(editing?.category ?: defaultCategory?.takeIf(categoryOptions::contains).orEmpty())
    }
    var account by remember(editing, accountOptions) {
        mutableStateOf(
            if (editing?.type == Type.TRANSFER && editing.amountCents >= 0) {
                editing.transferAccount ?: editing.account
            } else editing?.account ?: defaultAccount?.takeIf(accountOptions::contains)
                ?: accountOptions.firstOrNull().orEmpty()
        )
    }
    var transferAccount by remember(editing) {
        mutableStateOf(
            if (editing?.type == Type.TRANSFER && editing.amountCents >= 0) editing.account
            else editing?.transferAccount.orEmpty()
        )
    }
    var date by remember(editing) { mutableStateOf(editing?.date?.let(::parseStoredDate) ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var notes by remember(editing) { mutableStateOf(editing?.notes ?: "") }
    var cleared by remember(editing) { mutableStateOf(editing?.cleared ?: false) }
    var transactionType by remember(editing, defaultType) { mutableStateOf(editing?.type ?: defaultType) }
    var splitLines by remember(editing) { mutableStateOf(editing?.splits.orEmpty()) }
    var splitCalculatorIndex by remember { mutableStateOf<Int?>(null) }
    var splitAmountExpression by remember(editing) { mutableStateOf<String?>(null) }
    var rulesApplied by remember(editing) { mutableStateOf(false) }
    // A picker choice is explicit and must survive a rule preview triggered by a later payee
    // edit; a category filled in by an earlier rule preview is not.
    var categoryIsExplicit by remember(editing) { mutableStateOf(editing?.category?.isNotBlank() == true) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val tagRepository = remember { ActiveTagRepository(context) }
    var tagVersion by remember { mutableStateOf(0L) }
    val availableTags = remember(tagVersion) { tagRepository.tags(tagVersion) }
    val isOffBudget = account in offBudgetAccountOptions
    LaunchedEffect(isOffBudget) {
        if (isOffBudget) {
            category = ""
            categoryIsExplicit = false
            splitLines = splitLines.map { it.copy(category = "") }
        }
    }
    val isSplit = splitLines.isNotEmpty()
    val splitTotal = splitLines.sumOf { if (it.isOpposite) -it.amountCents else it.amountCents }
    val splitIsValid = !isSplit || (splitLines.size >= 2 && splitLines.all {
        (isOffBudget || it.category.isNotBlank()) && it.amountCents > 0
    } && splitTotal == amountCents)
    val canSave = amountCents > 0 && account.isNotBlank() &&
        (transactionType != Type.TRANSFER || transferAccount.isNotBlank()) && splitIsValid
    val cursorTransition = rememberInfiniteTransition(label = "Amount cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), repeatMode = RepeatMode.Reverse),
        label = "Amount cursor alpha",
    )
    val blinkingCursor = if (cursorAlpha > 0.5f) " │" else ""
    val currencyPrefix = currencyInputPrefix()
    val saveTransaction = {
        if (canSave) {
            onSave(
                Transaction(
                    id = editing?.id.orEmpty(),
                    date = storageDate(date),
                    payee = payee,
                    category = if (transactionType == Type.TRANSFER || isOffBudget) ""
                    else category,
                    account = account,
                    amount = (amountCents / 100L).toInt() * if (transactionType == Type.INCOME) 1 else -1,
                    cleared = cleared,
                    amountCents = amountCents * if (transactionType == Type.INCOME) 1 else -1,
                    type = transactionType,
                    transferAccount = transferAccount.takeIf { transactionType == Type.TRANSFER },
                    notes = notes,
                    splits = if (isOffBudget) splitLines.map { it.copy(category = "") } else splitLines,
                    rulesApplied = rulesApplied,
                    categoryIsExplicit = categoryIsExplicit,
                ),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
            }
            Text(stringResource(if (editing == null) R.string.transaction_add else R.string.transaction_edit),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (editing != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.transaction_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Type.entries.forEach { type ->
                    FilterChip(
                        selected = transactionType == type,
                        onClick = {
                            if (transactionType != type) rulesApplied = false
                            transactionType = type
                            if (type == Type.TRANSFER) splitLines = emptyList()
                        },
                        label = {
                            Text(
                                stringResource(type.labelRes()),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(Modifier.fillMaxWidth()) {
                val amountInput = when {
                    showCalculator && amountExpression != null -> amountExpression.orEmpty()
                    amountCents == 0L -> ""
                    else -> centsToInput(amountCents)
                }
                val amountPresentation = amountFieldPresentation(
                    currencyPrefix = currencyPrefix,
                    input = amountInput,
                    active = showCalculator,
                    cursor = blinkingCursor,
                    placeholder = stringResource(R.string.transaction_amount_label),
                )
                OutlinedTextField(
                    value = amountPresentation.value,
                    onValueChange = {}, readOnly = true,
                    placeholder = { if (amountPresentation.placeholder.isNotEmpty()) Text(amountPresentation.placeholder) },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Outlined.Calculate, contentDescription = null) },
                    supportingText = {
                        if (hideDecimalPlaces) Text(stringResource(R.string.transaction_decimal_hidden))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                EmptyAmountCaret(
                    visible = amountPresentation.showEmptyCaret,
                    alpha = cursorAlpha,
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                )
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        detectTapGestures {
                            amountExpression = null
                            showCalculator = true
                        }
                    },
                )
            }
            if (transactionType != Type.TRANSFER) {
                PickerTextField(
                    label = stringResource(R.string.transaction_payee), value = payee, options = payeeOptions,
                    supportingValues = accountBalanceLabels.mapKeys {
                        resources.getString(R.string.transaction_transfer_prefix, it.key)
                    },
                    onValueChange = { value ->
                        val transferTarget = accountOptions.firstOrNull {
                            value == resources.getString(R.string.transaction_transfer_prefix, it)
                        }
                        if (transferTarget != null) {
                            if (transferTarget != account) {
                                payee = ""
                                transferAccount = transferTarget
                                transactionType = Type.TRANSFER
                                category = ""
                                splitLines = emptyList()
                            }
                            return@PickerTextField
                        }
                        payee = value
                        if (editing == null && !isOffBudget && !isSplit) {
                            val preview = onPreviewRules(Transaction(
                                id = "",
                                account = account,
                                payee = value,
                                category = category,
                                amount = (amountCents / 100).toInt(),
                                amountCents = amountCents,
                                type = transactionType,
                                date = storageDate(date),
                                notes = notes,
                                cleared = cleared,
                            ))
                            payee = preview.payee
                            // A picker choice survives a rule the payee edit triggers; the rule
                            // may still fill in a category the user hasn't touched.
                            if (!categoryIsExplicit || category.isBlank()) category = preview.category
                            account = preview.account
                            cleared = preview.cleared
                            notes = preview.notes
                            parseStoredDate(preview.date)?.let { date = it }
                            amountCents = abs(preview.amountCents)
                            transactionType = preview.type
                            rulesApplied = preview.rulesApplied
                        }
                    },
                    allowCustom = true,
                    onFindNearby = onFindNearbyPayees,
                    onSavePayeeLocation = onSavePayeeLocation,
                    onForgetPayeeLocation = onForgetPayeeLocation,
                )
            }
            if (transactionType != Type.TRANSFER && !isSplit && !isOffBudget) {
                PickerTextField(
                    label = stringResource(R.string.transaction_category), value = category, options = categoryOptions,
                    onValueChange = { category = it; categoryIsExplicit = it.isNotBlank() },
                )
            }
            PickerTextField(
                label = stringResource(
                    if (transactionType == Type.TRANSFER) R.string.transaction_from else R.string.transaction_account,
                ),
                value = account, options = accountOptions,
                supportingValues = accountBalanceLabels,
                onValueChange = {
                    account = it
                    if (it in offBudgetAccountOptions) {
                        category = ""
                        splitLines = splitLines.map { line -> line.copy(category = "") }
                    }
                    if (transferAccount == it) { transferAccount = ""; rulesApplied = false }
                },
            )
            if (transactionType == Type.TRANSFER) {
                PickerTextField(
                    label = stringResource(R.string.transaction_to), value = transferAccount,
                    options = accountOptions.filterNot { it == account },
                    supportingValues = accountBalanceLabels,
                    onValueChange = { value ->
                        if (value == transferAccount) return@PickerTextField
                        transferAccount = value
                        if (editing == null && value.isNotBlank()) {
                            val preview = onPreviewRules(Transaction(
                                id = "",
                                account = account,
                                payee = payee,
                                category = category,
                                amount = (amountCents / 100).toInt(),
                                amountCents = amountCents,
                                type = Type.TRANSFER,
                                transferAccount = value,
                                date = storageDate(date),
                                notes = notes,
                                cleared = cleared,
                            ))
                            cleared = preview.cleared
                            notes = preview.notes
                            parseStoredDate(preview.date)?.let { date = it }
                            amountCents = abs(preview.amountCents)
                            rulesApplied = preview.rulesApplied
                        }
                    },
                )
            } else {
                if (!isSplit) {
                    FilledTonalButton(
                        onClick = {
                            splitLines = if (editing == null) listOf(SplitLine(), SplitLine())
                            else listOf(
                                SplitLine(category = category, amountCents = amountCents), SplitLine(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(
                            if (isOffBudget) R.string.transaction_split else R.string.transaction_split_multiple_categories,
                        ))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(
                                    if (isOffBudget) R.string.transaction_split else R.string.transaction_split_categories,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                category = if (isOffBudget) "" else splitLines.firstOrNull()?.category.orEmpty()
                                splitLines = emptyList()
                            }) { Text(stringResource(R.string.transaction_remove_split)) }
                        }
                        splitLines.forEachIndexed { index, line ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.transaction_split_number, index + 1),
                                    style = MaterialTheme.typography.labelLarge)
                                if (!isOffBudget) PickerTextField(
                                    label = stringResource(R.string.transaction_category),
                                    value = line.category,
                                    options = categoryOptions,
                                    onValueChange = { value ->
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(category = value)
                                        }
                                    },
                                )
                                Box(Modifier.fillMaxWidth()) {
                                    val splitAmountInput = when {
                                        splitCalculatorIndex == index && splitAmountExpression != null ->
                                            splitAmountExpression.orEmpty()
                                        line.amountCents == 0L -> ""
                                        else -> centsToInput(line.amountCents)
                                    }
                                    val splitAmountPresentation = amountFieldPresentation(
                                        currencyPrefix = currencyPrefix,
                                        input = splitAmountInput,
                                        active = splitCalculatorIndex == index,
                                        cursor = blinkingCursor,
                                        placeholder = stringResource(R.string.transaction_amount_label),
                                    )
                                    OutlinedTextField(
                                        value = splitAmountPresentation.value,
                                        onValueChange = {},
                                        readOnly = true,
                                        placeholder = {
                                            if (splitAmountPresentation.placeholder.isNotEmpty()) {
                                                Text(splitAmountPresentation.placeholder)
                                            }
                                        },
                                        singleLine = true,
                                        trailingIcon = { Icon(Icons.Outlined.Calculate, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    EmptyAmountCaret(
                                        visible = splitAmountPresentation.showEmptyCaret,
                                        alpha = cursorAlpha,
                                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                                    )
                                    Box(Modifier.matchParentSize().clickable {
                                        splitAmountExpression = null
                                        splitCalculatorIndex = index
                                    })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(
                                            if (line.isOpposite) R.string.transaction_opposite_direction
                                            else R.string.transaction_same_direction,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = line.isOpposite,
                                        onCheckedChange = { value ->
                                            splitLines = splitLines.toMutableList().also {
                                                it[index] = line.copy(isOpposite = value)
                                            }
                                        },
                                    )
                                }
                                if (line.amountCents == 0L && amountCents - splitTotal > 0) {
                                    TextButton(onClick = {
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(amountCents = amountCents - splitTotal)
                                        }
                                    }) {
                                        Text(stringResource(
                                            R.string.transaction_use_remaining,
                                            "$currencyPrefix${centsToInput(amountCents - splitTotal)}",
                                        ))
                                    }
                                }
                                PickerTextField(
                                    label = stringResource(R.string.transaction_payee_optional),
                                    value = line.payee,
                                    options = payeeOptions,
                                    supportingValues = accountBalanceLabels.mapKeys {
                                        resources.getString(R.string.transaction_transfer_prefix, it.key)
                                    },
                                    onValueChange = { value ->
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(payee = value)
                                        }
                                    },
                                    allowCustom = true,
                                )
                                TagAutocompleteField(
                                    value = line.notes,
                                    tags = availableTags,
                                    onValueChange = { value ->
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(notes = value)
                                        }
                                    },
                                    onCreateTag = { name -> tagRepository.create(name)?.also { tagVersion += 1 } },
                                    label = stringResource(R.string.transaction_split_note),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (splitLines.size > 2) {
                                    TextButton(onClick = {
                                        splitLines = splitLines.toMutableList().also { it.removeAt(index) }
                                    }) {
                                        Text(stringResource(R.string.transaction_remove_this_split),
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (index < splitLines.lastIndex) HorizontalDivider()
                            }
                        }
                        FilledTonalButton(
                            onClick = { splitLines = splitLines + SplitLine() },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = MaterialTheme.shapes.large,
                        ) { Text(stringResource(R.string.transaction_add_another_split)) }
                        Text(
                            if (splitTotal == amountCents) stringResource(R.string.transaction_split_total_matches)
                            else stringResource(
                                R.string.transaction_remaining,
                                "$currencyPrefix${centsToInput(amountCents - splitTotal)}",
                            ),
                            color = if (splitTotal == amountCents) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatDate(date), onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(R.string.transaction_date)) }, singleLine = true,
                    trailingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showDatePicker = true })
            }
            TagAutocompleteField(
                value = notes,
                tags = availableTags,
                onValueChange = { notes = it },
                onCreateTag = { name -> tagRepository.create(name)?.also { tagVersion += 1 } },
                label = stringResource(R.string.transaction_notes),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.transaction_cleared), modifier = Modifier.weight(1f))
                Switch(checked = cleared, onCheckedChange = { cleared = it })
            }
            TransactionSaveButton(
                canSave = canSave,
                onClick = saveTransaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }
    }
    if (showCalculator) CalculatorAmountSheet(
        title = stringResource(if (editing == null) R.string.transaction_amount else R.string.transaction_edit_amount),
        initialCents = amountCents,
        conventionalAmountEntry = conventionalAmountEntry,
        onDismiss = {
            showCalculator = false
            amountExpression = null
        },
        onApply = { amountCents = it },
        onExpressionChange = { amountExpression = it },
    )
    splitCalculatorIndex?.let { index ->
        val line = splitLines.getOrNull(index)
        if (line != null) CalculatorAmountSheet(
            title = stringResource(R.string.transaction_split_amount, index + 1),
            initialCents = line.amountCents,
            conventionalAmountEntry = conventionalAmountEntry,
            onDismiss = {
                splitCalculatorIndex = null
                splitAmountExpression = null
            },
            onApply = { value ->
                splitLines = splitLines.toMutableList().also { it[index] = line.copy(amountCents = value) }
            },
            onExpressionChange = { splitAmountExpression = it },
        ) else splitCalculatorIndex = null
    }
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.transaction_delete_question)) },
            text = { Text(stringResource(R.string.transaction_delete_budget_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(editing)
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) {
                Text(stringResource(R.string.action_cancel))
            } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun Type.labelRes(): Int = when (this) {
    Type.EXPENSE -> R.string.transaction_type_expense
    Type.INCOME -> R.string.transaction_type_income
    Type.TRANSFER -> R.string.transaction_type_transfer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSaveButton(
    canSave: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = canSave,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null)
        Text(stringResource(R.string.action_save), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun PickerTextField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    allowCustom: Boolean = false,
    supportingValues: Map<String, String> = emptyMap(),
    onFindNearby: (suspend () -> NearbyPayeeSearchResult)? = null,
    onSavePayeeLocation: (suspend (String) -> PayeeLocationSaveResult)? = null,
    onForgetPayeeLocation: (suspend (String) -> Boolean)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var knownNearbyPayees by remember { mutableStateOf<Set<String>?>(null) }
    var locationActionLoading by remember { mutableStateOf(false) }
    var locationActionMessage by remember { mutableStateOf<String?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<PayeeLocationInlineAction?>(null) }
    var showPermissionExplanation by remember { mutableStateOf(false) }
    val transferPrefix = resources.getString(R.string.transaction_transfer_prefix, "")
    val inlineAction = payeeLocationInlineAction(
        payee = value,
        ordinaryPayees = options.filterNot { it.startsWith(transferPrefix) }.toSet(),
        knownNearbyPayees = knownNearbyPayees,
        canFindNearby = onFindNearby != null,
        canSaveLocation = onSavePayeeLocation != null,
    )
    val runLocationAction: (PayeeLocationInlineAction) -> Unit = { action ->
        coroutineScope.launch {
            locationActionLoading = true
            locationActionMessage = null
            when (action) {
                PayeeLocationInlineAction.Nearby -> {
                    val result = runCatching { onFindNearby?.invoke() }.getOrNull()
                    if (result == null) {
                        locationActionMessage = resources.getString(R.string.location_nearby_failed)
                    } else {
                        knownNearbyPayees = result.options.mapTo(mutableSetOf()) { it.payee }
                        val closest = result.options.firstOrNull()
                        if (closest != null) {
                            onValueChange(closest.payee)
                            locationActionMessage = resources.getString(
                                R.string.location_selected_payee,
                                closest.payee,
                                closest.distance,
                            )
                        } else {
                            locationActionMessage = result.message
                                ?: resources.getString(R.string.location_none_within_range)
                        }
                    }
                }
                PayeeLocationInlineAction.SaveLocation -> {
                    val result = runCatching { onSavePayeeLocation?.invoke(value) }.getOrNull()
                    if (result == null) {
                        locationActionMessage = resources.getString(R.string.location_save_failed)
                    } else {
                        locationActionMessage = result.message
                        if (result.nowNearby) {
                            knownNearbyPayees = knownNearbyPayees.orEmpty() + value
                        }
                    }
                }
            }
            locationActionLoading = false
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (grants.values.any { it } || ForegroundLocationPermission.isGranted(context)) {
            action?.let(runLocationAction)
        } else {
            locationActionMessage = resources.getString(R.string.location_permission_not_granted_choose)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            singleLine = true,
            readOnly = true,
            trailingIcon = inlineAction?.let { action ->
                {
                    TextButton(
                        onClick = {
                            if (ForegroundLocationPermission.isGranted(context)) {
                                runLocationAction(action)
                            } else {
                                pendingPermissionAction = action
                                showPermissionExplanation = true
                            }
                        },
                        enabled = !locationActionLoading,
                    ) {
                        if (locationActionLoading) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null)
                            Text(
                                stringResource(
                                    if (action == PayeeLocationInlineAction.Nearby) R.string.location_nearby
                                    else R.string.location_save,
                                ),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            },
            supportingText = locationActionMessage?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize()
                .padding(end = if (inlineAction == null) 0.dp else 126.dp)
                .clickable { showPicker = true },
        )
    }
    if (showPicker) SearchableTransactionPicker(
        title = if (label == resources.getString(R.string.transaction_payee_optional)) {
            resources.getString(R.string.transaction_payee)
        } else label,
        selected = value,
        options = options,
        allowCustom = allowCustom,
        supportingValues = supportingValues,
        onFindNearby = onFindNearby,
        onForgetPayeeLocation = onForgetPayeeLocation,
        onNearbyResult = { result ->
            knownNearbyPayees = result.options.mapTo(mutableSetOf()) { it.payee }
        },
        onDismiss = { showPicker = false },
        onSelect = {
            onValueChange(it)
            showPicker = false
        },
    )
    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = {
                showPermissionExplanation = false
                pendingPermissionAction = null
            },
            title = { Text(stringResource(R.string.location_use_question)) },
            text = {
                Text(
                    stringResource(R.string.location_explanation_save),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionExplanation = false
                    locationPermissionLauncher.launch(ForegroundLocationPermission.permissions)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionExplanation = false
                    pendingPermissionAction = null
                }) { Text(stringResource(R.string.action_not_now)) }
            },
        )
    }
}

@Composable
private fun SearchableTransactionPicker(
    title: String,
    selected: String,
    options: List<String>,
    allowCustom: Boolean,
    supportingValues: Map<String, String>,
    onFindNearby: (suspend () -> NearbyPayeeSearchResult)?,
    onForgetPayeeLocation: (suspend (String) -> Boolean)?,
    onNearbyResult: (NearbyPayeeSearchResult) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var nearbyLoading by remember { mutableStateOf(false) }
    var nearbyOptions by remember { mutableStateOf(emptyList<NearbyPayeeOption>()) }
    var nearbyMessage by remember { mutableStateOf<String?>(null) }
    var showPermissionExplanation by remember { mutableStateOf(false) }
    var forgettingLocationId by remember { mutableStateOf<String?>(null) }
    val loadNearby = {
        if (!nearbyLoading) onFindNearby?.let { findNearby ->
            coroutineScope.launch {
                nearbyLoading = true
                nearbyMessage = null
                val result = runCatching { findNearby() }.getOrElse {
                    NearbyPayeeSearchResult(
                        message = resources.getString(R.string.location_find_failed),
                    )
                }
                nearbyOptions = result.options
                nearbyMessage = result.message
                onNearbyResult(result)
                nearbyLoading = false
            }
        }
        Unit
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it } || ForegroundLocationPermission.isGranted(context)) {
            loadNearby()
        } else {
            nearbyOptions = emptyList()
            nearbyMessage = resources.getString(R.string.location_permission_not_granted_search)
        }
    }
    LaunchedEffect(Unit) {
        if (onFindNearby != null && ForegroundLocationPermission.isGranted(context)) {
            loadNearby()
        }
    }
    val uniqueOptions = remember(options) { options.distinct() }
    val transferPrefix = resources.getString(R.string.transaction_transfer_prefix, "")
    val searchResults = remember(query, uniqueOptions, transferPrefix) {
        filterPickerOptions(uniqueOptions, query, transferPrefix)
    }
    // Only `grouped` (query.isBlank()) or `transferOptions` (also query.isBlank()) are ever
    // shown at once, but both were being sorted/grouped on every keystroke regardless of which
    // (if either) is actually visible; remember them keyed on the option list instead.
    val transferOptions = remember(uniqueOptions, transferPrefix) {
        alphabetizePickerOptions(uniqueOptions.filter { it.startsWith(transferPrefix) }, transferPrefix)
    }
    val grouped = remember(uniqueOptions, transferPrefix) {
        uniqueOptions.filterNot { it.startsWith(transferPrefix) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .groupBy { it.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetterOrDigit)?.toString() ?: "#" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                ActuaScreenHeader(title = title, onBack = onDismiss)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(stringResource(
                            if (allowCustom) R.string.picker_find_or_add else R.string.picker_search,
                            title.lowercase(),
                        ))
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)
                        .focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.sm, bottom = Spacing.xxl,
                    ),
                ) {
                    if (selected.isNotBlank() && query.isBlank() && selected in uniqueOptions) {
                        item { PickerSectionLabel(stringResource(R.string.picker_selected)) }
                        item {
                            PickerGroup(
                                listOf(selected), selected, supportingValues = supportingValues,
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (query.isBlank() && onFindNearby != null) {
                        item { PickerSectionLabel(stringResource(R.string.picker_nearby)) }
                        item {
                            FilledTonalButton(
                                onClick = {
                                    keyboard?.hide()
                                    if (ForegroundLocationPermission.isGranted(context)) {
                                        loadNearby()
                                    } else {
                                        showPermissionExplanation = true
                                    }
                                },
                                enabled = !nearbyLoading,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                if (nearbyLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null)
                                    Text(
                                        stringResource(
                                            if (nearbyOptions.isEmpty()) R.string.location_find_payees
                                            else R.string.location_refresh_payees,
                                        ),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                        if (nearbyOptions.isNotEmpty()) {
                            item {
                                NearbyPickerGroup(
                                    options = nearbyOptions,
                                    selected = selected,
                                    forgettingLocationId = forgettingLocationId,
                                    onSelect = { onSelect(it.payee) },
                                    onForget = onForgetPayeeLocation?.let { forgetLocation ->
                                        { option ->
                                            coroutineScope.launch {
                                                forgettingLocationId = option.locationId
                                                val deleted = runCatching {
                                                    forgetLocation(option.locationId)
                                                }.getOrDefault(false)
                                                if (deleted) {
                                                    nearbyOptions = nearbyOptions.filterNot {
                                                        it.locationId == option.locationId
                                                    }
                                                    nearbyMessage = resources.getString(
                                                        R.string.location_forgotten,
                                                        option.payee,
                                                    )
                                                    onNearbyResult(NearbyPayeeSearchResult(nearbyOptions, nearbyMessage))
                                                } else {
                                                    nearbyMessage = resources.getString(R.string.location_forget_failed)
                                                }
                                                forgettingLocationId = null
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        nearbyMessage?.let { message ->
                            item {
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                    if (query.isNotBlank() && searchResults.isNotEmpty()) {
                        item { PickerSectionLabel(stringResource(R.string.picker_search_results)) }
                        item {
                            PickerGroup(
                                options = searchResults,
                                selected = selected,
                                displayText = { it.removePrefix(transferPrefix) },
                                supportingValues = supportingValues,
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (query.isBlank() && transferOptions.isNotEmpty()) {
                        item { PickerSectionLabel(stringResource(R.string.picker_payments_transfers)) }
                        item {
                            PickerGroup(
                                options = transferOptions,
                                selected = selected,
                                displayText = { it.removePrefix(transferPrefix) },
                                supportingValues = supportingValues,
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (allowCustom && query.isNotBlank() && uniqueOptions.none {
                            it.equals(query.trim(), ignoreCase = true)
                        }
                    ) {
                        item { PickerSectionLabel(stringResource(R.string.picker_new, title.lowercase())) }
                        item {
                            PickerGroup(
                                options = listOf(query.trim()),
                                selected = "",
                                displayText = { resources.getString(R.string.picker_add_value, it) },
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (query.isBlank()) {
                        grouped.forEach { (letter, entries) ->
                            item(key = "heading-$letter") { PickerSectionLabel(letter) }
                            item(key = "group-$letter") {
                                PickerGroup(
                                    entries, selected, supportingValues = supportingValues,
                                    onSelect = onSelect,
                                )
                            }
                        }
                    }
                    if (query.isNotBlank() && searchResults.isEmpty() && !allowCustom) {
                        item {
                            Text(
                                stringResource(R.string.picker_no_matches),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text(stringResource(R.string.location_use_question)) },
            text = {
                Text(
                    stringResource(R.string.location_explanation_find),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionExplanation = false
                    locationPermissionLauncher.launch(ForegroundLocationPermission.permissions)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
        )
    }
}

data class NearbyPayeeSearchResult(
    val options: List<NearbyPayeeOption> = emptyList(),
    val message: String? = null,
)

data class NearbyPayeeOption(
    val payee: String,
    val distance: String,
    val locationId: String,
)

data class PayeeLocationSaveResult(
    val nowNearby: Boolean,
    val message: String,
)

internal enum class PayeeLocationInlineAction { Nearby, SaveLocation }

internal fun payeeLocationInlineAction(
    payee: String,
    ordinaryPayees: Set<String>,
    knownNearbyPayees: Set<String>?,
    canFindNearby: Boolean,
    canSaveLocation: Boolean,
): PayeeLocationInlineAction? = when {
    payee.isBlank() && canFindNearby -> PayeeLocationInlineAction.Nearby
    payee in ordinaryPayees && canSaveLocation && knownNearbyPayees?.contains(payee) != true ->
        PayeeLocationInlineAction.SaveLocation
    else -> null
}

internal data class AmountFieldPresentation(
    val value: String,
    val placeholder: String,
    val showEmptyCaret: Boolean,
)

internal fun amountFieldPresentation(
    currencyPrefix: String,
    input: String,
    active: Boolean,
    cursor: String,
    placeholder: String = "",
): AmountFieldPresentation = if (input.isEmpty()) {
    AmountFieldPresentation(
        value = "",
        placeholder = if (active) "" else placeholder,
        showEmptyCaret = active,
    )
} else {
    AmountFieldPresentation(
        value = currencyPrefix + input + if (active) cursor else "",
        placeholder = placeholder,
        showEmptyCaret = false,
    )
}

@Composable
private fun EmptyAmountCaret(
    visible: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier.height(56.dp).padding(start = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("│", color = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    }
}

internal fun filterPickerOptions(
    options: List<String>,
    query: String,
    transferPrefix: String = "",
): List<String> {
    val term = query.trim()
    if (term.isEmpty()) return alphabetizePickerOptions(options, transferPrefix)
    return alphabetizePickerOptions(
        options.filter { it.removePrefix(transferPrefix).contains(term, ignoreCase = true) },
        transferPrefix,
    )
}

internal fun alphabetizePickerOptions(
    options: List<String>,
    transferPrefix: String = "",
): List<String> = options.distinct()
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.removePrefix(transferPrefix) })

@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp, start = 4.dp),
    )
}

@Composable
private fun PickerGroup(
    options: List<String>,
    selected: String,
    displayText: (String) -> String = { it },
    supportingValues: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            options.forEachIndexed { index, option ->
                PickerRow(
                    text = displayText(option),
                    supportingText = supportingValues[option],
                    selected = option == selected,
                ) { onSelect(option) }
                if (index < options.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                }
            }
        }
    }
}

@Composable
private fun NearbyPickerGroup(
    options: List<NearbyPayeeOption>,
    selected: String,
    forgettingLocationId: String?,
    onSelect: (NearbyPayeeOption) -> Unit,
    onForget: ((NearbyPayeeOption) -> Unit)?,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option.payee == selected, onClick = { onSelect(option) })
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(option.payee, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            option.distance,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    onForget?.let {
                        TextButton(
                            onClick = { it(option) },
                            enabled = forgettingLocationId == null,
                        ) {
                            if (forgettingLocationId == option.locationId) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.action_forget), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (index < options.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    text: String,
    supportingText: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        supportingText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
