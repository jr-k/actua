package com.azimulkabir.actua.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.data.reports.CoreReportEngine.DisplayToken
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.text.DateFormatSymbols
import kotlin.math.absoluteValue
import kotlin.math.max

@Composable
fun ReportsScreen(
    snapshot: ReportSnapshot,
    hideDecimalPlaces: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onSearch: () -> Unit = {},
    favoriteReportIds: Set<String> = emptySet(),
    onFavoriteReportChange: (String, Boolean) -> Unit = { _, _ -> },
    scrollToTopRequest: Int = 0,
    initialPageId: String? = null,
    initialPageRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    var selectedPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = snapshot.dashboards.firstOrNull { it.id == selectedPageId }
        ?: snapshot.dashboards.firstOrNull()
    LaunchedEffect(snapshot.dashboards.map { it.id }) {
        // Dashboards load asynchronously and start empty, including right after process/config
        // recreation restores selectedPageId; skip the reset until there's something to check
        // against, or it would immediately discard the restored (or just-requested) selection.
        if (snapshot.dashboards.isNotEmpty() && snapshot.dashboards.none { it.id == selectedPageId }) {
            selectedPageId = snapshot.dashboards.firstOrNull()?.id
        }
    }
    LaunchedEffect(initialPageRequest) {
        if (initialPageRequest > 0) selectedPageId = initialPageId
    }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            Spacing.screenHorizontal, Spacing.screenHorizontal, Spacing.screenHorizontal, 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                ) {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.common_search_actua))
                    }
                }
            }
        }
        if (isLoading && selected == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.testTag("reportsLoadingIndicator"))
                }
            }
        } else if (selected == null) {
            item { EmptyReports() }
        } else {
            item {
                DashboardPicker(selected, snapshot.dashboards, pickerOpen, selected.id in favoriteReportIds,
                    onFavoriteChange = { onFavoriteReportChange(selected.id, it) }, onOpenChange = { pickerOpen = it }) {
                    selectedPageId = it; pickerOpen = false
                }
            }
            val unsupported = selected.widgets.filter { it.kind == ReportWidgetKind.UNSUPPORTED }
            if (unsupported.isNotEmpty()) item {
                val unsupportedNames = buildList {
                    for (widget in unsupported) add(localizedReportName(widget.name, widget.sourceType))
                }.joinToString()
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        pluralStringResource(
                            R.plurals.reports_unsupported_widgets,
                            unsupported.size,
                            unsupported.size,
                            unsupportedNames,
                        ),
                        Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val visible = selected.widgets.filterNot { it.kind == ReportWidgetKind.UNSUPPORTED }
            if (visible.isEmpty()) item { EmptyReports() }
            items(visible, key = { it.id }) { widget -> WidgetCard(widget, hideDecimalPlaces) }
        }
    }
}

@Composable
private fun DashboardPicker(
    selected: ReportDashboardPage,
    pages: List<ReportDashboardPage>,
    expanded: Boolean,
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    onOpenChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    val selectedName = localizedReportName(selected.name)
    Box {
        Card(
            Modifier.fillMaxWidth().clickable(enabled = pages.size > 1) { onOpenChange(true) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selectedName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { onFavoriteChange(!favorite) }) {
                    Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.reports_remove_favorite else R.string.reports_add_favorite,
                            selectedName,
                        ))
                }
                if (pages.size > 1) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.reports_switch_dashboard))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onOpenChange(false) }) {
            pages.forEach { page ->
                DropdownMenuItem(text = { Text(localizedReportName(page.name)) }, onClick = { onSelect(page.id) })
            }
        }
    }
}

