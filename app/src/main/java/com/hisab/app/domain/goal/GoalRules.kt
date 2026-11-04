package com.hisab.app.domain.goal

import java.time.LocalDate

/**
 * Savings-goal rules as pure functions.
 *
 * Financial model: a goal ALLOCATES (earmarks) money that stays in the account. No transaction is
 * created and no balance changes, so the same money can never be counted twice:
 *
 *   account balance      = opening balance + ledger movement          (unchanged by goals)
 *   allocated to goals   = sum of that account's goal contributions   (trashed goals excluded)
 *   available balance    = account balance - allocated to goals
 *
 * Contributions are signed rows: positive allocates, negative releases.
 */
enum class GoalStatus { IN_PROGRESS, COMPLETED, OVERDUE }

sealed interface GoalCheck {
    data object Ok : GoalCheck
    data object NotPositive : GoalCheck
    /** The account does not have this much unallocated money. */
    data class ExceedsAvailable(val availableMinor: Long) : GoalCheck
    /** Trying to release more than this goal has allocated (from that account). */
    data class ExceedsAllocated(val allocatedMinor: Long) : GoalCheck
}

object GoalRules {

    fun availableMinor(accountBalanceMinor: Long, allocatedToGoalsMinor: Long): Long =
        accountBalanceMinor - allocatedToGoalsMinor

    fun checkAllocate(amountMinor: Long, accountBalanceMinor: Long, accountAllocatedMinor: Long): GoalCheck {
        if (amountMinor <= 0) return GoalCheck.NotPositive
        val available = availableMinor(accountBalanceMinor, accountAllocatedMinor)
        return if (amountMinor > available) GoalCheck.ExceedsAvailable(available.coerceAtLeast(0)) else GoalCheck.Ok
    }

    /** [allocatedFromAccountMinor]: what this goal currently has allocated from the account being released to. */
    fun checkRelease(amountMinor: Long, allocatedFromAccountMinor: Long): GoalCheck {
        if (amountMinor <= 0) return GoalCheck.NotPositive
        return if (amountMinor > allocatedFromAccountMinor) GoalCheck.ExceedsAllocated(allocatedFromAccountMinor.coerceAtLeast(0))
        else GoalCheck.Ok
    }

    fun remainingMinor(targetMinor: Long, savedMinor: Long): Long = (targetMinor - savedMinor).coerceAtLeast(0)

    /** Whole percent, floored, clamped to 0..100 (99.9% shows 99, never a premature 100). */
    fun percent(savedMinor: Long, targetMinor: Long): Int {
        if (targetMinor <= 0 || savedMinor <= 0) return 0
        if (savedMinor >= targetMinor) return 100
        return ((savedMinor.toDouble() / targetMinor.toDouble()) * 100).toInt().coerceIn(0, 99)
    }

    fun status(targetMinor: Long, savedMinor: Long, targetDateEpochDay: Long?, today: LocalDate): GoalStatus = when {
        targetMinor > 0 && savedMinor >= targetMinor -> GoalStatus.COMPLETED
        targetDateEpochDay != null && LocalDate.ofEpochDay(targetDateEpochDay).isBefore(today) -> GoalStatus.OVERDUE
        else -> GoalStatus.IN_PROGRESS
    }
}
