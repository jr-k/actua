package com.azimulkabir.actua.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.azimulkabir.actua.ui.accounts.AccountsScreen
import com.azimulkabir.actua.ui.automation.BudgetAutomationScreen
import com.azimulkabir.actua.ui.budget.BudgetScreen
import com.azimulkabir.actua.ui.settings.SettingsScreen
import com.azimulkabir.actua.ui.settings.ConnectionScreen
import com.azimulkabir.actua.ui.settings.CreditCardsScreen
import com.azimulkabir.actua.ui.settings.RulesScreen
import com.azimulkabir.actua.ui.settings.SchedulesScreen
import com.azimulkabir.actua.ui.settings.FindSchedulesScreen
import com.azimulkabir.actua.ui.settings.BillsCalendarScreen
import com.azimulkabir.actua.ui.settings.ImportTransactionsScreen
import com.azimulkabir.actua.ui.settings.PayeeLocationsScreen
import com.azimulkabir.actua.ui.categories.ManageCategoriesScreen
import com.azimulkabir.actua.ui.categories.ReorderGroupsScreen
import com.azimulkabir.actua.ui.home.CustomizeHomeScreen
import com.azimulkabir.actua.ui.transactions.AddTransactionScreen
import com.azimulkabir.actua.ui.transactions.NearbyPayeeOption
import com.azimulkabir.actua.ui.transactions.NearbyPayeeSearchResult
import com.azimulkabir.actua.ui.transactions.PayeeLocationSaveResult
import com.azimulkabir.actua.ui.transactions.TransactionsScreen
import com.azimulkabir.actua.ui.reports.ReportsScreen
import com.azimulkabir.actua.ui.search.GlobalSearchScreen
import com.azimulkabir.actua.ui.home.HomeScreen
import com.azimulkabir.actua.ui.home.HomeDashboardProjection
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.TransactionStatusFilter
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.asDuplicate
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.location.AndroidLocationProvider
import com.azimulkabir.actua.data.location.CurrentLocationResult
import com.azimulkabir.actua.data.location.LocationUtils
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_AFTER_CHANGE
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.data.sync.SyncSignals
import com.azimulkabir.actua.data.sync.SyncStatus
import com.azimulkabir.actua.data.sync.SyncStatusStore
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.data.preferences.HomePreferences
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.preferences.LocationPreferences
import com.azimulkabir.actua.data.notifications.CreditCardDueNotificationScheduler
import com.azimulkabir.actua.data.notifications.CreditCardNotificationSettings
import com.azimulkabir.actua.ui.components.BalanceVisibility
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.DateDisplay
import com.azimulkabir.actua.ui.components.NumberDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.AppLaunchRequest
import com.azimulkabir.actua.R
import com.azimulkabir.actua.SHARED_IMPORT_ACTION
import com.azimulkabir.actua.widget.WidgetActions
import com.azimulkabir.actua.widget.WidgetUpdater

private enum class MainDestination(
    val preferenceValue: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("Home", R.string.navigation_home, Icons.Outlined.Home),
    Budget("Budget", R.string.navigation_budget, Icons.Outlined.PieChartOutline),
    Transactions("Transactions", R.string.navigation_transactions, Icons.Outlined.ReceiptLong),
    Accounts("Accounts", R.string.navigation_accounts, Icons.Outlined.AccountBalanceWallet),
    Manage("Manage", R.string.navigation_manage, Icons.Outlined.Tune),
}

private enum class DetailDestination { Main, Reports, Transactions, EditTransaction, Search, Connection, CreditCards, CreditCardStatements, CreditCardStatementDetail, Rules, Schedules, ImportTransactions, PayeeLocations, BillsCalendar, FindSchedules, NewSchedule, EditSchedule, ManageCategories, ReorderGroups, BudgetAutomation, CustomizeHome }

private data class TabSnapshot(
    val detail: DetailDestination = DetailDestination.Main,
    val transactionAccount: String? = null,
    val transactionCategory: String? = null,
    val transactionMonth: String? = null,
    val transactionSearch: String = "",
    val activeBudgetCategory: String? = null,
    val transactionsReturnCategory: String? = null,
)

internal fun shouldRequestForegroundSync(foregroundGeneration: Int): Boolean =
    foregroundGeneration > 0

/**
 * An "After change" sync only uploads a local edit already reflected on screen, so it never
 * needs the banner; "App open"/"Background" syncs can bring in data the screen doesn't have yet.
 */
internal fun shouldShowSyncBanner(status: SyncStatus): Boolean =
    status.running && status.activeTrigger != SYNC_TRIGGER_AFTER_CHANGE

