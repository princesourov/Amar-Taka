package com.hisab.app.di

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.appcompat.app.AppCompatDelegate
import com.hisab.app.data.backup.BackupPreferences
import com.hisab.app.data.local.database.DefaultDataSeeder
import com.hisab.app.data.local.database.RoomTransactionRunner
import com.hisab.app.data.repository.BackupPreferenceStore
import com.hisab.app.data.repository.BackupRepository
import com.hisab.app.utils.CurrencyConfig
import com.hisab.app.utils.UserPreferences
import com.hisab.app.data.local.database.HisabDatabase
import com.hisab.app.data.remote.AuthRepository
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.RecurringTransactionRepository
import com.hisab.app.data.repository.SavingsGoalRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.data.repository.TrashRepository
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

    private val transactionRunner by lazy { RoomTransactionRunner(database) }

    val accountRepository by lazy {
        AccountRepository(database.accountDao(), database.transactionDao(), database.savingsGoalDao(), database.recurringTransactionDao())
    }
    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }
    val personRepository by lazy { PersonRepository(database.personDao(), database.transactionDao()) }
    val categoryDao by lazy { database.categoryDao() }
    val budgetRepository by lazy { BudgetRepository(database.budgetDao(), database.transactionDao()) }
    val trashRepository by lazy {
        TrashRepository(
            database.trashDao(), database.transactionDao(), database.budgetDao(), database.savingsGoalDao(),
            database.accountDao(), database.categoryDao(), database.personDao(), database.recurringTransactionDao(),
            transactionRunner
        )
    }
    val savingsGoalRepository by lazy {
        SavingsGoalRepository(
            database.savingsGoalDao(), database.transactionDao(), database.accountDao(), accountRepository, transactionRunner
        )
    }
    val recurringTransactionRepository by lazy {
        RecurringTransactionRepository(database.recurringTransactionDao(), database.transactionDao())
    }

    private val userPreferences by lazy { UserPreferences(appContext) }
    val backupRepository by lazy {
        BackupRepository(
            database.backupDao(),
            object : BackupPreferenceStore {
                override fun read() = BackupPreferences(
                    userPreferences.currencySymbol, userPreferences.themeMode, userPreferences.notificationsEnabled
                )
                override fun apply(preferences: BackupPreferences) {
                    userPreferences.currencySymbol = preferences.currencySymbol
                    userPreferences.themeMode = preferences.themeMode
                    userPreferences.notificationsEnabled = preferences.notificationsEnabled
                    CurrencyConfig.symbol = preferences.currencySymbol
                    AppCompatDelegate.setDefaultNightMode(preferences.themeMode)
                }
            },
            { userId }
        )
    }

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
        ExecuteRecurringTransactionsUseCase(
            transactionRunner, database.recurringTransactionDao(), database.transactionDao(), database.accountDao()
        )
    }

    val authRepository by lazy { AuthRepository() }

    private val seeder by lazy { DefaultDataSeeder(database) }

    init {
        // Re-apply saved display preferences (also what a restored backup carries).
        CurrencyConfig.symbol = userPreferences.currencySymbol
        AppCompatDelegate.setDefaultNightMode(userPreferences.themeMode)
        applicationScope.launch {
            seeder.seedIfNeeded(userId)
            seeder.seedMissingDefaultCategories(userId) // additive + idempotent, safe for existing installs too
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
