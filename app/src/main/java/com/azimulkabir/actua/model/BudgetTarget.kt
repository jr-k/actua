package com.azimulkabir.actua.model

import androidx.annotation.StringRes
import com.azimulkabir.actua.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

/**
 * A user-facing projection of Actual's UI-managed goal templates ("Budget Automation").
 *
 * Type taxonomy matches upstream Actual Budget exactly (`displayTemplateTypes` in
 * packages/desktop-client/src/components/budget/goals/constants.ts): seven contribution
 * types ([Type.isOption] == false) shown in the "+ Add an automation" picker, plus two
 * standalone "Options" ([Type.LIMIT] balance cap, [Type.GOAL] long-term goal) that are not
 * swappable automation types - each category may have at most one of each ([Type.isSingleton]).
 */
data class BudgetTarget(
    val type: Type,
    val amountCents: Long = 0,
    val targetMonth: String? = null,
    val startingDate: String? = null,
    val priority: Int = 1,
    val note: String? = null,

    // FIXED
    val period: Period = Period.MONTH,
    val everyCount: Int = 1,

    // BY_DATE ("Save by date")
    val repeats: Boolean = false,
    val repeatEvery: Int = 1,
    val repeatAnnual: Boolean = false,
    val allowEarlySpending: Boolean = false,
    val spendFromMonth: String? = null,

    // SCHEDULE ("Cover schedule")
    val scheduleId: String? = null,
    val scheduleName: String? = null,
    val scheduleFull: Boolean = false,

    // HISTORICAL ("From history")
    val historicalMode: HistoricalMode = HistoricalMode.AVERAGE,
    val historicalMonths: Int = 3,

    // PERCENTAGE ("% of income")
    val percentage: Int = 0,
    val percentageSource: String = "available funds",
    val percentagePrevious: Boolean = false,

    // Adjustment ("increase"/"decrease" modifier) - SCHEDULE and HISTORICAL(AVERAGE) only,
    // matching upstream's `modifiers` grammar rule. adjustmentPercent/adjustmentAmountCents
    // is signed: positive means increase, negative means decrease.
    val adjustmentType: AdjustmentType? = null,
    val adjustmentPercent: Double? = null,
    val adjustmentAmountCents: Long? = null,

    // REMAINDER ("Whatever is left")
    val weight: Int = 1,

    // LIMIT's own cap (amountCents/period/start/hold below), and REMAINDER's optional
    // secondary cap (limitAmountCents/period/start/hold) - same shape, different amount field
    // so a REMAINDER row can carry both its weight and an optional cap at once.
    val limitPeriod: LimitPeriod? = null,
    val limitAmountCents: Long? = null,
    val limitStartDate: String? = null,
    val limitHold: Boolean = false,
) {
    enum class Type(@StringRes val labelRes: Int, @StringRes val explanationRes: Int) {
        FIXED(R.string.target_fixed_label, R.string.target_fixed_explanation),
        SCHEDULE(R.string.target_schedule_label, R.string.target_schedule_explanation),
        BY_DATE(R.string.target_by_date_label, R.string.target_by_date_explanation),
        PERCENTAGE(R.string.target_percentage_label, R.string.target_percentage_explanation),
        HISTORICAL(R.string.target_history_label, R.string.target_history_explanation),
        REFILL(R.string.target_refill_label, R.string.target_refill_explanation),
        REMAINDER(R.string.target_remainder_label, R.string.target_remainder_explanation),
        LIMIT(R.string.target_limit_label, R.string.target_limit_explanation),
        GOAL(
            R.string.target_goal_label,
            R.string.target_goal_explanation,
        );

        /** Balance cap and long-term goal are standalone "Options", not automation types. */
        val isOption: Boolean get() = this == LIMIT || this == GOAL

        /** At most one of these is allowed per category, matching upstream `SINGLETON_TYPES`. */
        val isSingleton: Boolean get() = this == LIMIT || this == GOAL || this == REFILL || this == REMAINDER

        /**
         * Types that carry a real priority in `goal_def`. Excludes the two Options (no
         * priority field at all) and [REMAINDER] (`priority` is always JSON `null` upstream -
         * remainder automations are never part of the priority-ordered funding pass, they run
         * once after it in [BudgetTemplatePlanner]).
         */
        val hasPriority: Boolean get() = !isOption && this != REMAINDER
    }

    enum class Period(val jsonValue: String) {
        DAY("day"),
        WEEK("week"),
        MONTH("month"),
        YEAR("year");

        companion object {
            fun fromJson(raw: String?): Period? = entries.firstOrNull { it.jsonValue == raw?.trim()?.lowercase() }
        }
    }

    enum class HistoricalMode { AVERAGE, COPY }

    enum class AdjustmentType(val jsonValue: String) { PERCENT("percent"), FIXED("fixed") }

    enum class LimitPeriod(val jsonValue: String) {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly");

        companion object {
            fun fromJson(raw: String?): LimitPeriod? = when (raw?.trim()?.lowercase()) {
                "daily" -> DAILY
                "weekly" -> WEEKLY
                "monthly" -> MONTHLY
                else -> null
            }
        }
    }

    fun limitForMonth(month: String): Long {
        val limit = limitAmountCents ?: return 0L
        return scaledLimitForMonth(limit, month, limitPeriod, limitStartDate)
    }

    /** The balance cap amount for [Type.LIMIT] rows, scaled by cadence for this month. */
    fun capForMonth(month: String): Long {
        if (type != Type.LIMIT) return 0L
        return scaledLimitForMonth(amountCents, month, limitPeriod ?: LimitPeriod.MONTHLY, limitStartDate)
    }

    private fun scaledLimitForMonth(
        amount: Long,
        month: String,
        period: LimitPeriod?,
        startDateRaw: String?,
    ): Long = when (period ?: LimitPeriod.MONTHLY) {
        LimitPeriod.DAILY -> {
            val days = runCatching { YearMonth.parse(month).lengthOfMonth().toLong() }.getOrDefault(0L)
            Math.multiplyExact(amount, days)
        }
        LimitPeriod.WEEKLY -> {
            val monthYear = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
            val monthStart = monthYear.atDay(1)
            val nextMonthStart = monthYear.plusMonths(1).atDay(1)
            var date = runCatching { LocalDate.parse(startDateRaw ?: monthStart.toString()) }.getOrNull() ?: monthStart
            while (date.isBefore(monthStart)) date = date.plusWeeks(1)
            var weeks = 0L
            while (date.isBefore(nextMonthStart)) {
                weeks += 1L
                date = date.plusWeeks(1)
            }
            Math.multiplyExact(amount, weeks)
        }
        LimitPeriod.MONTHLY -> amount
    }

    /**
     * This month's contribution for the types the local preview can evaluate exactly.
     * [Type.REFILL] (needs the sibling [Type.LIMIT] amount), [Type.SCHEDULE], [Type.PERCENTAGE]
     * and [Type.REMAINDER] are computed by [BudgetTemplatePlanner] instead, since they need
     * category/document-wide context this single row doesn't have.
     */
    fun suggestedBudget(category: BudgetCategory, month: String): Long = when (type) {
        Type.FIXED -> fixedSuggestedBudget(month)
        Type.BY_DATE -> byDateSuggestedBudget(category, month)
        Type.HISTORICAL -> when (historicalMode) {
            HistoricalMode.AVERAGE -> {
                val values = category.history.take(historicalMonths.coerceIn(1, 24))
                    .map { kotlin.math.abs(minOf(it.spentCents, 0L)) }
                val average = if (values.isEmpty()) 0L else (values.sum().toDouble() / values.size).toLong()
                max(0L, applyAdjustment(average))
            }
            HistoricalMode.COPY -> {
                val selected = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
                val previous = selected.minusMonths(historicalMonths.coerceAtLeast(1).toLong()).toString()
                category.history.firstOrNull { it.month == previous }?.assignedCents ?: 0L
            }
        }
        Type.GOAL, Type.REMAINDER, Type.PERCENTAGE, Type.SCHEDULE, Type.LIMIT, Type.REFILL -> 0L
    }

    /**
     * "Repeat every N days/weeks/months/years". Days/weeks count qualifying occurrences within
     * the month (matching upstream: "the entire month will be budgeted based on the number of
     * weeks/days in that month"); months/years fire only on cadence-aligned months.
     */
    private fun fixedSuggestedBudget(month: String): Long {
        val selected = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
        val every = everyCount.coerceAtLeast(1)
        return when (period) {
            Period.MONTH -> {
                val start = runCatching { YearMonth.parse((startingDate ?: "$month-01").take(7)) }.getOrNull() ?: selected
                val diff = ChronoUnit.MONTHS.between(start, selected)
                if (diff >= 0 && diff % every == 0L) amountCents else 0L
            }
            Period.YEAR -> {
                val start = runCatching { YearMonth.parse((startingDate ?: "$month-01").take(7)) }.getOrNull() ?: selected
                val diff = ChronoUnit.MONTHS.between(start, selected)
                if (diff >= 0 && diff % (every * 12L) == 0L) amountCents else 0L
            }
            Period.WEEK -> amountCents * countQualifyingDates(selected, every) { it.plusWeeks(1) }
            Period.DAY -> amountCents * countQualifyingDates(selected, every) { it.plusDays(1) }
        }
    }

    /** Applies an "increase"/"decrease" [adjustmentType] modifier, matching upstream `runAverage`. */
    internal fun applyAdjustment(amountCents: Long): Long = when (adjustmentType) {
        AdjustmentType.PERCENT -> Math.round(amountCents * (1.0 + (adjustmentPercent ?: 0.0) / 100.0))
        AdjustmentType.FIXED -> amountCents + (adjustmentAmountCents ?: 0L)
        null -> amountCents
    }

    private fun countQualifyingDates(selected: YearMonth, every: Int, advance: (LocalDate) -> LocalDate): Long {
        val start = runCatching { LocalDate.parse(startingDate) }.getOrNull() ?: selected.atDay(1)
        var date = start
        var index = 0L
        while (date.isBefore(selected.atDay(1))) {
            date = advance(date)
            index++
        }
        var count = 0L
        while (!date.isAfter(selected.atEndOfMonth())) {
            if (index % every == 0L) count++
            date = advance(date)
            index++
        }
        return count
    }

    private fun byDateSuggestedBudget(category: BudgetCategory, month: String): Long {
        val end = runCatching { YearMonth.parse(targetMonth) }.getOrNull() ?: return 0L
        val current = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
        val months = max(1L, ChronoUnit.MONTHS.between(current, end) + 1L)
        return ceil(max(0L, amountCents - category.carryoverCents).toDouble() / months).toLong()
    }

    /** JSON accepted by Actual's visual budget-automation editor and engine. */
    fun toGoalDef(): String {
        val row = JSONObject().put("directive", "template")
        if (type.hasPriority) row.put("priority", priority) else row.put("priority", JSONObject.NULL)
        note?.trim()?.takeIf(String::isNotEmpty)?.let { row.put("description", it) }
        when (type) {
            Type.FIXED -> row.put("type", "periodic").put("amount", units(amountCents))
                .put("period", JSONObject().put("period", period.jsonValue).put("amount", everyCount.coerceAtLeast(1)))
                .put("starting", startingDate)
            Type.BY_DATE -> {
                row.put("type", if (allowEarlySpending) "spend" else "by").put("amount", units(amountCents))
                    .put("month", targetMonth)
                if (allowEarlySpending) row.put("from", spendFromMonth ?: targetMonth)
                if (repeats) row.put("annual", repeatAnnual).put("repeat", repeatEvery.coerceAtLeast(1))
            }
            Type.SCHEDULE -> {
                row.put("type", "schedule")
                scheduleId?.takeIf(String::isNotBlank)?.let { row.put("scheduleId", it) }
                scheduleName?.takeIf(String::isNotBlank)?.let { row.put("name", it) }
                if (scheduleFull) row.put("full", true)
                putAdjustment(row)
            }
            Type.PERCENTAGE -> row.put("type", "percentage").put("percent", percentage)
                .put("category", percentageSource).put("previous", percentagePrevious)
            Type.HISTORICAL -> when (historicalMode) {
                HistoricalMode.AVERAGE -> row.put("type", "average").put("numMonths", historicalMonths.coerceIn(1, 24))
                    .also { putAdjustment(row) }
                HistoricalMode.COPY -> row.put("type", "copy").put("lookBack", historicalMonths.coerceIn(1, 24))
            }
            Type.REFILL -> row.put("type", "refill")
            Type.REMAINDER -> {
                row.put("type", "remainder").put("weight", weight.coerceAtLeast(1))
                val limit = limitPeriod?.let { period ->
                    val raw = JSONObject().put("amount", units(limitAmountCents ?: 0L)).put("period", period.jsonValue)
                        .put("hold", limitHold)
                    if (period == LimitPeriod.WEEKLY && !limitStartDate.isNullOrBlank()) raw.put("start", limitStartDate)
                    raw
                }
                if (limit != null) row.put("limit", limit)
            }
            Type.LIMIT -> {
                val period = limitPeriod ?: LimitPeriod.MONTHLY
                row.put("type", "limit").put("amount", units(amountCents)).put("hold", limitHold).put("period", period.jsonValue)
                if (period == LimitPeriod.WEEKLY && !limitStartDate.isNullOrBlank()) row.put("start", limitStartDate)
            }
            Type.GOAL -> return JSONArray().put(
                JSONObject().put("directive", "goal").put("type", "goal").put("amount", units(amountCents)).apply {
                    note?.trim()?.takeIf(String::isNotEmpty)?.let { put("description", it) }
                },
            ).toString()
        }
        return JSONArray().put(row).toString()
    }

    private fun putAdjustment(row: JSONObject) {
        when (adjustmentType) {
            AdjustmentType.PERCENT -> row.put("adjustmentType", "percent").put("adjustment", adjustmentPercent ?: 0.0)
            AdjustmentType.FIXED -> row.put("adjustmentType", "fixed").put("adjustment", units(adjustmentAmountCents ?: 0L))
            null -> Unit
        }
    }

    companion object {
        fun fromGoalDef(
            raw: String?,
            source: String?,
            percentageSources: Set<String> = setOf("available funds"),
        ): BudgetTarget? {
            if (raw.isNullOrBlank() || (source != "ui" && source != "notes")) return null
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
            val row = array.takeIf { it.length() == 1 }?.getJSONObject(0) ?: return null
            fun cents() = (row.optDouble("amount", 0.0) * 100.0).toLong()
            val priority = row.optInt("priority", 1)
            val note = row.optString("description").ifBlank { null }
            return when (row.optString("type")) {
                "periodic" -> {
                    val periodObj = row.optJSONObject("period")
                    val parsedPeriod = Period.fromJson(periodObj?.optString("period")) ?: Period.MONTH
                    BudgetTarget(
                        Type.FIXED, cents(),
                        startingDate = row.optString("starting").ifBlank { null },
                        priority = priority, note = note,
                        period = parsedPeriod, everyCount = periodObj?.optInt("amount", 1)?.coerceAtLeast(1) ?: 1,
                    )
                }
                "by", "spend" -> {
                    val isSpend = row.optString("type") == "spend"
                    val hasRepeat = row.has("annual") || row.has("repeat")
                    BudgetTarget(
                        Type.BY_DATE, cents(), row.optString("month").ifBlank { null },
                        priority = priority, note = note,
                        allowEarlySpending = isSpend,
                        spendFromMonth = if (isSpend) row.optString("from").ifBlank { null } else null,
                        repeats = hasRepeat,
                        repeatAnnual = row.optBoolean("annual", false),
                        repeatEvery = row.optInt("repeat", 1).coerceAtLeast(1),
                    )
                }
                "schedule" -> row.takeIf {
                    it.optString("directive") == "template" &&
                        (it.optString("scheduleId").isNotBlank() || it.optString("name").isNotBlank())
                }?.let {
                    val (adjType, adjPercent, adjCents) = parseAdjustment(it)
                    BudgetTarget(
                        Type.SCHEDULE, priority = priority, note = note,
                        scheduleId = it.optString("scheduleId").ifBlank { null },
                        scheduleName = it.optString("name").ifBlank { null },
                        scheduleFull = it.optBoolean("full", false),
                        adjustmentType = adjType, adjustmentPercent = adjPercent, adjustmentAmountCents = adjCents,
                    )
                }
                "average" -> {
                    val (adjType, adjPercent, adjCents) = parseAdjustment(row)
                    BudgetTarget(
                        Type.HISTORICAL, priority = priority, note = note,
                        historicalMode = HistoricalMode.AVERAGE, historicalMonths = row.optInt("numMonths", 3),
                        adjustmentType = adjType, adjustmentPercent = adjPercent, adjustmentAmountCents = adjCents,
                    )
                }
                "copy" -> row.takeIf { it.optString("directive") == "template" }?.let {
                    BudgetTarget(
                        Type.HISTORICAL, priority = priority, note = note,
                        historicalMode = HistoricalMode.COPY, historicalMonths = it.optInt("lookBack", 1),
                    )
                }
                "percentage" -> row.takeIf {
                    it.optString("directive") == "template" &&
                        it.optString("category").isNotBlank() &&
                        it.optString("category").lowercase() in percentageSources.map(String::lowercase).toSet() &&
                        it.optInt("percent", 0) in 1..100
                }?.let {
                    BudgetTarget(
                        Type.PERCENTAGE, priority = priority, note = note,
                        percentage = it.optInt("percent"),
                        percentageSource = it.optString("category"),
                        percentagePrevious = it.optBoolean("previous", false),
                    )
                }
                "refill" -> row.takeIf { it.optString("directive") == "template" }
                    ?.let { BudgetTarget(Type.REFILL, priority = priority, note = note) }
                "remainder" -> row.takeIf {
                    it.optString("directive") == "template" && it.isNull("priority") && it.optInt("weight", 0) > 0
                }?.let {
                    val limit = it.optJSONObject("limit")
                    val parsedLimitPeriod = limit?.optString("period")?.let(LimitPeriod::fromJson)
                    val parsedLimitCents = limit?.takeIf { l -> l.has("amount") }?.let { l ->
                        val amount = l.optDouble("amount", Double.NaN)
                        (amount * 100.0).toLong().takeIf { amount.isFinite() && it > 0 }
                    }
                    val validLimit = limit == null || (
                        parsedLimitPeriod != null && parsedLimitCents != null &&
                            (parsedLimitPeriod != LimitPeriod.WEEKLY ||
                                !limit.optString("start").isNullOrBlank() &&
                                runCatching { LocalDate.parse(limit.optString("start")) }.isSuccess)
                        )
                    if (!validLimit) null else BudgetTarget(
                        Type.REMAINDER, weight = it.getInt("weight"), note = note,
                        limitPeriod = parsedLimitPeriod,
                        limitAmountCents = parsedLimitCents,
                        limitStartDate = limit?.optString("start")?.ifBlank { null },
                        limitHold = limit?.optBoolean("hold", false) == true,
                    )
                }
                "limit" -> row.takeIf {
                    val period = LimitPeriod.fromJson(it.optString("period"))
                    val start = it.optString("start").ifBlank { null }
                    it.optString("directive") == "template" && it.isNull("priority") &&
                        period != null && cents() > 0L &&
                        (period != LimitPeriod.WEEKLY || start?.let { rawStart ->
                            runCatching { LocalDate.parse(rawStart) }.isSuccess
                        } == true) &&
                        (period == LimitPeriod.WEEKLY || start == null)
                }?.let {
                    val period = requireNotNull(LimitPeriod.fromJson(it.optString("period")))
                    BudgetTarget(
                        Type.LIMIT, cents(), note = note,
                        limitPeriod = period,
                        limitStartDate = it.optString("start").ifBlank { null },
                        limitHold = it.optBoolean("hold", false),
                    )
                }
                "goal" -> row.takeIf { it.optString("directive") == "goal" }
                    ?.let { BudgetTarget(Type.GOAL, cents(), note = note) }
                else -> null
            }
        }

        private fun units(cents: Long): Any = if (cents % 100L == 0L) cents / 100L else cents / 100.0

        /** Parses an "increase"/"decrease" [AdjustmentType] modifier, matching upstream `modifiers`. */
        private fun parseAdjustment(row: JSONObject): Triple<AdjustmentType?, Double?, Long?> {
            val type = when (row.optString("adjustmentType")) {
                "percent" -> AdjustmentType.PERCENT
                "fixed" -> AdjustmentType.FIXED
                else -> null
            }
            if (type == null || !row.has("adjustment")) return Triple(null, null, null)
            val value = row.optDouble("adjustment", Double.NaN)
            if (!value.isFinite()) return Triple(null, null, null)
            return when (type) {
                AdjustmentType.PERCENT -> Triple(type, value, null)
                AdjustmentType.FIXED -> Triple(type, null, (value * 100.0).toLong())
            }
        }
    }
}

