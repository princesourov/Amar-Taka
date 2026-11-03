package com.hisab.app.domain.recurring

import com.hisab.app.domain.model.RecurrenceFrequency
import java.time.LocalDate

/**
 * Pure schedule maths for recurring transactions.
 *
 * Occurrences are always computed from the ORIGINAL start date (start + n * period), never by
 * repeatedly adding one period to the previous run. Repeated addition drifts: Jan 31 + 1 month is
 * Feb 28, and Feb 28 + 1 month is Mar 28 — the series would silently move to the 28th forever.
 */
object RecurrenceSchedule {

    /** Safety cap so a corrupt start date can never generate an unbounded number of transactions in one run. */
    const val MAX_CATCH_UP = 400

    fun occurrence(frequency: RecurrenceFrequency, startEpochDay: Long, index: Long): Long {
        val start = LocalDate.ofEpochDay(startEpochDay)
        return when (frequency) {
            RecurrenceFrequency.DAILY -> start.plusDays(index)
            RecurrenceFrequency.WEEKLY -> start.plusWeeks(index)
            RecurrenceFrequency.MONTHLY -> start.plusMonths(index)
            RecurrenceFrequency.YEARLY -> start.plusYears(index)
        }.toEpochDay()
    }

    /**
     * Every occurrence date that is >= [nextRunEpochDay], <= [todayEpochDay] and (when set)
     * <= [endEpochDay] — i.e. everything that is due and hasn't run yet, including runs missed while
     * the app was closed.
     */
    fun dueOccurrences(
        frequency: RecurrenceFrequency,
        startEpochDay: Long,
        nextRunEpochDay: Long,
        endEpochDay: Long?,
        todayEpochDay: Long
    ): List<Long> {
        val due = ArrayList<Long>()
        var index = 0L
        // Skip occurrences that already ran. Bounded: even 100 years of daily runs is 36,525 steps.
        while (occurrence(frequency, startEpochDay, index) < nextRunEpochDay) {
            index++
            if (index > 100_000) return due
        }
        while (due.size < MAX_CATCH_UP) {
            val day = occurrence(frequency, startEpochDay, index)
            if (day > todayEpochDay) break
            if (endEpochDay != null && day > endEpochDay) break
            due += day
            index++
        }
        return due
    }

    /**
     * The first occurrence strictly after [lastRunEpochDay]; null when it would fall after
     * [endEpochDay] (the schedule is finished).
     */
    fun nextAfter(
        frequency: RecurrenceFrequency,
        startEpochDay: Long,
        lastRunEpochDay: Long,
        endEpochDay: Long?
    ): Long? {
        var index = 0L
        while (index <= 100_000) {
            val day = occurrence(frequency, startEpochDay, index)
            if (day > lastRunEpochDay) return if (endEpochDay != null && day > endEpochDay) null else day
            index++
        }
        return null
    }

    /** Outcome of running a schedule up to today. */
    data class Plan(
        /** Occurrence dates to turn into transactions (oldest first). */
        val toGenerate: List<Long>,
        /** Value to store as the new nextRunEpochDay. */
        val newNextRunEpochDay: Long,
        /** False once the schedule has no occurrence left on or before its end date. */
        val stillActive: Boolean
    )

    /**
     * What to do for a schedule today. When nothing is due, the stored state is returned unchanged,
     * so applying a plan twice is harmless.
     */
    fun plan(
        frequency: RecurrenceFrequency,
        startEpochDay: Long,
        nextRunEpochDay: Long,
        endEpochDay: Long?,
        todayEpochDay: Long,
        isActive: Boolean = true
    ): Plan {
        val due = dueOccurrences(frequency, startEpochDay, nextRunEpochDay, endEpochDay, todayEpochDay)
        if (due.isEmpty()) {
            // Nothing due; also finish a schedule whose next run is already past its end date.
            val finished = endEpochDay != null && nextRunEpochDay > endEpochDay
            return Plan(emptyList(), nextRunEpochDay, isActive && !finished)
        }
        val last = due.last()
        val next = nextAfter(frequency, startEpochDay, last, endEpochDay)
        return Plan(due, next ?: (last + 1), next != null)
    }
}
