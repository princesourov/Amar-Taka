package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.PersonDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PersonRepository(
    private val personDao: PersonDao,
    private val transactionDao: TransactionDao
) {
    fun getActivePeople(userId: String): Flow<List<PersonEntity>> = personDao.getActivePeople(userId)

    fun getNetBalance(userId: String, personId: String): Flow<Long> =
        transactionDao.getNetBalanceForPerson(userId, personId)

    fun getTotalReceivable(userId: String): Flow<Long> = transactionDao.getTotalReceivable(userId)

    fun getTotalPayable(userId: String): Flow<Long> = transactionDao.getTotalPayable(userId)

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
}
