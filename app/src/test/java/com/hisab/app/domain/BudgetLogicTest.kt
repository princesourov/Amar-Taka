package com.hisab.app.domain

import com.hisab.app.domain.budget.BudgetEvaluator
import com.hisab.app.domain.budget.BudgetKey
import com.hisab.app.domain.budget.BudgetPeriods
import com.hisab.app.domain.budget.BudgetRules
import com.hisab.app.domain.budget.BudgetState
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.domain.report.DateSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class BudgetLogicTest {
    private val d = { y: Int, m: Int, day: Int -> LocalDate.of(y, m, day) }

    // ---------- periods ----------

    @Test fun period_containing_reference() {
        val ref = d(2026, 11, 4) // Wednesday
        assertEquals(DateSpan(ref, ref), BudgetPeriods.periodContaining(BudgetScope.DAILY, ref, DayOfWeek.MONDAY))
        assertEquals(DateSpan(d(2026, 11, 2), d(2026, 11, 8)), BudgetPeriods.periodContaining(BudgetScope.WEEKLY, ref, DayOfWeek.MONDAY))
        assertEquals(DateSpan(d(2026, 11, 1), d(2026, 11, 30)), BudgetPeriods.periodContaining(BudgetScope.MONTHLY, ref, DayOfWeek.MONDAY))
    }

    @Test fun monthly_period_handles_leap_february_and_december() {
        assertEquals(d(2028, 2, 29), BudgetPeriods.periodContaining(BudgetScope.MONTHLY, d(2028, 2, 3), DayOfWeek.MONDAY).end)
        assertEquals(d(2026, 12, 31), BudgetPeriods.periodContaining(BudgetScope.MONTHLY, d(2026, 12, 3), DayOfWeek.MONDAY).end)
    }

    @Test fun budget_takes_effect_from_its_start_and_keeps_recurring() {
        val start = d(2026, 3, 15).toEpochDay()
        val feb = DateSpan(d(2026, 2, 1), d(2026, 2, 28))
        val march = DateSpan(d(2026, 3, 1), d(2026, 3, 31))
        val april = DateSpan(d(2026, 4, 1), d(2026, 4, 30))
        assertFalse(BudgetPeriods.isInEffect(start, feb))
        assertTrue(BudgetPeriods.isInEffect(start, march))   // started mid-month: that month counts
        assertTrue(BudgetPeriods.isInEffect(start, april))   // and it recurs
        assertTrue(BudgetPeriods.isInEffect(null, feb))      // no start date: always in effect
    }

    @Test fun reference_date_for_month_view() {
        val today = d(2026, 11, 4)
        assertEquals(today, BudgetPeriods.referenceDateForMonth(YearMonth.of(2026, 11), today))
        assertEquals(d(2026, 10, 31), BudgetPeriods.referenceDateForMonth(YearMonth.of(2026, 10), today))
        assertEquals(d(2026, 12, 1), BudgetPeriods.referenceDateForMonth(YearMonth.of(2026, 12), today))
    }

    // ---------- utilization ----------

    @Test fun utilization_states_and_thresholds() {
        assertEquals(BudgetState.ON_TRACK, BudgetEvaluator.evaluate(10_000, 0).state)
        assertEquals(BudgetState.ON_TRACK, BudgetEvaluator.evaluate(10_000, 7_999).state)
        assertEquals(BudgetState.NEAR_LIMIT, BudgetEvaluator.evaluate(10_000, 8_000).state)
        assertEquals(BudgetState.NEAR_LIMIT, BudgetEvaluator.evaluate(10_000, 9_999).state)
        assertEquals(BudgetState.LIMIT_REACHED, BudgetEvaluator.evaluate(10_000, 10_000).state)
        assertEquals(BudgetState.OVER, BudgetEvaluator.evaluate(10_000, 10_001).state)
    }

    @Test fun remaining_overspent_and_percent() {
        val e = BudgetEvaluator.evaluate(300_000, 220_000)
        assertEquals(80_000L, e.remainingMinor); assertEquals(0L, e.overspentMinor)
        assertEquals(73.333, e.usedPercent, 0.001); assertEquals(73, e.progressPercent)

        val over = BudgetEvaluator.evaluate(300_000, 450_000)
        assertEquals(-150_000L, over.remainingMinor); assertEquals(150_000L, over.overspentMinor)
        assertEquals(150.0, over.usedPercent, 1e-9); assertEquals(100, over.progressPercent) // bar clamps, text does not
    }

    @Test fun zero_or_negative_budget_is_invalid_and_safe() {
        val z = BudgetEvaluator.evaluate(0, 5_000)
        assertEquals(BudgetState.INVALID, z.state); assertEquals(0.0, z.usedPercent, 0.0); assertEquals(0, z.progressPercent)
        assertEquals(BudgetState.INVALID, BudgetEvaluator.evaluate(-5, 0).state)
    }

    @Test fun negative_net_spend_from_refunds_is_shown_as_zero_spent() {
        val e = BudgetEvaluator.evaluate(10_000, -2_500)
        assertEquals(0L, e.spentMinor); assertEquals(10_000L, e.remainingMinor); assertEquals(BudgetState.ON_TRACK, e.state)
    }

    // ---------- duplicates ----------

    private val existing = listOf(
        BudgetKey("1", BudgetScope.MONTHLY, "food"),
        BudgetKey("2", BudgetScope.MONTHLY, null),
        BudgetKey("3", BudgetScope.WEEKLY, "food"),
        BudgetKey("4", BudgetScope.MONTHLY, "bus", isDeleted = true)
    )

    @Test fun conflict_same_period_and_category() {
        assertEquals("1", BudgetRules.findConflict(existing, BudgetScope.MONTHLY, "food", null)?.id)
        assertEquals("2", BudgetRules.findConflict(existing, BudgetScope.MONTHLY, null, null)?.id)
    }

    @Test fun no_conflict_for_different_period_or_category_or_deleted() {
        assertNull(BudgetRules.findConflict(existing, BudgetScope.DAILY, "food", null))
        assertNull(BudgetRules.findConflict(existing, BudgetScope.MONTHLY, "rent", null))
        assertNull(BudgetRules.findConflict(existing, BudgetScope.MONTHLY, "bus", null)) // only a trashed one exists
    }

    @Test fun editing_a_budget_does_not_conflict_with_itself() {
        assertNull(BudgetRules.findConflict(existing, BudgetScope.MONTHLY, "food", ignoreId = "1"))
        // ...but changing it into another existing budget's slot does
        assertEquals("3", BudgetRules.findConflict(existing, BudgetScope.WEEKLY, "food", ignoreId = "1")?.id)
    }

    // ---------- validation ----------

    @Test fun validation_accepts_valid_amount_and_trims_name() {
        val v = BudgetRules.validate(" 3,000.50 ", "  Groceries ")
        assertTrue(v is BudgetRules.Validation.Valid)
        v as BudgetRules.Validation.Valid
        assertEquals(300_050L, v.amountMinor); assertEquals("Groceries", v.name)
    }

    @Test fun blank_name_becomes_null() {
        val v = BudgetRules.validate("10", "   ") as BudgetRules.Validation.Valid
        assertNull(v.name)
    }

    @Test fun validation_rejects_bad_amounts() {
        assertEquals(BudgetRules.Validation.AmountRequired, BudgetRules.validate("", null))
        assertEquals(BudgetRules.Validation.AmountRequired, BudgetRules.validate(null, null))
        assertEquals(BudgetRules.Validation.AmountNotPositive, BudgetRules.validate("0", null))
        assertEquals(BudgetRules.Validation.AmountNotPositive, BudgetRules.validate("0.00", null))
        assertEquals(BudgetRules.Validation.AmountInvalid, BudgetRules.validate("-5", null))
        assertEquals(BudgetRules.Validation.AmountInvalid, BudgetRules.validate("abc", null))
        assertEquals(BudgetRules.Validation.AmountInvalid, BudgetRules.validate("1.2.3", null))
        assertEquals(BudgetRules.Validation.AmountInvalid, BudgetRules.validate(".", null))
        assertEquals(BudgetRules.Validation.AmountTooManyDecimals, BudgetRules.validate("1.234", null))
        assertEquals(BudgetRules.Validation.AmountTooLarge, BudgetRules.validate("99999999999999", null))
    }

    @Test fun validation_rejects_overlong_name() {
        assertEquals(BudgetRules.Validation.NameTooLong, BudgetRules.validate("10", "x".repeat(BudgetRules.MAX_NAME_LENGTH + 1)))
        assertTrue(BudgetRules.validate("10", "x".repeat(BudgetRules.MAX_NAME_LENGTH)) is BudgetRules.Validation.Valid)
    }
}
