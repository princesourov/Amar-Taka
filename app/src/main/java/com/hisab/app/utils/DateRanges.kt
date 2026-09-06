package com.hisab.app.utils

import com.hisab.app.domain.model.BudgetScope
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

enum class DateRangeType {
    TODAY, LAST_3_DAYS, LAST_7_DAYS, LAST_15_DAYS, LAST_30_DAYS, LAST_90_DAYS,
    LAST_6_MONTHS, THIS_MONTH, PREVIOUS_MONTH, THIS_YEAR, LAST_YEAR, CUSTOM
}

data class DateRange(
    val type: DateRangeType,
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    val startMillis: Long
        get() = startDate.atStartOfDay(DateRanges.zone()).toInstant().toEpochMilli()
    val endMillis: Long
        get() = endDate.plusDays(1).atStartOfDay(DateRanges.zone()).toInstant().toEpochMilli() - 1L
}

object DateRanges {
    internal fun zone(): ZoneId = ZoneId.systemDefault()
    private fun LocalDate.startMillis(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()
    private fun LocalDate.endMillis(): Long = plusDays(1).startMillis() - 1L
    private fun LocalDate.dayRange() = DateRange(DateRangeType.CUSTOM, this, this)

    fun todayRange(): Pair<Long, Long> {
        val today = LocalDate.now(zone())
        return today.startMillis() to today.endMillis()
    }

    fun thisWeekRange(): Pair<Long, Long> {
        val today = LocalDate.now(zone())
        val startOfWeek = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1L)
        return startOfWeek.startMillis() to startOfWeek.plusWeeks(1).minusDays(1).endMillis()
    }

    fun thisMonthRange(): Pair<Long, Long> {
        val range = forMonth(YearMonth.now(zone()))
        return range.startMillis to range.endMillis
    }

    fun rangeForBudgetScope(scope: BudgetScope): Pair<Long, Long> = when (scope) {
        BudgetScope.DAILY -> todayRange()
        BudgetScope.WEEKLY -> thisWeekRange()
        BudgetScope.MONTHLY -> thisMonthRange()
    }

    fun forMonth(month: YearMonth): DateRange {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        return DateRange(DateRangeType.THIS_MONTH, start, end)
    }

    fun forDate(date: LocalDate): DateRange = date.dayRange()

    fun resolve(
        type: DateRangeType,
        now: LocalDate = LocalDate.now(zone()),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ): DateRange {
        return when (type) {
            DateRangeType.TODAY -> DateRange(type, now, now)
            DateRangeType.LAST_3_DAYS -> DateRange(type, now.minusDays(2), now)
            DateRangeType.LAST_7_DAYS -> DateRange(type, now.minusDays(6), now)
            DateRangeType.LAST_15_DAYS -> DateRange(type, now.minusDays(14), now)
            DateRangeType.LAST_30_DAYS -> DateRange(type, now.minusDays(29), now)
            DateRangeType.LAST_90_DAYS -> DateRange(type, now.minusDays(89), now)
            DateRangeType.LAST_6_MONTHS -> DateRange(type, now.minusMonths(6).plusDays(1), now)
            DateRangeType.THIS_MONTH -> DateRange(type, now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth()))
            DateRangeType.PREVIOUS_MONTH -> {
                val month = YearMonth.from(now).minusMonths(1)
                DateRange(type, month.atDay(1), month.atEndOfMonth())
            }
            DateRangeType.THIS_YEAR -> DateRange(type, now.withDayOfYear(1), now.withDayOfYear(now.lengthOfYear()))
            DateRangeType.LAST_YEAR -> {
                val year = now.year - 1
                DateRange(type, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            }
            DateRangeType.CUSTOM -> {
                val start = customStart ?: now
                val end = customEnd ?: now
                if (end.isBefore(start)) DateRange(type, end, start) else DateRange(type, start, end)
            }
        }
    }
}
