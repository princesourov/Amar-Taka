package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.RecurringTransactionDao
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RecurringTransactionRepository(
    private val recurringDao: RecurringTransactionDao,
    private val transactionDao: com.hisab.app.data.local.dao.TransactionDao
) {

    fun getActiveRecurring(userId: String): Flow<List<RecurringTransactionEntity>> =
        recurringDao.getActiveRecurring(userId)

    /** Active and paused schedules. */
    fun getAllRecurring(userId: String): Flow<List<RecurringTransactionEntity>> =
        recurringDao.getAllRecurring(userId)

    suspend fun setActive(id: String, active: Boolean) = recurringDao.setActive(id, active)

    /** Deletes the schedule only; transactions it already created stay in the ledger. */
    suspend fun delete(id: String) = recurringDao.deleteById(id)

    suspend fun getById(id: String): RecurringTransactionEntity? = recurringDao.getById(id)

    /** Generated transactions of one schedule, newest first (including ones the user later trashed - see isDeleted). */
    suspend fun history(scheduleId: String) = transactionDao.getGeneratedByRecurring(scheduleId)

    /**
     * Edits amount / category / account / frequency / end date / note of a schedule. The next run is
     * NOT moved backwards (that would regenerate history); it moves only if the new start would be later.
     * Already generated transactions are never rewritten.
     */
    suspend fun update(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        categoryId: String?,
        accountId: String,
        frequency: RecurrenceFrequency,
        endDateEpochDay: Long?,
        note: String?
    ): Boolean {
        val current = recurringDao.getById(id) ?: return false
        recurringDao.update(
            current.copy(
                type = type.name, amountMinor = amountMinor, categoryId = categoryId, accountId = accountId,
                frequency = frequency.name, endDateEpochDay = endDateEpochDay, note = note
            )
        )
        return true
    }

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
