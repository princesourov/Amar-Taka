package com.hisab.app.domain

import com.hisab.app.domain.debt.DebtLedger
import com.hisab.app.domain.debt.DebtStatus
import com.hisab.app.domain.debt.DebtTxn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtLedgerTest {
    private fun lend(id: String, amt: Long, day: Long) = DebtTxn(id, "LENDING", amt, day)
    private fun borrow(id: String, amt: Long, day: Long) = DebtTxn(id, "BORROWING", amt, day)
    private fun gotBack(id: String, amt: Long, day: Long) = DebtTxn(id, "REPAYMENT_RECEIVED", amt, day)
    private fun paid(id: String, amt: Long, day: Long) = DebtTxn(id, "REPAYMENT_MADE", amt, day)

    @Test fun lendingOnlyIsActiveReceivable() {
        val d = DebtLedger.build(listOf(lend("a", 10_000, 1)))
        assertEquals(10_000, d.receivableMinor); assertEquals(0, d.payableMinor); assertEquals(10_000, d.netMinor)
        assertEquals(DebtStatus.ACTIVE, d.records.single().status)
    }

    @Test fun partialRepaymentReducesRemainingAndMarksPartiallyPaid() {
        val d = DebtLedger.build(listOf(lend("a", 10_000, 1), gotBack("r1", 3_000, 2)))
        val rec = d.records.single()
        assertEquals(7_000, d.receivableMinor); assertEquals(3_000, rec.paidMinor); assertEquals(7_000, rec.remainingMinor)
        assertEquals(DebtStatus.PARTIALLY_PAID, rec.status)
        assertEquals("r1", rec.payments.single().paymentId)
    }

    @Test fun fullSettlementLeavesNothingOutstanding() {
        val d = DebtLedger.build(listOf(lend("a", 10_000, 1), gotBack("r1", 4_000, 2), gotBack("r2", 6_000, 3)))
        assertEquals(0, d.receivableMinor); assertEquals(DebtStatus.SETTLED, d.records.single().status)
        assertEquals(2, d.records.single().payments.size)
    }

    @Test fun repaymentsAreAppliedOldestDebtFirst() {
        val d = DebtLedger.build(listOf(lend("old", 5_000, 1), lend("new", 5_000, 5), gotBack("r", 6_000, 6)))
        val byId = d.records.associateBy { it.id }
        assertEquals(DebtStatus.SETTLED, byId.getValue("old").status)
        assertEquals(DebtStatus.PARTIALLY_PAID, byId.getValue("new").status)
        assertEquals(1_000, byId.getValue("new").paidMinor)
        assertEquals(4_000, d.receivableMinor)
    }

    @Test fun oneRepaymentCanSpanTwoDebtsWithoutDoubleCounting() {
        val d = DebtLedger.build(listOf(lend("a", 1_000, 1), lend("b", 1_000, 2), gotBack("r", 1_500, 3)))
        assertEquals(1_500, d.records.sumOf { it.paidMinor })
        assertEquals(500, d.receivableMinor)
        assertEquals(0, d.surplusReceivedMinor)
    }

    @Test fun borrowingAndLendingAreKeptInTheirOwnDirections() {
        val d = DebtLedger.build(listOf(lend("l", 10_000, 1), borrow("b", 4_000, 2), paid("p", 1_000, 3)))
        assertEquals(10_000, d.receivableMinor); assertEquals(3_000, d.payableMinor); assertEquals(7_000, d.netMinor)
    }

    @Test fun aRepaymentInOneDirectionNeverTouchesTheOther() {
        val d = DebtLedger.build(listOf(lend("l", 10_000, 1), borrow("b", 4_000, 2), gotBack("r", 10_000, 3)))
        assertEquals(0, d.receivableMinor); assertEquals(4_000, d.payableMinor)
    }

    @Test fun overpaymentIsReportedAsSurplusNotAsNegativeDebt() {
        val d = DebtLedger.build(listOf(lend("a", 1_000, 1), gotBack("r", 1_500, 2)))
        assertEquals(0, d.receivableMinor); assertEquals(500, d.surplusReceivedMinor); assertEquals(0, d.payableMinor)
    }

    @Test fun repaymentWithoutAnyDebtIsAllSurplus() {
        val d = DebtLedger.build(listOf(paid("p", 700, 1)))
        assertEquals(700, d.surplusMadeMinor); assertTrue(d.records.isEmpty())
    }

    @Test fun ignoresUnrelatedTypesAndNonPositiveAmounts() {
        val d = DebtLedger.build(listOf(DebtTxn("x", "EXPENSE", 5_000, 1), lend("z", 0, 1), lend("a", 100, 1)))
        assertEquals(100, d.receivableMinor); assertEquals(1, d.records.size)
    }

    @Test fun orderingIsDeterministicForSameDay() {
        val a = DebtLedger.build(listOf(lend("b", 100, 1), lend("a", 100, 1), gotBack("r", 100, 2)))
        assertEquals(DebtStatus.SETTLED, a.records.first { it.id == "a" }.status)
        assertEquals(DebtStatus.ACTIVE, a.records.first { it.id == "b" }.status)
    }

    @Test fun totalsClampEachPersonAtZeroBeforeSumming() {
        val t = DebtLedger.totals(listOf(10_000L to 0L, -500L to 3_000L, 0L to 0L))
        assertEquals(10_000, t.receivableMinor); assertEquals(3_000, t.payableMinor); assertEquals(7_000, t.netMinor)
    }

    @Test fun settledPeopleDoNotAppearInTotals() {
        assertEquals(0, DebtLedger.totals(listOf(0L to 0L)).netMinor)
    }

    @Test fun outstandingNeverNegative() {
        assertEquals(0, DebtLedger.outstanding(100, 250)); assertEquals(60, DebtLedger.outstanding(100, 40))
    }
}