@Composable
private fun WidgetCard(widget: ReportWidget, hideDecimals: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(localizedReportName(widget.name, widget.sourceType),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (widget.kind) {
                ReportWidgetKind.SUMMARY -> Text(
                    widget.percentage?.let { "${"%.2f".format(it)}%" }
                        ?: formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                ReportWidgetKind.NET_WORTH -> {
                    Text(formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TrendChart(widget.points)
                    PointLabels(widget.points, hideDecimals)
                }
                ReportWidgetKind.CASH_FLOW -> CashFlow(widget.points, hideDecimals)
                ReportWidgetKind.SPENDING -> Spending(widget, hideDecimals)
                ReportWidgetKind.MARKDOWN -> Text(widget.markdown.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReportWidgetKind.AGE_OF_MONEY -> AgeOfMoney(widget)
                ReportWidgetKind.FORMULA -> Formula(widget, hideDecimals)
                ReportWidgetKind.CUSTOM_REPORT -> CategoryBars(widget, hideDecimals)
                ReportWidgetKind.CALENDAR -> CalendarReport(widget, hideDecimals)
                ReportWidgetKind.CROSSOVER -> Crossover(widget, hideDecimals)
                ReportWidgetKind.BUDGET_ANALYSIS -> ComparisonSeries(
                    widget.points,
                    stringResource(R.string.reports_budgeted),
                    stringResource(R.string.reports_spent),
                    hideDecimals,
                )
                ReportWidgetKind.SANKEY -> Sankey(widget, hideDecimals)
                ReportWidgetKind.BALANCE_FORECAST -> {
                    Text(formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TrendChart(widget.points)
                    PointLabels(widget.points, hideDecimals)
                }
                ReportWidgetKind.MONTE_CARLO -> {
                    Text(stringResource(R.string.reports_projected, formatMoneyCents(widget.valueCents ?: 0, hideDecimals)),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    ComparativeTrendChart(widget.points)
                    Text(stringResource(R.string.reports_projection_caption), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ReportWidgetKind.UNSUPPORTED -> Unit
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<ReportPoint>) {
    val color = MaterialTheme.colorScheme.primary
    if (points.isEmpty()) return
    val minimum = points.minOf { it.primaryCents }
    val maximum = points.maxOf { it.primaryCents }
    val span = (maximum - minimum).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val step = if (points.size <= 1) 0f else size.width / (points.size - 1)
        points.zipWithNext().forEachIndexed { index, pair ->
            fun y(value: Long) = size.height - ((value - minimum).toFloat() / span * size.height)
            drawLine(color, Offset(step * index, y(pair.first.primaryCents)),
                Offset(step * (index + 1), y(pair.second.primaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PointLabels(points: List<ReportPoint>, hideDecimals: Boolean) {
    if (points.isEmpty()) Text(stringResource(R.string.common_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Row(Modifier.fillMaxWidth()) {
        Text(formatReportPeriod(points.first().period), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatMoneyCents(points.last().primaryCents, hideDecimals), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(formatReportPeriod(points.last().period), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CashFlow(points: List<ReportPoint>, hideDecimals: Boolean) {
    if (points.isEmpty()) { Text(stringResource(R.string.common_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val maximum = points.maxOf { max(it.primaryCents, it.secondaryCents) }.coerceAtLeast(1)
    points.forEach { point ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(formatReportPeriod(point.period), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
                Text(stringResource(R.string.reports_in, formatMoneyCents(point.primaryCents, hideDecimals)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.reports_out, formatMoneyCents(point.secondaryCents, hideDecimals)), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(point.primaryCents.toFloat().coerceAtLeast(1f) / maximum).height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, PillShape))
                Spacer(Modifier.weight(point.secondaryCents.toFloat().coerceAtLeast(1f) / maximum).height(6.dp)
                    .background(MaterialTheme.colorScheme.tertiary, PillShape))
            }
        }
    }
}

@Composable
private fun Spending(widget: ReportWidget, hideDecimals: Boolean) {
    val current = widget.valueCents ?: 0
    val comparison = widget.comparisonCents ?: 0
    val maximum = max(current.coerceAtLeast(0), comparison.coerceAtLeast(0)).coerceAtLeast(1)
    Text(formatMoneyCents(current, hideDecimals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.reports_comparison, formatMoneyCents(comparison, hideDecimals)), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.fillMaxWidth((current.toFloat() / maximum).coerceIn(0f, 1f)).height(9.dp)
        .background(MaterialTheme.colorScheme.primary, PillShape))
}

@Composable
private fun AgeOfMoney(widget: ReportWidget) {
    val days = widget.valueCents
    Text(if (days == null) stringResource(R.string.reports_no_age)
        else pluralStringResource(R.plurals.reports_days, days.toInt(), days.toInt()),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
    TrendChart(widget.points)
    if (widget.points.isNotEmpty()) Row(Modifier.fillMaxWidth()) {
        Text(formatReportPeriod(widget.points.first().period), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatReportPeriod(widget.points.last().period), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Formula(widget: ReportWidget, hideDecimals: Boolean) {
    widget.valueCents?.let {
        Text(formatMoneyCents(it, hideDecimals), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
    } ?: Text(
        widget.markdown?.let { localizedReportText(it) } ?: stringResource(R.string.reports_formula_unavailable),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CategoryBars(widget: ReportWidget, hideDecimals: Boolean) {
    if (widget.categories.isEmpty()) {
        Text(stringResource(R.string.common_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maximum = widget.categories.maxOf { it.spentCents.absoluteValue }.coerceAtLeast(1)
    widget.categories.take(10).forEach { category ->
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(localizedReportText(category.name), maxLines = 1, modifier = Modifier.weight(1f))
                Text(formatMoneyCents(category.spentCents, hideDecimals), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.fillMaxWidth((category.spentCents.absoluteValue.toFloat() / maximum).coerceIn(0f, 1f))
                .height(7.dp).background(MaterialTheme.colorScheme.primary, PillShape))
        }
    }
}

@Composable
private fun CalendarReport(widget: ReportWidget, hideDecimals: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val dated = widget.points.mapNotNull { point ->
        runCatching { LocalDate.parse(point.period) }.getOrNull()?.let { it to point }
    }
    if (dated.isEmpty()) {
        Text(stringResource(R.string.common_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val month = YearMonth.from(dated.last().first)
    Row(Modifier.fillMaxWidth()) {
        Text(month.month.getDisplayName(TextStyle.FULL, locale) + " ${month.year}",
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("▲ ${formatMoneyCents(widget.valueCents ?: 0, hideDecimals)}", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("▼ ${formatMoneyCents(widget.comparisonCents ?: 0, hideDecimals)}", color = MaterialTheme.colorScheme.error)
    }
    val values = dated.filter { YearMonth.from(it.first) == month }.associate { it.first.dayOfMonth to it.second }
    Row(Modifier.fillMaxWidth()) {
        DateFormatSymbols.getInstance(locale).shortWeekdays
            .let { names -> listOf(names[1], names[2], names[3], names[4], names[5], names[6], names[7]) }
            .forEach { day ->
            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { it }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (week + List(7 - week.size) { null }).forEach { day ->
                val point = day?.let(values::get)
                Column(
                    Modifier.weight(1f).height(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(day?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if ((point?.primaryCents ?: 0) > 0) Spacer(Modifier.weight(1f).height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, PillShape))
                        if ((point?.secondaryCents ?: 0) > 0) Spacer(Modifier.weight(1f).height(3.dp)
                            .background(MaterialTheme.colorScheme.error, PillShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun Crossover(widget: ReportWidget, hideDecimals: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val months = widget.valueCents
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(if (months == null) stringResource(R.string.reports_not_reached)
            else stringResource(R.string.reports_years, String.format(locale, "%.1f", months / 12.0)),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.reports_years_to_retire), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ComparativeTrendChart(widget.points)
    Text(stringResource(R.string.reports_investment_expenses, formatMoneyCents(widget.comparisonCents ?: 0, hideDecimals)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ComparisonSeries(points: List<ReportPoint>, primary: String, secondary: String, hideDecimals: Boolean) {
    if (points.isEmpty()) { Text(stringResource(R.string.common_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    ComparativeTrendChart(points)
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reports_series_primary, primary), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.reports_series_primary, secondary), color = MaterialTheme.colorScheme.tertiary)
    }
    val last = points.last()
    Text("${formatReportPeriod(last.period)} · ${formatMoneyCents(last.primaryCents, hideDecimals)} / ${formatMoneyCents(last.secondaryCents, hideDecimals)}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Sankey(widget: ReportWidget, hideDecimals: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(0.8f)) {
            Text(stringResource(R.string.reports_income), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoneyCents(widget.valueCents ?: 0, hideDecimals), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            widget.categories.take(8).forEach { category ->
                Row(Modifier.fillMaxWidth()) {
                    Text(localizedReportText(category.name), maxLines = 1, modifier = Modifier.weight(1f))
                    Text(formatMoneyCents(category.spentCents, hideDecimals), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ComparativeTrendChart(points: List<ReportPoint>) {
    if (points.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val minimum = points.minOf { minOf(it.primaryCents, it.secondaryCents) }
    val maximum = points.maxOf { maxOf(it.primaryCents, it.secondaryCents) }
    val span = (maximum - minimum).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val step = if (points.size <= 1) 0f else size.width / (points.size - 1)
        fun y(value: Long) = size.height - ((value - minimum).toFloat() / span * size.height)
        points.zipWithNext().forEachIndexed { index, pair ->
            drawLine(primary, Offset(step * index, y(pair.first.primaryCents)),
                Offset(step * (index + 1), y(pair.second.primaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(secondary, Offset(step * index, y(pair.first.secondaryCents)),
                Offset(step * (index + 1), y(pair.second.secondaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun EmptyReports() {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.reports_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.reports_empty_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun localizedReportName(name: String, sourceType: String? = null): String = when (name) {
    DisplayToken.UNSUPPORTED_REPORT -> sourceType?.takeIf(String::isNotBlank)?.let {
        stringResource(R.string.reports_unsupported_report_type, it)
    } ?: stringResource(R.string.reports_unsupported_report)
    else -> localizedReportText(name)
}

@Composable
private fun localizedReportText(value: String): String = when {
    value.isBlank() -> stringResource(R.string.common_unknown)
    value == DisplayToken.DASHBOARD -> stringResource(R.string.reports_default_dashboard)
    value == DisplayToken.UNTITLED -> stringResource(R.string.reports_untitled)
    value == DisplayToken.SUMMARY -> stringResource(R.string.reports_summary)
    value == DisplayToken.NET_WORTH -> stringResource(R.string.reports_net_worth)
    value == DisplayToken.CASH_FLOW -> stringResource(R.string.reports_cash_flow)
    value == DisplayToken.SPENDING -> stringResource(R.string.reports_spending)
    value == DisplayToken.NOTES -> stringResource(R.string.reports_notes)
    value == DisplayToken.AGE_OF_MONEY -> stringResource(R.string.reports_age_of_money)
    value == DisplayToken.FORMULA -> stringResource(R.string.reports_formula)
    value == DisplayToken.CUSTOM_REPORT -> stringResource(R.string.reports_custom_report)
    value == DisplayToken.CALENDAR -> stringResource(R.string.reports_calendar)
    value == DisplayToken.CROSSOVER -> stringResource(R.string.reports_crossover)
    value == DisplayToken.BUDGET_ANALYSIS -> stringResource(R.string.reports_budget_analysis)
    value == DisplayToken.SANKEY -> stringResource(R.string.reports_sankey)
    value == DisplayToken.BALANCE_FORECAST -> stringResource(R.string.reports_balance_forecast)
    value == DisplayToken.MONTE_CARLO -> stringResource(R.string.reports_monte_carlo)
    value == DisplayToken.UNSUPPORTED_REPORT -> stringResource(R.string.reports_unsupported_report)
    value == DisplayToken.UNCATEGORIZED -> stringResource(R.string.reports_uncategorized)
    value == DisplayToken.OTHER -> stringResource(R.string.reports_other)
    value == DisplayToken.FORMULA_UNSUPPORTED -> stringResource(R.string.reports_formula_unsupported)
    else -> value
}

@Composable
private fun formatReportPeriod(period: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return runCatching {
        YearMonth.parse(period.take(7)).format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
    }.getOrElse {
        runCatching {
            LocalDate.parse(period).format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale))
        }.getOrDefault(period)
    }
}