data class BudgetTemplateChange(
    val groupName: String,
    val categoryId: String,
    val categoryName: String,
    val currentCents: Long,
    val proposedCents: Long,
)

data class BudgetGoalChange(
    val groupName: String,
    val categoryId: String,
    val categoryName: String,
    val currentCents: Long?,
    val proposedCents: Long?,
)

data class BudgetTemplatePreview(
    val month: String,
    val changes: List<BudgetTemplateChange>,
    val unchangedCount: Int,
    val unsupportedCategories: List<String>,
    val limitedCategories: List<String> = emptyList(),
    val cappedCategories: List<String> = emptyList(),
    val skippedExistingCount: Int = 0,
    val overwriteExisting: Boolean = false,
    val goalChanges: List<BudgetGoalChange> = emptyList(),
) {
    val netBudgetChangeCents: Long = changes.sumOf { it.proposedCents - it.currentCents }
}

/** Preview-first planner for the UI-managed target types Actua can evaluate exactly. */
object BudgetTemplatePlanner {
    fun preview(
        groups: List<BudgetGroup>,
        month: String,
        availableBudgetCents: Long = Long.MAX_VALUE,
        overwriteExisting: Boolean = false,
        schedules: List<BudgetScheduleFunding> = emptyList(),
    ): BudgetTemplatePreview {
        val changes = mutableListOf<BudgetTemplateChange>()
        val unsupported = mutableListOf<String>()
        val limited = mutableListOf<String>()
        val capped = mutableListOf<String>()
        val goalChanges = mutableListOf<BudgetGoalChange>()
        var unchanged = 0
        var skippedExisting = 0
        val supported = groups.filterNot { it.isIncome || it.hidden }.flatMap { group ->
            group.categories.filterNot { it.isIncome || it.hidden }.map { group to it }
        }
        val scheduleNames = schedules.flatMap { it.referenceNames }.toSet()
        val percentageSources = groups.filter { it.isIncome }.flatMap { it.categories }.flatMap { category ->
            listOfNotNull(category.id, category.name).map { it to category.balanceCents.coerceAtLeast(0L) }
        }.toMap() + ("all income" to groups.filter { it.isIncome }.flatMap { it.categories }
            .sumOf { it.balanceCents.coerceAtLeast(0L) })
        val eligible = supported.filter { (_, category) ->
            val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            val hasTargets = targets.isNotEmpty()
            val unresolvedSchedule = targets.any {
                it.type == BudgetTarget.Type.SCHEDULE &&
                    ((it.scheduleId ?: it.scheduleName).orEmpty() !in scheduleNames ||
                        schedules.none { schedule ->
                            targetReference(it) in schedule.referenceNames &&
                                (schedule.categoryId == null || schedule.categoryId == category.id)
                        })
            }
            val canRun = !category.hasUnsupportedTarget && hasTargets && !unresolvedSchedule
            if (canRun && !overwriteExisting && category.assignedCents != 0L) skippedExisting++
            canRun && (overwriteExisting || category.assignedCents == 0L)
        }
        eligible.forEach { (group, category) ->
            if (remainderLimit(category) != null || capTarget(category) != null) {
                capped += "${group.name} · ${category.name}"
            }
        }
        val proposed = mutableMapOf<BudgetCategory, Long>()
        var available = if (availableBudgetCents == Long.MAX_VALUE) Long.MAX_VALUE else
            availableBudgetCents + if (overwriteExisting) eligible.sumOf { it.second.assignedCents } else 0L
        val releasedByLimit = eligible.sumOf { (_, category) ->
            val cap = effectiveCap(category, month) ?: return@sumOf 0L
            val excess = max(0L, category.carryoverCents - cap)
            val release = capTarget(category)?.limitHold == false || remainderLimit(category)?.limitHold == false
            if (excess > 0L && release) excess else 0L
        }
        if (available != Long.MAX_VALUE) available += releasedByLimit
        eligible.forEach { (_, category) ->
            val cap = effectiveCap(category, month) ?: return@forEach
            val excess = max(0L, category.carryoverCents - cap)
            val release = capTarget(category)?.limitHold == false || remainderLimit(category)?.limitHold == false
            if (excess > 0L && release) proposed[category] = -excess
        }
        val priorities = eligible.flatMap {
            it.second.automations.ifEmpty { listOfNotNull(it.second.target) }
        }.filterNot { it.type == BudgetTarget.Type.REMAINDER || it.type.isOption }
            .map(BudgetTarget::priority).distinct().sorted()

        for (priority in priorities) {
            val priorityAvailableStart = available
            for ((group, category) in eligible) {
                val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
                if (targets.isEmpty()) continue
                val capLimit = effectiveCap(category, month)
                if (capLimit != null && category.carryoverCents >= capLimit) continue
                val atPriority = targets.filter { it.priority == priority && !it.type.isOption }
                if (atPriority.isEmpty()) continue
                val before = proposed[category] ?: 0L
                val requested = requestedAtPriority(
                    atPriority, category, month, priorityAvailableStart, schedules, percentageSources,
                )
                val refillCap = if (targets.any { it.type == BudgetTarget.Type.REFILL }) {
                    capTarget(category)?.amountCents
                } else null
                val cap = listOfNotNull(refillCap, capLimit).minOrNull()
                val capped = cap?.let { minOf(requested, max(0L, it - category.carryoverCents - before)) } ?: requested
                val allocated = if (available == Long.MAX_VALUE || priority <= 0) capped else
                    minOf(capped, max(0L, available))
                if (allocated < capped) limited += "${group.name} · ${category.name}"
                proposed[category] = before + allocated
                if (available != Long.MAX_VALUE) available -= allocated
            }
        }
        distributeRemainder(eligible, proposed, available, month)
        for ((group, category) in supported) {
            val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            val unresolvedSchedule = targets.any {
                it.type == BudgetTarget.Type.SCHEDULE &&
                    schedules.none { schedule ->
                        targetReference(it) in schedule.referenceNames && schedule.active &&
                            (schedule.categoryId == null || schedule.categoryId == category.id)
                    }
            }
            if (category.hasUnsupportedTarget || unresolvedSchedule) {
                unsupported += "${group.name} · ${category.name}"
                continue
            }
            val goal = targetBalanceGoal(targets, category, schedules)
            val goalChanged = goal != category.goalCents || goal != null && !category.longGoal
            if (goalChanged && (targets.isEmpty() || overwriteExisting || category.assignedCents == 0L)) {
                val id = category.id
                if (id == null) unsupported += "${group.name} · ${category.name}"
                else goalChanges += BudgetGoalChange(group.name, id, category.name, category.goalCents, goal)
            }
            if (targets.isEmpty()) continue
            if (!overwriteExisting && category.assignedCents != 0L) continue
            val hasFundingTarget = targets.any { it.type != BudgetTarget.Type.LIMIT && it.type != BudgetTarget.Type.GOAL }
            val amount = proposed[category] ?: if (!hasFundingTarget && capTarget(category) != null) {
                category.assignedCents
            } else 0L
            if (amount == category.assignedCents) {
                    unchanged++
                } else {
                    val id = category.id
                    if (id == null) unsupported += "${group.name} · ${category.name}"
                    else changes += BudgetTemplateChange(
                        group.name, id, category.name, category.assignedCents, amount,
                    )
                }
        }
        return BudgetTemplatePreview(
            month = month,
            changes = changes,
            unchangedCount = unchanged,
            unsupportedCategories = unsupported.distinct(),
            limitedCategories = limited.distinct(),
            cappedCategories = capped.distinct(),
            skippedExistingCount = skippedExisting,
            overwriteExisting = overwriteExisting,
            goalChanges = goalChanges,
        )
    }

