package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.AccountDao
import com.hisab.app.data.local.dao.SavingsGoalDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.database.TransactionRunner
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.GoalContributionEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.domain.goal.GoalCheck
import com.hisab.app.domain.goal.GoalRules
import com.hisab.app.domain.goal.GoalStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** A goal with its saved total (sum of contribution rows) - the only way the UI should read "saved". */
data class GoalWithProgress(val goal: SavingsGoalEntity, val savedMinor: Long) {
    val remainingMinor: Long get() = GoalRules.remainingMinor(goal.targetAmountMinor, savedMinor)
    val percent: Int get() = GoalRules.percent(savedMinor, goal.targetAmountMinor)
    fun status(today: LocalDate = LocalDate.now()): GoalStatus =
        GoalRules.status(goal.targetAmountMinor, savedMinor, goal.targetDateEpochDay, today)
}

/** One account's total, what goals have earmarked from it, and what is genuinely free to spend/allocate. */
data class AccountAvailability(val account: AccountEntity, val balanceMinor: Long, val allocatedMinor: Long) {
    val availableMinor: Long get() = GoalRules.availableMinor(balanceMinor, allocatedMinor)
}

data class GoalOverview(
    val goals: List<GoalWithProgress>,
    val accounts: List<AccountAvailability>,
    /** Everything currently earmarked for live goals (including the carried-over, account-less bucket). */
    val totalAllocatedMinor: Long
)

sealed interface GoalResult {
    data class Done(val id: String) : GoalResult
    data class Rejected(val check: GoalCheck) : GoalResult
    data object NotFound : GoalResult
}

/**
 * Goals use an ALLOCATION model (see domain/goal/GoalRules): contributing earmarks money that stays
 * in the account, so no transaction is created and nothing is counted twice. Every change is an
 * append-only contribution row, checked and written inside one database transaction so two quick taps
 * cannot over-allocate an account.
 */
class SavingsGoalRepository(
    private val goalDao: SavingsGoalDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val accountRepository: AccountRepository,
    private val runner: TransactionRunner
) {
    fun observeGoals(userId: String): Flow<List<GoalWithProgress>> =
        combine(goalDao.getActiveGoals(userId), goalDao.observeGoalTotals(userId)) { goals, totals ->
            val byGoal = totals.associate { it.goalId to it.totalMinor }
            goals.map { GoalWithProgress(it, byGoal[it.id] ?: 0L) }
        }

    fun observeGoal(goalId: String): Flow<GoalWithProgress?> =
        combine(goalDao.observeById(goalId), goalDao.observeContributions(goalId)) { goal, rows ->
            goal?.let { GoalWithProgress(it, rows.sumOf { r -> r.amountMinor }) }
        }

    fun observeContributions(goalId: String): Flow<List<GoalContributionEntity>> = goalDao.observeContributions(goalId)

    fun getDeletedGoals(userId: String): Flow<List<SavingsGoalEntity>> = goalDao.getDeletedGoals(userId)

    fun observeAccountAvailability(userId: String): Flow<List<AccountAvailability>> =
        combine(accountRepository.getAccountsWithBalances(userId), goalDao.observeAllocationsByAccount(userId)) { accounts, allocs ->
            val byAccount = allocs.filter { it.accountId != null }.associate { it.accountId!! to it.totalMinor }
            accounts.map { (acc, balance) -> AccountAvailability(acc, balance, byAccount[acc.id] ?: 0L) }
        }

    fun observeOverview(userId: String): Flow<GoalOverview> =
        combine(observeGoals(userId), observeAccountAvailability(userId), goalDao.observeAllocationsByAccount(userId)) { goals, accounts, allocs ->
            GoalOverview(goals, accounts, allocs.sumOf { it.totalMinor })
        }

    suspend fun getGoalOnce(id: String): SavingsGoalEntity? = goalDao.getById(id)

    suspend fun createGoal(
        userId: String,
        name: String,
        targetAmountMinor: Long,
        targetDateEpochDay: Long? = null,
        accountId: String? = null,
        iconKey: String = "savings",
        note: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        goalDao.insert(
            SavingsGoalEntity(
                id = id, userId = userId, name = name, targetAmountMinor = targetAmountMinor,
                savedAmountMinor = 0L, targetDateEpochDay = targetDateEpochDay, isActive = true,
                createdAtMillis = now, accountId = accountId, iconKey = iconKey, note = note,
                updatedAtMillis = now, deletedAtMillis = null
            )
        )
        return id
    }

    /** Editing a goal never touches money: the allocation rows are not changed. */
    suspend fun updateGoal(
        goalId: String, name: String, targetAmountMinor: Long, targetDateEpochDay: Long?,
        accountId: String?, iconKey: String, note: String?
    ): GoalResult {
        val current = goalDao.getById(goalId)?.takeIf { it.isActive } ?: return GoalResult.NotFound
        goalDao.update(
            current.copy(
                name = name, targetAmountMinor = targetAmountMinor, targetDateEpochDay = targetDateEpochDay,
                accountId = accountId, iconKey = iconKey, note = note, updatedAtMillis = System.currentTimeMillis()
            )
        )
        return GoalResult.Done(goalId)
    }

    /** Earmarks [amountMinor] of [accountId] for the goal, if the account has that much unallocated. */
    suspend fun allocate(goalId: String, accountId: String, amountMinor: Long, note: String?, dateMillis: Long = System.currentTimeMillis()): GoalResult =
        runner.run {
            val goal = goalDao.getById(goalId)?.takeIf { it.isActive } ?: return@run GoalResult.NotFound
            val account = accountDao.getById(accountId)?.takeIf { it.isActive } ?: return@run GoalResult.NotFound
            val balance = account.openingBalanceMinor + transactionDao.getNetMovementForAccountOnce(accountId)
            val check = GoalRules.checkAllocate(amountMinor, balance, goalDao.getAccountAllocatedOnce(accountId))
            if (check != GoalCheck.Ok) return@run GoalResult.Rejected(check)
            insertContribution(goal.id, accountId, amountMinor, note, dateMillis)
        }

    /** Releases [amountMinor] back to [accountId] (null = the carried-over bucket); never more than the goal holds there. */
    suspend fun release(goalId: String, accountId: String?, amountMinor: Long, note: String?, dateMillis: Long = System.currentTimeMillis()): GoalResult =
        runner.run {
            val goal = goalDao.getById(goalId)?.takeIf { it.isActive } ?: return@run GoalResult.NotFound
            val check = GoalRules.checkRelease(amountMinor, goalDao.getGoalAllocationOnce(goalId, accountId))
            if (check != GoalCheck.Ok) return@run GoalResult.Rejected(check)
            insertContribution(goal.id, accountId, -amountMinor, note, dateMillis)
        }

    private suspend fun insertContribution(goalId: String, accountId: String?, signedMinor: Long, note: String?, dateMillis: Long): GoalResult {
        val id = UUID.randomUUID().toString()
        goalDao.insertContribution(
            GoalContributionEntity(
                id = id, goalId = goalId, accountId = accountId, amountMinor = signedMinor,
                note = note?.ifBlank { null }, dateMillis = dateMillis, createdAtMillis = System.currentTimeMillis()
            )
        )
        return GoalResult.Done(id)
    }

    /** Moves the goal to Trash: its allocation is released from every account until it is restored. */
    suspend fun deleteGoal(goalId: String) = goalDao.softDelete(goalId, System.currentTimeMillis())

    suspend fun restoreGoal(goalId: String) = goalDao.restore(goalId, System.currentTimeMillis())
}
