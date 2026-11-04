package com.hisab.app.domain

import com.hisab.app.domain.dashboard.DashboardMath
import com.hisab.app.domain.goal.GoalCheck
import com.hisab.app.domain.goal.GoalRules
import com.hisab.app.domain.goal.GoalStatus
import com.hisab.app.domain.trash.PurgeBlock
import com.hisab.app.domain.trash.TrashRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalRulesTest {
    @Test fun spec_example_50000_account_allocate_10000() {
        // ৳50,000 in the account, nothing allocated yet, allocate ৳10,000.
        assertEquals(GoalCheck.Ok, GoalRules.checkAllocate(1_000_000, 5_000_000, 0))
        // Total balance is unchanged by goals; available drops by exactly the allocation.
        assertEquals(4_000_000, GoalRules.availableMinor(5_000_000, 1_000_000))
        assertEquals(4_000_000, DashboardMath.availableMinor(5_000_000, 1_000_000))
    }

    @Test fun cannotAllocateMoreThanAvailableSoMoneyIsNeverCountedTwice() {
        // 50,000 balance, 45,000 already earmarked: only 5,000 free.
        assertEquals(GoalCheck.ExceedsAvailable(500_000), GoalRules.checkAllocate(600_000, 5_000_000, 4_500_000))
        assertEquals(GoalCheck.Ok, GoalRules.checkAllocate(500_000, 5_000_000, 4_500_000))
    }

    @Test fun overAllocatedAccountReportsZeroFreeNotNegative() {
        assertEquals(GoalCheck.ExceedsAvailable(0), GoalRules.checkAllocate(1, 100, 500))
    }

    @Test fun amountMustBePositive() {
        assertEquals(GoalCheck.NotPositive, GoalRules.checkAllocate(0, 100, 0))
        assertEquals(GoalCheck.NotPositive, GoalRules.checkRelease(-5, 100))
    }

    @Test fun releaseIsLimitedToWhatTheGoalHoldsInThatAccount() {
        assertEquals(GoalCheck.ExceedsAllocated(300), GoalRules.checkRelease(400, 300))
        assertEquals(GoalCheck.Ok, GoalRules.checkRelease(300, 300))
    }

    @Test fun percentIsFlooredAndNeverPrematurelyComplete() {
        assertEquals(0, GoalRules.percent(0, 100)); assertEquals(33, GoalRules.percent(1, 3))
        assertEquals(99, GoalRules.percent(9_999, 10_000)); assertEquals(100, GoalRules.percent(10_000, 10_000))
        assertEquals(100, GoalRules.percent(20_000, 10_000)); assertEquals(0, GoalRules.percent(5, 0))
    }

    @Test fun remainingNeverNegative() {
        assertEquals(0, GoalRules.remainingMinor(100, 150)); assertEquals(70, GoalRules.remainingMinor(100, 30))
    }

    @Test fun statusCompletedBeatsOverdue() {
        val today = LocalDate.of(2026, 11, 3)
        val past = today.minusDays(5).toEpochDay()
        assertEquals(GoalStatus.COMPLETED, GoalRules.status(100, 100, past, today))
        assertEquals(GoalStatus.OVERDUE, GoalRules.status(100, 50, past, today))
        assertEquals(GoalStatus.IN_PROGRESS, GoalRules.status(100, 50, today.plusDays(1).toEpochDay(), today))
        assertEquals(GoalStatus.IN_PROGRESS, GoalRules.status(100, 50, today.toEpochDay(), today)) // due today is not overdue
        assertEquals(GoalStatus.IN_PROGRESS, GoalRules.status(100, 0, null, today))
    }

    @Test fun savingsRateMatchesReportDefinitionAndIsNullWithoutIncome() {
        assertEquals(40, DashboardMath.savingsRatePercent(10_000, 6_000))
        assertEquals(-50, DashboardMath.savingsRatePercent(10_000, 15_000))
        assertNull(DashboardMath.savingsRatePercent(0, 500))
        assertEquals(4_000, DashboardMath.netCashFlow(10_000, 6_000))
    }

    @Test fun trashPurgeRules() {
        assertNull(TrashRules.accountPurgeBlock(0, 0, 0))
        assertEquals(PurgeBlock.HAS_TRANSACTIONS, TrashRules.accountPurgeBlock(1, 0, 0))
        assertEquals(PurgeBlock.HAS_RECURRING, TrashRules.accountPurgeBlock(0, 2, 0))
        assertEquals(PurgeBlock.HAS_GOALS, TrashRules.accountPurgeBlock(0, 0, 1))
        assertEquals(PurgeBlock.HAS_BUDGETS, TrashRules.categoryPurgeBlock(0, 1, 0))
        assertNull(TrashRules.personPurgeBlock(0)); assertEquals(PurgeBlock.HAS_TRANSACTIONS, TrashRules.personPurgeBlock(3))
    }

    @Test fun accountDeleteRules() {
        assertNull(TrashRules.accountDeleteBlock(0, 0, 0))
        assertEquals(TrashRules.AccountDeleteBlock.NON_ZERO_BALANCE, TrashRules.accountDeleteBlock(500, 0, 0))
        assertEquals(TrashRules.AccountDeleteBlock.NON_ZERO_BALANCE, TrashRules.accountDeleteBlock(-500, 0, 0))
        assertEquals(TrashRules.AccountDeleteBlock.HAS_GOAL_ALLOCATION, TrashRules.accountDeleteBlock(500, 200, 0))
        assertEquals(TrashRules.AccountDeleteBlock.HAS_ACTIVE_RECURRING, TrashRules.accountDeleteBlock(0, 0, 1))
    }
}
