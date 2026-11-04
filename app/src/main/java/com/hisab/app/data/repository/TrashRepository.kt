package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.AccountDao
import com.hisab.app.data.local.dao.BudgetDao
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.dao.PersonDao
import com.hisab.app.data.local.dao.RecurringTransactionDao
import com.hisab.app.data.local.dao.SavingsGoalDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.dao.TrashDao
import com.hisab.app.data.local.database.TransactionRunner
import com.hisab.app.domain.trash.PurgeBlock
import com.hisab.app.domain.trash.TrashRules

sealed interface TrashResult {
    data object Done : TrashResult
    data object NotFound : TrashResult
    /** A transaction cannot come back while the account / person it belongs to is itself in Trash. */
    data class NeedsRestoreFirst(val name: String, val isAccount: Boolean) : TrashResult
    /** Another live budget now covers the same period + category. */
    data object BudgetConflict : TrashResult
    /** Still referenced by other records, so it stays in Trash (restorable) instead of orphaning history. */
    data class KeptBecauseInUse(val reason: PurgeBlock) : TrashResult
}

/** [keptInUse]: accounts/categories/people that could not be deleted forever because history still points at them. */
data class EmptyTrashResult(val keptInUse: Int)

/**
 * Restore and permanent deletion for everything the Trash screen lists:
 * transactions, budgets, goals (soft deleted with their own flag), and accounts, categories and
 * people (archived = isActive false). Multi-statement operations run in one database transaction.
 */
class TrashRepository(
    private val trashDao: TrashDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: SavingsGoalDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val personDao: PersonDao,
    private val recurringDao: RecurringTransactionDao,
    private val runner: TransactionRunner
) {
    // ---- restore -------------------------------------------------------------------------------

    /** Restores the row exactly as it was (same id, amount, date, relationships) - no copy is created. */
    suspend fun restoreTransaction(id: String): TrashResult = runner.run {
        val tx = transactionDao.getById(id)?.takeIf { it.isDeleted } ?: return@run TrashResult.NotFound
        for (accountId in listOfNotNull(tx.sourceAccountId, tx.destinationAccountId).distinct()) {
            val account = accountDao.getById(accountId)
            if (account != null && !account.isActive) return@run TrashResult.NeedsRestoreFirst(account.name, isAccount = true)
        }
        tx.personId?.let { personId ->
            val person = personDao.getById(personId)
            if (person != null && !person.isActive) return@run TrashResult.NeedsRestoreFirst(person.name, isAccount = false)
        }
        transactionDao.restore(id, System.currentTimeMillis())
        TrashResult.Done
    }

    suspend fun restoreBudget(id: String): TrashResult =
        if (budgetDao.restoreUnlessConflict(id, System.currentTimeMillis())) TrashResult.Done
        else if (budgetDao.getById(id) == null) TrashResult.NotFound else TrashResult.BudgetConflict

    suspend fun restoreGoal(id: String): TrashResult {
        if (goalDao.getById(id)?.takeIf { !it.isActive } == null) return TrashResult.NotFound
        goalDao.restore(id, System.currentTimeMillis())
        return TrashResult.Done
    }

    suspend fun restoreAccount(id: String): TrashResult {
        if (accountDao.getById(id)?.takeIf { !it.isActive } == null) return TrashResult.NotFound
        accountDao.reactivate(id)
        return TrashResult.Done
    }

    suspend fun restoreCategory(id: String): TrashResult {
        if (categoryDao.getById(id)?.takeIf { !it.isActive } == null) return TrashResult.NotFound
        categoryDao.setActive(id, true, null)
        return TrashResult.Done
    }

    suspend fun restorePerson(id: String): TrashResult {
        if (personDao.getById(id)?.takeIf { !it.isActive } == null) return TrashResult.NotFound
        personDao.reactivate(id)
        return TrashResult.Done
    }

    // ---- delete forever ------------------------------------------------------------------------

    suspend fun purgeTransaction(id: String) = trashDao.purgeTransaction(id)
    suspend fun purgeBudget(id: String) = trashDao.purgeBudget(id)
    suspend fun purgeGoal(id: String) = goalDao.purge(id)

    suspend fun purgeAccount(id: String): TrashResult = runner.run {
        val block = accountPurgeBlock(id)
        if (block != null) return@run TrashResult.KeptBecauseInUse(block)
        accountDao.purgeTrashed(id)
        TrashResult.Done
    }

    suspend fun purgeCategory(id: String): TrashResult = runner.run {
        val block = categoryPurgeBlock(id)
        if (block != null) return@run TrashResult.KeptBecauseInUse(block)
        categoryDao.purgeTrashed(id)
        TrashResult.Done
    }

    suspend fun purgePerson(id: String): TrashResult = runner.run {
        val block = TrashRules.personPurgeBlock(transactionDao.countReferencingPerson(id))
        if (block != null) return@run TrashResult.KeptBecauseInUse(block)
        personDao.purgeTrashed(id)
        TrashResult.Done
    }

    /** Deletes everything in Trash that is safe to delete; referenced accounts/categories/people stay (and are counted). */
    suspend fun emptyTrash(userId: String): EmptyTrashResult = runner.run {
        trashDao.emptyTrash(userId) // transactions (+ attachments) and budgets
        goalDao.getDeletedGoalsOnce(userId).forEach { goalDao.purge(it.id) }
        var kept = 0
        accountDao.getDeletedAccountsOnce(userId).forEach {
            if (accountPurgeBlock(it.id) == null) accountDao.purgeTrashed(it.id) else kept++
        }
        categoryDao.getDeletedCategoriesOnce(userId).forEach {
            if (categoryPurgeBlock(it.id) == null) categoryDao.purgeTrashed(it.id) else kept++
        }
        personDao.getDeletedPeopleOnce(userId).forEach {
            if (TrashRules.personPurgeBlock(transactionDao.countReferencingPerson(it.id)) == null) personDao.purgeTrashed(it.id) else kept++
        }
        EmptyTrashResult(kept)
    }

    private suspend fun accountPurgeBlock(id: String): PurgeBlock? = TrashRules.accountPurgeBlock(
        transactionDao.countReferencingAccount(id), recurringDao.countForAccount(id), goalDao.countReferencingAccount(id)
    )

    private suspend fun categoryPurgeBlock(id: String): PurgeBlock? = TrashRules.categoryPurgeBlock(
        transactionDao.countReferencingCategory(id), categoryDao.countBudgetsUsing(id), recurringDao.countForCategory(id)
    )
}
