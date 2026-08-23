package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.AccountDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.domain.model.AccountType
import com.hisab.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class AccountRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {
    fun getActiveAccounts(userId: String): Flow<List<AccountEntity>> = accountDao.getActiveAccounts(userId)

    /** Current balance = opening balance + net ledger movement. Always live, never stored. */
    fun getCurrentBalance(account: AccountEntity): Flow<Long> =
        transactionDao.getNetMovementForAccount(account.id).map { net -> account.openingBalanceMinor + net }

    /**
     * Every active account paired with its live balance. Shared by AccountsViewModel (the full
     * Accounts screen) and DashboardViewModel (the dashboard's account strip) so the combine-many-
     * flows logic exists exactly once, not duplicated across two ViewModels.
     */
    fun getAccountsWithBalances(userId: String): Flow<List<Pair<AccountEntity, Long>>> =
        getActiveAccounts(userId).flatMapLatest { accounts ->
            if (accounts.isEmpty()) flowOf(emptyList())
            else combine(accounts.map { acc -> getCurrentBalance(acc).map { bal -> acc to bal } }) { it.toList() }
        }

    suspend fun getAccountByIdOnce(id: String): AccountEntity? = accountDao.getById(id)

    suspend fun createAccount(
        userId: String,
        name: String,
        type: AccountType,
        iconKey: String,
        openingBalanceMinor: Long,
        notes: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        accountDao.insert(
            AccountEntity(
                id = id,
                userId = userId,
                name = name,
                type = type.name,
                iconKey = iconKey,
                openingBalanceMinor = openingBalanceMinor,
                notes = notes,
                isActive = true,
                createdAtMillis = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING.name
            )
        )
        return id
    }

    suspend fun createCustomAccount(
        userId: String,
        name: String,
        iconKey: String,
        openingBalanceMinor: Long
    ): String = createAccount(userId, name, AccountType.CUSTOM, iconKey, openingBalanceMinor)

    /**
     * Editing openingBalanceMinor is safe under this architecture specifically because current
     * balance is never stored — it's recomputed live as opening + ledger movement every time it's
     * read, so correcting a wrong opening balance here takes effect immediately everywhere,
     * with no separate recalculation step.
     */
    suspend fun updateAccount(account: AccountEntity, name: String, openingBalanceMinor: Long, notes: String?) {
        accountDao.update(account.copy(name = name, openingBalanceMinor = openingBalanceMinor, notes = notes))
    }

    /** Soft delete via the existing isActive flag — history involving this account stays intact and correct. */
    suspend fun deactivateAccount(id: String) {
        accountDao.deactivate(id)
    }
}
