package com.hisab.app.utils

import com.hisab.app.domain.model.BudgetScope
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/** Millisecond [start, end] ranges for "today" / "this week" / "this month", in the device's zone. */
object DateRanges {
    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun LocalDate.startMillis(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()

    fun todayRange(): Pair<Long, Long> {
        val today = LocalDate.now(zone())
        return today.startMillis() to (today.plusDays(1).startMillis() - 1)
    }

    fun thisWeekRange(): Pair<Long, Long> {
        val today = LocalDate.now(zone())
        val startOfWeek = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1L)
        return startOfWeek.startMillis() to (startOfWeek.plusWeeks(1).startMillis() - 1)
    }

    fun thisMonthRange(): Pair<Long, Long> {
        val startOfMonth = LocalDate.now(zone()).withDayOfMonth(1)
        return startOfMonth.startMillis() to (startOfMonth.plusMonths(1).startMillis() - 1)
    }

    fun rangeForBudgetScope(scope: BudgetScope): Pair<Long, Long> = when (scope) {
        BudgetScope.DAILY -> todayRange()
        BudgetScope.WEEKLY -> thisWeekRange()
        BudgetScope.MONTHLY -> thisMonthRange()
    }
}
