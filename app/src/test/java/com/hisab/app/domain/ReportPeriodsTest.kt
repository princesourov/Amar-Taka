package com.hisab.app.domain

import com.hisab.app.domain.report.DateSpan
import com.hisab.app.domain.report.ReportPeriodType
import com.hisab.app.domain.report.ReportPeriods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class ReportPeriodsTest {
    private val d = { y: Int, m: Int, day: Int -> LocalDate.of(y, m, day) }

    @Test fun today_and_yesterday() {
        val today = d(2026, 3, 1)
        assertEquals(DateSpan(today, today), ReportPeriods.resolve(ReportPeriodType.TODAY, today))
        // Crosses the month boundary
        assertEquals(DateSpan(d(2026, 2, 28), d(2026, 2, 28)), ReportPeriods.resolve(ReportPeriodType.YESTERDAY, today))
    }

    @Test fun yesterday_crosses_year_boundary() {
        val s = ReportPeriods.resolve(ReportPeriodType.YESTERDAY, d(2026, 1, 1))
        assertEquals(d(2025, 12, 31), s.start)
    }

    @Test fun this_week_respects_first_day_of_week() {
        val wed = d(2026, 11, 4) // Wednesday
        val mon = ReportPeriods.resolve(ReportPeriodType.THIS_WEEK, wed, DayOfWeek.MONDAY)
        assertEquals(DateSpan(d(2026, 11, 2), d(2026, 11, 8)), mon)
        val sun = ReportPeriods.resolve(ReportPeriodType.THIS_WEEK, wed, DayOfWeek.SUNDAY)
        assertEquals(DateSpan(d(2026, 11, 1), d(2026, 11, 7)), sun)
        val sat = ReportPeriods.resolve(ReportPeriodType.THIS_WEEK, wed, DayOfWeek.SATURDAY)
        assertEquals(DateSpan(d(2026, 10, 31), d(2026, 11, 6)), sat)
    }

    @Test fun week_that_spans_month_end_is_one_span() {
        val s = ReportPeriods.resolve(ReportPeriodType.THIS_WEEK, d(2026, 12, 31), DayOfWeek.MONDAY)
        assertEquals(DateSpan(d(2026, 12, 28), d(2027, 1, 3)), s)
    }

    @Test fun last_week_is_the_seven_days_before_this_week() {
        val s = ReportPeriods.resolve(ReportPeriodType.LAST_WEEK, d(2026, 11, 4), DayOfWeek.MONDAY)
        assertEquals(DateSpan(d(2026, 10, 26), d(2026, 11, 1)), s)
    }

    @Test fun this_month_is_full_calendar_month_including_leap_february() {
        assertEquals(DateSpan(d(2028, 2, 1), d(2028, 2, 29)), ReportPeriods.resolve(ReportPeriodType.THIS_MONTH, d(2028, 2, 10)))
        assertEquals(DateSpan(d(2026, 2, 1), d(2026, 2, 28)), ReportPeriods.resolve(ReportPeriodType.THIS_MONTH, d(2026, 2, 10)))
    }

    @Test fun last_month_crosses_year_boundary() {
        assertEquals(DateSpan(d(2025, 12, 1), d(2025, 12, 31)), ReportPeriods.resolve(ReportPeriodType.LAST_MONTH, d(2026, 1, 15)))
    }

    @Test fun last_month_from_march_31_is_february() {
        assertEquals(DateSpan(d(2026, 2, 1), d(2026, 2, 28)), ReportPeriods.resolve(ReportPeriodType.LAST_MONTH, d(2026, 3, 31)))
    }

    @Test fun last_3_months_is_rolling_and_inclusive() {
        val s = ReportPeriods.resolve(ReportPeriodType.LAST_3_MONTHS, d(2026, 5, 15))
        assertEquals(DateSpan(d(2026, 2, 16), d(2026, 5, 15)), s)
        // from Mar 31 -> Dec 31 minus clamping: 2025-12-31 + 1 day
        val s2 = ReportPeriods.resolve(ReportPeriodType.LAST_3_MONTHS, d(2026, 3, 31))
        assertEquals(DateSpan(d(2026, 1, 1), d(2026, 3, 31)), s2)
    }

    @Test fun this_year() {
        assertEquals(DateSpan(d(2026, 1, 1), d(2026, 12, 31)), ReportPeriods.resolve(ReportPeriodType.THIS_YEAR, d(2026, 6, 6)))
    }

    @Test fun custom_uses_given_span() {
        val c = DateSpan(d(2026, 1, 5), d(2026, 1, 9))
        assertEquals(c, ReportPeriods.resolve(ReportPeriodType.CUSTOM, d(2026, 6, 6), custom = c))
    }

    @Test fun dayCount_is_inclusive() {
        assertEquals(1L, DateSpan(d(2026, 1, 1), d(2026, 1, 1)).dayCount)
        assertEquals(31L, DateSpan(d(2026, 1, 1), d(2026, 1, 31)).dayCount)
    }

    @Test(expected = IllegalArgumentException::class) fun span_rejects_reversed_dates() {
        DateSpan(d(2026, 1, 2), d(2026, 1, 1))
    }

    @Test fun millis_cover_the_whole_last_day() {
        val zone = ZoneId.of("Asia/Dhaka")
        val s = DateSpan(d(2026, 3, 1), d(2026, 3, 1))
        val start = s.startMillis(zone)
        val end = s.endMillis(zone)
        assertEquals(24L * 60 * 60 * 1000 - 1, end - start)
        // the next day's midnight is NOT inside
        assertEquals(end + 1, DateSpan(d(2026, 3, 2), d(2026, 3, 2)).startMillis(zone))
    }

    @Test fun millis_on_a_dst_day_are_23_hours() {
        val zone = ZoneId.of("America/New_York")
        val s = DateSpan(d(2026, 3, 8), d(2026, 3, 8)) // spring forward
        assertEquals(23L * 60 * 60 * 1000 - 1, s.endMillis(zone) - s.startMillis(zone))
    }

    // ---- previous equivalent period ----

    @Test fun previous_of_in_progress_month_is_like_for_like() {
        val today = d(2026, 3, 10)
        val cur = ReportPeriods.resolve(ReportPeriodType.THIS_MONTH, today)
        val prev = ReportPeriods.previous(ReportPeriodType.THIS_MONTH, cur, today)
        assertEquals(DateSpan(d(2026, 2, 1), d(2026, 2, 10)), prev)
    }

    @Test fun previous_like_for_like_is_clipped_to_shorter_month() {
        val today = d(2026, 3, 31)
        val cur = ReportPeriods.resolve(ReportPeriodType.THIS_MONTH, today)
        val prev = ReportPeriods.previous(ReportPeriodType.THIS_MONTH, cur, today)
        assertEquals(DateSpan(d(2026, 2, 1), d(2026, 2, 28)), prev)
    }

    @Test fun previous_of_in_progress_month_in_january_is_december() {
        val today = d(2026, 1, 5)
        val cur = ReportPeriods.resolve(ReportPeriodType.THIS_MONTH, today)
        assertEquals(DateSpan(d(2025, 12, 1), d(2025, 12, 5)), ReportPeriods.previous(ReportPeriodType.THIS_MONTH, cur, today))
    }

    @Test fun previous_of_completed_last_month_is_the_whole_month_before() {
        val today = d(2026, 3, 10)
        val cur = ReportPeriods.resolve(ReportPeriodType.LAST_MONTH, today)
        assertEquals(DateSpan(d(2026, 1, 1), d(2026, 1, 31)), ReportPeriods.previous(ReportPeriodType.LAST_MONTH, cur, today))
    }

    @Test fun previous_of_in_progress_week() {
        val today = d(2026, 11, 4) // Wednesday, 3rd day of Mon-start week
        val cur = ReportPeriods.resolve(ReportPeriodType.THIS_WEEK, today, DayOfWeek.MONDAY)
        assertEquals(DateSpan(d(2026, 10, 26), d(2026, 10, 28)), ReportPeriods.previous(ReportPeriodType.THIS_WEEK, cur, today))
    }

    @Test fun previous_of_today_is_yesterday_and_of_yesterday_is_the_day_before() {
        val today = d(2026, 3, 1)
        assertEquals(DateSpan(d(2026, 2, 28), d(2026, 2, 28)),
            ReportPeriods.previous(ReportPeriodType.TODAY, ReportPeriods.resolve(ReportPeriodType.TODAY, today), today))
        assertEquals(DateSpan(d(2026, 2, 27), d(2026, 2, 27)),
            ReportPeriods.previous(ReportPeriodType.YESTERDAY, ReportPeriods.resolve(ReportPeriodType.YESTERDAY, today), today))
    }

    @Test fun previous_of_this_year_is_like_for_like() {
        val today = d(2026, 2, 10)
        val cur = ReportPeriods.resolve(ReportPeriodType.THIS_YEAR, today)
        assertEquals(DateSpan(d(2025, 1, 1), d(2025, 2, 10)), ReportPeriods.previous(ReportPeriodType.THIS_YEAR, cur, today))
    }

    @Test fun previous_of_last_3_months_is_the_3_months_before() {
        val today = d(2026, 5, 15)
        val cur = ReportPeriods.resolve(ReportPeriodType.LAST_3_MONTHS, today)
        val prev = ReportPeriods.previous(ReportPeriodType.LAST_3_MONTHS, cur, today)
        assertEquals(d(2025, 11, 16), prev.start)
        assertEquals(d(2026, 2, 15), prev.end)
        // no gap and no overlap with the current period
        assertEquals(cur.start.minusDays(1), prev.end)
    }

    @Test fun previous_of_custom_is_same_length_immediately_before() {
        val cur = DateSpan(d(2026, 3, 10), d(2026, 3, 16)) // 7 days
        val prev = ReportPeriods.previous(ReportPeriodType.CUSTOM, cur, d(2026, 6, 1))
        assertEquals(DateSpan(d(2026, 3, 3), d(2026, 3, 9)), prev)
        assertEquals(cur.dayCount, prev.dayCount)
    }

    @Test fun contains_is_inclusive() {
        val s = DateSpan(d(2026, 3, 1), d(2026, 3, 31))
        assertTrue(d(2026, 3, 1) in s); assertTrue(d(2026, 3, 31) in s)
        assertFalse(d(2026, 2, 28) in s); assertFalse(d(2026, 4, 1) in s)
    }
}
