package com.azimulkabir.actua.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.data.preferences.withAppLanguage
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleWidgetEntry
import com.azimulkabir.actua.data.schedules.ScheduleWidgetPeriod
import com.azimulkabir.actua.data.schedules.ScheduleWidgetProjection
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.NumberDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

object WidgetActions {
    const val BUDGET = "com.azimulkabir.actua.widget.BUDGET"
    const val ACCOUNTS = "com.azimulkabir.actua.widget.ACCOUNTS"
    const val CATEGORY = "com.azimulkabir.actua.widget.CATEGORY"
    const val ADD_EXPENSE = "com.azimulkabir.actua.widget.ADD_EXPENSE"
    const val ADD_INCOME = "com.azimulkabir.actua.widget.ADD_INCOME"
    const val ADD_TRANSFER = "com.azimulkabir.actua.widget.ADD_TRANSFER"
    const val SEARCH = "com.azimulkabir.actua.widget.SEARCH"
    const val SCHEDULES = "com.azimulkabir.actua.widget.SCHEDULES"
    const val EXTRA_TARGET = "widget_target"
}

enum class WidgetKind { Categories, Accounts }

internal class WidgetPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun selected(kind: WidgetKind, widgetId: Int): Set<String> =
        values.getStringSet(key(kind, widgetId), emptySet()).orEmpty()

    fun save(kind: WidgetKind, widgetId: Int, ids: Set<String>) {
        values.edit().putStringSet(key(kind, widgetId), ids).apply()
    }

    fun remove(kind: WidgetKind, widgetIds: IntArray) {
        values.edit().apply {
            widgetIds.forEach { remove(key(kind, it)) }
        }.apply()
    }

    private fun key(kind: WidgetKind, widgetId: Int) = "${kind.name.lowercase()}_$widgetId"

    private companion object { const val NAME = "home_widget_preferences" }
}

internal class ScheduleWidgetPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun periodDays(widgetId: Int): Int = values.getInt(key(widgetId), ScheduleWidgetPeriod.FOURTEEN.days)

    fun save(widgetId: Int, days: Int) {
        values.edit().putInt(key(widgetId), days).apply()
    }

    fun remove(widgetIds: IntArray) {
        values.edit().apply {
            widgetIds.forEach { remove(key(it)) }
        }.apply()
    }

    private fun key(widgetId: Int) = "period_$widgetId"

    private companion object { const val NAME = "schedule_widget_preferences" }
}

abstract class ActuaWidgetProvider : AppWidgetProvider() {
    abstract val kind: WidgetKind?

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        scheduleUpdate(context, manager, widgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        scheduleUpdate(context, manager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        kind?.let { WidgetPreferences(context).remove(it, widgetIds) }
    }

    private fun scheduleUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                val localizedContext = context.withAppLanguage()
                widgetIds.forEach { WidgetUpdater.update(localizedContext, manager, it, this) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}

class BudgetSnapshotWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null
}

class FavouriteCategoriesWidgetProvider : ActuaWidgetProvider() {
    override val kind = WidgetKind.Categories
}

class QuickTransactionWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null
}

class AccountBalancesWidgetProvider : ActuaWidgetProvider() {
    override val kind = WidgetKind.Accounts
}

class ScheduledTransactionsWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        super.onDeleted(context, widgetIds)
        ScheduleWidgetPreferences(context).remove(widgetIds)
    }
}

object WidgetUpdater {
    private const val COMPACT_HEIGHT_DP = 100

    private val providers = listOf(
        BudgetSnapshotWidgetProvider::class.java,
        FavouriteCategoriesWidgetProvider::class.java,
        QuickTransactionWidgetProvider::class.java,
        AccountBalancesWidgetProvider::class.java,
        ScheduledTransactionsWidgetProvider::class.java,
    )

