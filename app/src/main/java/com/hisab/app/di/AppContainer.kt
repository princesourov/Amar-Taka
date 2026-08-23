package com.hisab.app.di

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hisab.app.data.local.database.DefaultDataSeeder
import com.hisab.app.data.local.database.HisabDatabase
import com.hisab.app.data.remote.AuthRepository
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.RecurringTransactionRepository
import com.hisab.app.data.repository.SavingsGoalRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.usecase.BorrowMoneyUseCase
import com.hisab.app.domain.usecase.DeleteTransactionUseCase
import com.hisab.app.domain.usecase.EditTransactionUseCase
import com.hisab.app.domain.usecase.ExecuteRecurringTransactionsUseCase
import com.hisab.app.domain.usecase.LendMoneyUseCase
import com.hisab.app.domain.usecase.RecordExpenseUseCase
import com.hisab.app.domain.usecase.RecordIncomeUseCase
import com.hisab.app.domain.usecase.RecordRepaymentMadeUseCase
import com.hisab.app.domain.usecase.RecordRepaymentReceivedUseCase
import com.hisab.app.domain.usecase.TransferMoneyUseCase
import com.hisab.app.utils.LocalUserProvider
import com.hisab.app.utils.RecurringTransactionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Deliberately NOT Hilt/Dagger. Annotation-processor-generated DI is hard to
 * eyeball for correctness without a compiler to check it, and this whole
 * project hasn't been build-verified yet — so Phase 1 uses a plain manual
 * container instead, which is easy to read and reason about by eye. Moving
 * to Hilt later is a reasonable Phase 3+ refinement once there's a test
 * suite to catch DI mistakes.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = HisabDatabase.getInstance(appContext)
    private val localUserProvider = LocalUserProvider(appContext)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userId: String get() = localUserProvider.userId

    val accountRepository by lazy { AccountRepository(database.accountDao(), database.transactionDao()) }
    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }
    val personRepository by lazy { PersonRepository(database.personDao(), database.transactionDao()) }
    val categoryDao by lazy { database.categoryDao() }
    val budgetRepository by lazy { BudgetRepository(database.budgetDao(), database.transactionDao()) }
    val savingsGoalRepository by lazy { SavingsGoalRepository(database.savingsGoalDao()) }
    val recurringTransactionRepository by lazy { RecurringTransactionRepository(database.recurringTransactionDao()) }

    val recordExpenseUseCase by lazy { RecordExpenseUseCase(transactionRepository) }
    val recordIncomeUseCase by lazy { RecordIncomeUseCase(transactionRepository) }
    val transferMoneyUseCase by lazy { TransferMoneyUseCase(transactionRepository) }
    val lendMoneyUseCase by lazy { LendMoneyUseCase(transactionRepository) }
    val borrowMoneyUseCase by lazy { BorrowMoneyUseCase(transactionRepository) }
    val recordRepaymentReceivedUseCase by lazy { RecordRepaymentReceivedUseCase(transactionRepository) }
    val recordRepaymentMadeUseCase by lazy { RecordRepaymentMadeUseCase(transactionRepository) }
    val editTransactionUseCase by lazy { EditTransactionUseCase(transactionRepository) }
    val deleteTransactionUseCase by lazy { DeleteTransactionUseCase(transactionRepository) }
    val executeRecurringTransactionsUseCase by lazy {
        ExecuteRecurringTransactionsUseCase(database.recurringTransactionDao(), recordExpenseUseCase, recordIncomeUseCase)
    }

    val authRepository by lazy { AuthRepository() }

    private val seeder by lazy { DefaultDataSeeder(database) }

    init {
        applicationScope.launch {
            seeder.seedIfNeeded(userId)
            seeder.seedIncomeCategoriesIfMissing(userId) // additive + idempotent, safe for existing installs too
            executeRecurringTransactionsUseCase(userId) // catch up immediately, don't wait for the daily job
        }
        scheduleRecurringTransactionWorker()
    }

    private fun scheduleRecurringTransactionWorker() {
        val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "recurring_transactions", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