    private fun distributeRemainder(
        eligible: List<Pair<BudgetGroup, BudgetCategory>>,
        proposed: MutableMap<BudgetCategory, Long>,
        startingAvailable: Long,
        month: String,
    ): Long {
        var available = startingAvailable
        if (available == Long.MAX_VALUE || available <= 0L) return available
        while (available > 0L) {
            val active = eligible.mapNotNull { (_, category) ->
                val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
                val remainderTargets = targets.filter { it.type == BudgetTarget.Type.REMAINDER }
                val weight = remainderTargets.sumOf { it.weight.toLong() }
                if (weight <= 0L) return@mapNotNull null
                val before = proposed[category] ?: 0L
                val caps = buildList<Long> {
                    remainderTargets.firstOrNull()?.takeIf { it.limitAmountCents != null }
                        ?.let { add(it.limitForMonth(month)) }
                    capTarget(category)?.let { add(it.capForMonth(month)) }
                }
                val cap = caps.minOrNull()?.let { maxLimit ->
                    val remaining = max(0L, maxLimit - category.carryoverCents - before)
                    if (remaining <= 0L) 0L else remaining
                }
                if (cap != null && cap <= 0L) return@mapNotNull null
                Triple(category, weight, cap)
            }
            if (active.isEmpty()) break
            val totalWeight = active.sumOf { it.second }
            val beforePass = available
            val allocations = active.map { (category, weight, cap) ->
                val before = proposed[category] ?: 0L
                val base = (available * weight) / totalWeight
                category to minOf(base, cap ?: Long.MAX_VALUE)
            }
            allocations.forEach { (category, allocated) ->
                if (allocated > 0L) {
                    proposed[category] = (proposed[category] ?: 0L) + allocated
                    available -= allocated
                }
            }
            if (available > 0L) {
                active.asReversed().forEach { (category, _, cap) ->
                    if (available <= 0L) return@forEach
                    val before = proposed[category] ?: 0L
                    val hasCapacity = cap == null || category.carryoverCents + before < cap
                    if (hasCapacity) {
                        proposed[category] = before + 1L
                        available--
                    }
                }
            }
            if (available == beforePass) break
        }
        return available
    }

