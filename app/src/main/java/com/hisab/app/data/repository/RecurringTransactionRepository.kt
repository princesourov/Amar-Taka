package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.RecurringTransactionDao
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RecurringTransactionRepository(private val recurringDao: RecurringTransactionDao) {

    fun getActiveRecurring(userId: String): Flow<List<RecurringTransactionEntity>> =
        recurringDao.getActiveRecurring(userId)

    suspend fun createRecurring(
        userId: String,
        type: TransactionType,
        amountMinor: Long,
        categoryId: String?,
        accountId: String,
        frequency: RecurrenceFrequency,
        startDateEpochDay: Long,
        endDateEpochDay: Long? = null,
        note: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        recurringDao.insert(
            RecurringTransactionEntity(
                id = id, userId = userId, type = type.name, amountMinor = amountMinor,
                categoryId = categoryId, accountId = accountId, personId = null,
                frequency = frequency.name, startDateEpochDay = startDateEpochDay,
                endDateEpochDay = endDateEpochDay, nextRunEpochDay = startDateEpochDay,
                note = note, isActive = true
            )
        )
        return id
    }
}