@Composable
internal fun SyncStatusBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .testTag("syncStatusBannerContent"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .testTag("syncStatusBannerIndicator"),
            )
            Text(
                stringResource(R.string.navigation_syncing_budget),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun BudgetSwitchOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .clearAndSetSemantics { contentDescription = "Loading budget" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text("Loading budget…", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    foregroundGeneration: Int = 0,
    launchRequest: AppLaunchRequest? = null,
    onLaunchRequestConsumed: () -> Unit = {},
    onAppearanceChange: (String) -> Unit = {},
    onUseDynamicColorChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val locationPreferences = remember { LocationPreferences(context) }
    val displayPreferences = remember { DisplayPreferences(context) }
    val languageTag = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()
    val favoritePreferences = remember { FavoritePreferences(context) }
    val homePreferences = remember { HomePreferences(context) }
    var homeLayout by remember { mutableStateOf(homePreferences.layout()) }
    val creditCardNotificationSettings = remember { CreditCardNotificationSettings(context) }
    var creditCardNotificationsEnabled by remember {
        mutableStateOf(creditCardNotificationSettings.isEnabled)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        creditCardNotificationSettings.isEnabled = granted
        creditCardNotificationsEnabled = granted
        CreditCardDueNotificationScheduler.refresh(context)
    }
    var repositoryVersion by remember { mutableStateOf(0) }
    var budgetReplacementInProgress by remember { mutableStateOf(false) }
    var budgetReplacementCompleted by remember { mutableStateOf(false) }
    val favoriteBudgetId = remember(repositoryVersion) { ActiveBudgetStore(context).budgetId ?: "no-budget" }
    val repository = remember(repositoryVersion) { ActuaRepository(context) }
    var dataVersion by remember { mutableStateOf(0) }
    var sharedImportText by remember { mutableStateOf<String?>(null) }
    val syncStatusStore = remember { SyncStatusStore(context) }
    val syncStatusGeneration by SyncSignals.statusGeneration.collectAsState()
    val syncDataGeneration by SyncSignals.dataGeneration.collectAsState()
    var syncStatus by remember { mutableStateOf(syncStatusStore.read()) }
    var budgetMonth by rememberSaveable {
        mutableStateOf(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()))
    }
    val budgetGroups = remember(dataVersion, budgetMonth) { repository.budgetGroups(budgetMonth) }
    // Marks the first frame where the landing (Budget) screen has real data to show, so
    // Macrobenchmark's StartupTimingMetric can capture time-to-full-display instead of only
    // time-to-initial-display (which fires on the first empty/placeholder frame).
    var reportedFullyDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(repository.isUsingActualBudget, budgetGroups) {
        if (!reportedFullyDrawn && repository.isUsingActualBudget) {
            reportedFullyDrawn = true
            (context as? Activity)?.reportFullyDrawn()
        }
    }
    val budgetScheduleFunding = remember(dataVersion, budgetMonth) {
        repository.budgetScheduleFunding(budgetMonth)
    }
    val budgetOverview = remember(dataVersion, budgetMonth) { repository.budgetOverview(budgetMonth) }
    val accounts = remember(dataVersion) { repository.accounts() }
    var hideReconciledTransactions by remember {
        mutableStateOf(displayPreferences.hideReconciledTransactions)
    }
    var transactionStatusFilter by rememberSaveable { mutableStateOf(TransactionStatusFilter.ALL) }
    val transactions = remember(dataVersion) { repository.transactions() }
    val filteredTransactions = remember(dataVersion, hideReconciledTransactions, transactionStatusFilter) {
        repository.transactions(hideReconciled = hideReconciledTransactions, statusFilter = transactionStatusFilter)
    }
    val searchTransactions: suspend (String) -> List<Transaction> = remember(repository, hideReconciledTransactions, transactionStatusFilter) {
        { query -> withContext(Dispatchers.IO) {
            repository.transactions(query, hideReconciled = hideReconciledTransactions, statusFilter = transactionStatusFilter)
        } }
    }
    val categoryNames = remember(dataVersion) { repository.categoryNames() }
    val payeeNames = remember(dataVersion) { repository.payeeNames() }
    val creditCards = remember(dataVersion) { repository.creditCards() }
    // reportSnapshot, rules/rulesSupported/scheduleOwnedRuleIds/ruleEditorData, and
    // reorderCategoryGroups are each read by exactly one destination (Reports, Rules,
    // ManageCategories/ReorderGroups) that isn't always on screen, unlike the values above. Per
    // #325 ("small mutations should produce correspondingly small UI/data work"), they're
    // remember()ed at their point of use instead of unconditionally here, so e.g. marking a
    // transaction cleared while on the Budget tab no longer also recomputes Reports' aggregation
    // or refetches rule-editor data (accounts/categories/payees) that nothing is currently
    // showing — matching the existing pattern `payeeLocations` already used below.
    val schedules = remember(dataVersion) { repository.schedules() }
    val linkableSchedules = remember(schedules) {
        schedules.filterNot { it.schedule.completed }.map {
            com.azimulkabir.actua.ui.transactions.ScheduleOption(it.schedule.id, it.title)
        }
    }
    var editingScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingAutomationCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable {
        mutableStateOf(MainDestination.entries.firstOrNull { it.preferenceValue == displayPreferences.startPage }
            ?: MainDestination.Accounts)
    }
    // Bottom navigation selection is deliberately separate from the displayed destination. A
    // tap can then draw its selected state before the next destination starts composing. This is
    // particularly important for destinations whose local cached data still needs preparation.
    var selectedTab by rememberSaveable { mutableStateOf(destination) }
    var tabSwitchJob by remember { mutableStateOf<Job?>(null) }
    var detail by rememberSaveable { mutableStateOf(DetailDestination.Main) }
    var transactionAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionSearch by rememberSaveable { mutableStateOf("") }
    var searchReturnsToReports by rememberSaveable { mutableStateOf(false) }
    // Search may be entered from Reports, but a tab switch must not leave that return target
    // behind for a later search started elsewhere.
    LaunchedEffect(selectedTab, destination, detail) {
        if (detail != DetailDestination.Search || selectedTab != destination) {
            searchReturnsToReports = false
        }
    }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var newTransactionType by remember { mutableStateOf(com.azimulkabir.actua.model.Type.EXPENSE) }
    var editorReturnsToTransactions by rememberSaveable { mutableStateOf(false) }
    var editorReturnsToCategory by rememberSaveable { mutableStateOf(false) }
    var activeBudgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var reopenBudgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionsReturnCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val tabSnapshots = remember { mutableStateMapOf<MainDestination, TabSnapshot>() }
    val rootRequests = remember { mutableStateMapOf<MainDestination, Int>() }
    val tabStateHolder = rememberSaveableStateHolder()
    var reportSnapshot by remember(repository) { mutableStateOf<ReportSnapshot?>(null) }
    var reportSnapshotVersion by remember(repository) { mutableStateOf(-1) }
    var addOrigin by rememberSaveable { mutableStateOf(MainDestination.Accounts) }
    var transactionFabExpanded by rememberSaveable { mutableStateOf(true) }
    var accountsSyncing by remember { mutableStateOf(false) }
    var transactionsRefreshing by remember { mutableStateOf(false) }
    var reconcileOpen by remember { mutableStateOf(false) }
    var scheduleReturnsToBills by rememberSaveable { mutableStateOf(false) }
    var scheduleReturnsToTransactions by rememberSaveable { mutableStateOf(false) }
    var scheduleReturnsToTransactionsTab by rememberSaveable { mutableStateOf(false) }
    var billsCalendarReturnsToSchedules by rememberSaveable { mutableStateOf(false) }
    var creditCardsReturnToBills by rememberSaveable { mutableStateOf(false) }
    var statementsAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var statementsReturnToTransactions by rememberSaveable { mutableStateOf(false) }
    var selectedStatement by remember { mutableStateOf<com.azimulkabir.actua.model.CreditCardCycle.StatementRecord?>(null) }
    var editorReturnsToStatementDetail by rememberSaveable { mutableStateOf(false) }
    var hideDecimalPlaces by remember { mutableStateOf(displayPreferences.hideDecimalPlaces) }
    var currencyCode by remember { mutableStateOf(displayPreferences.currencyCode) }
    var currencySymbolOnly by remember { mutableStateOf(displayPreferences.currencySymbolOnly) }
    var dateFormat by remember { mutableStateOf(displayPreferences.dateFormat) }
    var numberFormat by remember { mutableStateOf(displayPreferences.numberFormat) }
    var showHiddenCategories by remember { mutableStateOf(displayPreferences.showHiddenCategories) }
    var showSpentColumn by remember { mutableStateOf(displayPreferences.showSpentColumn) }
    var showBudgetProgressBars by remember { mutableStateOf(displayPreferences.showBudgetProgressBars) }
    var budgetView by remember { mutableStateOf(displayPreferences.budgetView) }
    var showBudgetOverview by remember { mutableStateOf(displayPreferences.showBudgetOverview) }
    var showGroupTotals by remember { mutableStateOf(displayPreferences.showGroupTotals) }
    var hideFullySpentCategories by remember { mutableStateOf(displayPreferences.hideFullySpentCategories) }
    var budgetCategoryView by remember { mutableStateOf(displayPreferences.budgetCategoryView) }
    var showCategoryFilters by remember { mutableStateOf(displayPreferences.showCategoryFilters) }
    var favoritesOnly by remember { mutableStateOf(displayPreferences.favoritesOnly) }
    var favoriteCategoryIds by remember(favoriteBudgetId) {
        mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.CATEGORY))
    }
    var favoriteAccountIds by remember(favoriteBudgetId) {
        mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.ACCOUNT))
    }
    var favoriteReportIds by remember(favoriteBudgetId) {
        mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.REPORT))
    }
    var requestedReportPageId by remember { mutableStateOf<String?>(null) }
    var requestedReportPageRequest by remember { mutableStateOf(0) }
    var hideBalances by remember { mutableStateOf(displayPreferences.hideBalances) }
    var appearance by remember { mutableStateOf(displayPreferences.appearance) }
    var useDynamicColor by remember { mutableStateOf(displayPreferences.useDynamicColor) }
    var startPage by remember { mutableStateOf(displayPreferences.startPage) }
    var defaultAccount by remember { mutableStateOf(displayPreferences.defaultAccount) }
    var groupTransactionsByDate by remember { mutableStateOf(displayPreferences.groupTransactionsByDate) }
    var showAccountsMonthlySummary by remember { mutableStateOf(displayPreferences.showAccountsMonthlySummary) }
    var conventionalAmountEntry by remember { mutableStateOf(displayPreferences.conventionalAmountEntry) }
    var showBottomNavigationLabels by remember { mutableStateOf(displayPreferences.showBottomNavigationLabels) }
    var showCurrentBalanceSummary by remember { mutableStateOf(displayPreferences.showCurrentBalanceSummary) }
    var showRunningBalance by remember { mutableStateOf(displayPreferences.showRunningBalance) }
    var showNotes by remember { mutableStateOf(displayPreferences.showNotes) }
    BalanceVisibility.hidden = hideBalances
    CurrencyDisplay.code = currencyCode
    CurrencyDisplay.symbolOnly = currencySymbolOnly
    DateDisplay.format = dateFormat
    NumberDisplay.format = numberFormat
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun mutate(@StringRes labelRes: Int, action: () -> Boolean): Boolean {
        val label = resources.getString(labelRes)
        return runCatching(action).fold(
        onSuccess = { changed ->
            if (changed) dataVersion += 1
            else errorMessage = resources.getString(R.string.navigation_mutation_incomplete, label)
            changed
        },
        onFailure = {
            errorMessage = resources.getString(R.string.navigation_mutation_failed, label)
            false
        },
    )
    }

    fun refreshTransactions() {
        if (transactionsRefreshing) return
        transactionsRefreshing = true
        coroutineScope.launch {
            try {
                when (withContext(Dispatchers.IO) {
                    ActualSyncRunner.run(context, trigger = "Pull to refresh")
                }) {
                    is SyncRunResult.Success -> Unit
                    SyncRunResult.NotConfigured -> dataVersion += 1
                    SyncRunResult.EncryptionKeyUnavailable -> {
                        errorMessage = "Unlock this encrypted budget before syncing."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = error.message?.takeIf(String::isNotBlank) ?: "Sync failed."
            } finally {
                transactionsRefreshing = false
            }
        }
    }

    fun syncFromAccounts() {
        if (accountsSyncing || syncStatus.running) return
        accountsSyncing = true
        coroutineScope.launch {
            try {
                when (withContext(Dispatchers.IO) {
                    ActualSyncRunner.run(context, trigger = "Manual")
                }) {
                    is SyncRunResult.Success -> withContext(Dispatchers.IO) {
                        CreditCardDueNotificationScheduler.refresh(appContext)
                        WidgetUpdater.requestAll(appContext)
                    }
                    SyncRunResult.NotConfigured -> {
                        errorMessage = "Download and select a budget first."
                    }
                    SyncRunResult.EncryptionKeyUnavailable -> {
                        errorMessage = "Unlock this encrypted budget before syncing."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = error.message?.takeIf(String::isNotBlank) ?: "Sync failed."
            } finally {
                accountsSyncing = false
            }
        }
    }

    // Same contract as [mutate], but the (disk I/O) mutation runs off the main thread and
    // [onChanged] fires afterwards on the main thread once the local write has durably
    // completed. Use this for mutations on interaction-critical paths (e.g. dismissing an
    // editor) so local DB work never blocks the UI thread.
    fun mutateAsync(@StringRes labelRes: Int, action: () -> Boolean, onChanged: () -> Unit) {
        val label = resources.getString(labelRes)
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        dataVersion += 1
                        onChanged()
                    } else {
                        errorMessage = resources.getString(R.string.navigation_mutation_incomplete, label)
                    }
                },
                onFailure = {
                    errorMessage = resources.getString(R.string.navigation_mutation_failed, label)
                },
            )
        }
    }

    fun returnFromEditSchedule() {
        when {
            scheduleReturnsToTransactionsTab -> {
                destination = MainDestination.Transactions
                detail = DetailDestination.Main
            }
            scheduleReturnsToTransactions -> detail = DetailDestination.Transactions
            scheduleReturnsToBills -> detail = DetailDestination.BillsCalendar
            else -> detail = DetailDestination.Schedules
        }
        scheduleReturnsToBills = false
        scheduleReturnsToTransactions = false
        scheduleReturnsToTransactionsTab = false
    }

    fun openAddTransaction() {
        addOrigin = destination
        editingTransaction = null
        newTransactionType = com.azimulkabir.actua.model.Type.EXPENSE
        editorReturnsToTransactions = false
        editorReturnsToCategory = false
        detail = DetailDestination.EditTransaction
    }

    fun openAddTransactionForAccount() {
        editingTransaction = null
        newTransactionType = com.azimulkabir.actua.model.Type.EXPENSE
        editorReturnsToTransactions = true
        editorReturnsToCategory = false
        transactionFabExpanded = true
        detail = DetailDestination.EditTransaction
    }

    fun openAddTransactionForCategory() {
        transactionCategory = activeBudgetCategory
        editingTransaction = null
        newTransactionType = com.azimulkabir.actua.model.Type.EXPENSE
        editorReturnsToTransactions = false
        editorReturnsToCategory = true
        transactionFabExpanded = true
        detail = DetailDestination.EditTransaction
    }

    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) transactionFabExpanded = false
                if (available.y > 2f) transactionFabExpanded = true
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    LaunchedEffect(syncStatusGeneration) {
        syncStatus = syncStatusStore.read()
    }

    LaunchedEffect(syncDataGeneration) {
        if (syncDataGeneration > 0) dataVersion += 1
    }

    LaunchedEffect(budgetReplacementCompleted) {
        if (budgetReplacementCompleted) {
            // repositoryVersion has already rebuilt the repository and all screen projections.
            // Keep the blocker through the first frame that can display those new values.
            withFrameNanos { }
            budgetReplacementInProgress = false
            budgetReplacementCompleted = false
        }
    }

    LaunchedEffect(destination) {
        if (tabSwitchJob?.isActive != true) selectedTab = destination
    }

    LaunchedEffect(detail) {
        // Otherwise a later plain visit to Reports (bottom tab, Manage) would replay this stale
        // request and force the view back to whatever favorite was last opened from Home.
        if (detail != DetailDestination.Reports) {
            requestedReportPageId = null
            requestedReportPageRequest = 0
        }
    }

    LaunchedEffect(destination, detail, dataVersion, repository) {
        val needsReportSnapshot = detail == DetailDestination.Reports ||
            (destination == MainDestination.Home && detail == DetailDestination.Main)
        if (needsReportSnapshot && reportSnapshotVersion != dataVersion) {
            try {
                val loaded = withContext(Dispatchers.IO) { repository.reports() }
                reportSnapshot = loaded
                reportSnapshotVersion = dataVersion
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                errorMessage = resources.getString(R.string.navigation_reports_load_failed)
            }
        }
    }

    LaunchedEffect(foregroundGeneration) {
        if (!shouldRequestForegroundSync(foregroundGeneration)) return@LaunchedEffect
        val result = try {
            withContext(Dispatchers.IO) {
                ActualSyncRunner.run(context, allowRecentSuccess = true, trigger = "App open")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // This automatic app-open sync is opportunistic: a cold launch can race the
            // network coming up (e.g. via a launcher shortcut straight into the editor),
            // and ActualSyncRunner.run already records the failure in SyncStatusStore for
            // the Connection screen. Surfacing every transient failure here as a Snackbar
            // would alarm the user over something that resolves on the next sync attempt.
            null
        }
        when (result) {
            is SyncRunResult.Success -> {
                // Reminder planning opens the selected budget and widget discovery crosses
                // Binder; the successful sync has already refreshed visible data, so defer
                // this non-critical maintenance work from the main thread.
                withContext(Dispatchers.IO) {
                    CreditCardDueNotificationScheduler.refresh(appContext)
                    WidgetUpdater.requestAll(appContext)
                }
            }
            SyncRunResult.NotConfigured -> Unit
            SyncRunResult.EncryptionKeyUnavailable -> {
                errorMessage = resources.getString(R.string.navigation_unlock_before_syncing)
            }
            null -> Unit
        }
    }

    LaunchedEffect(launchRequest?.nonce) {
        val request = launchRequest ?: return@LaunchedEffect
        if (!repository.isUsingActualBudget) {
            destination = MainDestination.Manage
            detail = DetailDestination.Connection
            onLaunchRequestConsumed()
            return@LaunchedEffect
        }
        when (request.action) {
            SHARED_IMPORT_ACTION -> {
                sharedImportText = request.target
                destination = MainDestination.Manage
                detail = DetailDestination.ImportTransactions
            }
            WidgetActions.BUDGET -> {
                destination = MainDestination.Budget
                detail = DetailDestination.Main
            }
            WidgetActions.CATEGORY -> {
                destination = MainDestination.Budget
                activeBudgetCategory = null
                reopenBudgetCategory = request.target
                detail = DetailDestination.Main
            }
            WidgetActions.ACCOUNTS -> {
                destination = MainDestination.Accounts
                transactionAccount = request.target
                transactionCategory = null
                transactionMonth = null
                transactionSearch = ""
                detail = if (request.target == null) DetailDestination.Main else DetailDestination.Transactions
            }
            WidgetActions.SEARCH -> {
                destination = MainDestination.Transactions
                detail = DetailDestination.Search
            }
            WidgetActions.SCHEDULES -> {
                destination = MainDestination.Manage
                detail = DetailDestination.Schedules
            }
            WidgetActions.ADD_EXPENSE, WidgetActions.ADD_INCOME, WidgetActions.ADD_TRANSFER -> {
                destination = MainDestination.Transactions
                addOrigin = MainDestination.Transactions
                editingTransaction = null
                editorReturnsToTransactions = false
                editorReturnsToCategory = false
                newTransactionType = when (request.action) {
                    WidgetActions.ADD_INCOME -> com.azimulkabir.actua.model.Type.INCOME
                    WidgetActions.ADD_TRANSFER -> com.azimulkabir.actua.model.Type.TRANSFER
                    else -> com.azimulkabir.actua.model.Type.EXPENSE
                }
                detail = DetailDestination.EditTransaction
            }
        }
        onLaunchRequestConsumed()
    }

    BackHandler(enabled = detail != DetailDestination.Main || destination != MainDestination.Budget) {
        when {
            detail == DetailDestination.EditTransaction && editorReturnsToCategory -> {
                reopenBudgetCategory = transactionCategory
                detail = DetailDestination.Main
                destination = MainDestination.Budget
                editingTransaction = null
                editorReturnsToCategory = false
            }
            detail == DetailDestination.BillsCalendar && billsCalendarReturnsToSchedules -> {
                billsCalendarReturnsToSchedules = false
                detail = DetailDestination.Schedules
            }
            detail == DetailDestination.CreditCardStatementDetail -> {
                detail = DetailDestination.CreditCardStatements
            }
            detail == DetailDestination.CreditCardStatements -> {
                detail = if (statementsReturnToTransactions) DetailDestination.Transactions else DetailDestination.CreditCards
                statementsReturnToTransactions = false
            }
            detail == DetailDestination.EditTransaction && editorReturnsToTransactions -> {
                detail = DetailDestination.Transactions
                editingTransaction = null
            }
            detail == DetailDestination.ReorderGroups -> {
                detail = DetailDestination.ManageCategories
            }
            detail == DetailDestination.CustomizeHome -> {
                detail = DetailDestination.Main
            }
            detail == DetailDestination.BudgetAutomation -> {
                reopenBudgetCategory = editingAutomationCategory
                editingAutomationCategory = null
                detail = DetailDestination.Main
            }
            detail == DetailDestination.Search && searchReturnsToReports -> {
                searchReturnsToReports = false
                detail = DetailDestination.Reports
            }
            detail != DetailDestination.Main -> {
                if (detail == DetailDestination.Transactions && transactionsReturnCategory != null) {
                    reopenBudgetCategory = transactionsReturnCategory
                    transactionsReturnCategory = null
                    destination = MainDestination.Budget
                }
                detail = DetailDestination.Main
                editingTransaction = null
            }
            else -> destination = MainDestination.Budget
        }
    }

    BackHandler(enabled = budgetReplacementInProgress) { }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (shouldShowSyncBanner(syncStatus) && repository.isUsingActualBudget) {
                SyncStatusBanner()
            }
        },
        floatingActionButton = {
            val onMainTab = detail == DetailDestination.Main && destination in setOf(
                MainDestination.Home,
                MainDestination.Budget,
                MainDestination.Accounts,
                MainDestination.Transactions,
            )
            val inAccount = detail == DetailDestination.Transactions && transactionAccount != null
            val inBudgetCategory = detail == DetailDestination.Main &&
                destination == MainDestination.Budget && activeBudgetCategory != null
            if (repository.isUsingActualBudget && !reconcileOpen && (onMainTab || inAccount)) {
                ExtendedFloatingActionButton(
                    onClick = when {
                        inBudgetCategory -> ::openAddTransactionForCategory
                        inAccount -> ::openAddTransactionForAccount
                        else -> ::openAddTransaction
                    },
                    expanded = transactionFabExpanded,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.navigation_transaction)) },
                )
            }
        },
        bottomBar = {
            if (detail != DetailDestination.EditTransaction) NavigationBar(
                modifier = if (showBottomNavigationLabels) {
                    Modifier
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                        .height(64.dp)
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                windowInsets = if (showBottomNavigationLabels) {
                    NavigationBarDefaults.windowInsets
                } else {
                    WindowInsets(0, 0, 0, 0)
                },
            ) {
                MainDestination.entries.forEach { item ->
                    val itemLabel = stringResource(item.labelRes)
                    NavigationBarItem(
                        selected = selectedTab == item,
                        onClick = {
                            if (item != MainDestination.Manage && !repository.isUsingActualBudget) {
                                tabSwitchJob?.cancel()
                                selectedTab = MainDestination.Manage
                                destination = MainDestination.Manage
                                detail = DetailDestination.Connection
                                return@NavigationBarItem
                            }
                            if (item == destination) {
                                tabSwitchJob?.cancel()
                                selectedTab = destination
                                if (detail != DetailDestination.Main) {
                                    searchReturnsToReports = false
                                    detail = DetailDestination.Main
                                    editingTransaction = null
                                    transactionAccount = null
                                    transactionCategory = null
                                    transactionMonth = null
                                    transactionSearch = ""
                                    transactionsReturnCategory = null
                                } else {
                                    rootRequests[item] = (rootRequests[item] ?: 0) + 1
                                }
                            } else {
                                tabSwitchJob?.cancel()
                                tabSnapshots[destination] = TabSnapshot(
                                    detail = detail.takeUnless {
                                        it == DetailDestination.Search || it == DetailDestination.EditTransaction
                                    } ?: DetailDestination.Main,
                                    transactionAccount = transactionAccount,
                                    transactionCategory = transactionCategory,
                                    transactionMonth = transactionMonth,
                                    transactionSearch = transactionSearch,
                                    activeBudgetCategory = activeBudgetCategory,
                                    transactionsReturnCategory = transactionsReturnCategory,
                                )
                                val restored = tabSnapshots[item] ?: TabSnapshot()
                                selectedTab = item
                                tabSwitchJob = coroutineScope.launch {
                                    // Let the navigation indicator render before composing the
                                    // target. No artificial delay is introduced: this resumes on
                                    // the very next frame.
                                    withFrameNanos { }
                                    destination = item
                                    detail = restored.detail
                                    transactionAccount = restored.transactionAccount
                                    transactionCategory = restored.transactionCategory
                                    transactionMonth = restored.transactionMonth
                                    transactionSearch = restored.transactionSearch
                                    activeBudgetCategory = restored.activeBudgetCategory
                                    reopenBudgetCategory = restored.activeBudgetCategory
                                    transactionsReturnCategory = restored.transactionsReturnCategory
                                }
                            }
                            transactionFabExpanded = true
                        },
                        icon = { Icon(item.icon, contentDescription = itemLabel) },
                        label = if (showBottomNavigationLabels) {
                            {
                                Text(
                                    text = itemLabel,
                                    maxLines = 1,
                                    softWrap = false,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 8.sp,
                                        maxFontSize = 12.sp,
                                    ),
                                )
                            }
                        } else null,
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding).nestedScroll(fabScrollConnection)
        AnimatedContent(
            targetState = detail to destination,
            transitionSpec = {
                val openingDetail = initialState.first == DetailDestination.Main &&
                    targetState.first != DetailDestination.Main
                val closingDetail = initialState.first != DetailDestination.Main &&
                    targetState.first == DetailDestination.Main
                when {
                    openingDetail -> (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 5 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 10 })
                    closingDetail -> (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 5 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 })
                    else -> (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.985f)) togetherWith
                        (fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 1.015f))
                // Detail destinations can differ radically in height. Animating their bounds
                // repeatedly measures both full-screen trees; keep the motion on layers instead.
                }.using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false))
            },
            label = "Main navigation motion",
        ) { (shownDetail, shownDestination) ->
        tabStateHolder.SaveableStateProvider("${shownDestination.name}:${shownDetail.name}") {
        when (shownDetail) {
            DetailDestination.Reports -> ReportsScreen(
                reportSnapshot ?: ReportSnapshot(emptyList(), emptyList(), 0),
                hideDecimalPlaces, contentModifier,
                isLoading = reportSnapshot == null || reportSnapshotVersion != dataVersion,
                onSearch = {
                    searchReturnsToReports = true
                    detail = DetailDestination.Search
                },
                favoriteReportIds = favoriteReportIds,
                onFavoriteReportChange = { id, favorite ->
                    favoritePreferences.set(favoriteBudgetId, FavoritePreferences.Type.REPORT, id, favorite)
                    favoriteReportIds = favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.REPORT)
                },
                scrollToTopRequest = 0,
                initialPageId = requestedReportPageId,
                initialPageRequest = requestedReportPageRequest,
            )
            DetailDestination.Transactions -> TransactionsScreen(
                accountName = transactionAccount,
                categoryName = transactionCategory,
                month = transactionMonth,
                onBack = {
                    transactionsReturnCategory?.let {
                        reopenBudgetCategory = it
                        transactionsReturnCategory = null
                        destination = MainDestination.Budget
                    }
                    detail = DetailDestination.Main
                },
                onEdit = {
                    editingTransaction = it
                    editorReturnsToTransactions = true
                    detail = DetailDestination.EditTransaction
                },
                modifier = contentModifier,
                transactions = filteredTransactions,
                allTransactions = transactions,
                searchTransactions = searchTransactions,
                transactionStatusFilter = transactionStatusFilter,
                onTransactionStatusFilterChange = { transactionStatusFilter = it },
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                groupTransactionsByDate = groupTransactionsByDate,
                onGroupTransactionsByDateChange = {
                    displayPreferences.groupTransactionsByDate = it
                    groupTransactionsByDate = it
                },
                hideReconciledTransactions = hideReconciledTransactions,
                onHideReconciledTransactionsChange = {
                    displayPreferences.hideReconciledTransactions = it
                    hideReconciledTransactions = it
                },
                onSetCleared = { transaction, cleared ->
                    mutate(R.string.navigation_updating_transaction) { repository.setTransactionCleared(transaction.id, cleared) }
                },
                onReconcileAccount = { account ->
                    mutate(R.string.navigation_reconciling_account) { repository.reconcileAccount(account.id) }
                },
                onCreateReconciliationAdjustment = { account, difference ->
                    mutate(R.string.navigation_creating_reconciliation_adjustment) {
                        repository.createReconciliationAdjustment(account.id, difference)
                    }
                },
                onDelete = { transaction ->
                    mutate(R.string.navigation_deleting_transaction) { repository.deleteTransaction(transaction.id) }
                },
                onDeleteMultiple = { transactionsToDelete ->
                    mutate(R.string.navigation_deleting_transactions) {
                        repository.deleteTransactions(transactionsToDelete.map { it.id }) > 0
                    }
                },
                onDuplicate = { transaction ->
                    mutate(R.string.navigation_duplicating_transaction) { repository.saveTransaction(transaction.asDuplicate()); true }
                },
                onDuplicateMultiple = { transactionsToDuplicate ->
                    mutate(R.string.navigation_duplicating_transactions) {
                        transactionsToDuplicate.forEach { repository.saveTransaction(it.asDuplicate()) }
                        transactionsToDuplicate.isNotEmpty()
                    }
                },
                onLinkSchedule = { transactionsToLink, scheduleId ->
                    mutate(R.string.navigation_linking_schedule) {
                        repository.linkScheduleTransactions(scheduleId, transactionsToLink.map { it.id }) > 0
                    }
                },
                onUnlinkSchedule = { transactionsToUnlink ->
                    mutate(R.string.navigation_unlinking_schedule) {
                        repository.unlinkScheduleFromTransactions(transactionsToUnlink.map { it.id }) > 0
                    }
                },
                onViewSchedule = { scheduleId ->
                    editingScheduleId = scheduleId
                    scheduleReturnsToBills = false
                    scheduleReturnsToTransactions = true
                    scheduleReturnsToTransactionsTab = false
                    detail = DetailDestination.EditSchedule
                },
                linkableSchedules = linkableSchedules,
                account = accounts.firstOrNull { it.name == transactionAccount },
                creditCard = creditCards.firstOrNull { card ->
                    card.accountId == accounts.firstOrNull { it.name == transactionAccount }?.id
                },
                onSaveAccountNote = { note ->
                    accounts.firstOrNull { it.name == transactionAccount }?.let { account ->
                        mutate(R.string.navigation_saving_account_note) { repository.setAccountNote(account.id, note) }
                    }
                },
                onViewStatements = {
                    accounts.firstOrNull { it.name == transactionAccount }?.let { account ->
                        statementsAccountId = account.id
                        statementsReturnToTransactions = true
                        detail = DetailDestination.CreditCardStatements
                    }
                },
                initialSearch = transactionSearch,
                showCurrentBalanceSummary = showCurrentBalanceSummary,
                onShowCurrentBalanceSummaryChange = {
                    displayPreferences.showCurrentBalanceSummary = it
                    showCurrentBalanceSummary = it
                },
                showRunningBalance = showRunningBalance,
                onShowRunningBalanceChange = {
                    displayPreferences.showRunningBalance = it
                    showRunningBalance = it
                },
                showNotes = showNotes,
                onReconcileVisibilityChange = { reconcileOpen = it },
                isRefreshing = transactionsRefreshing,
                onRefresh = ::refreshTransactions,
            )
            DetailDestination.EditTransaction -> {
            // Otherwise these are rebuilt from the whole account/payee lists on every
            // recomposition of this (very broad) composable while the editor is open, e.g. on
            // every keystroke/amount-entry change, instead of only when the underlying data or
            // display toggles actually change.
            val nonClosedAccounts = remember(accounts) { accounts.filterNot { it.closed } }
            val accountOptions = remember(nonClosedAccounts) { nonClosedAccounts.map { it.name } }
            val offBudgetAccountOptions = remember(nonClosedAccounts) {
                nonClosedAccounts.filter { it.offBudget }.mapTo(mutableSetOf()) { it.name }
            }
            val accountBalanceLabels = remember(nonClosedAccounts, hideBalances, hideDecimalPlaces) {
                if (hideBalances) emptyMap() else nonClosedAccounts
                    .associate { it.name to formatMoneyCents(it.balanceCents, hideDecimalPlaces) }
            }
            val payeeOptions = remember(payeeNames, nonClosedAccounts, configuration) {
                (payeeNames + nonClosedAccounts.map {
                    resources.getString(R.string.navigation_transfer_payee, it.name)
                }).distinct()
            }
            AddTransactionScreen(
                editing = editingTransaction,
                defaultType = newTransactionType,
                onBack = {
                    if (editorReturnsToCategory) {
                        reopenBudgetCategory = transactionCategory
                        destination = MainDestination.Budget
                    }
                    detail = when {
                        editorReturnsToStatementDetail -> DetailDestination.CreditCardStatementDetail
                        editorReturnsToTransactions -> DetailDestination.Transactions
                        else -> DetailDestination.Main
                    }
                    editingTransaction = null
                    if (!editorReturnsToTransactions && !editorReturnsToCategory && !editorReturnsToStatementDetail) destination = addOrigin
                    editorReturnsToCategory = false
                    editorReturnsToStatementDetail = false
                },
                onSave = { savedTransaction ->
                    val wasEditing = editingTransaction != null
                    coroutineScope.launch {
                        // The local CRDT write is disk I/O; keep it off the main thread so the
                        // editor dismisses as soon as the transaction is durably saved locally,
                        // without waiting on anything network-related (sync is scheduled
                        // separately and runs fully asynchronously).
                        val result = withContext(Dispatchers.IO) {
                            runCatching { repository.saveTransaction(savedTransaction) }
                        }
                        result.onFailure {
                            errorMessage = resources.getString(R.string.navigation_saving_transaction_failed)
                        }
                        if (result.isSuccess) {
                            dataVersion += 1
                            WidgetUpdater.requestAll(context)
                            if (!wasEditing &&
                                savedTransaction.type != com.azimulkabir.actua.model.Type.TRANSFER &&
                                savedTransaction.payee.isNotBlank() &&
                                locationPreferences.recordPayeeLocations &&
                                repository.payeeLocationWritesSupported()
                            ) {
                                val location = AndroidLocationProvider(context).currentCoordinates()
                                if (location is CurrentLocationResult.Success) {
                                    withContext(Dispatchers.IO) {
                                        repository.recordPayeeLocation(savedTransaction.payee, location.coordinates)
                                    }
                                }
                            }
                            editingTransaction = null
                            if (editorReturnsToCategory) {
                                reopenBudgetCategory = transactionCategory
                                destination = MainDestination.Budget
                                detail = DetailDestination.Main
                                editorReturnsToCategory = false
                            } else if (editorReturnsToStatementDetail) {
                                detail = DetailDestination.CreditCardStatementDetail
                                editorReturnsToStatementDetail = false
                            } else if (editorReturnsToTransactions) {
                                detail = DetailDestination.Transactions
                            } else if (wasEditing) {
                                detail = DetailDestination.Main
                            } else {
                                destination = MainDestination.Transactions
                                transactionAccount = null
                                transactionCategory = null
                                transactionMonth = null
                                transactionSearch = ""
                                detail = DetailDestination.Main
                            }
                        }
                    }
                },
                onDelete = { transaction ->
                    mutateAsync(R.string.navigation_deleting_transaction, { repository.deleteTransaction(transaction.id) }) {
                        editingTransaction = null
                        detail = when {
                            editorReturnsToStatementDetail -> {
                                editorReturnsToStatementDetail = false
                                DetailDestination.CreditCardStatementDetail
                            }
                            editorReturnsToTransactions -> DetailDestination.Transactions
                            else -> {
                                destination = addOrigin
                                DetailDestination.Main
                            }
                        }
                    }
                },
                modifier = contentModifier,
                    accountOptions = accountOptions,
                    offBudgetAccountOptions = offBudgetAccountOptions,
                    accountBalanceLabels = accountBalanceLabels,
                    categoryOptions = categoryNames,
                    payeeOptions = payeeOptions,
                    defaultAccount = if (editingTransaction == null && editorReturnsToTransactions) {
                        transactionAccount ?: defaultAccount
                    } else {
                        defaultAccount
                    },
                    defaultCategory = if (editingTransaction == null && editorReturnsToCategory) {
                        transactionCategory
                    } else {
                        null
                    },
                    hideDecimalPlaces = hideDecimalPlaces,
                    conventionalAmountEntry = conventionalAmountEntry,
                    onPreviewRules = repository::previewRules,
                    onFindNearbyPayees = {
                        when (val location = AndroidLocationProvider(context).currentCoordinates()) {
                            is CurrentLocationResult.Success -> {
                                val nearby = withContext(Dispatchers.IO) {
                                    repository.nearbyPayees(location.coordinates)
                                }
                                NearbyPayeeSearchResult(
                                    options = nearby.map {
                                        NearbyPayeeOption(
                                            payee = it.payeeName,
                                            distance = LocationUtils.formatDistance(it.distanceMeters),
                                            locationId = it.locationId,
                                        )
                                    },
                                    message = if (nearby.isEmpty()) {
                                        resources.getString(R.string.navigation_no_nearby_payee_locations)
                                    } else {
                                        null
                                    },
                                )
                            }
                            CurrentLocationResult.PermissionDenied -> NearbyPayeeSearchResult(
                                message = resources.getString(R.string.navigation_location_permission_search),
                            )
                            CurrentLocationResult.ServicesDisabled -> NearbyPayeeSearchResult(
                                message = resources.getString(R.string.navigation_location_disabled_search),
                            )
                            CurrentLocationResult.Timeout -> NearbyPayeeSearchResult(
                                message = resources.getString(R.string.navigation_location_timeout_search),
                            )
                            CurrentLocationResult.Unavailable -> NearbyPayeeSearchResult(
                                message = resources.getString(R.string.navigation_location_unavailable_search),
                            )
                            is CurrentLocationResult.Inaccurate -> NearbyPayeeSearchResult(
                                message = resources.getString(R.string.navigation_location_inaccurate_search),
                            )
                        }
                    },
                    onSavePayeeLocation = if (repository.payeeLocationWritesSupported()) {
                        { payeeName ->
                            when (val location = AndroidLocationProvider(context).currentCoordinates()) {
                                is CurrentLocationResult.Success -> {
                                    val recorded = withContext(Dispatchers.IO) {
                                        repository.recordPayeeLocation(payeeName, location.coordinates)
                                    }
                                    if (recorded) dataVersion += 1
                                    PayeeLocationSaveResult(
                                        nowNearby = true,
                                        message = if (recorded) {
                                            resources.getString(R.string.navigation_location_saved, payeeName)
                                        } else {
                                            resources.getString(R.string.navigation_location_already_saved, payeeName)
                                        },
                                    )
                                }
                                CurrentLocationResult.PermissionDenied -> PayeeLocationSaveResult(
                                    false, resources.getString(R.string.navigation_location_permission_save),
                                )
                                CurrentLocationResult.ServicesDisabled -> PayeeLocationSaveResult(
                                    false, resources.getString(R.string.navigation_location_disabled_save),
                                )
                                CurrentLocationResult.Timeout -> PayeeLocationSaveResult(
                                    false, resources.getString(R.string.navigation_location_timeout_save),
                                )
                                CurrentLocationResult.Unavailable -> PayeeLocationSaveResult(
                                    false, resources.getString(R.string.navigation_location_unavailable_save),
                                )
                                is CurrentLocationResult.Inaccurate -> PayeeLocationSaveResult(
                                    false, resources.getString(R.string.navigation_location_inaccurate_save),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onForgetPayeeLocation = if (repository.payeeLocationWritesSupported()) {
                        { locationId ->
                            withContext(Dispatchers.IO) {
                                repository.deletePayeeLocation(locationId)
                            }.also { deleted ->
                                if (deleted) dataVersion += 1
                            }
                        }
                    } else {
                        null
                    },
            )
            }
            DetailDestination.Search -> GlobalSearchScreen(
                transactions = filteredTransactions,
                searchTransactions = remember(repository, hideReconciledTransactions, transactionStatusFilter) {
                    { query, limit, offset ->
                        withContext(Dispatchers.IO) {
                            repository.transactions(query, limit, offset,
                                hideReconciled = hideReconciledTransactions,
                                statusFilter = transactionStatusFilter)
                        }
                    }
                },
                accounts = accounts,
                payees = payeeNames,
                categories = categoryNames,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = {
                    detail = if (searchReturnsToReports) DetailDestination.Reports else DetailDestination.Main
                    searchReturnsToReports = false
                },
                onTransactionEdit = {
                    addOrigin = destination
                    editingTransaction = it
                    editorReturnsToTransactions = false
                    detail = DetailDestination.EditTransaction
                },
                onTransactionDelete = { transaction ->
                    mutate(R.string.navigation_deleting_transaction) { repository.deleteTransaction(transaction.id) }
                },
                onTransactionClearedChange = { transaction, cleared ->
                    mutate(R.string.navigation_updating_transaction) { repository.setTransactionCleared(transaction.id, cleared) }
                },
                onAccountClick = {
                    transactionAccount = it; transactionCategory = null; transactionMonth = null; transactionSearch = ""
                    destination = MainDestination.Accounts; detail = DetailDestination.Transactions
                },
                onCategoryClick = {
                    transactionAccount = null; transactionCategory = it; transactionMonth = null; transactionSearch = ""
                    destination = MainDestination.Budget; detail = DetailDestination.Transactions
                },
                onPayeeClick = {
                    transactionAccount = null; transactionCategory = null; transactionMonth = null; transactionSearch = it
                    destination = MainDestination.Accounts; detail = DetailDestination.Transactions
                },
                modifier = contentModifier,
            )
            DetailDestination.Connection -> ConnectionScreen(
                onBack = { detail = DetailDestination.Main },
                foregroundGeneration = foregroundGeneration,
                onBeforeBudgetReplacement = {
                    if (!budgetReplacementInProgress) {
                        budgetReplacementInProgress = true
                        budgetReplacementCompleted = false
                        repository.close()
                    }
                },
                onBudgetInstalled = {
                    repositoryVersion += 1
                    dataVersion += 1
                    if (budgetReplacementInProgress) budgetReplacementCompleted = true
                    CreditCardDueNotificationScheduler.refresh(context)
                },
                modifier = contentModifier,
            )
            DetailDestination.CreditCards -> CreditCardsScreen(
                cards = creditCards,
                accounts = accounts,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = {
                    detail = if (creditCardsReturnToBills) DetailDestination.BillsCalendar else DetailDestination.Main
                    creditCardsReturnToBills = false
                },
                onSave = { accountId, day, paymentDue, limit ->
                    if (mutate(R.string.navigation_saving_credit_card) { repository.setCreditCard(accountId, day, paymentDue, limit) }) {
                        CreditCardDueNotificationScheduler.refresh(context)
                    }
                },
                onRemove = { accountId ->
                    if (mutate(R.string.navigation_removing_credit_card) { repository.setCreditCard(accountId, null) }) {
                        CreditCardDueNotificationScheduler.refresh(context)
                    }
                },
                notificationsEnabled = creditCardNotificationsEnabled,
                onNotificationsEnabledChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        creditCardNotificationSettings.isEnabled = enabled
                        creditCardNotificationsEnabled = enabled
                        CreditCardDueNotificationScheduler.refresh(context)
                    }
                },
                onViewStatements = { card ->
                    statementsAccountId = card.accountId
                    detail = DetailDestination.CreditCardStatements
                },
                modifier = contentModifier,
            )
            DetailDestination.CreditCardStatements -> {
                val accountId = statementsAccountId
                val card = creditCards.firstOrNull { it.accountId == accountId }
                val statements = remember(accountId, dataVersion) {
                    accountId?.let { repository.fetchRecentStatements(it) } ?: emptyList()
                }
                com.azimulkabir.actua.ui.settings.CreditCardStatementsScreen(
                    accountName = card?.accountName ?: "",
                    statements = statements,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onBack = {
                        detail = if (statementsReturnToTransactions) DetailDestination.Transactions else DetailDestination.CreditCards
                        statementsReturnToTransactions = false
                    },
                    onSelectStatement = { statement ->
                        selectedStatement = statement
                        detail = DetailDestination.CreditCardStatementDetail
                    },
                    modifier = contentModifier,
                )
            }
            DetailDestination.CreditCardStatementDetail -> {
                val statement = selectedStatement
                val accountId = statementsAccountId
                if (statement == null || accountId == null) {
                    detail = DetailDestination.CreditCardStatements
                } else {
                    var statementTransactions by remember(statement) { mutableStateOf<List<Transaction>?>(null) }
                    LaunchedEffect(statement, accountId, dataVersion) {
                        statementTransactions = repository.fetchStatementTransactions(
                            accountId, statement.startDate.yyyymmdd, statement.endDate.yyyymmdd,
                        )
                    }
                    com.azimulkabir.actua.ui.settings.CreditCardStatementDetailScreen(
                        statement = statement,
                        transactions = statementTransactions ?: emptyList(),
                        isLoading = statementTransactions == null,
                        hideDecimalPlaces = hideDecimalPlaces,
                        onBack = { detail = DetailDestination.CreditCardStatements },
                        onSelectTransaction = { tx ->
                            editingTransaction = tx
                            editorReturnsToStatementDetail = true
                            detail = DetailDestination.EditTransaction
                        },
                        modifier = contentModifier,
                    )
                }
            }
            DetailDestination.Rules -> RulesScreen(
                rules = remember(dataVersion) { repository.rules() },
                supported = remember(dataVersion) { repository.rulesSupported() },
                scheduleOwnedRuleIds = remember(dataVersion) { repository.scheduleOwnedRuleIds() },
                editorData = remember(dataVersion) { repository.ruleEditorData() },
                onBack = { detail = DetailDestination.Main },
                onSave = { rule -> mutate(R.string.navigation_saving_rule) { repository.saveRule(rule) } },
                onDelete = { ruleId -> mutate(R.string.navigation_deleting_rule) { repository.deleteRule(ruleId) } },
                modifier = contentModifier,
            )
            DetailDestination.ManageCategories -> ManageCategoriesScreen(
                groups = remember(dataVersion) { repository.categoryGroupsForReorder() },
                onBack = { detail = DetailDestination.Main },
                onReorderGroupsClick = { detail = DetailDestination.ReorderGroups },
                onCreateGroup = { name -> mutate(R.string.navigation_creating_group) { repository.createCategoryGroup(name) } },
                onRenameGroup = { group, name -> mutate(R.string.navigation_renaming_group) { repository.renameCategoryGroup(group, name) } },
                onSetGroupHidden = { group, hidden ->
                    mutate(if (hidden) R.string.navigation_hiding_group else R.string.navigation_showing_group) {
                        repository.setCategoryGroupHidden(group, hidden)
                    }
                },
                onCreateCategory = { group, name -> mutate(R.string.navigation_creating_category) { repository.createCategory(group, name) } },
                onRenameCategory = { group, category, name ->
                    mutate(R.string.navigation_renaming_category) { repository.renameCategory(group, category, name) }
                },
                onSetCategoryHidden = { group, category, hidden ->
                    mutate(if (hidden) R.string.navigation_hiding_category else R.string.navigation_showing_category) {
                        repository.setCategoryHidden(group, category, hidden)
                    }
                },
                onDeleteCategory = { group, category -> mutate(R.string.navigation_deleting_category) { repository.deleteCategory(group, category) } },
                onMoveCategory = { move -> mutate(R.string.navigation_reordering_category) { repository.moveCategory(move) } },
                modifier = contentModifier,
            )
            DetailDestination.ReorderGroups -> ReorderGroupsScreen(
                groups = remember(dataVersion) { repository.categoryGroupsForReorder() },
                onBack = { detail = DetailDestination.ManageCategories },
                onMoveGroup = { move -> mutate(R.string.navigation_reordering_category_group) { repository.moveCategoryGroup(move) } },
                modifier = contentModifier,
            )
            DetailDestination.CustomizeHome -> CustomizeHomeScreen(
                layout = homeLayout,
                onBack = { detail = DetailDestination.Main },
                onLayoutChange = { updated ->
                    homePreferences.save(updated)
                    homeLayout = updated
                },
                modifier = contentModifier,
            )
            DetailDestination.BudgetAutomation -> budgetGroups.firstNotNullOfOrNull { g ->
                g.categories.firstOrNull { it.name == editingAutomationCategory }?.let { g to it }
            }?.let { (group, category) ->
                BudgetAutomationScreen(
                    group = group,
                    category = category,
                    month = budgetMonth,
                    hideDecimalPlaces = hideDecimalPlaces,
                    scheduleFunding = budgetScheduleFunding,
                    incomeCategories = budgetGroups.filter { it.isIncome }.flatMap { it.categories }
                        .map { it.name }.filter { it.isNotBlank() }.distinct(),
                    onBack = {
                        reopenBudgetCategory = editingAutomationCategory
                        editingAutomationCategory = null
                        detail = DetailDestination.Main
                    },
                    onSave = { automations ->
                        mutate(R.string.navigation_saving_automations) {
                            repository.setCategoryAutomations(category.id.orEmpty(), automations)
                        }
                    },
                    modifier = contentModifier,
                )
            } ?: run { detail = DetailDestination.Main }
            DetailDestination.Schedules -> SchedulesScreen(
                schedules = schedules,
                hideDecimalPlaces = hideDecimalPlaces,
                canAdd = accounts.any { !it.closed },
                onBack = { detail = DetailDestination.Main },
                onAdd = {
                    scheduleReturnsToBills = false
                    detail = DetailDestination.NewSchedule
                },
                onFind = { detail = DetailDestination.FindSchedules },
                onCalendar = {
                    billsCalendarReturnsToSchedules = true
                    detail = DetailDestination.BillsCalendar
                },
                onEdit = { id ->
                    editingScheduleId = id
                    scheduleReturnsToBills = false
                    scheduleReturnsToTransactions = false
                    scheduleReturnsToTransactionsTab = false
                    detail = DetailDestination.EditSchedule
                },
                onPost = { id, today ->
                    mutate(if (today) R.string.navigation_posting_schedule_today else R.string.navigation_posting_schedule) {
                        repository.postScheduleTransaction(id, today)
                    }
                },
                onSkip = { id ->
                    mutate(R.string.navigation_skipping_next_date) { repository.skipScheduleNextDate(id) }
                },
                onSetCompleted = { id, completed ->
                    mutate(if (completed) R.string.navigation_completing_schedule else R.string.navigation_restarting_schedule) {
                        repository.setScheduleCompleted(id, completed)
                    }
                },
                onDelete = { id ->
                    mutate(R.string.navigation_deleting_schedule) { repository.deleteSchedule(id) }
                },
                modifier = contentModifier,
            )
            DetailDestination.PayeeLocations -> PayeeLocationsScreen(
                locations = remember(dataVersion) { repository.payeeLocations() },
                writesSupported = repository.payeeLocationWritesSupported(),
                onBack = { detail = DetailDestination.Main },
                onDelete = { id ->
                    if (mutate(R.string.navigation_deleting_payee_location) { repository.deletePayeeLocation(id) }) {
                        dataVersion += 1
                    }
                },
                onClearPayee = { payeeId ->
                    if (mutate(R.string.navigation_clearing_payee_locations) {
                            repository.clearPayeeLocations(payeeId) > 0
                        }) {
                        dataVersion += 1
                    }
                },
                modifier = contentModifier,
            )
            DetailDestination.ImportTransactions -> ImportTransactionsScreen(
                accounts = accounts.filterNot { it.closed },
                duplicateKeys = repository::importDuplicateKeys,
                onImport = { accountId, candidates ->
                    mutate(R.string.navigation_importing_transactions) {
                        repository.importTransactions(accountId, candidates) == candidates.size
                    }
                },
                onBack = { detail = DetailDestination.Main },
                modifier = contentModifier,
                initialSharedText = sharedImportText,
                onSharedTextConsumed = { sharedImportText = null },
            )
            DetailDestination.BillsCalendar -> BillsCalendarScreen(
                loadItems = { year, month, cardBills ->
                    repository.billCalendarItems(year, month, cardBills)
                },
                refreshKey = dataVersion,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = {
                    detail = if (billsCalendarReturnsToSchedules) {
                        billsCalendarReturnsToSchedules = false
                        DetailDestination.Schedules
                    } else {
                        DetailDestination.Main
                    }
                },
                onAddSchedule = {
                    scheduleReturnsToBills = true
                    detail = DetailDestination.NewSchedule
                },
                onConfigureCards = {
                    creditCardsReturnToBills = true
                    detail = DetailDestination.CreditCards
                },
                onEditSchedule = { id ->
                    editingScheduleId = id
                    scheduleReturnsToBills = true
                    scheduleReturnsToTransactions = false
                    scheduleReturnsToTransactionsTab = false
                    detail = DetailDestination.EditSchedule
                },
                onPost = { id, today ->
                    mutate(if (today) R.string.navigation_posting_schedule_today else R.string.navigation_posting_schedule) {
                        repository.postScheduleTransaction(id, today)
                    }
                },
                onSkip = { id -> mutate(R.string.navigation_skipping_occurrence) { repository.skipScheduleNextDate(id) } },
                onDelete = { id -> mutate(R.string.navigation_deleting_schedule) { repository.deleteSchedule(id) } },
                modifier = contentModifier,
            )
            DetailDestination.FindSchedules -> {
                val proposals by produceState<List<com.azimulkabir.actua.data.schedules.ScheduleDiscovery.DisplayProposal>?>(
                    initialValue = null,
                    key1 = dataVersion,
                ) {
                    value = withContext(Dispatchers.IO) { repository.discoverSchedules() }
                }
                FindSchedulesScreen(
                    proposals = proposals,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onBack = { detail = DetailDestination.Schedules },
                    onCreate = { selected ->
                        if (mutate(R.string.navigation_creating_schedules) {
                            repository.createDiscoveredSchedules(selected)
                        }) {
                            detail = DetailDestination.Schedules
                        }
                    },
                    modifier = contentModifier,
                )
            }
            DetailDestination.NewSchedule -> com.azimulkabir.actua.ui.settings.EditScheduleScreen(
                item = null,
                accounts = accounts,
                payeeOptions = payeeNames,
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                onBack = {
                    detail = if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules
                    scheduleReturnsToBills = false
                },
                onSave = { fields, payeeName ->
                    if (mutate(R.string.navigation_creating_schedule) {
                        repository.createSchedule(fields, payeeName)
                    }) {
                        detail = if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules
                        scheduleReturnsToBills = false
                    }
                },
                modifier = contentModifier,
            )
            DetailDestination.EditSchedule -> schedules.firstOrNull {
                it.schedule.id == editingScheduleId
            }?.let { item ->
                com.azimulkabir.actua.ui.settings.EditScheduleScreen(
                    item = item,
                    accounts = accounts,
                    payeeOptions = payeeNames,
                    hideDecimalPlaces = hideDecimalPlaces,
                    conventionalAmountEntry = conventionalAmountEntry,
                    linkedTransactions = remember(dataVersion, item.schedule.id) {
                        repository.scheduleTransactions(item.schedule.id)
                    },
                    onBack = {
                        returnFromEditSchedule()
                    },
                    onSave = { fields, payeeName ->
                        if (mutate(R.string.navigation_saving_schedule) {
                            repository.updateSchedule(item.schedule.id, fields, payeeName)
                        }) {
                            returnFromEditSchedule()
                        }
                    },
                    onDelete = {
                        if (mutate(R.string.navigation_deleting_schedule) { repository.deleteSchedule(item.schedule.id) }) {
                            returnFromEditSchedule()
                        }
                    },
                    onUnlinkTransaction = { transactionId ->
                        mutate(R.string.navigation_unlinking_transaction) {
                            repository.unlinkScheduleTransaction(item.schedule.id, transactionId)
                        }
                    },
                    modifier = contentModifier,
                )
            } ?: run { detail = DetailDestination.Schedules }
            DetailDestination.Main -> if (!repository.isUsingActualBudget && destination != MainDestination.Manage) {
                NoBudgetScreen(contentModifier) {
                    destination = MainDestination.Manage
                    detail = DetailDestination.Connection
                }
            } else when (shownDestination) {
                MainDestination.Home -> HomeScreen(
                    modifier = contentModifier,
                    projection = remember(
                        dataVersion,
                        favoriteCategoryIds,
                        favoriteAccountIds,
                        favoriteReportIds,
                        reportSnapshot,
                        budgetMonth,
                    ) {
                        HomeDashboardProjection.from(
                            budgetOverview = budgetOverview,
                            budgetGroups = budgetGroups,
                            accounts = accounts,
                            reportDashboards = reportSnapshot?.dashboards.orEmpty(),
                            schedules = schedules,
                            transactions = transactions,
                            favoriteCategoryIds = favoriteCategoryIds,
                            favoriteAccountIds = favoriteAccountIds,
                            favoriteReportIds = favoriteReportIds,
                            month = budgetMonth,
                        )
                    },
                    sections = homeLayout.visibleSections,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onBudgetClick = { destination = MainDestination.Budget },
                    onAccountsClick = { destination = MainDestination.Accounts },
                    onSchedulesClick = {
                        destination = MainDestination.Manage
                        detail = DetailDestination.Schedules
                    },
                    onTransactionsClick = { destination = MainDestination.Transactions },
                    onReportsClick = { detail = DetailDestination.Reports },
                    onReportClick = { id ->
                        requestedReportPageId = id
                        requestedReportPageRequest += 1
                        detail = DetailDestination.Reports
                    },
                    onCustomizeClick = { detail = DetailDestination.CustomizeHome },
                    returnToRootRequest = rootRequests[MainDestination.Home] ?: 0,
                )
                MainDestination.Budget -> BudgetScreen(
                    contentModifier,
                    groups = budgetGroups,
                    overview = budgetOverview,
                    month = budgetMonth,
                    onMonthChange = { budgetMonth = it },
                    hideDecimalPlaces = hideDecimalPlaces,
                    showHidden = showHiddenCategories,
                    onShowHiddenChange = {
                        displayPreferences.showHiddenCategories = it
                        showHiddenCategories = it
                    },
                    showSpent = showSpentColumn,
                    onShowSpentChange = {
                        displayPreferences.showSpentColumn = it
                        showSpentColumn = it
                    },
                    showProgressBars = showBudgetProgressBars,
                    onShowProgressBarsChange = {
                        displayPreferences.showBudgetProgressBars = it
                        showBudgetProgressBars = it
                    },
                    budgetView = budgetView,
                    onBudgetViewChange = {
                        displayPreferences.budgetView = it
                        budgetView = it
                    },
                    showOverview = showBudgetOverview,
                    onShowOverviewChange = {
                        displayPreferences.showBudgetOverview = it
                        showBudgetOverview = it
                    },
                    showGroupTotals = showGroupTotals,
                    onShowGroupTotalsChange = {
                        displayPreferences.showGroupTotals = it
                        showGroupTotals = it
                    },
                    hideFullySpent = hideFullySpentCategories,
                    onHideFullySpentChange = {
                        displayPreferences.hideFullySpentCategories = it
                        hideFullySpentCategories = it
                    },
                    categoryView = budgetCategoryView,
                    onCategoryViewChange = {
                        displayPreferences.budgetCategoryView = it
                        budgetCategoryView = it
                    },
                    showCategoryFilters = showCategoryFilters,
                    onShowCategoryFiltersChange = {
                        displayPreferences.showCategoryFilters = it
                        showCategoryFilters = it
                    },
                    favoritesOnly = favoritesOnly,
                    onFavoritesOnlyChange = {
                        displayPreferences.favoritesOnly = it
                        favoritesOnly = it
                    },
                    favoriteCategoryIds = favoriteCategoryIds,
                    onFavoriteCategoryChange = { id, favorite ->
                        favoritePreferences.set(favoriteBudgetId, FavoritePreferences.Type.CATEGORY, id, favorite)
                        favoriteCategoryIds = favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.CATEGORY)
                        WidgetUpdater.requestAll(context)
                    },
                    onSetCategoryHidden = { group, category, hidden ->
                        mutate(if (hidden) R.string.navigation_hiding_category else R.string.navigation_showing_category) {
                            repository.setCategoryHidden(group, category, hidden)
                        }
                    },
                    onSetGroupHidden = { group, hidden ->
                        mutate(if (hidden) R.string.navigation_hiding_group else R.string.navigation_showing_group) {
                            repository.setCategoryGroupHidden(group, hidden)
                        }
                    },
                    onRenameCategory = { group, category, name ->
                        mutate(R.string.navigation_renaming_category) { repository.renameCategory(group, category, name) }
                    },
                    onRenameGroup = { group, name ->
                        mutate(R.string.navigation_renaming_group) { repository.renameCategoryGroup(group, name) }
                    },
                    onShowCategoryTransactions = { category, thisMonth, returnToDetails ->
                        activeBudgetCategory = null
                        transactionsReturnCategory = category.takeIf { returnToDetails }
                        transactionAccount = null
                        transactionCategory = category
                        transactionMonth = if (thisMonth) budgetMonth else null
                        transactionSearch = ""
                        detail = DetailDestination.Transactions
                    },
                    onTransferBudget = { fromGroup, fromCategory, toGroup, toCategory, amount ->
                        mutate(R.string.navigation_moving_budget) {
                            repository.transferBudget(fromGroup, fromCategory, toGroup, toCategory, amount, budgetMonth)
                        }
                    },
                    onSetBudgetAmount = { group, category, amount ->
                        mutate(R.string.navigation_updating_budget) {
                            repository.setBudgetAmount(group, category, amount, budgetMonth)
                        }
                    },
                    onSetCategoryNote = { categoryId, note ->
                        mutate(R.string.navigation_saving_category_note) {
                            repository.setCategoryNote(categoryId, note)
                        }
                    },
                    onSetCategoryCarryover = { categoryId, enabled ->
                        mutate(R.string.navigation_updating_rollover) {
                            repository.setCategoryCarryover(categoryId, enabled, budgetMonth)
                        }
                    },
                    onHoldForNextMonth = { amount ->
                        mutate(R.string.navigation_holding_for_next_month) {
                            repository.setBufferedAmount(budgetMonth, amount)
                        }
                    },
                    onResetNextMonthBuffer = {
                        mutate(R.string.navigation_resetting_next_month_buffer) {
                            repository.resetNextMonthBuffer(budgetMonth)
                        }
                    },
                    onCopyPreviousMonth = {
                        mutate(R.string.navigation_copying_previous_month_budget) {
                            repository.copyPreviousMonthBudget(budgetMonth)
                        }
                    },
                    onEditAutomations = { _, category ->
                        editingAutomationCategory = category.name
                        detail = DetailDestination.BudgetAutomation
                    },
                    onApplyBudgetTemplate = { preview ->
                        mutate(R.string.navigation_applying_budget_template) {
                            repository.applyBudgetTemplate(preview)
                        }
                    },
                    scheduleFunding = budgetScheduleFunding,
                    onPreviewCleanup = { repository.previewCleanup(budgetMonth) },
                    onApplyCleanup = { preview ->
                        mutate(R.string.navigation_applying_month_end_cleanup) {
                            repository.applyCleanup(preview)
                        }
                    },
                    onSearch = { detail = DetailDestination.Search },
                    onManageCategories = { detail = DetailDestination.ManageCategories },
                    transactions = filteredTransactions,
                    onDeleteCategory = { group, category ->
                        mutate(R.string.navigation_deleting_category) {
                            repository.deleteCategory(group, category)
                        }
                    },
                    onEditTransaction = { transaction ->
                        activeBudgetCategory = null
                        editingTransaction = transaction
                        editorReturnsToTransactions = true
                        transactionAccount = null
                        transactionCategory = transaction.category
                        transactionMonth = null
                        transactionSearch = ""
                        detail = DetailDestination.EditTransaction
                    },
                    onDeleteTransaction = { transaction ->
                        mutate(R.string.navigation_deleting_transaction) {
                            repository.deleteTransaction(transaction.id)
                        }
                    },
                    requestedCategoryDetails = reopenBudgetCategory,
                    onCategoryDetailsChange = { category ->
                        activeBudgetCategory = category
                        if (category == reopenBudgetCategory) reopenBudgetCategory = null
                    },
                    returnToRootRequest = rootRequests[MainDestination.Budget] ?: 0,
                    showNotes = showNotes,
                )
                MainDestination.Accounts -> AccountsScreen(
                    modifier = contentModifier,
                    accounts = accounts,
                    transactions = transactions,
                    hideDecimalPlaces = hideDecimalPlaces,
                    showMonthlySummary = showAccountsMonthlySummary,
                    onShowMonthlySummaryChange = {
                        displayPreferences.showAccountsMonthlySummary = it
                        showAccountsMonthlySummary = it
                    },
                    creditCards = creditCards,
                    onAccountClick = {
                        transactionAccount = it
                        transactionCategory = null; transactionMonth = null
                        transactionSearch = ""
                        detail = DetailDestination.Transactions
                    },
                    onAllAccountsClick = {
                        transactionAccount = null
                        transactionCategory = null; transactionMonth = null
                        transactionSearch = ""
                        detail = DetailDestination.Transactions
                    },
                    onCloseAccount = { account ->
                        mutate(if (account.closed) R.string.navigation_reopening_account else R.string.navigation_closing_account) {
                            repository.setAccountClosed(account.name, !account.closed)
                        }
                    },
                    onRenameAccount = { account, name ->
                        mutate(R.string.navigation_renaming_account) {
                            repository.renameAccount(account.name, name)
                        }
                    },
                    onChangeAccountType = { account, type ->
                        mutate(R.string.navigation_changing_account_type) {
                            repository.setAccountType(account.name, type)
                        }
                    },
                    onCreateAccount = { name, offBudget, balance, type ->
                        mutate(R.string.navigation_creating_account) {
                            repository.createAccount(name, offBudget, balance, type)
                        }
                    },
                    onSearch = { detail = DetailDestination.Search },
                    syncing = accountsSyncing || syncStatus.running,
                    onSync = ::syncFromAccounts,
                    favoriteAccountIds = favoriteAccountIds,
                    onFavoriteAccountChange = { id, favorite ->
                        favoritePreferences.set(favoriteBudgetId, FavoritePreferences.Type.ACCOUNT, id, favorite)
                        favoriteAccountIds = favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.ACCOUNT)
                    },
                    scrollToTopRequest = rootRequests[MainDestination.Accounts] ?: 0,
                )
                MainDestination.Transactions -> TransactionsScreen(
                    accountName = null,
                    categoryName = null,
                    month = null,
                    onBack = {},
                    onEdit = {
                        addOrigin = MainDestination.Transactions
                        editingTransaction = it
                        editorReturnsToTransactions = false
                        detail = DetailDestination.EditTransaction
                    },
                    modifier = contentModifier,
                    transactions = filteredTransactions,
                    transactionStatusFilter = transactionStatusFilter,
                    onTransactionStatusFilterChange = { transactionStatusFilter = it },
                    hideDecimalPlaces = hideDecimalPlaces,
                    conventionalAmountEntry = conventionalAmountEntry,
                    groupTransactionsByDate = groupTransactionsByDate,
                    onGroupTransactionsByDateChange = {
                        displayPreferences.groupTransactionsByDate = it
                        groupTransactionsByDate = it
                    },
                    hideReconciledTransactions = hideReconciledTransactions,
                    onHideReconciledTransactionsChange = {
                        displayPreferences.hideReconciledTransactions = it
                        hideReconciledTransactions = it
                    },
                    onSetCleared = { transaction, cleared ->
                        mutate(R.string.navigation_updating_transaction) {
                            repository.setTransactionCleared(transaction.id, cleared)
                        }
                    },
                    onReconcileAccount = { account ->
                        mutate(R.string.navigation_reconciling_account) {
                            repository.reconcileAccount(account.id)
                        }
                    },
                    onCreateReconciliationAdjustment = { account, difference ->
                        mutate(R.string.navigation_creating_reconciliation_adjustment) {
                            repository.createReconciliationAdjustment(account.id, difference)
                        }
                    },
                    onDelete = { transaction ->
                        mutate(R.string.navigation_deleting_transaction) {
                            repository.deleteTransaction(transaction.id)
                        }
                    },
                    onDeleteMultiple = { transactionsToDelete ->
                        mutate(R.string.navigation_deleting_transactions) {
                            repository.deleteTransactions(transactionsToDelete.map { it.id }) > 0
                        }
                    },
                    onDuplicate = { transaction ->
                        mutate(R.string.navigation_duplicating_transaction) {
                            repository.saveTransaction(transaction.asDuplicate()); true
                        }
                    },
                    onDuplicateMultiple = { transactionsToDuplicate ->
                        mutate(R.string.navigation_duplicating_transactions) {
                            transactionsToDuplicate.forEach { repository.saveTransaction(it.asDuplicate()) }
                            transactionsToDuplicate.isNotEmpty()
                        }
                    },
                    onLinkSchedule = { transactionsToLink, scheduleId ->
                        mutate(R.string.navigation_linking_schedule) {
                            repository.linkScheduleTransactions(scheduleId, transactionsToLink.map { it.id }) > 0
                        }
                    },
                    onUnlinkSchedule = { transactionsToUnlink ->
                        mutate(R.string.navigation_unlinking_schedule) {
                            repository.unlinkScheduleFromTransactions(transactionsToUnlink.map { it.id }) > 0
                        }
                    },
                    onViewSchedule = { scheduleId ->
                        editingScheduleId = scheduleId
                        scheduleReturnsToBills = false
                        scheduleReturnsToTransactions = false
                        scheduleReturnsToTransactionsTab = true
                        detail = DetailDestination.EditSchedule
                    },
                    linkableSchedules = linkableSchedules,
                    showBackButton = false,
                    returnToRootRequest = rootRequests[MainDestination.Transactions] ?: 0,
                    isRefreshing = transactionsRefreshing,
                    onRefresh = ::refreshTransactions,
                )
                MainDestination.Manage -> SettingsScreen(
                    modifier = contentModifier,
                    onConnectionClick = { detail = DetailDestination.Connection },
                    hideDecimalPlaces = hideDecimalPlaces,
                    onHideDecimalPlacesChange = {
                        displayPreferences.hideDecimalPlaces = it
                        hideDecimalPlaces = it
                        WidgetUpdater.requestAll(context)
                    },
                    showNotes = showNotes,
                    onShowNotesChange = {
                        displayPreferences.showNotes = it
                        showNotes = it
                    },
                    currencyCode = currencyCode,
                    onCurrencyCodeChange = {
                        displayPreferences.currencyCode = it
                        currencyCode = it
                        WidgetUpdater.requestAll(context)
                    },
                    currencySymbolOnly = currencySymbolOnly,
                    onCurrencySymbolOnlyChange = {
                        displayPreferences.currencySymbolOnly = it
                        currencySymbolOnly = it
                        WidgetUpdater.requestAll(context)
                    },
                    languageTag = languageTag,
                    onLanguageChange = { tag ->
                        val locales = if (tag.isBlank()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(tag)
                        }
                        AppCompatDelegate.setApplicationLocales(locales)
                        WidgetUpdater.requestAll(context)
                        CreditCardDueNotificationScheduler.refresh(context)
                    },
                    dateFormat = dateFormat,
                    onDateFormatChange = {
                        displayPreferences.dateFormat = it
                        dateFormat = it
                    },
                    numberFormat = numberFormat,
                    onNumberFormatChange = {
                        displayPreferences.numberFormat = it
                        numberFormat = it
                        WidgetUpdater.requestAll(context)
                    },
                    hideBalances = hideBalances,
                    onHideBalancesChange = {
                        displayPreferences.hideBalances = it
                        hideBalances = it
                        WidgetUpdater.requestAll(context)
                    },
                    appearance = appearance,
                    onAppearanceChange = {
                        displayPreferences.appearance = it
                        appearance = it
                        onAppearanceChange(it)
                    },
                    useDynamicColor = useDynamicColor,
                    onUseDynamicColorChange = {
                        displayPreferences.useDynamicColor = it
                        useDynamicColor = it
                        onUseDynamicColorChange(it)
                    },
                    startPage = startPage,
                    onStartPageChange = {
                        displayPreferences.startPage = it
                        startPage = it
                    },
                    accountOptions = accounts.filterNot { it.closed }.map { it.name },
                    defaultAccount = defaultAccount,
                    onDefaultAccountChange = {
                        displayPreferences.defaultAccount = it
                        defaultAccount = it
                    },
                    groupTransactionsByDate = groupTransactionsByDate,
                    onGroupTransactionsByDateChange = {
                        displayPreferences.groupTransactionsByDate = it
                        groupTransactionsByDate = it
                    },
                    showAccountsMonthlySummary = showAccountsMonthlySummary,
                    onShowAccountsMonthlySummaryChange = {
                        displayPreferences.showAccountsMonthlySummary = it
                        showAccountsMonthlySummary = it
                    },
                    onBillsCalendarClick = {
                        billsCalendarReturnsToSchedules = false
                        detail = DetailDestination.BillsCalendar
                    },
                    onCreditCardsClick = {
                        creditCardsReturnToBills = false
                        detail = DetailDestination.CreditCards
                    },
                    onRulesClick = { detail = DetailDestination.Rules },
                    onSchedulesClick = { detail = DetailDestination.Schedules },
                    onImportTransactionsClick = { detail = DetailDestination.ImportTransactions },
                    onPayeeLocationsClick = { detail = DetailDestination.PayeeLocations },
                    onReportsClick = { detail = DetailDestination.Reports },
                    onCustomizeHomeClick = { detail = DetailDestination.CustomizeHome },
                    conventionalAmountEntry = conventionalAmountEntry,
                    onConventionalAmountEntryChange = {
                        displayPreferences.conventionalAmountEntry = it
                        conventionalAmountEntry = it
                    },
                    showBottomNavigationLabels = showBottomNavigationLabels,
                    onShowBottomNavigationLabelsChange = {
                        displayPreferences.showBottomNavigationLabels = it
                        showBottomNavigationLabels = it
                    },
                    showCurrentBalanceSummary = showCurrentBalanceSummary,
                    onShowCurrentBalanceSummaryChange = {
                        displayPreferences.showCurrentBalanceSummary = it
                        showCurrentBalanceSummary = it
                    },
                    returnToRootRequest = rootRequests[MainDestination.Manage] ?: 0,
                )
            }
        }
        }
        }
    }
    if (budgetReplacementInProgress) BudgetSwitchOverlay()
    }
}

@Composable
private fun NoBudgetScreen(modifier: Modifier, onConnect: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.navigation_no_budget_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.navigation_no_budget_message),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onConnect) { Text(stringResource(R.string.navigation_connect_to_actual)) }
    }
}
