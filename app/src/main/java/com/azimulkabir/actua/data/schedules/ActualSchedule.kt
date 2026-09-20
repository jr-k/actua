package com.azimulkabir.actua.data.schedules

data class ActualSchedule(
    val id: String, val name: String?, val nextDate: DayDate, val nextDateRowId: String,
    val baseNextDateTimestamp: Long?, val accountId: String, val payeeId: String?,
    val categoryId: String?, val amount: ScheduledAmount?, val dateCondition: ScheduleDateCondition,
)

data class ActualScheduleSummary(
    val id: String, val name: String?, val ruleId: String?, val nextDate: DayDate?,
    val nextDateRowId: String?, val baseNextDateTimestamp: Long?, val accountId: String?,
    val payeeId: String?, val amount: ScheduledAmount?, val amountOp: ScheduleAmountOp,
    val dateOp: String?, val dateCondition: ScheduleDateCondition?, val postsTransaction: Boolean,
    val completed: Boolean, val customUpcomingLength: String?, val sortOrder: Double?,
    val isCustom: Boolean, val conditionsJson: String?, val actionsJson: String?, val categoryId: String?,
) {
    val isRecurring get() = dateCondition is ScheduleDateCondition.Recurring
    val postAmount get() = amount?.postAmount ?: 0
}

data class ScheduleListItem(
    val schedule: ActualScheduleSummary,
    val status: ScheduleStatus,
    val accountName: String?,
    val payeeName: String?,
) {
    val title: String get() = schedule.name?.takeIf(String::isNotBlank)
        ?: payeeName?.takeIf(String::isNotBlank)
        ?: accountName?.takeIf(String::isNotBlank)
        ?: ""
}

data class ScheduleLinkedTransaction(
    val id: String,
    val date: DayDate?,
    val payeeName: String,
    val accountName: String,
    val amountCents: Long,
)

fun Iterable<ScheduleListItem>.sortedForDisplay() = sortedWith(
    compareBy<ScheduleListItem> { it.schedule.sortOrder == null }
        .thenBy { it.schedule.sortOrder ?: Double.MAX_VALUE }
        .thenBy { it.schedule.nextDate == null }
        .thenBy { it.schedule.nextDate }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy { it.schedule.id },
)
