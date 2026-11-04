package com.hisab.app.domain.trash

/**
 * When may something in Trash be deleted FOREVER?
 *
 * Transactions, budgets and goals own their data, so they can always be purged (a goal takes its
 * contribution rows with it). Accounts, categories and people are referenced BY other rows, so
 * purging one that is still referenced would orphan history (a transaction pointing at a missing
 * account). Those stay in Trash - restorable, invisible everywhere else - until nothing references them.
 */
enum class PurgeBlock { HAS_TRANSACTIONS, HAS_BUDGETS, HAS_RECURRING, HAS_GOALS }

object TrashRules {

    fun accountPurgeBlock(transactionRefs: Int, recurringRefs: Int, goalRefs: Int): PurgeBlock? = when {
        transactionRefs > 0 -> PurgeBlock.HAS_TRANSACTIONS
        recurringRefs > 0 -> PurgeBlock.HAS_RECURRING
        goalRefs > 0 -> PurgeBlock.HAS_GOALS
        else -> null
    }

    fun categoryPurgeBlock(transactionRefs: Int, budgetRefs: Int, recurringRefs: Int): PurgeBlock? = when {
        transactionRefs > 0 -> PurgeBlock.HAS_TRANSACTIONS
        budgetRefs > 0 -> PurgeBlock.HAS_BUDGETS
        recurringRefs > 0 -> PurgeBlock.HAS_RECURRING
        else -> null
    }

    fun personPurgeBlock(transactionRefs: Int): PurgeBlock? =
        if (transactionRefs > 0) PurgeBlock.HAS_TRANSACTIONS else null

    /**
     * Archiving (moving to Trash) an account is refused while it would hide real money or break a plan:
     * a non-zero balance, money earmarked for goals, or an active recurring schedule that posts to it.
     */
    enum class AccountDeleteBlock { NON_ZERO_BALANCE, HAS_GOAL_ALLOCATION, HAS_ACTIVE_RECURRING }

    fun accountDeleteBlock(balanceMinor: Long, allocatedToGoalsMinor: Long, activeRecurring: Int): AccountDeleteBlock? = when {
        allocatedToGoalsMinor != 0L -> AccountDeleteBlock.HAS_GOAL_ALLOCATION
        activeRecurring > 0 -> AccountDeleteBlock.HAS_ACTIVE_RECURRING
        balanceMinor != 0L -> AccountDeleteBlock.NON_ZERO_BALANCE
        else -> null
    }
}
