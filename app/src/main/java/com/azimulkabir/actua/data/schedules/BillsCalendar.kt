package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.model.CreditCardStatus
import kotlin.math.abs

enum class BillFilter { ALL, UPCOMING, OVERDUE, PAID }
enum class BillsTabMode { RECURRING, CARD_BILLS }

data class BillCalendarItem(
    val id: String,
    val date: DayDate,
    val title: String,
    val amountCents: Long,
    val categoryName: String?,
    val accountName: String?,
    val status: ScheduleStatus,
    val scheduleId: String? = null,
    val isCurrentOccurrence: Boolean = false,
    val isRecurring: Boolean = false,
    val isCreditCard: Boolean = false,
)

data class BillsMonthSummary(
    val upcomingCents: Long,
    val overdueCents: Long,
    val paidCents: Long,
    val clearedCount: Int,
    val totalCount: Int,
)

object BillsCalendarEngine {
    fun leadingEmptyDays(year: Int, month: Int) = (DayDate(year, month, 1).weekday + 5) % 7

    fun daysInMonth(year: Int, month: Int) =
        (1..DayDate.lastDay(year, month)).map { DayDate(year, month, it) }

    fun itemsForSchedules(
        schedules: List<ScheduleListItem>,
        paymentDates: Map<String, Set<DayDate>>,
        categoryNames: Map<String, String>,
        year: Int,
        month: Int,
        today: DayDate = DayDate.today(),
    ): List<BillCalendarItem> {
        val start = DayDate(year, month, 1)
        val end = DayDate(year, month, DayDate.lastDay(year, month))
        return buildList {
            schedules.forEach { listItem ->
                val schedule = listItem.schedule
                if (schedule.completed) {
                    schedule.nextDate?.takeIf { it.year == year && it.month == month }?.let { date ->
                        add(item(listItem, date, ScheduleStatus.COMPLETED, categoryNames, true))
                    }
                    return@forEach
                }
                val dates = when (val condition = schedule.dateCondition) {
                    is ScheduleDateCondition.Recurring -> buildSet {
                        schedule.nextDate?.takeIf { it.year == year && it.month == month }?.let(::add)
                        addAll(ScheduleRecurrence.upcomingDates(condition.config, 40, start)
                            .filter { it in start..end })
                    }.sorted()
                    is ScheduleDateCondition.Fixed -> listOf(condition.day).filter { it.year == year && it.month == month }
                    else -> listOfNotNull(schedule.nextDate).filter { it.year == year && it.month == month }
                }
                dates.forEachIndexed { index, date ->
                    val status = when {
                        date == schedule.nextDate -> listItem.status
                        occurrencePaid(schedule, date, dates.getOrNull(index + 1), paymentDates[schedule.id].orEmpty()) -> ScheduleStatus.PAID
                        date < today -> ScheduleStatus.MISSED
                        date == today -> ScheduleStatus.DUE
                        else -> ScheduleStatus.UPCOMING
                    }
                    add(item(listItem, date, status, categoryNames, date == schedule.nextDate))
                }
            }
        }.sortedWith(compareBy(BillCalendarItem::date, BillCalendarItem::title))
    }

    fun itemsForCreditCards(
        cards: List<CreditCardStatus>,
        year: Int,
        month: Int,
        today: DayDate = DayDate.today(),
    ) = cards.filterNot(CreditCardStatus::closed).mapNotNull { card ->
        val due = card.cycle.upcomingDueDate(DayDate(year, month, 1))
        due.takeIf { it.year == year && it.month == month }?.let {
            val owed = maxOf(0, -card.balanceCents)
            BillCalendarItem(
                id = "cc_${card.accountId}_${due.yyyymmdd}",
                date = due,
                title = card.accountName,
                amountCents = -owed,
                categoryName = null,
                accountName = card.accountName,
                status = when {
                    owed == 0L -> ScheduleStatus.PAID
                    due < today -> ScheduleStatus.MISSED
                    due == today -> ScheduleStatus.DUE
                    else -> ScheduleStatus.UPCOMING
                },
                isCreditCard = true,
            )
        }
    }.sortedBy(BillCalendarItem::date)

    fun summarize(items: List<BillCalendarItem>): BillsMonthSummary {
        var upcoming = 0L
        var overdue = 0L
        var paid = 0L
        var cleared = 0
        items.forEach { item ->
            when (item.status) {
                ScheduleStatus.PAID, ScheduleStatus.COMPLETED -> { paid += abs(item.amountCents); cleared++ }
                ScheduleStatus.MISSED -> overdue += abs(item.amountCents)
                else -> upcoming += abs(item.amountCents)
            }
        }
        return BillsMonthSummary(upcoming, overdue, paid, cleared, items.size)
    }

    fun filter(items: List<BillCalendarItem>, filter: BillFilter, selectedDate: DayDate?) =
        items.filter { item ->
            (selectedDate == null || item.date == selectedDate) && when (filter) {
                BillFilter.ALL -> true
                BillFilter.UPCOMING -> item.status in setOf(ScheduleStatus.SCHEDULED, ScheduleStatus.UPCOMING, ScheduleStatus.DUE)
                BillFilter.OVERDUE -> item.status == ScheduleStatus.MISSED
                BillFilter.PAID -> item.status in setOf(ScheduleStatus.PAID, ScheduleStatus.COMPLETED)
            }
        }

    private fun item(
        listItem: ScheduleListItem,
        date: DayDate,
        status: ScheduleStatus,
        categoryNames: Map<String, String>,
        current: Boolean,
    ) = BillCalendarItem(
        id = "${listItem.schedule.id}_${date.yyyymmdd}",
        date = date,
        title = listItem.title,
        amountCents = listItem.schedule.postAmount,
        categoryName = listItem.schedule.categoryId?.let(categoryNames::get),
        accountName = listItem.accountName,
        status = status,
        scheduleId = listItem.schedule.id,
        isCurrentOccurrence = current,
        isRecurring = listItem.schedule.isRecurring,
    )

    private fun occurrencePaid(
        schedule: ActualScheduleSummary,
        date: DayDate,
        nextDate: DayDate?,
        payments: Set<DayDate>,
    ): Boolean {
        val start = ScheduleStatusCalculator.occurrenceMatchStartDate(
            date, schedule.dateOp, schedule.postsTransaction,
        )
        val nextStart = nextDate?.let {
            ScheduleStatusCalculator.occurrenceMatchStartDate(it, schedule.dateOp, schedule.postsTransaction)
        }
        return payments.any { it >= start && (nextStart == null || it < nextStart) }
    }
}
