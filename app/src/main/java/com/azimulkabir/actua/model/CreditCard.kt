package com.azimulkabir.actua.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.schedules.DayDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CreditCardConfig(
    val statementDay: Int,
    val dueOffsetDays: Int = CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS,
    val limitCents: Long? = null,
    val dueDay: Int? = null,
)

data class CreditCardStatus(
    val accountId: String,
    val accountName: String,
    val balanceCents: Long,
    val config: CreditCardConfig,
    val cycleSpendCents: Long,
    val availableCreditCents: Long?,
    val closed: Boolean,
) {
    val cycle: CreditCardCycle get() = CreditCardCycle(config.statementDay, config.paymentDue)
}

/** Unpaid cards first by nearest due date, then paid cards, with a stable name tie-break. */
fun Iterable<CreditCardStatus>.sortedForPaymentPriority(today: DayDate = DayDate.today()) =
    sortedWith(compareBy<CreditCardStatus>(
        { if (it.balanceCents < 0) 0 else 1 },
        { it.cycle.daysUntilDue(today) },
        { it.accountName.lowercase(Locale.ROOT) },
        { it.accountId },
    ))

/** Billing-cycle calculations matching Actuali iOS CreditCardCycle. */
data class CreditCardCycle(
    val statementDay: Int,
    val paymentDue: PaymentDue = PaymentDue.DaysAfter(DEFAULT_DUE_OFFSET_DAYS),
) {
    sealed interface PaymentDue {
        data class DaysAfter(val days: Int) : PaymentDue
        data class DayOfMonth(val day: Int) : PaymentDue
    }

    constructor(statementDay: Int, dueOffsetDays: Int) :
        this(statementDay, PaymentDue.DaysAfter(dueOffsetDays))

    val dueOffsetDays: Int get() = when (val due = paymentDue) {
        is PaymentDue.DaysAfter -> due.days
        is PaymentDue.DayOfMonth -> DEFAULT_DUE_OFFSET_DAYS
    }

    init {
        require(statementDay in 1..31)
        when (val due = paymentDue) {
            is PaymentDue.DaysAfter -> require(due.days in 1..MAX_DUE_OFFSET_DAYS)
            is PaymentDue.DayOfMonth -> require(due.day in 1..31)
        }
    }

    fun cycleRange(today: DayDate = DayDate.today()): Pair<DayDate, DayDate> {
        val close = minOf(statementDay, DayDate.lastDay(today.year, today.month))
        return if (today.day > close) {
            val next = today.addingMonths(1)
            DayDate(today.year, today.month, close).addingDays(1) to
                DayDate(next.year, next.month, minOf(statementDay, DayDate.lastDay(next.year, next.month)))
        } else {
            val previous = today.addingMonths(-1)
            DayDate(previous.year, previous.month,
                minOf(statementDay, DayDate.lastDay(previous.year, previous.month))).addingDays(1) to
                DayDate(today.year, today.month, close)
        }
    }

    fun previousStatementDate(today: DayDate = DayDate.today()) = cycleRange(today).first.addingDays(-1)

    fun dueDate(statement: DayDate): DayDate = when (val due = paymentDue) {
        is PaymentDue.DaysAfter -> statement.addingDays(due.days)
        is PaymentDue.DayOfMonth -> {
            val month = if (due.day > statementDay) statement else statement.addingMonths(1)
            DayDate(month.year, month.month, minOf(due.day, DayDate.lastDay(month.year, month.month)))
        }
    }

    fun upcomingDueDate(today: DayDate = DayDate.today()): DayDate {
        var due = dueDate(cycleRange(today).second)
        var statement = previousStatementDate(today)
        repeat(dueOffsetDays / 28 + 2) {
            val statementDue = dueDate(statement)
            if (today > statementDue) return due
            due = statementDue
            statement = previousStatementDate(statement)
        }
        return due
    }

    fun daysRemainingInCycle(today: DayDate = DayDate.today()) =
        maxOf(0, today.daysUntil(cycleRange(today).second))

    fun daysUntilDue(today: DayDate = DayDate.today()) = maxOf(0, today.daysUntil(upcomingDueDate(today)))

    /** The last three closed billing statement cycles, ordered newest to oldest. */
    fun recentStatementCycles(today: DayDate = DayDate.today()): List<StatementCycle> {
        val cycles = mutableListOf<StatementCycle>()
        var currentEnd = previousStatementDate(today)
        repeat(3) {
            val prevEnd = previousStatementDate(currentEnd)
            val start = prevEnd.addingDays(1)
            val due = dueDate(currentEnd)
            cycles += StatementCycle(start, currentEnd, due)
            currentEnd = prevEnd
        }
        return cycles
    }

    @Composable
    fun dueSummary(today: DayDate = DayDate.today()): String = when (val days = daysUntilDue(today)) {
        0 -> stringResource(R.string.credit_card_due_today)
        1 -> stringResource(R.string.credit_card_due_tomorrow_summary)
        else -> {
            val due = upcomingDueDate(today)
            val locale = LocalConfiguration.current.locales[0]
            val formatted = java.time.LocalDate.of(due.year, due.month, due.day)
                .format(DateTimeFormatter.ofPattern("dd-MMM-yy", locale))
            stringResource(R.string.credit_card_due_date, formatted, days)
        }
    }

    @Composable
    fun dueShortSummary(today: DayDate = DayDate.today()): String {
        val days = daysUntilDue(today)
        return if (days <= 1) {
            dueSummary(today)
        } else {
            pluralStringResource(R.plurals.credit_card_due_in_days_short, days, days)
        }
    }

    /** A closed billing cycle's date range and the payment due date for its statement. */
    data class StatementCycle(val start: DayDate, val end: DayDate, val dueDate: DayDate)

    /** Status of the payment due for a credit card statement. */
    data class StatementDue(
        /** Balance in cents owed when the statement closed (positive). */
        val statementBalance: Long,
        /** Payments/credits in cents received since the statement closed (positive). */
        val paymentsSince: Long,
        /** Remaining balance in cents to pay for this statement (positive). */
        val remainingDue: Long,
        /** Payment due date for this statement. */
        val dueDate: DayDate,
    ) {
        val isPaid: Boolean get() = remainingDue == 0L && statementBalance > 0L
    }

    /** Record of a closed credit card billing statement with spend, due, and transaction metrics. */
    data class StatementRecord(
        val startDate: DayDate,
        val endDate: DayDate,
        val dueDate: DayDate,
        val statementBalance: Long,
        val paymentsSince: Long,
        val remainingDue: Long,
        /** Outflow spend in cents during the billing cycle (positive). */
        val totalSpend: Long,
    ) {
        val id: Int get() = endDate.yyyymmdd
        val isPaid: Boolean get() = remainingDue == 0L && statementBalance > 0L
    }

    companion object {
        const val DEFAULT_DUE_OFFSET_DAYS = 15
        const val MAX_DUE_OFFSET_DAYS = 60

        /** Computes the statement payment status given raw balances and payments. */
        fun calculateStatementDue(
            statementRawBalance: Long,
            paymentsSince: Long,
            liveBalance: Long,
            dueDate: DayDate,
        ): StatementDue {
            val statementOwed = maxOf(0L, -statementRawBalance)
            val unpaid = maxOf(0L, statementOwed - paymentsSince)
            val remaining = minOf(unpaid, maxOf(0L, -liveBalance))
            return StatementDue(statementOwed, paymentsSince, remaining, dueDate)
        }
    }
}

val CreditCardConfig.paymentDue: CreditCardCycle.PaymentDue
    get() = dueDay?.let(CreditCardCycle.PaymentDue::DayOfMonth)
        ?: CreditCardCycle.PaymentDue.DaysAfter(dueOffsetDays)
