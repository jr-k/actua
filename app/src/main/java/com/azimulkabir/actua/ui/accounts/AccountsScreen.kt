package com.azimulkabir.actua.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.ui.components.RenameDialog
import com.azimulkabir.actua.ui.components.NewAccountDialog
import com.azimulkabir.actua.ui.components.ChangeAccountTypeDialog
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.components.MonetaryText
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.AmountTypography
import com.azimulkabir.actua.ui.theme.Spacing
import java.text.NumberFormat
import kotlin.math.absoluteValue

private data class AccountSection(val title: String, val accounts: List<Account>)

private val sampleAccountSections = listOf(
    AccountSection("On budget", listOf(
        Account("Everyday account", 48_250, "Bank"),
        Account("Cash", 3_400, "Cash"),
        Account("Savings", 86_500, "Savings"),
        Account("Credit card", -12_780, "Credit"),
    )),
    AccountSection("Off budget", listOf(
        Account("Investment account", 125_000, "Investment"),
        Account("Motorbike loan", -65_000, "Loan"),
    )),
    AccountSection("Closed accounts", listOf(
        Account("Old bank account", 0, "Bank"),
    )),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    accounts: List<Account> = sampleAccountSections.flatMap { it.accounts },
    transactions: List<Transaction> = emptyList(),
    hideDecimalPlaces: Boolean = false,
    showMonthlySummary: Boolean = true,
    onShowMonthlySummaryChange: (Boolean) -> Unit = {},
    creditCards: List<CreditCardStatus> = emptyList(),
    onAccountClick: (String) -> Unit = {},
    onAllAccountsClick: () -> Unit = {},
    onCloseAccount: (Account) -> Unit = {},
    onRenameAccount: (Account, String) -> Unit = { _, _ -> },
    onChangeAccountType: (Account, String) -> Unit = { _, _ -> },
    onCreateAccount: (String, Boolean, String, String) -> Unit = { _, _, _, _ -> },
    onSearch: () -> Unit = {},
    syncing: Boolean = false,
    onSync: () -> Unit = {},
    favoriteAccountIds: Set<String> = emptySet(),
    onFavoriteAccountChange: (String, Boolean) -> Unit = { _, _ -> },
    scrollToTopRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    var collapsedSections by remember { mutableStateOf(setOf("Closed accounts")) }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var renamingAccount by remember { mutableStateOf<Account?>(null) }
    var changingTypeAccount by remember { mutableStateOf<Account?>(null) }
    // Otherwise this re-filters the whole account list on every recomposition of this screen
    // (e.g. opening the overflow menu or selecting an account), not just when `accounts` changes.
    val accountSections = remember(accounts) {
        listOf(
            AccountSection("On budget", accounts.filter { !it.offBudget && !it.closed }),
            AccountSection("Off budget", accounts.filter { it.offBudget && !it.closed }),
            AccountSection("Closed accounts", accounts.filter { it.closed }),
        ).filter { it.accounts.isNotEmpty() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Accounts") {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSync,
                        enabled = !syncing,
                        modifier = Modifier.semantics {
                            contentDescription = if (syncing) "Syncing" else "Sync now"
                        },
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = null)
                        }
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search Actua")
                    }
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add account")
                    }
                    Box {
                        IconButton(onClick = { accountMenuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Account display options")
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Monthly summary") },
                                trailingIcon = {
                                    Switch(
                                        checked = showMonthlySummary,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    onShowMonthlySummaryChange(!showMonthlySummary)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Expand all") },
                                onClick = {
                                    collapsedSections = emptySet()
                                    accountMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Collapse all") },
                                onClick = {
                                    collapsedSections = accountSections.mapTo(mutableSetOf()) { it.title }
                                    accountMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Otherwise this is a linear scan repeated per visible row, per recomposition
        // (O(accounts x creditCards) overall), instead of a single O(creditCards) pass.
        val creditCardByAccountId = remember(creditCards) { creditCards.associateBy { it.accountId } }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            item { AccountsSummary(accounts, transactions, onAllAccountsClick, hideDecimalPlaces, showMonthlySummary) }
            accountSections.forEach { section ->
                val collapsed = section.title in collapsedSections
                stickyHeader(key = "account-header-${section.title}") {
                    AccountSectionHeader(
                        section = section,
                        collapsed = collapsed,
                        hideDecimalPlaces = hideDecimalPlaces,
                        onClick = {
                            collapsedSections = if (collapsed) collapsedSections - section.title
                            else collapsedSections + section.title
                        },
                    )
                }
                itemsIndexed(section.accounts, key = { _, account -> "${section.title}-${account.name}" }) { index, account ->
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                    ) {
                        AccountRow(
                            account = account,
                            creditCard = creditCardByAccountId[account.id],
                            showTopDivider = index > 0,
                            onClick = { onAccountClick(account.name) },
                        onLongClick = { selectedAccount = account },
                            hideDecimalPlaces = hideDecimalPlaces,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    selectedAccount?.let { account ->
        AccountActionsSheet(
            account = account,
            onDismiss = { selectedAccount = null },
            onViewTransactions = { selectedAccount = null; onAccountClick(account.name) },
            onRename = { selectedAccount = null; renamingAccount = account },
            onChangeType = { selectedAccount = null; changingTypeAccount = account },
            onClose = { selectedAccount = null; onCloseAccount(account) },
            favorite = account.id in favoriteAccountIds,
            onFavoriteChange = { onFavoriteAccountChange(account.id, it) },
        )
    }
    if (showAddSheet) NewAccountDialog(onDismiss = { showAddSheet = false }) { name, offBudget, balance, type ->
        onCreateAccount(name, offBudget, balance, type); showAddSheet = false
    }
    renamingAccount?.let { account -> RenameDialog("Rename account", account.name,
        onDismiss = { renamingAccount = null }, onSave = { name -> onRenameAccount(account, name); renamingAccount = null }) }
    changingTypeAccount?.let { account -> ChangeAccountTypeDialog(account.name, account.type,
        onDismiss = { changingTypeAccount = null },
        onSave = { type -> onChangeAccountType(account, type); changingTypeAccount = null }) }
}

@Composable
private fun AccountsSummary(
    accounts: List<Account>,
    transactions: List<Transaction>,
    onClick: () -> Unit,
    hideDecimalPlaces: Boolean,
    showMonthlySummary: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    val total = accounts.sumOf { it.balanceCents }
    // Skip the full-transaction-list scan and calculator entirely when the summary isn't shown,
    // and otherwise only recompute it when `transactions` actually changes, not on every
    // recomposition of this row (e.g. opening the overflow menu elsewhere on the screen).
    val summary = if (showMonthlySummary) {
        remember(transactions) {
            val monthKey = java.text.SimpleDateFormat("yyyyMM", java.util.Locale.US).format(java.util.Date())
            val monthTransactions = transactions.filter { it.date.filter(Char::isDigit).startsWith(monthKey) }
            AccountMonthlySummaryCalculator.calculate(monthTransactions)
        }
    } else null
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = {}),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(start = Spacing.screenHorizontal, top = Spacing.md, end = Spacing.md, bottom = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All accounts", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                MonetaryText(total, hideDecimalPlaces, style = AmountTypography.rowAmount.copy(fontWeight = FontWeight.Bold))
                Icon(Icons.Outlined.ChevronRight, contentDescription = "View all transactions")
            }
            if (summary != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                Text(java.text.SimpleDateFormat("MMMM yyyy", locale).format(java.util.Date()), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryStat("Income", summary.incomeCents, hideDecimalPlaces = hideDecimalPlaces)
                    SummaryStat("Expenses", summary.expenseCents, Alignment.CenterHorizontally, hideDecimalPlaces)
                    SummaryStat("Net", summary.netCents, Alignment.End, hideDecimalPlaces,
                        Modifier.padding(end = Spacing.xl))
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, amount: Long, alignment: Alignment.Horizontal = Alignment.Start,
    hideDecimalPlaces: Boolean, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = alignment, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MonetaryText(amount, hideDecimalPlaces)
    }
}

@Composable
private fun AccountSectionHeader(section: AccountSection, collapsed: Boolean,
    hideDecimalPlaces: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(220), label = "account section")
    val total = section.accounts.sumOf { it.balanceCents }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                role = Role.Button, onClick = onClick, onLongClick = {},
            ).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand ${section.title}" else "Collapse ${section.title}",
                modifier = Modifier.rotate(rotation))
            Text(section.title, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f))
            MonetaryText(total, hideDecimalPlaces)
            // Match the space occupied by the account-row disclosure chevron.
            Spacer(Modifier.width(Spacing.screenHorizontal))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: Account,
    creditCard: CreditCardStatus?,
    showTopDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    hideDecimalPlaces: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        if (showTopDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = Spacing.screenHorizontal, end = Spacing.md),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = Spacing.screenHorizontal, top = 15.dp, end = Spacing.md, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyMedium)
                Text(creditCard?.let { "${it.cycle.dueShortSummary()} · Spend ${formatMoneyCents(it.cycleSpendCents, hideDecimalPlaces)}" }
                    ?: account.type, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MonetaryText(account.balanceCents, hideDecimalPlaces)
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Open ${account.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountActionsSheet(
    account: Account,
    onDismiss: () -> Unit,
    onViewTransactions: () -> Unit,
    onRename: () -> Unit,
    onChangeType: () -> Unit,
    onClose: () -> Unit,
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ActuaSheetTitle(account.name)
            AccountSheetAction(if (favorite) "Remove from favorites" else "Add to favorites", onClick = { onFavoriteChange(!favorite) })
            AccountSheetAction("View transactions", onViewTransactions)
            AccountSheetAction("Rename account", onRename)
            AccountSheetAction("Change account type", onChangeType)
            AccountSheetAction(if (account.closed) "Reopen account" else "Close account", onClose, destructive = !account.closed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            ActuaSheetTitle("Add account")
            AccountSheetAction("Bank account", onDismiss)
            AccountSheetAction("Cash account", onDismiss)
            AccountSheetAction("Credit card", onDismiss)
            AccountSheetAction("Savings account", onDismiss)
            AccountSheetAction("Off-budget account", onDismiss)
        }
    }
}

@Composable
private fun AccountSheetAction(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
