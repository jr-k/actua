package com.azimulkabir.actua.model

import org.json.JSONArray
import org.json.JSONObject

/** Loss-aware projection of Actual's categories.goal_def automation array. */
data class BudgetAutomationDocument(
    val supported: List<BudgetTarget>,
    val unsupportedTypes: List<String> = emptyList(),
    val editable: Boolean = true,
) {
    val hasUnsupported: Boolean get() = unsupportedTypes.isNotEmpty() || !editable

    companion object {
        const val UNSUPPORTED_UNKNOWN = "unknown"
        const val UNSUPPORTED_INVALID_DEFINITION = "invalid definition"
        const val UNSUPPORTED_BY_PRIORITIES = "by priorities"
        const val UNSUPPORTED_INVALID_SUPPORTED_DEFINITION = "invalid supported definition"

        fun decode(
            raw: String?,
            source: String?,
            percentageSources: Set<String> = setOf("available funds"),
        ): BudgetAutomationDocument {
            if (raw.isNullOrBlank()) return BudgetAutomationDocument(emptyList())
            val array = runCatching { JSONArray(raw) }.getOrNull()
                ?: return BudgetAutomationDocument(emptyList(), listOf(UNSUPPORTED_INVALID_DEFINITION), source == "ui")
            val rows = (0 until array.length()).mapNotNull(array::optJSONObject)
            if (rows.size != array.length()) {
                return BudgetAutomationDocument(emptyList(), listOf(UNSUPPORTED_INVALID_DEFINITION), false)
            }
            val supported = mutableListOf<BudgetTarget>()
            val unsupported = mutableListOf<String>()
            rows.forEach { row ->
                val type = row.optString("type").ifBlank { UNSUPPORTED_UNKNOWN }
                val target = BudgetTarget.fromGoalDef(
                    JSONArray().put(JSONObject(row.toString())).toString(),
                    "ui",
                    percentageSources,
                )
                if (target == null) unsupported += type else supported += target
            }
            if (supported.filter { it.type == BudgetTarget.Type.BY_DATE }.map(BudgetTarget::priority).distinct().size > 1) {
                unsupported += UNSUPPORTED_BY_PRIORITIES
            }
            if (validate(supported).isNotEmpty()) unsupported += UNSUPPORTED_INVALID_SUPPORTED_DEFINITION
            return BudgetAutomationDocument(supported, unsupported.distinct(), editable = source == "ui")
        }

        fun encode(targets: List<BudgetTarget>): String? {
            if (targets.isEmpty()) return null
            validate(targets).firstOrNull()?.let { throw IllegalArgumentException(it) }
            val array = JSONArray()
            targets.forEach { target ->
                val encoded = JSONArray(target.toGoalDef())
                for (index in 0 until encoded.length()) array.put(encoded.getJSONObject(index))
            }
            return array.toString()
        }

        fun validate(targets: List<BudgetTarget>): List<String> = buildList {
            if (targets.size > 20) add("A category can have at most 20 automations")
            listOf(
                BudgetTarget.Type.REFILL to "refill",
                BudgetTarget.Type.LIMIT to "balance cap",
                BudgetTarget.Type.GOAL to "long-term goal",
                BudgetTarget.Type.REMAINDER to "remainder",
            ).forEach { (type, label) ->
                if (targets.count { it.type == type } > 1) add("Only one $label automation is allowed")
            }
            if (targets.any { it.type == BudgetTarget.Type.REFILL } &&
                targets.none { it.type == BudgetTarget.Type.LIMIT }
            ) {
                add("Refill to cap needs a balance cap automation")
            }
            if (targets.any { it.type == BudgetTarget.Type.REMAINDER && it.weight < 1 }) {
                add("Remainder weight must be at least 1")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.REMAINDER && it.limitPeriod != null &&
                        it.limitAmountCents != null && it.limitAmountCents <= 0
                }) {
                add("Remainder limits must be positive")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.REMAINDER &&
                        ((it.limitPeriod == null) != (it.limitAmountCents == null))
                }) {
                add("Remainder limits need both a period and amount")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.REMAINDER &&
                        it.limitPeriod == BudgetTarget.LimitPeriod.WEEKLY &&
                        (it.limitStartDate.isNullOrBlank() ||
                            runCatching { java.time.LocalDate.parse(it.limitStartDate) }.isFailure)
                }) {
                add("Weekly remainder limits need a valid start date")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.LIMIT && it.limitPeriod == BudgetTarget.LimitPeriod.WEEKLY &&
                        (it.limitStartDate.isNullOrBlank() ||
                            runCatching { java.time.LocalDate.parse(it.limitStartDate) }.isFailure)
                }) {
                add("Weekly balance caps need a valid start date")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.LIMIT && it.limitPeriod != BudgetTarget.LimitPeriod.WEEKLY &&
                        !it.limitStartDate.isNullOrBlank()
                }) {
                add("Only weekly balance caps can have a start date")
            }
            if (targets.any { it.type.hasPriority && it.priority < 0 }) add("Automation priority cannot be negative")
            if (targets.any { it.type == BudgetTarget.Type.LIMIT && it.amountCents <= 0 }) {
                add("Balance cap needs a positive amount")
            }
            if (targets.any { it.type == BudgetTarget.Type.PERCENTAGE && it.percentage !in 1..100 }) {
                add("Percentage automations must be between 1 and 100")
            }
            if (targets.any { it.type == BudgetTarget.Type.HISTORICAL && it.historicalMonths !in 1..24 }) {
                add("From-history automations must look back 1 to 24 months")
            }
            if (targets.any { it.type == BudgetTarget.Type.PERCENTAGE && it.percentageSource.isBlank() }) {
                add("Percentage automations need an income source")
            }
            if (targets.any {
                    it.adjustmentType == BudgetTarget.AdjustmentType.PERCENT && it.adjustmentPercent == null ||
                        it.adjustmentType == BudgetTarget.AdjustmentType.FIXED && it.adjustmentAmountCents == null
                }) {
                add("Adjustments need a value")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.SCHEDULE &&
                        it.scheduleId.isNullOrBlank() && it.scheduleName.isNullOrBlank()
                }) {
                add("Schedule automations need a schedule ID or name")
            }
            val scheduleAndByPriorities = targets.filter {
                it.type == BudgetTarget.Type.SCHEDULE || it.type == BudgetTarget.Type.BY_DATE
            }.map(BudgetTarget::priority).distinct()
            if (scheduleAndByPriorities.size > 1) {
                add("Schedule and date automations must use the same priority")
            }
            if (targets.filter { it.type == BudgetTarget.Type.BY_DATE }.map(BudgetTarget::priority).distinct().size > 1) {
                add("Date targets must use the same priority")
            }
            targets.forEachIndexed { index, target ->
                if (
                    (target.type == BudgetTarget.Type.FIXED || target.type == BudgetTarget.Type.BY_DATE ||
                        target.type == BudgetTarget.Type.GOAL) &&
                    target.amountCents <= 0
                ) {
                    add("Automation ${index + 1} needs a positive amount")
                }
                if (target.type == BudgetTarget.Type.BY_DATE &&
                    runCatching { java.time.YearMonth.parse(target.targetMonth.orEmpty()) }.isFailure
                ) add("Automation ${index + 1} needs a valid target month")
                if (target.type == BudgetTarget.Type.FIXED &&
                    (target.period == BudgetTarget.Period.WEEK || target.period == BudgetTarget.Period.DAY) &&
                    runCatching { java.time.LocalDate.parse(target.startingDate.orEmpty()) }.isFailure
                ) add("Automation ${index + 1} needs a valid starting date")
            }
        }
    }
}
