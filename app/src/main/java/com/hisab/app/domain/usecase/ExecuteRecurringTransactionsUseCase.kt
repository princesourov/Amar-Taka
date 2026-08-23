package com.hisab.app.domain.usecase

import com.hisab.app.data.local.dao.RecurringTransactionDao
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs once a day (via RecurringTransactionWorker) and once on app start (via
 * AppContainer). A due recurring entry becomes a REAL transaction through
 * RecordExpenseUseCase / RecordIncomeUseCase — exactly the same call a person
 * makes tapping "Save" by hand — so it obeys the same rules (duplicate check
 * skipped here on purpose: a recurring charge firing on schedule isn't a
 * duplicate, it's the whole point).
 */
class ExecuteRecurringTransactionsUseCase(
    private val recurringDao: RecurringTransactionDao,
    private val recordExpenseUseCase: RecordExpenseUseCase,
    private val recordIncomeUseCase: RecordIncomeUseCase
) {
    suspend operator fun invoke(userId: String) {
        val today = LocalDate.now().toEpochDay()
        val due = recurringDao.getDueForExecution(today).filter { it.userId == userId }
        for (recurring in due) {
            executeOne(userId, recurring)
            advance(recurring)
        }
    }

    private suspend fun executeOne(userId: String, recurring: RecurringTransactionEntity) {
        val dateMillis = LocalDate.ofEpochDay(recurring.nextRunEpochDay)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        when (TransactionType.valueOf(recurring.type)) {
            TransactionType.EXPENSE -> {
                val categoryId = recurring.categoryId ?: return
                recordExpenseUseCase(
                    userId, recurring.amountMinor, categoryId, recurring.accountId,
                    recurring.note, dateMillis, force = true
                )
            }
            TransactionType.INCOME -> recordIncomeUseCase(
                userId, recurring.amountMinor, recurring.accountId, recurring.categoryId,
                recurring.note, dateMillis, force = true
            )
            else -> Unit // only expense/income recurring entries are supported so far
        }
    }

    private suspend fun advance(recurring: RecurringTransactionEntity) {
        val next = nextRunDate(RecurrenceFrequency.valueOf(recurring.frequency), recurring.nextRunEpochDay)
        val stillActive = recurring.endDateEpochDay == null || next <= recurring.endDateEpochDay
        recurringDao.update(recurring.copy(nextRunEpochDay = next, isActive = stillActive))
    }

    private fun nextRunDate(frequency: RecurrenceFrequency, fromEpochDay: Long): Long {
        val date = LocalDate.ofEpochDay(fromEpochDay)
        return when (frequency) {
            RecurrenceFrequency.DAILY -> date.plusDays(1)
            RecurrenceFrequency.WEEKLY -> date.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> date.plusMonths(1)
            RecurrenceFrequency.YEARLY -> date.plusYears(1)
        }.toEpochDay()
    }
}
