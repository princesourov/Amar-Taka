package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.PersonDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.domain.model.PersonWithBalance
import com.hisab.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class PersonRepository(
    private val personDao: PersonDao,
    private val transactionDao: TransactionDao
) {
    fun getActivePeople(userId: String): Flow<List<PersonEntity>> = personDao.getActivePeople(userId)

    fun observePerson(personId: String): Flow<PersonEntity?> = personDao.observeById(personId)

    suspend fun getPersonByIdOnce(id: String): PersonEntity? = personDao.getById(id)

    fun getNetBalance(userId: String, personId: String): Flow<Long> =
        transactionDao.getNetBalanceForPerson(userId, personId)

    fun getTransactionsForPerson(userId: String, personId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForPerson(userId, personId)

    fun getTotalReceivable(userId: String): Flow<Long> = transactionDao.getTotalReceivable(userId)

    fun getTotalPayable(userId: String): Flow<Long> = transactionDao.getTotalPayable(userId)

    /** Every active person paired with their live net balance — same combine-many-flows
     *  pattern already used for budgets and accounts, kept in the repository so it exists
     *  exactly once. */
    fun getPeopleWithBalances(userId: String): Flow<List<PersonWithBalance>> =
        getActivePeople(userId).flatMapLatest { people ->
            if (people.isEmpty()) flowOf(emptyList())
            else combine(people.map { p -> getNetBalance(userId, p.id).map { bal -> PersonWithBalance(p, bal) } }) { it.toList() }
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

    /** Soft delete via isActive — past transactions with this person stay intact and still
     *  correctly count toward receivable/payable totals, exactly like a deactivated account. */
    suspend fun deactivatePerson(id: String) {
        personDao.deactivate(id)
    }
}
