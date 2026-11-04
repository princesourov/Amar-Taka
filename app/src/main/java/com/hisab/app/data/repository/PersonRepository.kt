package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.PersonDao
import com.hisab.app.data.local.dao.PersonDebtRow
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.domain.debt.DebtLedger
import com.hisab.app.domain.debt.DebtTotals
import com.hisab.app.domain.debt.DebtTxn
import com.hisab.app.domain.debt.PersonDebt
import com.hisab.app.domain.model.PersonWithBalance
import com.hisab.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class PersonRepository(
    private val personDao: PersonDao,
    private val transactionDao: TransactionDao
) {
    fun getActivePeople(userId: String): Flow<List<PersonEntity>> = personDao.getActivePeople(userId)

    fun observePerson(personId: String): Flow<PersonEntity?> = personDao.observeById(personId)

    suspend fun getPersonByIdOnce(id: String): PersonEntity? = personDao.getById(id)

    fun getTransactionsForPerson(userId: String, personId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForPerson(userId, personId)

    /** Every person's debt position, directions apart (people with no debt rows are absent). */
    private fun observeDebtRows(userId: String): Flow<Map<String, PersonDebtRow>> =
        transactionDao.observeDebtRows(userId).map { rows -> rows.associateBy { it.personId } }

    /** Receivable / payable / net across everyone: the Dashboard figures. Settled debts contribute zero. */
    fun observeDebtTotals(userId: String): Flow<DebtTotals> =
        transactionDao.observeDebtRows(userId).map { rows ->
            DebtLedger.totals(rows.map { it.receivableMinor to it.payableMinor })
        }

    /** One person's records with FIFO-allocated repayments and statuses (Active / Partially paid / Settled). */
    fun observePersonDebt(userId: String, personId: String): Flow<PersonDebt> =
        transactionDao.getTransactionsForPerson(userId, personId).map { txns -> DebtLedger.build(txns.map { it.toDebtTxn() }) }

    fun getPeopleWithBalances(userId: String): Flow<List<PersonWithBalance>> =
        combine(getActivePeople(userId), observeDebtRows(userId)) { people, rows ->
            people.map { p ->
                val row = rows[p.id]
                PersonWithBalance(p, row?.receivableClamped ?: 0L, row?.payableClamped ?: 0L)
            }
        }

    suspend fun createPerson(userId: String, name: String, phone: String?, email: String?, notes: String?): String {
        val id = UUID.randomUUID().toString()
        personDao.insert(
            PersonEntity(
                id = id,
                userId = userId,
                name = name,
                phone = phone,
                email = email,
                address = null,
                photoUri = null,
                notes = notes,
                isActive = true,
                createdAtMillis = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING.name
            )
        )
        return id
    }

    suspend fun updatePerson(person: PersonEntity, name: String, phone: String?, email: String?, notes: String?) {
        personDao.update(person.copy(name = name, phone = phone, email = email, notes = notes))
    }

    fun getAllPeople(userId: String): Flow<List<PersonEntity>> = personDao.getAllPeople(userId)

    fun getDeletedPeople(userId: String): Flow<List<PersonEntity>> = personDao.getDeletedPeople(userId)

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /** Money is still owed in one direction; settle it first so it cannot vanish from the totals. */
        data class HasOutstanding(val receivableMinor: Long, val payableMinor: Long) : DeleteResult
    }

    /**
     * "Delete" = move to Trash. Refused while the person still owes or is owed money: a hidden
     * person with an open debt would keep counting in the Dashboard totals with no screen to settle it.
     * History (all their transactions) is never touched.
     */
    suspend fun deletePerson(userId: String, id: String): DeleteResult {
        val row = transactionDao.getDebtRow(userId, id, null)
        val receivable = row?.receivableClamped ?: 0L
        val payable = row?.payableClamped ?: 0L
        if (receivable > 0 || payable > 0) return DeleteResult.HasOutstanding(receivable, payable)
        personDao.deactivate(id, System.currentTimeMillis())
        return DeleteResult.Deleted
    }

    suspend fun restorePerson(id: String) = personDao.reactivate(id)
}

internal fun TransactionEntity.toDebtTxn() = DebtTxn(
    id = id, type = type, amountMinor = amountMinor, dateMillis = transactionDateMillis,
    createdAtMillis = createdAtMillis, note = note, dueDateEpochDay = dueDateEpochDay
)
