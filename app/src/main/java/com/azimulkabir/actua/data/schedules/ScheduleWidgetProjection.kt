package com.azimulkabir.actua.data.schedules

/** One unpaid occurrence surfaced to the upcoming-schedules widget. */
data class ScheduleWidgetEntry(
    val item: ScheduleListItem,
    val dueDate: DayDate,
    val overdue: Boolean,
)

/** Supported widget period choices, in days. */
enum class ScheduleWidgetPeriod(val days: Int) { SEVEN(7), FOURTEEN(14), THIRTY(30) }

object ScheduleWidgetProjection {
    /** Unpaid, incomplete schedules due within [periodDays] of [today], overdue-first. */
    fun upcoming(schedules: List<ScheduleListItem>, today: DayDate, periodDays: Int): List<ScheduleWidgetEntry> {
        val horizon = today.addingDays(periodDays)
        return schedules
            .filter { it.status != ScheduleStatus.COMPLETED && it.status != ScheduleStatus.PAID }
            .mapNotNull { item -> item.schedule.nextDate?.let { date -> item to date } }
            .filter { (_, date) -> date <= horizon }
            .map { (item, date) -> ScheduleWidgetEntry(item, date, date < today) }
            .sortedBy { it.dueDate }
    }

    /** "Today", "Tomorrow", "In 5 days", "3 days overdue". */
    fun relativeDueLabel(today: DayDate, dueDate: DayDate): String {
        val days = today.daysUntil(dueDate)
        return when {
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days > 1 -> "In $days days"
            days == -1 -> "1 day overdue"
            else -> "${-days} days overdue"
        }
    }
}