    private fun remainderLimit(category: BudgetCategory): BudgetTarget? =
        category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            .firstOrNull { it.type == BudgetTarget.Type.REMAINDER && it.limitAmountCents != null }

    private fun capTarget(category: BudgetCategory): BudgetTarget? =
        category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            .firstOrNull { it.type == BudgetTarget.Type.LIMIT }

    private fun effectiveCap(category: BudgetCategory, month: String): Long? = listOfNotNull(
        remainderLimit(category)?.limitForMonth(month),
        capTarget(category)?.capForMonth(month),
    ).minOrNull()

    private fun requestedAtPriority(
        targets: List<BudgetTarget>,
        category: BudgetCategory,
        month: String,
        availableAtPriorityStart: Long,
        schedules: List<BudgetScheduleFunding>,
        percentageSources: Map<String, Long>,
    ): Long {
        val by = targets.filter { it.type == BudgetTarget.Type.BY_DATE }
        val ordinary = targets.filterNot {
            it.type == BudgetTarget.Type.BY_DATE || it.type == BudgetTarget.Type.REFILL ||
            it.type.isOption || it.type == BudgetTarget.Type.REMAINDER ||
            it.type == BudgetTarget.Type.PERCENTAGE || it.type == BudgetTarget.Type.SCHEDULE
        }
            .sumOf { it.suggestedBudget(category, month) }
        val schedule = targets.filter { it.type == BudgetTarget.Type.SCHEDULE }.sumOf { target ->
            val funding = schedules.firstOrNull {
                targetReference(target) in it.referenceNames &&
                    (it.categoryId == null || it.categoryId == category.id)
            } ?: return@sumOf 0L
            val base = if (target.scheduleFull) funding.amountCents * funding.occurrencesInMonth
                else funding.requestedBudget(category.carryoverCents)
            max(0L, target.applyAdjustment(base))
        }
        val percentage = targets.filter { it.type == BudgetTarget.Type.PERCENTAGE }
            .sumOf { target ->
            if (target.percentagePrevious) return@sumOf 0L // local preview doesn't model prior-month income yet
            val source = if (target.percentageSource.equals("available funds", ignoreCase = true)) {
                availableAtPriorityStart
            } else {
                percentageSources.entries.firstOrNull {
                    it.key.equals(target.percentageSource, ignoreCase = true)
                }?.value ?: 0L
            }
            if (source == Long.MAX_VALUE) 0L
            else Math.round(max(0L, source).toDouble() * target.percentage / 100.0)
            }
        val byAmount = if (by.isEmpty()) 0L else combinedByDate(by, category, month)
        val refill = targets.firstOrNull { it.type == BudgetTarget.Type.REFILL }
            ?.let { capTarget(category)?.amountCents }
            ?.let { max(0L, it - category.carryoverCents) } ?: 0L
        return max(0L, ordinary + byAmount + refill + percentage + schedule)
    }