    fun requestAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        providers.forEach { provider ->
            val component = ComponentName(app, provider)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                app.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    this.component = component
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }
    }

    internal fun update(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        provider: ActuaWidgetProvider,
    ) {
        when (provider) {
            is BudgetSnapshotWidgetProvider -> updateBudget(context, manager, widgetId)
            is FavouriteCategoriesWidgetProvider -> updateCategories(context, manager, widgetId)
            is QuickTransactionWidgetProvider -> updateQuickTransaction(context, manager, widgetId)
            is AccountBalancesWidgetProvider -> updateAccounts(context, manager, widgetId)
            is ScheduledTransactionsWidgetProvider -> updateSchedules(context, manager, widgetId)
            else -> Unit
        }
    }

    private fun isCompact(manager: AppWidgetManager, widgetId: Int): Boolean {
        val options = manager.getAppWidgetOptions(widgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, Int.MAX_VALUE)
        return minHeight <= COMPACT_HEIGHT_DP
    }

    private fun updateBudget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val layout = if (isCompact(manager, widgetId)) {
            R.layout.widget_budget_snapshot_compact
        } else {
            R.layout.widget_budget_snapshot
        }
        val views = RemoteViews(context.packageName, layout)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_budget_snapshot))
        views.setTextViewText(R.id.widget_ready_label, context.getString(R.string.widget_ready))
        views.setTextViewText(R.id.widget_budgeted_label, context.getString(R.string.widget_budgeted))
        views.setTextViewText(R.id.widget_balance_label, context.getString(R.string.widget_balance))
        val repository = ActuaRepository(context)
        try {
            if (!repository.isUsingActualBudget) {
                views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_no_budget))
                views.setTextViewText(R.id.widget_ready_value, "—")
                views.setTextViewText(R.id.widget_budgeted_value, "—")
                views.setTextViewText(R.id.widget_balance_value, "—")
            } else {
                val overview = repository.budgetOverview()
                val month = YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", contextLocale(context)))
                views.setTextViewText(R.id.widget_month, month)
                views.setTextViewText(
                    R.id.widget_ready_value,
                    overview.toBudgetCents?.let { money(context, it) } ?: "—",
                )
                views.setTextViewText(R.id.widget_budgeted_value, money(context, overview.budgetedCents))
                views.setTextViewText(R.id.widget_balance_value, money(context, overview.availableCents))
            }
            views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.BUDGET, widgetId))
            manager.updateAppWidget(widgetId, views)
        } finally {
            repository.close()
        }
    }

    private fun updateCategories(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_favourite_categories)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_favourite_categories))
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_categories))
        val repository = ActuaRepository(context)
        try {
            val all = repository.budgetGroups().asSequence()
                .filterNot { it.hidden || it.isIncome }
                .flatMap { it.categories.asSequence() }
                .filterNot { it.hidden }
                .toList()
            val budgetId = ActiveBudgetStore(context).budgetId ?: "no-budget"
            val favorites = FavoritePreferences(context).ids(budgetId, FavoritePreferences.Type.CATEGORY)
            val rows = all.filter { it.id in favorites }.take(4)
            bindRows(
                context, views, rows.map { row ->
                    val spent = row.spentCents.coerceAtLeast(0)
                    val denominator = row.assignedCents.coerceAtLeast(1)
                    WidgetRow(row.name.ifBlank { context.getString(R.string.common_unknown) }, money(context, row.balanceCents),
                        ((spent * 100 / denominator).coerceIn(0, 100)).toInt(), row.name)
                }, widgetId, WidgetActions.CATEGORY, showProgress = true,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.BUDGET, widgetId))
            manager.updateAppWidget(widgetId, views)
        } finally {
            repository.close()
        }
    }

    private fun updateQuickTransaction(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val layout = if (isCompact(manager, widgetId)) {
            R.layout.widget_quick_transaction_compact
        } else {
            R.layout.widget_quick_transaction
        }
        val views = RemoteViews(context.packageName, layout)
        views.setTextViewText(R.id.widget_expense, context.getString(R.string.widget_expense))
        views.setTextViewText(R.id.widget_income, context.getString(R.string.widget_income))
        views.setTextViewText(R.id.widget_transfer, context.getString(R.string.widget_transfer))
        views.setOnClickPendingIntent(R.id.widget_expense, open(context, WidgetActions.ADD_EXPENSE, widgetId))
        views.setOnClickPendingIntent(R.id.widget_income, open(context, WidgetActions.ADD_INCOME, widgetId))
        views.setOnClickPendingIntent(R.id.widget_transfer, open(context, WidgetActions.ADD_TRANSFER, widgetId))
        manager.updateAppWidget(widgetId, views)
    }

    private fun updateAccounts(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_account_balances)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_account_balances))
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_accounts))
        val repository = ActuaRepository(context)
        try {
            val all = repository.accounts().filterNot { it.closed }
            val selected = WidgetPreferences(context).selected(WidgetKind.Accounts, widgetId)
            val rows = if (selected.isEmpty()) all.take(4) else all.filter { it.id in selected }.take(4)
            bindRows(
                context, views, rows.map {
                    WidgetRow(
                        it.name.ifBlank { context.getString(R.string.common_unknown) },
                        money(context, it.balanceCents),
                        0,
                        it.name,
                    )
                },
                widgetId, WidgetActions.ACCOUNTS, showProgress = false,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.ACCOUNTS, widgetId))
            manager.updateAppWidget(widgetId, views)
        } finally {
            repository.close()
        }
    }

    private fun updateSchedules(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val compact = isCompact(manager, widgetId)
        val layout = if (compact) R.layout.widget_scheduled_transactions_compact else R.layout.widget_scheduled_transactions
        val maxRows = if (compact) 2 else 4
        val views = RemoteViews(context.packageName, layout)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_upcoming_schedules))
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_schedules))
        val repository = ActuaRepository(context)
        try {
            val entries = if (!repository.isUsingActualBudget) {
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_budget))
                emptyList()
            } else {
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_schedules))
                val today = DayDate.today()
                val periodDays = ScheduleWidgetPreferences(context).periodDays(widgetId)
                ScheduleWidgetProjection.upcoming(repository.schedules(today), today, periodDays).take(maxRows)
            }
            bindScheduleRows(context, views, entries, widgetId, maxRows)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.SCHEDULES, widgetId))
            manager.updateAppWidget(widgetId, views)
        } finally {
            repository.close()
        }
    }

    private fun bindScheduleRows(
        context: Context,
        views: RemoteViews,
        entries: List<ScheduleWidgetEntry>,
        widgetId: Int,
        maxRows: Int,
    ) {
        val containers = intArrayOf(
            R.id.widget_schedule_row_1, R.id.widget_schedule_row_2, R.id.widget_schedule_row_3, R.id.widget_schedule_row_4,
        ).take(maxRows)
        val titles = intArrayOf(
            R.id.widget_schedule_title_1, R.id.widget_schedule_title_2, R.id.widget_schedule_title_3, R.id.widget_schedule_title_4,
        ).take(maxRows)
        val dues = intArrayOf(
            R.id.widget_schedule_due_1, R.id.widget_schedule_due_2, R.id.widget_schedule_due_3, R.id.widget_schedule_due_4,
        ).take(maxRows)
        val amounts = intArrayOf(
            R.id.widget_schedule_amount_1, R.id.widget_schedule_amount_2, R.id.widget_schedule_amount_3, R.id.widget_schedule_amount_4,
        ).take(maxRows)
        containers.indices.forEach { index ->
            val entry = entries.getOrNull(index)
            views.setViewVisibility(containers[index], if (entry == null) View.GONE else View.VISIBLE)
            if (entry != null) {
                views.setTextViewText(titles[index], entry.item.title)
                views.setTextViewText(dues[index], relativeDueLabel(context, entry.dueDate))
                views.setTextViewText(amounts[index], scheduleAmount(context, entry.item.schedule))
                views.setOnClickPendingIntent(
                    containers[index], open(context, WidgetActions.SCHEDULES, widgetId * 10 + index + 1),
                )
            }
        }
        views.setViewVisibility(R.id.widget_empty, if (entries.isEmpty()) View.VISIBLE else View.GONE)
    }

    private fun relativeDueLabel(context: Context, dueDate: DayDate): String {
        val days = DayDate.today().daysUntil(dueDate)
        return when {
            days == 0 -> context.getString(R.string.widget_due_today)
            days == 1 -> context.getString(R.string.widget_due_tomorrow)
            days > 1 -> context.resources.getQuantityString(R.plurals.widget_due_in_days, days, days)
            else -> context.resources.getQuantityString(R.plurals.widget_days_overdue, -days, -days)
        }
    }

    private fun scheduleAmount(context: Context, schedule: ActualScheduleSummary): String {
        val amount = schedule.amount
        return if (amount is ScheduledAmount.Range) {
            val low = minOf(amount.first, amount.second)
            val high = maxOf(amount.first, amount.second)
            "${money(context, low)} – ${money(context, high)}"
        } else {
            (if (schedule.amountOp == ScheduleAmountOp.APPROXIMATE) "~ " else "") + money(context, schedule.postAmount)
        }
    }

    private data class WidgetRow(val name: String, val amount: String, val progress: Int, val target: String)

    private fun bindRows(
        context: Context,
        views: RemoteViews,
        rows: List<WidgetRow>,
        widgetId: Int,
        action: String,
        showProgress: Boolean,
    ) {
        val containers = intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4)
        val names = intArrayOf(R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4)
        val amounts = intArrayOf(R.id.widget_amount_1, R.id.widget_amount_2, R.id.widget_amount_3, R.id.widget_amount_4)
        val progress = intArrayOf(R.id.widget_progress_1, R.id.widget_progress_2, R.id.widget_progress_3, R.id.widget_progress_4)
        containers.indices.forEach { index ->
            val row = rows.getOrNull(index)
            views.setViewVisibility(containers[index], if (row == null) View.GONE else View.VISIBLE)
            if (row != null) {
                views.setTextViewText(names[index], row.name)
                views.setTextViewText(amounts[index], row.amount)
                views.setViewVisibility(progress[index], if (showProgress) View.VISIBLE else View.GONE)
                if (showProgress) views.setProgressBar(progress[index], 100, row.progress, false)
                views.setOnClickPendingIntent(
                    containers[index], open(context, action, widgetId * 10 + index + 1, row.target),
                )
            }
        }
        views.setViewVisibility(R.id.widget_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)
    }

    private fun open(context: Context, action: String, requestCode: Int, target: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            target?.let { putExtra(WidgetActions.EXTRA_TARGET, it) }
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun money(context: Context, cents: Long): String {
        val preferences = DisplayPreferences(context)
        CurrencyDisplay.code = preferences.currencyCode
        CurrencyDisplay.symbolOnly = preferences.currencySymbolOnly
        NumberDisplay.format = preferences.numberFormat
        return formatMoneyCents(cents, preferences.hideDecimalPlaces,
            respectBalanceVisibility = false, locale = contextLocale(context))
            .let { if (preferences.hideBalances) "••••" else it }
    }

    private fun contextLocale(context: Context): Locale = context.resources.configuration.locales[0]
}
