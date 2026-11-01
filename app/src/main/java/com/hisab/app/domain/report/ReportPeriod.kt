package com.hisab.app.domain.report

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure (Android-free) date-range logic for Reports and Budgets, so month/year/week boundaries are
 * covered by plain JUnit tests instead of being re-derived in each screen.
 *
 * Every range is an INCLUSIVE pair of calendar days. Converting to epoch millis is done in one
 * place ([DateSpan.startMillis]/[DateSpan.endMillis]) so "end of day" inclusivity and the user's
 * timezone are handled consistently everywhere.
 */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end ($end) must not be before start ($start)" }
    }

    /** Number of calendar days covered, inclusive of both ends. */
    val dayCount: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    fun startMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Last millisecond of [end]'s day, so a BETWEEN query includes the whole final day. */
    fun endMillis(zone: ZoneId): Long = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

enum class ReportPeriodType {
    TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, THIS_YEAR, CUSTOM
}

object ReportPeriods {

    /**
     * Resolves a period to a calendar span.
     *
     * THIS_WEEK / THIS_MONTH / THIS_YEAR return the FULL calendar week/month/year (not just up to
     * today), because that is the period the user is looking at. LAST_3_MONTHS is a rolling window
     * ending today, consistent with the app's existing "Last 6 Months" range.
     */
    fun resolve(
        type: ReportPeriodType,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        custom: DateSpan? = null
    ): DateSpan = when (type) {
        ReportPeriodType.TODAY -> DateSpan(today, today)
        ReportPeriodType.YESTERDAY -> today.minusDays(1).let { DateSpan(it, it) }
        ReportPeriodType.THIS_WEEK -> weekContaining(today, firstDayOfWeek)
        ReportPeriodType.LAST_WEEK -> weekContaining(today.minusWeeks(1), firstDayOfWeek)
        ReportPeriodType.THIS_MONTH -> monthSpan(YearMonth.from(today))
        ReportPeriodType.LAST_MONTH -> monthSpan(YearMonth.from(today).minusMonths(1))
        ReportPeriodType.LAST_3_MONTHS -> DateSpan(today.minusMonths(3).plusDays(1), today)
        ReportPeriodType.THIS_YEAR -> DateSpan(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31))
        ReportPeriodType.CUSTOM -> {
            val c = custom ?: DateSpan(today, today)
            c
        }
    }

    /**
     * The "previous equivalent period" used for comparisons.
     *
     * For a period still in progress (this week/month/year) the comparison is like-for-like: the same
     * number of elapsed days of the previous week/month/year. Comparing 10 days of this month with a
     * whole previous month would make every in-progress month look like a spending drop.
     */
    fun previous(type: ReportPeriodType, current: DateSpan, today: LocalDate): DateSpan {
        val effectiveEnd = if (today.isBefore(current.end) && !today.isBefore(current.start)) today else current.end
        val elapsed = ChronoUnit.DAYS.between(current.start, effectiveEnd) + 1 // >= 1
        return when (type) {
            ReportPeriodType.TODAY, ReportPeriodType.YESTERDAY ->
                DateSpan(current.start.minusDays(1), current.end.minusDays(1))
            ReportPeriodType.THIS_WEEK -> {
                val s = current.start.minusWeeks(1)
                DateSpan(s, s.plusDays(elapsed - 1))
            }
            ReportPeriodType.LAST_WEEK -> DateSpan(current.start.minusWeeks(1), current.end.minusWeeks(1))
            ReportPeriodType.THIS_MONTH -> {
                val prev = YearMonth.from(current.start).minusMonths(1)
                val s = prev.atDay(1)
                DateSpan(s, minOf(s.plusDays(elapsed - 1), prev.atEndOfMonth()))
            }
            ReportPeriodType.LAST_MONTH -> monthSpan(YearMonth.from(current.start).minusMonths(1))
            ReportPeriodType.LAST_3_MONTHS ->
                DateSpan(current.start.minusMonths(3), current.start.minusDays(1))
            ReportPeriodType.THIS_YEAR -> {
                val s = LocalDate.of(current.start.year - 1, 1, 1)
                DateSpan(s, minOf(s.plusDays(elapsed - 1), LocalDate.of(s.year, 12, 31)))
            }
            ReportPeriodType.CUSTOM -> {
                val len = current.dayCount
                val end = current.start.minusDays(1)
                DateSpan(end.minusDays(len - 1), end)
            }
        }
    }

    fun weekContaining(date: LocalDate, firstDayOfWeek: DayOfWeek): DateSpan {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return DateSpan(start, start.plusDays(6))
    }

    fun monthSpan(month: YearMonth): DateSpan = DateSpan(month.atDay(1), month.atEndOfMonth())
}
