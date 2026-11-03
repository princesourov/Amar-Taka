package com.hisab.app.domain.usecase

import androidx.room.withTransaction
import com.hisab.app.data.local.dao.AccountDao
import com.hisab.app.data.local.dao.RecurringTransactionDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.database.HisabDatabase
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.domain.model.SyncStatus
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.domain.recurring.RecurrenceSchedule
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Turns due recurring schedules into real transactions. Runs on app start and daily via WorkManager.
 *
 * Safety properties:
 *  - Catch-up: every missed occurrence is generated (not just one per run).
 *  - No drift: dates come from [RecurrenceSchedule] (start + n periods), so Jan 31 → Feb 28 → Mar 31.
 *  - Idempotent: each generated transaction gets the deterministic id "rec-<scheduleId>-<epochDay>" and is
 *    inserted with IGNORE, so a retry, a crash halfway, or the worker and app start racing can never create
 *    a duplicate. A generated transaction the user later trashed is not regenerated (the id still exists).
 *  - Atomic: the transactions and the schedule's new nextRun are written in one database transaction.
 *  - Serialised by a mutex (this class is a singleton in AppContainer).
 *  - A schedule whose account is gone/inactive is left untouched (not advanced), so nothing is silently lost.
 */
class ExecuteRecurringTransactionsUseCase(
    private val database: HisabDatabase,
    private val recurringDao: RecurringTransactionDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) {
    private val mutex = Mutex()

    /** @return how many transactions were created. */
    suspend operator fun invoke(userId: String, today: LocalDate = LocalDate.now(DateRanges.zone())): Int =
        mutex.withLock {
            val zone = DateRanges.zone()
            var created = 0
            val due = recurringDao.getDueForExecution(today.toEpochDay()).filter { it.userId == userId }
            for (recurring in due) {
                val type = runCatching { TransactionType.valueOf(recurring.type) }.getOrNull()
                if (type != TransactionType.EXPENSE && type != TransactionType.INCOME) continue // unsupported kinds are left alone
                val account = accountDao.getById(recurring.accountId)
                if (account == null || !account.isActive) continue

                val plan = RecurrenceSchedule.plan(
                    RecurrenceFrequency.valueOf(recurring.frequency),
                    recurring.startDateEpochDay, recurring.nextRunEpochDay,
                    recurring.endDateEpochDay, today.toEpochDay(), recurring.isActive
                )
                database.withTransaction {
                    for (day in plan.toGenerate) {
                        val millis = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
                        if (transactionDao.insertIfAbsent(build(recurring, type, day, millis)) != -1L) created++
                    }
                    recurringDao.update(recurring.copy(nextRunEpochDay = plan.newNextRunEpochDay, isActive = plan.stillActive))
                }
            }
            created
        }

    companion object {
        fun transactionId(scheduleId: String, epochDay: Long) = "rec-$scheduleId-$epochDay"

        internal fun build(r: RecurringTransactionEntity, type: TransactionType, epochDay: Long, dateMillis: Long): TransactionEntity {
            val now = System.currentTimeMillis()
            return TransactionEntity(
                id = transactionId(r.id, epochDay),
                userId = r.userId,
                type = type.name,
                amountMinor = r.amountMinor,
                categoryId = r.categoryId, // may be null → shows as Uncategorized rather than dropping the run
                sourceAccountId = if (type == TransactionType.EXPENSE) r.accountId else null,
                destinationAccountId = if (type == TransactionType.INCOME) r.accountId else null,
                note = r.note,
                transactionDateMillis = dateMillis,
                syncStatus = SyncStatus.PENDING.name,
                createdAtMillis = now,
                updatedAtMillis = now,
                isDeleted = false
            )
        }
    }
}
