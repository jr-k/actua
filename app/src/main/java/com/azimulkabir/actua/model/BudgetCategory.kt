package com.azimulkabir.actua.model

import androidx.annotation.StringRes
import com.azimulkabir.actua.R

data class BudgetCategory(
    val name: String,
    val assigned: Int,
    val spent: Int,
    private val actualAvailable: Int? = null,
    private val actualAssignedCents: Long? = null,
    val id: String? = null,
    val availableCents: Long? = null,
    val hidden: Boolean = false,
    val spentCents: Long = spent.toLong() * 100,
    val carryoverEnabled: Boolean = false,
    val note: String = "",
    val history: List<BudgetHistory> = emptyList(),
    val isIncome: Boolean = false,
    val target: BudgetTarget? = null,
    val hasUnsupportedTarget: Boolean = false,
    val automations: List<BudgetTarget> = target?.let(::listOf).orEmpty(),
    val unsupportedAutomationTypes: List<String> = emptyList(),
    val automationReadOnly: Boolean = false,
    val goalCents: Long? = null,
    val longGoal: Boolean = false,
    val cleanupTargets: List<CleanupTarget> = emptyList(),
    val cleanupInvalid: Boolean = false,
) {
    val available: Int get() = actualAvailable ?: assigned - spent
    val assignedCents: Long get() = actualAssignedCents ?: assigned.toLong() * 100
    val balanceCents: Long get() = availableCents ?: available.toLong() * 100
    val carryoverCents: Long get() = balanceCents - assignedCents + spentCents

    /**
     * The full balance this category is working toward, independent of this month's
     * installment. A locally-known "goal only", "by date" or "cover schedule" target wins
     * over Actual's server-synced [goalCents]: the server value can be a stale monthly
     * installment amount left over from before an automation was last (re-)applied, while the
     * target definition itself always resolves to the true end goal. [goalCents] is only used
     * as a fallback when no supported target is present locally (e.g. a target type Actua
     * doesn't model) or a schedule target can't yet be resolved against [schedules].
     */
    fun effectiveGoalCents(schedules: List<BudgetScheduleFunding> = emptyList()): Long? {
        val targets = automations.ifEmpty { target?.let(::listOf).orEmpty() }
        val hasBalanceTarget = targets.any {
            it.type == BudgetTarget.Type.GOAL ||
                it.type == BudgetTarget.Type.BY_DATE ||
                it.type == BudgetTarget.Type.SCHEDULE
        }
        if (hasBalanceTarget) {
            BudgetTemplatePlanner.targetBalanceGoal(targets, this, schedules)?.let { return it }
        }
        return goalCents?.takeIf { it > 0L }
    }

    val usesGoalProgress: Boolean
        get() = longGoal || automations.ifEmpty { target?.let(::listOf).orEmpty() }.any {
            it.type == BudgetTarget.Type.GOAL || it.type == BudgetTarget.Type.BY_DATE ||
                it.type == BudgetTarget.Type.SCHEDULE
        }

    val showsProgressBar: Boolean
        get() = usesGoalProgress || assignedCents != 0L || spentCents != 0L

    val progressState: BudgetProgressState
        get() = when {
            balanceCents < 0L -> BudgetProgressState.OVERSPENT
            balanceCents == 0L && spentCents != 0L -> BudgetProgressState.SPENT
            balanceCents == 0L && assignedCents == 0L && carryoverCents == 0L -> BudgetProgressState.UNASSIGNED
            spentCents != 0L -> BudgetProgressState.SPENDING
            else -> BudgetProgressState.FUNDED
        }

    fun progressFraction(schedules: List<BudgetScheduleFunding> = emptyList()): Float {
        // Preserve Actua's long-term targets, including unresolved schedule fallback.
        if (usesGoalProgress) {
            val goal = effectiveGoalCents(schedules)
            if (goal != null && goal > 0L) return (balanceCents.toFloat() / goal).coerceIn(0f, 1f)
            return if (assignedCents <= 0L) 0f else (balanceCents.toFloat() / assignedCents).coerceIn(0f, 1f)
        }
        // Actuali spending capacity includes carryover. Convert before abs/addition to
        // avoid Long overflow; these floating-point values are presentation-only.
        val spentAmount = kotlin.math.abs(spentCents.toDouble())
        val capacity = spentAmount + balanceCents.coerceAtLeast(0L).toDouble()
        return if (capacity > 0.0) (spentAmount / capacity).toFloat().coerceIn(0f, 1f) else 0f
    }
}

enum class BudgetProgressState(@StringRes val labelRes: Int) {
    UNASSIGNED(R.string.budget_progress_unassigned),
    FUNDED(R.string.budget_progress_funded),
    SPENDING(R.string.budget_progress_partially_spent),
    SPENT(R.string.budget_progress_fully_spent),
    OVERSPENT(R.string.budget_progress_overspent),
}

enum class BudgetCategoryView(val storageValue: String, @StringRes val labelRes: Int) {
    ALL("All", R.string.budget_filter_all),
    OVERSPENT("Overspent", R.string.budget_filter_overspent),
    UNDERFUNDED("Underfunded", R.string.budget_filter_underfunded),
    OVERFUNDED("Overfunded", R.string.budget_filter_overfunded),
    MONEY_AVAILABLE("Money Available", R.string.budget_filter_money_available);

    fun matches(category: BudgetCategory, schedules: List<BudgetScheduleFunding> = emptyList()): Boolean = when (this) {
        ALL -> true
        OVERSPENT -> category.balanceCents < 0L
        UNDERFUNDED -> {
            val goal = category.effectiveGoalCents(schedules)
            (goal ?: 0L) > 0L && category.balanceCents < goal!!
        }
        OVERFUNDED -> {
            val goal = category.effectiveGoalCents(schedules)
            (goal ?: 0L) > 0L && category.balanceCents > goal!!
        }
        MONEY_AVAILABLE -> category.balanceCents > 0L
    }

    companion object {
        fun fromStorageValue(value: String): BudgetCategoryView =
            entries.find { it.storageValue == value } ?: ALL
    }
}

data class BudgetHistory(val month: String, val assignedCents: Long, val spentCents: Long)

data class BudgetOverview(
    val toBudgetCents: Long?,
    val budgetedCents: Long,
    val spentCents: Long,
    val availableCents: Long,
    val bufferedCents: Long = 0,
)

data class BudgetGroup(
    val name: String,
    val categories: List<BudgetCategory>,
    val hidden: Boolean = false,
    val isIncome: Boolean = false,
)
