package com.hisab.app.domain

import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.domain.recurring.RecurrenceSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceScheduleTest {
    private fun e(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun days(l: List<Long>) = l.map { LocalDate.ofEpochDay(it).toString() }

    @Test fun monthly_series_anchored_on_the_31st_does_not_drift() {
        val start = e(2026, 1, 31)
        val due = RecurrenceSchedule.dueOccurrences(RecurrenceFrequency.MONTHLY, start, start, null, e(2026, 5, 31))
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"), days(due))
    }

    @Test fun nothing_due_before_the_next_run() {
        val start = e(2026, 3, 1)
        assertTrue(RecurrenceSchedule.dueOccurrences(RecurrenceFrequency.DAILY, start, e(2026, 3, 10), null, e(2026, 3, 9)).isEmpty())
    }

    @Test fun catch_up_generates_every_missed_run_exactly_once() {
        val start = e(2026, 3, 1)
        val due = RecurrenceSchedule.dueOccurrences(RecurrenceFrequency.WEEKLY, start, e(2026, 3, 8), null, e(2026, 3, 30))
        assertEquals(listOf("2026-03-08", "2026-03-15", "2026-03-22", "2026-03-29"), days(due))
    }

    @Test fun already_executed_runs_are_skipped_via_next_run() {
        val start = e(2026, 3, 1)
        // next run is Mar 3: Mar 1 and Mar 2 already happened
        val due = RecurrenceSchedule.dueOccurrences(RecurrenceFrequency.DAILY, start, e(2026, 3, 3), null, e(2026, 3, 4))
        assertEquals(listOf("2026-03-03", "2026-03-04"), days(due))
    }

    @Test fun end_date_stops_the_series() {
        val start = e(2026, 3, 1)
        val due = RecurrenceSchedule.dueOccurrences(RecurrenceFrequency.DAILY, start, start, e(2026, 3, 3), e(2026, 3, 10))
        assertEquals(3, due.size)
        assertNull(RecurrenceSchedule.nextAfter(RecurrenceFrequency.DAILY, start, e(2026, 3, 3), e(2026, 3, 3)))
    }

    @Test fun next_after_returns_following_occurrence() {
        val start = e(2026, 1, 31)
        assertEquals("2026-03-31", LocalDate.ofEpochDay(RecurrenceSchedule.nextAfter(RecurrenceFrequency.MONTHLY, start, e(2026, 2, 28), null)!!).toString())
    }

    @Test fun yearly_leap_day() {
        val start = e(2028, 2, 29)
        val due = RecurrenceSchedule.dueOccurrences(RecurrenceFrequency.YEARLY, start, start, null, e(2030, 3, 1))
        assertEquals(listOf("2028-02-29", "2029-02-28", "2030-02-28"), days(due))
    }

    @Test fun catch_up_is_capped() {
        val start = e(2000, 1, 1)
        val due = RecurrenceSchedule.dueOccurrences(RecurrenceFrequency.DAILY, start, start, null, e(2026, 1, 1))
        assertEquals(RecurrenceSchedule.MAX_CATCH_UP, due.size)
    }

    @Test fun plan_catches_up_and_moves_next_run_past_today() {
        val start = e(2026, 1, 31)
        val p = RecurrenceSchedule.plan(RecurrenceFrequency.MONTHLY, start, start, null, e(2026, 3, 15))
        assertEquals(listOf("2026-01-31", "2026-02-28"), days(p.toGenerate))
        assertEquals("2026-03-31", LocalDate.ofEpochDay(p.newNextRunEpochDay).toString())
        assertTrue(p.stillActive)
    }

    @Test fun plan_is_idempotent_once_applied() {
        val start = e(2026, 3, 1)
        val first = RecurrenceSchedule.plan(RecurrenceFrequency.DAILY, start, start, null, e(2026, 3, 3))
        val second = RecurrenceSchedule.plan(RecurrenceFrequency.DAILY, start, first.newNextRunEpochDay, null, e(2026, 3, 3))
        assertEquals(3, first.toGenerate.size)
        assertTrue(second.toGenerate.isEmpty())
        assertEquals(first.newNextRunEpochDay, second.newNextRunEpochDay)
    }

    @Test fun plan_finishes_schedule_at_end_date() {
        val start = e(2026, 3, 1)
        val p = RecurrenceSchedule.plan(RecurrenceFrequency.DAILY, start, start, e(2026, 3, 2), e(2026, 3, 10))
        assertEquals(2, p.toGenerate.size)
        assertEquals(false, p.stillActive)
    }

    @Test fun plan_with_nothing_due_changes_nothing() {
        val start = e(2026, 5, 1)
        val p = RecurrenceSchedule.plan(RecurrenceFrequency.WEEKLY, start, start, null, e(2026, 4, 1))
        assertTrue(p.toGenerate.isEmpty())
        assertEquals(start, p.newNextRunEpochDay)
        assertTrue(p.stillActive)
    }

    @Test fun plan_respects_catch_up_cap_and_resumes_next_run() {
        val start = e(2020, 1, 1)
        val p = RecurrenceSchedule.plan(RecurrenceFrequency.DAILY, start, start, null, e(2026, 1, 1))
        assertEquals(RecurrenceSchedule.MAX_CATCH_UP, p.toGenerate.size)
        assertEquals(p.toGenerate.last() + 1, p.newNextRunEpochDay)
    }
}