    private fun targetReference(target: BudgetTarget): String =
        target.scheduleId?.takeIf(String::isNotBlank)
            ?: target.scheduleName?.trim().orEmpty()

    /**
     * The full balance a category is working toward, independent of how much of it is
     * budgeted this month. An explicit long-term goal wins when present; otherwise a
     * "Save by date" or "Cover schedule" template contributes its whole target amount, so
     * progress reflects completion of the goal rather than this month's installment.
     */
    internal fun targetBalanceGoal(
        targets: List<BudgetTarget>,
        category: BudgetCategory,
        schedules: List<BudgetScheduleFunding>,
    ): Long? {
        targets.firstOrNull { it.type == BudgetTarget.Type.GOAL }?.let { return it.amountCents }
        val byDate = targets.filter { it.type == BudgetTarget.Type.BY_DATE }.sumOf { it.amountCents }
        val schedule = targets.filter { it.type == BudgetTarget.Type.SCHEDULE }.sumOf { target ->
            schedules.firstOrNull {
                targetReference(target) in it.referenceNames &&
                    (it.categoryId == null || it.categoryId == category.id)
            }?.amountCents ?: 0L
        }
        val total = byDate + schedule
        return total.takeIf { it > 0L }
    }

    /** Matches Actual's batch treatment of sibling `by` templates: carryover is deducted once. */
    private fun combinedByDate(targets: List<BudgetTarget>, category: BudgetCategory, month: String): Long {
        val current = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
        val months = targets.map { target ->
            runCatching { YearMonth.parse(target.targetMonth.orEmpty()) }.getOrNull()
                ?.let { max(0L, ChronoUnit.MONTHS.between(current, it)) } ?: 0L
        }
        val shortest = months.minOrNull() ?: 0L
        val needed = targets.zip(months).sumOf { (target, targetMonths) ->
            if (targetMonths > shortest) {
                Math.round(target.amountCents.toDouble() / (targetMonths + 1L) * (shortest + 1L))
            } else target.amountCents
        }
        return max(0L, Math.round((needed - category.carryoverCents).toDouble() / (shortest + 1L)))
    }
}
