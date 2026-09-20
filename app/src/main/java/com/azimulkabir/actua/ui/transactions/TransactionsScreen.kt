package com.azimulkabir.actua.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.azimulkabir.actua.model.TransactionStatusFilter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.R
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.formatStoredDate
import com.azimulkabir.actua.ui.components.CalculatorAmountState
import com.azimulkabir.actua.ui.components.CompactCalculatorPad
import com.azimulkabir.actua.ui.components.coloredTagText
import com.azimulkabir.actua.ui.components.rememberActualTagColors
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.theme.AmountTypography
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.success
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

private val sampleTransactions = listOf(
    Transaction("1", "Today", "Agora Super Shop", "Groceries", "Everyday account", -2_450, true),
    Transaction("2", "Today", "Salary", "Income", "Everyday account", 72_000, true),
    Transaction("3", "Today", "Pathao", "Transport", "Credit card", -380, false),
    Transaction("4", "Yesterday", "DESCO", "Electricity", "Everyday account", -2_700, true),
    Transaction("5", "Yesterday", "Coffee World", "Dining", "Credit card", -620, false),
    Transaction("6", "1 Sep 2026", "Landlord", "Rent", "Everyday account", -35_000, true),
    Transaction("7", "1 Sep 2026", "ISP", "Internet", "Everyday account", -1_500, true),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    accountName: String?,
    categoryName: String? = null,
    month: String? = null,
    onBack: () -> Unit,
    onEdit: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    transactions: List<Transaction> = sampleTransactions,
    transactionStatusFilter: TransactionStatusFilter = TransactionStatusFilter.ALL,
    onTransactionStatusFilterChange: (TransactionStatusFilter) -> Unit = {},
    hideDecimalPlaces: Boolean = false,
    conventionalAmountEntry: Boolean = true,
    groupTransactionsByDate: Boolean = true,
    onGroupTransactionsByDateChange: (Boolean) -> Unit = {},
    hideReconciledTransactions: Boolean = false,
    onHideReconciledTransactionsChange: (Boolean) -> Unit = {},
    onSetCleared: (Transaction, Boolean) -> Unit = { _, _ -> },
    onReconcileAccount: (Account) -> Boolean = { false },
    onCreateReconciliationAdjustment: (Account, Long) -> Boolean = { _, _ -> false },
    onDelete: (Transaction) -> Unit = {},
    onDuplicate: (Transaction) -> Unit = {},
    onDuplicateMultiple: (List<Transaction>) -> Unit = {},
    account: Account? = null,
    creditCard: CreditCardStatus? = null,
    onSaveAccountNote: (String) -> Unit = {},
    searchTransactions: (suspend (String) -> List<Transaction>)? = null,
    initialSearch: String = "",
    showBackButton: Boolean = true,
    showCurrentBalanceSummary: Boolean = true,
    onShowCurrentBalanceSummaryChange: (Boolean) -> Unit = {},
    showRunningBalance: Boolean = false,
    onShowRunningBalanceChange: (Boolean) -> Unit = {},
    showNotes: Boolean = true,
    allTransactions: List<Transaction> = transactions,
    onReconcileVisibilityChange: (Boolean) -> Unit = {},
    returnToRootRequest: Int = 0,
    onDeleteMultiple: (List<Transaction>) -> Unit = {},
    onLinkSchedule: (List<Transaction>, String) -> Unit = { _, _ -> },
    onUnlinkSchedule: (List<Transaction>) -> Unit = {},
    onViewSchedule: (String) -> Unit = {},
    linkableSchedules: List<ScheduleOption> = emptyList(),
    onViewStatements: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var search by remember(initialSearch) { mutableStateOf(initialSearch) }
    var showSearch by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var viewed by remember { mutableStateOf<Transaction?>(null) }
    var selectionModeOn by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(selectionModeOn) { if (!selectionModeOn) selectedIds = emptySet() }
    var bulkMenuOpen by remember { mutableStateOf(false) }
    var showLinkSchedulePicker by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var reconcileOpen by remember(account?.id) { mutableStateOf(false) }
    LaunchedEffect(reconcileOpen) { onReconcileVisibilityChange(reconcileOpen) }
    DisposableEffect(Unit) {
        onDispose { onReconcileVisibilityChange(false) }
    }
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) {
            when {
                viewed != null -> viewed = null
                showSearch -> showSearch = false
                else -> listState.animateScrollToItem(0)
            }
        }
    }
    var accountNote by remember(account) { mutableStateOf(account?.note.orEmpty()) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val tagColors = rememberActualTagColors(transactions)
    val accountDetailPreferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            "account_detail_preferences",
            android.content.Context.MODE_PRIVATE,
        )
    }
    var showAccountNotes by remember(account?.id) {
        mutableStateOf(account?.let {
            accountDetailPreferences.getBoolean("show_notes_${it.id}", true)
        } ?: true)
    }
    var showCreditCardSection by remember(account?.id) {
        mutableStateOf(account?.let {
            accountDetailPreferences.getBoolean("show_credit_card_section_${it.id}", true)
        } ?: true)
    }

    var searchResults by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var completedQuery by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf(false) }
    LaunchedEffect(search, transactions, searchTransactions) {
        completedQuery = null
        searchError = false
        if (search.isNotBlank() && searchTransactions != null) {
            delay(200)
            try {
                searchResults = searchTransactions(search)
                completedQuery = search
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                searchError = true
            }
        }
    }
    val searchingDatabase = search.isNotBlank() && searchTransactions != null
    val candidates = if (searchingDatabase) {
        if (completedQuery == search) searchResults else emptyList()
    } else transactions
    // `candidates`/filters change far less often than this composable recomposes (e.g. every
    // row tap in selection mode), so this needs its own remember rather than recomputing the
    // filter over the whole transaction list on every recomposition.
    val visible = remember(candidates, accountName, categoryName, month, hideReconciledTransactions, transactionStatusFilter, searchingDatabase, search) {
        // transactionStatusFilter (e.g. "Reconciled") supersedes the hideReconciledTransactions
        // preference, matching ActualBudgetDatabase.fetchTransactions — otherwise picking the
        // "Reconciled" chip while "hide reconciled" is on would filter every result back out.
        candidates.filter {
            (accountName == null || it.account == accountName) &&
                (categoryName == null || it.category == categoryName) &&
                (month == null || it.date.filter(Char::isDigit).startsWith(month.replace("-", ""))) &&
                (transactionStatusFilter != TransactionStatusFilter.ALL || !hideReconciledTransactions || !it.reconciled) &&
                (searchingDatabase || search.isBlank() ||
                    (listOf(it.payee, it.category, it.account, it.notes, it.transferAccount.orEmpty()) +
                        it.splits.flatMap { split -> listOf(split.payee, split.notes, split.category) })
                        .any { text -> text.contains(search, ignoreCase = true) })
        }
    }
    // Otherwise re-grouped inside the LazyColumn content block on every recomposition of this
    // screen (e.g. every selection-mode tap), not just when `visible` actually changes.
    val groupedByDate = remember(visible) { visible.groupBy { it.date } }
    val runningBalances = remember(allTransactions, accountName, showRunningBalance) {
        if (showRunningBalance && accountName != null) accountRunningBalances(allTransactions, accountName)
        else emptyMap()
    }

    AnimatedContent(
        targetState = reconcileOpen && account != null,
        transitionSpec = {
            if (targetState) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 10 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 })
            // Reconciliation and the register are both large trees. Only their layers should
            // move during navigation; interpolating bounds causes avoidable list remeasurement.
            }.using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false))
        },
        label = "Reconcile navigation motion",
    ) { showingReconcile ->
    if (showingReconcile && account != null) {
        ReconcileAccountScreen(
            modifier = modifier,
            account = account,
            // Otherwise re-filtered on every recomposition of this AnimatedContent branch, not
            // just when `transactions`/`account` actually change.
            transactions = remember(transactions, account.name) { transactions.filter { it.account == account.name } },
            // Reconciliation always shows exact cents, even when normal lists hide decimals.
            hideDecimalPlaces = false,
            conventionalAmountEntry = conventionalAmountEntry,
            onBack = { reconcileOpen = false },
            onSetCleared = onSetCleared,
            onReconcile = { if (onReconcileAccount(account)) reconcileOpen = false },
            onCreateAdjustment = { difference -> onCreateReconciliationAdjustment(account, difference) },
        )
    } else Column(modifier = modifier.fillMaxSize()) {
        ActuaScreenHeader(
            title = categoryName ?: accountName ?: stringResource(
                if (showBackButton) R.string.transactions_all_accounts else R.string.transactions_title,
            ),
            onBack = if (showBackButton) onBack else null,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.transactions_search))
                    }
                    IconButton(onClick = { selectionModeOn = !selectionModeOn }) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(
                                if (selectionModeOn) R.string.transactions_exit_selection else R.string.transactions_select,
                            ),
                            tint = if (selectionModeOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.transactions_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ToggleItem(stringResource(R.string.transactions_group_by_date),
                                groupTransactionsByDate, onGroupTransactionsByDateChange)
                            account?.let { selectedAccount ->
                                if (!selectedAccount.closed) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.transactions_reconcile)) },
                                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                        onClick = { menuOpen = false; reconcileOpen = true },
                                    )
                                    HorizontalDivider()
                                }
                                ToggleItem(
                                    stringResource(R.string.transactions_show_balance_summary),
                                    showCurrentBalanceSummary,
                                    onShowCurrentBalanceSummaryChange,
                                )
                                ToggleItem(
                                    stringResource(R.string.transactions_running_balance),
                                    showRunningBalance,
                                    onShowRunningBalanceChange,
                                )
                                if (showNotes) {
                                    ToggleItem(stringResource(R.string.transactions_show_notes), showAccountNotes) { show ->
                                        showAccountNotes = show
                                        accountDetailPreferences.edit()
                                            .putBoolean("show_notes_${selectedAccount.id}", show).apply()
                                    }
                                }
                                if (creditCard != null) {
                                    ToggleItem(stringResource(R.string.transactions_show_credit_card), showCreditCardSection) { show ->
                                        showCreditCardSection = show
                                        accountDetailPreferences.edit()
                                            .putBoolean("show_credit_card_section_${selectedAccount.id}", show).apply()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
        ) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.transactions_search)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        AnimatedVisibility(
            visible = selectionModeOn,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
        ) {
            val selectedTransactions = visible.filter { it.id in selectedIds }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selectedIds.isEmpty()) stringResource(R.string.transactions_tap_to_select)
                        else pluralStringResource(R.plurals.transactions_selected, selectedIds.size, selectedIds.size),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { selectionModeOn = false }) { Text(stringResource(R.string.action_cancel)) }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { bulkMenuOpen = true }, enabled = selectedTransactions.isNotEmpty()) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.transactions_bulk_actions))
                        }
                        DropdownMenu(expanded = bulkMenuOpen, onDismissRequest = { bulkMenuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.transactions_mark_cleared)) }, onClick = {
                                bulkMenuOpen = false
                                selectedTransactions.filterNot { it.cleared }.forEach { onSetCleared(it, true) }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.transactions_mark_uncleared)) }, onClick = {
                                bulkMenuOpen = false
                                selectedTransactions.filter { it.cleared }.forEach { onSetCleared(it, false) }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = {
                                bulkMenuOpen = false
                                onDuplicateMultiple(selectedTransactions)
                                selectionModeOn = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.transactions_link_schedule)) }, onClick = {
                                bulkMenuOpen = false
                                showLinkSchedulePicker = true
                            })
                            if (selectedTransactions.any { it.scheduleId != null }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.transactions_unlink_schedule)) }, onClick = {
                                    bulkMenuOpen = false
                                    onUnlinkSchedule(selectedTransactions.filter { it.scheduleId != null })
                                    selectionModeOn = false
                                })
                            }
                            if (selectedTransactions.size == 1) {
                                selectedTransactions.first().scheduleId?.let { scheduleId ->
                                    DropdownMenuItem(text = { Text(stringResource(R.string.transactions_view_schedule)) }, onClick = {
                                        bulkMenuOpen = false
                                        selectionModeOn = false
                                        onViewSchedule(scheduleId)
                                    })
                                }
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { bulkMenuOpen = false; confirmBulkDelete = true },
                            )
                        }
                    }
                }
            }
        }
        if (searchingDatabase && completedQuery != search) {
            Text(stringResource(if (searchError) R.string.search_failed else R.string.search_searching),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.ALL,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.ALL) },
                label = { Text(stringResource(R.string.transactions_filter_all)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.UNCATEGORIZED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.UNCATEGORIZED) },
                label = { Text(stringResource(R.string.transactions_filter_uncategorized)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.UNCLEARED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.UNCLEARED) },
                label = { Text(stringResource(R.string.transactions_filter_uncleared)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.CLEARED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.CLEARED) },
                label = { Text(stringResource(R.string.transactions_filter_cleared)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.RECONCILED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.RECONCILED) },
                label = { Text(stringResource(R.string.transactions_filter_reconciled)) }
            )
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                account?.let { selectedAccount ->
                    item("account-details") {
                        AccountDetails(
                            selectedAccount,
                            creditCard,
                            accountNote,
                            { savedNote -> accountNote = savedNote; onSaveAccountNote(savedNote) },
                            hideDecimalPlaces,
                            showCurrentBalanceSummary,
                            showNotes && showAccountNotes,
                            onViewStatements,
                            showCreditCardSection,
                        )
                    }
                }
                item("transaction-total") {
                    val total = visible.sumOf { it.amountCents }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = 10.dp)) {
                        Text(pluralStringResource(R.plurals.transactions_count, visible.size, visible.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Amount(total, FontWeight.Bold, hideDecimalPlaces)
                    }
                }
                if (visible.isEmpty()) item("empty-transactions") {
                    Text(
                        when {
                            search.isNotBlank() -> stringResource(R.string.search_no_matching_transactions)
                            transactionStatusFilter == TransactionStatusFilter.UNCATEGORIZED ->
                                stringResource(R.string.transactions_empty_uncategorized)
                            transactionStatusFilter == TransactionStatusFilter.UNCLEARED ->
                                stringResource(R.string.transactions_empty_uncleared)
                            transactionStatusFilter == TransactionStatusFilter.CLEARED ->
                                stringResource(R.string.transactions_empty_cleared)
                            transactionStatusFilter == TransactionStatusFilter.RECONCILED ->
                                stringResource(R.string.transactions_empty_reconciled)
                            hideReconciledTransactions ->
                                stringResource(R.string.transactions_empty_unreconciled)
                            else -> stringResource(R.string.transactions_empty)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screenHorizontal),
                    )
                }
                if (groupTransactionsByDate) {
                    groupedByDate.forEach { (date, transactions) ->
                        stickyHeader(key = date) {
                            Text(formatTransactionDate(date), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm))
                        }
                        items(transactions, key = { it.id }) { transaction ->
                            // Every visible row reads the same `selectedIds` set, so a plain
                            // `transaction.id in selectedIds` recomposes every visible row on any
                            // selection toggle. derivedStateOf only reports a change (and thus only
                            // recomposes) the row(s) whose membership actually flipped.
                            val isSelected by remember(transaction.id) { derivedStateOf { transaction.id in selectedIds } }
                            TransactionRow(transaction, hideDecimalPlaces, showDate = false,
                                onClick = {
                                    if (selectionModeOn) {
                                        selectedIds = selectedIds.toggle(transaction.id)
                                    } else viewed = transaction
                                },
                                showAccount = accountName == null,
                                onLongClick = {
                                    if (selectionModeOn) selectedIds = selectedIds.toggle(transaction.id)
                                    else selected = transaction
                                },
                                onClearedClick = { onSetCleared(transaction, !transaction.cleared) }, tagColors = tagColors,
                                selectionMode = selectionModeOn, selected = isSelected,
                                runningBalanceCents = runningBalances[transaction.id])
                        }
                    }
                } else {
                    items(visible, key = { it.id }) { transaction ->
                        val isSelected by remember(transaction.id) { derivedStateOf { transaction.id in selectedIds } }
                        TransactionRow(transaction, hideDecimalPlaces, showDate = true,
                            onClick = {
                                if (selectionModeOn) {
                                    selectedIds = selectedIds.toggle(transaction.id)
                                } else viewed = transaction
                            },
                            showAccount = accountName == null,
                            onLongClick = {
                                if (selectionModeOn) selectedIds = selectedIds.toggle(transaction.id)
                                else selected = transaction
                            },
                            onClearedClick = { onSetCleared(transaction, !transaction.cleared) }, tagColors = tagColors,
                            selectionMode = selectionModeOn, selected = isSelected,
                            runningBalanceCents = runningBalances[transaction.id])
                    }
                }
            }
        }
    }
    }
    viewed?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { viewed = null },
            onEdit = { viewed = null; onEdit(transaction) },
            onDelete = { viewed = null; onDelete(transaction) },
            onDuplicate = { viewed = null; onDuplicate(transaction) },
            tagColors = tagColors,
        )
    }
    selected?.let { transaction ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ActuaSheetTitle(
                    transactionRowPresentation(
                        transaction,
                        showAccount = false,
                        resources = resources,
                    ).title,
                )
                Action(stringResource(R.string.transaction_edit)) { selected = null; onEdit(transaction) }
                Action(stringResource(R.string.transactions_duplicate_transaction)) { selected = null; onDuplicate(transaction) }
                Action(stringResource(
                    if (transaction.cleared) R.string.transactions_mark_uncleared else R.string.transactions_mark_cleared,
                )) {
                    selected = null
                    onSetCleared(transaction, !transaction.cleared)
                }
                Action(stringResource(R.string.action_select)) {
                    selected = null
                    selectionModeOn = true
                    selectedIds = setOf(transaction.id)
                }
                Action(stringResource(R.string.transactions_delete_transaction), destructive = true) {
                    selected = null
                    onDelete(transaction)
                }
            }
        }
    }
    if (confirmBulkDelete) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(pluralStringResource(R.plurals.transactions_delete_count_question, count, count)) },
            text = { Text(stringResource(R.string.transactions_delete_multiple_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMultiple(visible.filter { it.id in selectedIds })
                    confirmBulkDelete = false
                    selectionModeOn = false
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) {
                Text(stringResource(R.string.action_cancel))
            } },
        )
    }
    if (showLinkSchedulePicker) {
        ModalBottomSheet(onDismissRequest = { showLinkSchedulePicker = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ActuaSheetTitle(stringResource(R.string.transactions_link_schedule))
                if (linkableSchedules.isEmpty()) {
                    Text(stringResource(R.string.transactions_no_schedules), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                } else {
                    val ids = selectedIds
                    linkableSchedules.forEach { schedule ->
                        Action(schedule.name) {
                            showLinkSchedulePicker = false
                            onLinkSchedule(visible.filter { it.id in ids }, schedule.id)
                            selectionModeOn = false
                        }
                    }
                }
            }
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

data class ScheduleOption(val id: String, val name: String)

@Composable
private fun ReconcileAccountScreen(
    modifier: Modifier = Modifier,
    account: Account,
    transactions: List<Transaction>,
    hideDecimalPlaces: Boolean,
    conventionalAmountEntry: Boolean,
    onBack: () -> Unit,
    onSetCleared: (Transaction, Boolean) -> Unit,
    onReconcile: () -> Unit,
    onCreateAdjustment: (Long) -> Boolean,
) {
    var bankBalance by remember(account.id) { mutableStateOf<Long?>(null) }
    var calculatorKey by remember(account.id) { mutableStateOf(0) }
    val calculator = remember(account.id, calculatorKey) {
        CalculatorAmountState(bankBalance ?: 0L, allowsNegative = true,
            conventionalAmountEntry = conventionalAmountEntry)
    }
    var reviewExpanded by remember(account.id) { mutableStateOf(false) }
    var adjustmentConfirmation by remember { mutableStateOf<Long?>(null) }
    // Otherwise re-filtered on every recomposition of this screen, including every digit typed
    // into the bank-balance calculator and every reviewExpanded toggle.
    val uncleared = remember(transactions) { transactions.filter { !it.cleared && !it.reconciled } }
    val difference = bankBalance?.minus(account.clearedCents)
    BackHandler(onBack = onBack)

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.transactions_reconcile),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(account.name.ifBlank { stringResource(R.string.transaction_unknown_account) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(48.dp))
        }

        LazyColumn(Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("balances") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.reconcile_cleared_balance), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatReconciliationMoney(account.clearedCents, hideDecimalPlaces),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.reconcile_bank_balance_question),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                bankBalance?.let { formatReconciliationMoney(it, hideDecimalPlaces) }
                                    ?: stringResource(R.string.reconcile_enter_bank_balance),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (bankBalance == null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = {
                                bankBalance = account.clearedCents
                                calculatorKey++
                            }) { Text(stringResource(R.string.reconcile_use_cleared_balance)) }
                        }
                    }
                }
            }

            difference?.let { amount ->
                if (amount == 0L) {
                    item("match") {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                Text(stringResource(R.string.transaction_reconciled), style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(stringResource(R.string.reconcile_balance_matches),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center)
                                Button(onClick = onReconcile, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Lock, contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.reconcile_lock_transactions))
                                }
                                Text(stringResource(R.string.reconcile_lock_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    item("difference") {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.reconcile_difference),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text((if (amount > 0L) "+" else "") + formatReconciliationMoney(amount, hideDecimalPlaces),
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                FilledTonalButton(onClick = { reviewExpanded = !reviewExpanded },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(if (uncleared.isEmpty()) {
                                        stringResource(R.string.reconcile_no_uncleared_review)
                                    } else {
                                        pluralStringResource(
                                            R.plurals.reconcile_review_uncleared,
                                            uncleared.size,
                                            uncleared.size,
                                        )
                                    })
                                }
                                TextButton(onClick = { adjustmentConfirmation = amount }, modifier = Modifier.align(Alignment.End)) {
                                    Text(stringResource(R.string.reconcile_create_adjustment))
                                }
                            }
                        }
                    }
                    if (reviewExpanded) {
                        if (uncleared.isEmpty()) item("no-uncleared") {
                            Text(stringResource(R.string.reconcile_difference_explanation),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp))
                        } else items(uncleared, key = { "reconcile-${it.id}" }) { transaction ->
                            ReconciliationTransactionRow(transaction, hideDecimalPlaces) {
                                onSetCleared(transaction, true)
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
            CompactCalculatorPad(
                calculator = calculator,
                conventionalAmountEntry = conventionalAmountEntry,
                allowSign = true,
                showDisplay = false,
                onValueChange = { bankBalance = it },
                onDone = { bankBalance = calculator.finish() },
            )
        }
    }

    adjustmentConfirmation?.let { amount ->
        AlertDialog(
            onDismissRequest = { adjustmentConfirmation = null },
            title = { Text(stringResource(R.string.reconcile_create_adjustment_question)) },
            text = { Text(stringResource(
                R.string.reconcile_adjustment_message,
                formatReconciliationMoney(amount, hideDecimalPlaces),
            )) },
            confirmButton = { TextButton(onClick = {
                if (onCreateAdjustment(amount)) adjustmentConfirmation = null
            }) { Text(stringResource(R.string.reconcile_create_adjustment_action)) } },
            dismissButton = { TextButton(onClick = { adjustmentConfirmation = null }) {
                Text(stringResource(R.string.action_cancel))
            } },
        )
    }
}

@Composable
private fun ReconciliationTransactionRow(
    transaction: Transaction,
    hideDecimalPlaces: Boolean,
    onMarkCleared: () -> Unit,
) {
    Surface(onClick = onMarkCleared, color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(transaction.payee.ifBlank { stringResource(R.string.transaction_unknown_payee) },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(formatStoredDate(transaction.date), transaction.category)
                    .filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatReconciliationMoney(transaction.amountCents, hideDecimalPlaces),
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
            Checkbox(checked = false, onCheckedChange = { onMarkCleared() })
        }
    }
}

private fun formatReconciliationMoney(cents: Long, hideDecimalPlaces: Boolean): String =
    formatMoneyCents(cents, hideDecimalPlaces, respectBalanceVisibility = false)

private fun com.azimulkabir.actua.data.schedules.DayDate.formatted(locale: Locale): String =
    java.time.LocalDate.of(year, month, day)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, yyyy", locale))

@Composable
internal fun AccountDetails(account: Account, card: CreditCardStatus?, note: String,
    onSaveNote: (String) -> Unit, hideDecimals: Boolean, showSummary: Boolean, showNotes: Boolean,
    onViewStatements: (() -> Unit)? = null, showCreditCardSection: Boolean = true) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val detailPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("account_detail_preferences", android.content.Context.MODE_PRIVATE)
    }
    var balanceExpanded by remember(account.id) {
        mutableStateOf(detailPreferences.getBoolean("balance_expanded_${account.id}", false))
    }
    var noteEditorOpen by remember(account.id) { mutableStateOf(false) }
    var noteDraft by remember(account.id, noteEditorOpen) { mutableStateOf(note) }
    val toggleBalance = {
        balanceExpanded = !balanceExpanded
        detailPreferences.edit().putBoolean("balance_expanded_${account.id}", balanceExpanded).apply()
    }
    val balanceArrowRotation by animateFloatAsState(
        targetValue = if (balanceExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "Balance disclosure",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSummary) Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BalanceColumn(stringResource(R.string.transaction_cleared), account.clearedCents, hideDecimals,
                        Modifier.weight(1f), alignment = Alignment.Start)
                    BalanceColumn(stringResource(R.string.account_balance), account.balanceCents, hideDecimals,
                        Modifier.weight(1f), emphasized = true, alignment = Alignment.CenterHorizontally)
                    BalanceColumn(stringResource(R.string.transaction_uncleared), account.unclearedCents, hideDecimals,
                        Modifier.weight(1f), alignment = Alignment.End)
                }
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = toggleBalance),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.transaction_reconciled),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (balanceExpanded) R.string.account_reconciled_details_collapse
                            else R.string.account_reconciled_details_show,
                        ),
                        modifier = Modifier.padding(start = 8.dp).size(20.dp).rotate(balanceArrowRotation),
                    )
                }
                AnimatedVisibility(
                    visible = balanceExpanded,
                    enter = fadeIn(tween(180)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(220)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        card?.availableCreditCents?.let {
                            DetailAmount(stringResource(R.string.account_available_credit), it, hideDecimals)
                        }
                        card?.config?.limitCents?.let {
                            DetailAmount(stringResource(R.string.account_credit_limit), it, hideDecimals)
                        }
                        if (card != null) HorizontalDivider()
                        DetailAmount(stringResource(R.string.transaction_reconciled), account.reconciledCents, hideDecimals)
                    }
                }
            }
        }
        if (showCreditCardSection) card?.let {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account_billing_cycle),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val (cycleStart, cycleEnd) = it.cycle.cycleRange()
                    Text("${cycleStart.formatted(locale)} – ${cycleEnd.formatted(locale)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it.cycle.dueSummary(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    DetailAmount(stringResource(R.string.account_cycle_spend), it.cycleSpendCents, hideDecimals)
                    if (onViewStatements != null) {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onViewStatements),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.account_statement_history),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
        if (showNotes) Text(stringResource(R.string.account_note),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (showNotes) Surface(
            onClick = { noteEditorOpen = true },
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = note.ifBlank { stringResource(R.string.account_add_note) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.isBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = if (note.isBlank()) 1 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (noteEditorOpen) {
        AlertDialog(
            onDismissRequest = { noteEditorOpen = false },
            title = { Text(stringResource(
                if (note.isBlank()) R.string.account_add_note else R.string.account_edit_note,
            )) },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    placeholder = { Text(stringResource(R.string.account_note_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveNote(noteDraft)
                    noteEditorOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { noteEditorOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun BalanceColumn(label: String, amountCents: Long, hideDecimals: Boolean,
    modifier: Modifier = Modifier, emphasized: Boolean = false, alignment: Alignment.Horizontal = Alignment.CenterHorizontally) {
    val formatted = formatMoneyCents(amountCents, hideDecimals)
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label $formatted" },
        horizontalAlignment = alignment,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            formatted,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailAmount(label: String, amount: Long, hideDecimals: Boolean, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
        Text(formatMoneyCents(amount, hideDecimals), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ToggleItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(text = { Text(label) }, trailingIcon = {
        Checkbox(checked = checked, onCheckedChange = null)
    }, onClick = { onChange(!checked) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(transaction: Transaction, hideDecimalPlaces: Boolean,
    showDate: Boolean, showAccount: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    onClearedClick: (() -> Unit)? = null, tagColors: Map<String, String>? = null,
    selectionMode: Boolean = false, selected: Boolean = false, runningBalanceCents: Long? = null) {
    val resources = LocalResources.current
    val presentation = transactionRowPresentation(transaction, showAccount, resources)
    val effectiveTagColors = tagColors ?: rememberActualTagColors(transaction)
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md), verticalAlignment = Alignment.Top) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        }
        ClearedIndicator(transaction.cleared, onClearedClick, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(presentation.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            presentation.transferContext?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            } ?: Spacer(Modifier.height(7.dp))
            CategoryChip(presentation.categoryLabel, transaction.type == Type.TRANSFER)
            if (transaction.notes.isNotBlank()) {
                Text(coloredTagText(transaction.notes, effectiveTagColors), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(transaction.amountCents, FontWeight.SemiBold, hideDecimalPlaces)
            val runningBalanceLabel = runningBalanceCents?.let { formatMoneyCents(it, hideDecimalPlaces) }
            val secondaryLine = runningBalanceLabel ?: presentation.accountLabel
            secondaryLine?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium,
                    color = if (runningBalanceLabel != null) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            } ?: Spacer(Modifier.height(7.dp))
            if (showDate) {
                Spacer(Modifier.height(if (secondaryLine != null) 5.dp else 0.dp))
                Text(formatTransactionDate(transaction.date), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

internal data class TransactionRowPresentation(
    val title: String,
    val categoryLabel: String,
    val accountLabel: String?,
    val transferContext: String?,
)

internal fun transactionRowPresentation(
    transaction: Transaction,
    showAccount: Boolean,
    resources: android.content.res.Resources? = null,
): TransactionRowPresentation {
    if (transaction.type != Type.TRANSFER) {
        val isSplit = transaction.splits.isNotEmpty()
        return TransactionRowPresentation(
            title = transaction.payee.ifBlank {
                resources?.getString(
                    if (isSplit) R.string.transaction_split_label else R.string.transaction_unknown_payee,
                ).orEmpty()
            },
            categoryLabel = transaction.category.ifBlank {
                resources?.getString(
                    if (isSplit) R.string.transaction_split_label else R.string.transaction_uncategorized,
                ).orEmpty()
            },
            accountLabel = transaction.account.takeIf { showAccount && it.isNotBlank() },
            transferContext = null,
        )
    }
    val otherAccount = transaction.transferAccount?.ifBlank { null }
        ?: transaction.payee.ifBlank {
            resources?.getString(R.string.transaction_unknown_account).orEmpty()
        }
    val outgoing = transaction.amountCents < 0
    return TransactionRowPresentation(
        title = resources?.getString(
            if (outgoing) R.string.transaction_transfer_to else R.string.transaction_transfer_from,
            otherAccount,
        ) ?: otherAccount,
        categoryLabel = resources?.getString(R.string.transaction_type_transfer).orEmpty(),
        accountLabel = null,
        transferContext = if (!showAccount) null else resources?.getString(
            if (outgoing) R.string.transaction_from_account else R.string.transaction_to_account,
            transaction.account,
        ) ?: transaction.account,
    )
}

// transactions must be newest-first (as loaded from Actual); reversing gives the chronological fold order.
internal fun accountRunningBalances(transactions: List<Transaction>, accountName: String): Map<String, Long> {
    var balance = 0L
    val balances = LinkedHashMap<String, Long>()
    for (transaction in transactions.asReversed()) {
        if (transaction.account != accountName) continue
        balance += transaction.amountCents
        balances[transaction.id] = balance
    }
    return balances
}

@Composable
private fun CategoryChip(label: String, transfer: Boolean) {
    Surface(
        color = if (transfer) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (transfer) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun ClearedIndicator(cleared: Boolean, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(18.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CircleShape,
        color = if (cleared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = stringResource(
                if (cleared) R.string.transaction_cleared else R.string.transaction_uncleared,
            ),
            tint = if (cleared) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsSheet(
    transaction: Transaction,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit = {},
    tagColors: Map<String, String>? = null,
) {
    var confirmDelete by remember(transaction.id) { mutableStateOf(false) }
    val presentation = transactionRowPresentation(
        transaction,
        showAccount = true,
        resources = LocalResources.current,
    )
    val effectiveTagColors = tagColors ?: rememberActualTagColors(transaction)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.transaction_details), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Amount(transaction.amountCents, FontWeight.Bold, hideDecimalPlaces)
                ClearedIndicator(transaction.cleared, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            TransactionDetail(stringResource(R.string.transaction_payee), presentation.title)
            TransactionDetail(stringResource(R.string.transaction_date), formatTransactionDate(transaction.date))
            TransactionDetail(stringResource(R.string.transaction_category), presentation.categoryLabel)
            TransactionDetail(
                stringResource(R.string.transaction_account),
                transaction.account.ifBlank { stringResource(R.string.transaction_unknown_account) },
            )
            transaction.transferAccount?.takeIf(String::isNotBlank)?.let {
                TransactionDetail(stringResource(R.string.transaction_transfer_account), it)
            }
            TransactionDetail(
                stringResource(R.string.transaction_status),
                stringResource(if (transaction.cleared) R.string.transaction_cleared else R.string.transaction_uncleared),
            )
            transaction.notes.takeIf(String::isNotBlank)?.let {
                TransactionTagDetail(stringResource(R.string.transaction_notes), it, effectiveTagColors)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDuplicate) { Text(stringResource(R.string.action_duplicate)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.transaction_delete_question)) },
            text = { Text(stringResource(R.string.transaction_delete_message)) },
            confirmButton = { TextButton(onClick = onDelete) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) {
                Text(stringResource(R.string.action_cancel))
            } },
        )
    }
}

@Composable
private fun TransactionDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

@Composable
private fun TransactionTagDetail(label: String, value: String, tagColors: Map<String, String>) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(coloredTagText(value, tagColors), modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

internal fun formatTransactionDate(value: String): String {
    return formatStoredDate(value)
}

@Composable
private fun Amount(value: Long, weight: FontWeight, hideDecimalPlaces: Boolean, modifier: Modifier = Modifier) {
    Text(formatMoneyCents(value, hideDecimalPlaces, showPositiveSign = true), style = AmountTypography.rowAmount.copy(fontWeight = weight), textAlign = TextAlign.End,
        color = if (value >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier)
}

@Composable
private fun Action(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface) }, onClick = onClick, modifier = Modifier.fillMaxWidth())
}
