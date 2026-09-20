package com.azimulkabir.actua.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSectionHeader
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Stable Home root. Dashboard slices fill these keyed sections independently, preserving this
 * list's scroll position when a tab is reselected or individual section data changes.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    projection: HomeDashboardProjection = HomeDashboardProjection.empty(),
    sections: List<HomeSection> = HomeSection.entries,
    hideDecimalPlaces: Boolean = false,
    onBudgetClick: () -> Unit = {},
    onAccountsClick: () -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onTransactionsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    onReportClick: (String) -> Unit = {},
    onCustomizeClick: () -> Unit = {},
    returnToRootRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        item(key = "home-header") {
            ActuaScreenHeader(title = stringResource(R.string.home_title)) {
                IconButton(onClick = onCustomizeClick) {
                    Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.home_customize))
                }
            }
        }
        sections.forEach { section ->
            item(key = section.name) {
                Column {
                    HomeSectionHeader(stringResource(section.titleRes))
                    when (section) {
                        HomeSection.READY_TO_BUDGET -> ReadyToBudgetCard(projection.budgetOverview, hideDecimalPlaces, onBudgetClick)
                        HomeSection.FAVORITE_CATEGORIES -> CategoryRows(projection.favoriteCategories, hideDecimalPlaces, onBudgetClick)
                        HomeSection.FAVORITE_ACCOUNTS -> AccountRows(projection.favoriteAccounts, hideDecimalPlaces, onAccountsClick)
                        HomeSection.UPCOMING -> ScheduleRows(projection.upcomingSchedules, hideDecimalPlaces, onSchedulesClick)
                        HomeSection.THIS_MONTH -> ThisMonthCard(projection.monthTransactions, hideDecimalPlaces, onTransactionsClick)
                        HomeSection.REPORTS -> ReportRows(projection.favoriteReports, onReportClick, onReportsClick)
                        HomeSection.RECENT_ACTIVITY -> TransactionRows(projection.recentTransactions, hideDecimalPlaces, onTransactionsClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String) = ActuaSectionHeader(title = title)

@Composable private fun ReadyToBudgetCard(overview: BudgetOverview, hideDecimals: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.home_ready_to_budget), style = MaterialTheme.typography.labelLarge); Text(formatMoneyCents(overview.toBudgetCents ?: 0L, hideDecimals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); if (overview.toBudgetCents == null) Text(stringResource(R.string.home_no_budget_month), style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Outlined.PieChartOutline, contentDescription = null)
        }
    }
}

@Composable private fun CategoryRows(categories: List<BudgetCategory>, hideDecimals: Boolean, onClick: () -> Unit) {
    if (categories.isEmpty()) HomeEmptyRow(stringResource(R.string.home_no_favorite_categories), onClick) else categories.take(5).forEach { HomeValueRow(it.name, stringResource(R.string.home_available), it.balanceCents, hideDecimals, onClick) }
}
@Composable private fun AccountRows(accounts: List<Account>, hideDecimals: Boolean, onClick: () -> Unit) {
    if (accounts.isEmpty()) HomeEmptyRow(stringResource(R.string.home_no_favorite_accounts), onClick) else accounts.take(5).forEach { HomeValueRow(it.name, accountTypeLabel(it.type), it.balanceCents, hideDecimals, onClick) }
}
@Composable private fun ScheduleRows(schedules: List<ScheduleListItem>, hideDecimals: Boolean, onClick: () -> Unit) {
    val visible = schedules.filter { it.status !in setOf(ScheduleStatus.COMPLETED, ScheduleStatus.PAID) }.take(5)
    if (visible.isEmpty()) HomeEmptyRow(stringResource(R.string.home_no_upcoming), onClick) else visible.forEach { HomeValueRow(it.title, scheduleLabel(it), it.schedule.postAmount, hideDecimals, onClick) }
}
@Composable private fun ReportRows(reports: List<ReportDashboardPage>, onReportClick: (String) -> Unit, onViewAllClick: () -> Unit) {
    if (reports.isEmpty()) {
        HomeDestinationRow(stringResource(R.string.home_dashboards), Icons.Outlined.BarChart, onViewAllClick)
    } else {
        reports.take(5).forEach { report ->
            HomeDestinationRow(report.name, Icons.Outlined.BarChart) { onReportClick(report.id) }
        }
        HomeDestinationRow(stringResource(R.string.home_view_all_reports), Icons.Outlined.BarChart, onViewAllClick)
    }
}
@Composable private fun ThisMonthCard(transactions: List<Transaction>, hideDecimals: Boolean, onClick: () -> Unit) {
    var income = 0L
    var spending = 0L
    transactions.forEach {
        when {
            it.amountCents > 0L -> income += it.amountCents
            it.amountCents < 0L -> spending -= it.amountCents
        }
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm).clickable(onClick = onClick)) { Row(Modifier.fillMaxWidth().padding(Spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) { SummaryValue(stringResource(R.string.home_income), income, hideDecimals); SummaryValue(stringResource(R.string.home_spent), spending, hideDecimals); Column { Text(stringResource(R.string.home_activity), style = MaterialTheme.typography.labelMedium); Text(transactions.size.toString(), fontWeight = FontWeight.SemiBold) } } }
}
@Composable private fun SummaryValue(label: String, amount: Long, hideDecimals: Boolean) { Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatMoneyCents(amount, hideDecimals), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
@Composable private fun TransactionRows(transactions: List<Transaction>, hideDecimals: Boolean, onClick: () -> Unit) { if (transactions.isEmpty()) HomeEmptyRow(stringResource(R.string.home_no_recent_activity), onClick) else transactions.take(5).forEach { HomeValueRow(it.payee.ifBlank { stringResource(R.string.home_transaction) }, it.category.ifBlank { it.account }, it.amountCents, hideDecimals, onClick) } }
@Composable private fun HomeValueRow(title: String, subtitle: String, amount: Long, hideDecimals: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text(formatMoneyCents(amount, hideDecimals), fontWeight = FontWeight.SemiBold) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) }
@Composable private fun HomeEmptyRow(label: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) } }
@Composable private fun HomeDestinationRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null); Spacer(Modifier.width(Spacing.md)); Text(label, Modifier.weight(1f)); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) } }
@Composable
private fun scheduleLabel(item: ScheduleListItem): String {
    val locale = LocalConfiguration.current.locales[0]
    val due = item.schedule.nextDate?.let { date ->
        when (DayDate.today().daysUntil(date)) {
            in Int.MIN_VALUE..-1 -> stringResource(R.string.home_overdue)
            0 -> stringResource(R.string.home_due_today)
            1 -> stringResource(R.string.home_due_tomorrow)
            else -> stringResource(
                R.string.home_due_date,
                runCatching { LocalDate.parse(date.iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
                    .getOrDefault(date.iso),
            )
        }
    } ?: stringResource(R.string.home_scheduled)
    return listOfNotNull(due, item.accountName?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

@Composable
private fun accountTypeLabel(type: String): String = when (type.lowercase()) {
    "checking" -> stringResource(R.string.home_account_checking)
    "savings" -> stringResource(R.string.home_account_savings)
    "credit" -> stringResource(R.string.home_account_credit)
    "investment" -> stringResource(R.string.home_account_investment)
    "mortgage" -> stringResource(R.string.home_account_mortgage)
    "debt" -> stringResource(R.string.home_account_debt)
    else -> type
}
