package com.hisab.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hisab.app.data.local.dao.AccountDao
import com.hisab.app.data.local.dao.BudgetDao
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.dao.PersonDao
import com.hisab.app.data.local.dao.RecurringTransactionDao
import com.hisab.app.data.local.dao.ReminderDao
import com.hisab.app.data.local.dao.SavingsGoalDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.AttachmentEntity
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.ReminderEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class, AccountEntity::class, CategoryEntity::class, PersonEntity::class,
        TransactionEntity::class, BudgetEntity::class, SavingsGoalEntity::class,
        RecurringTransactionEntity::class, ReminderEntity::class, AttachmentEntity::class
    ],
    version = 1,
    // Turned off for Phase 1 to avoid needing a version-controlled schema directory.
    // Turn this on (and set room { schemaDirectory(...) } in build.gradle.kts) once
    // you start writing real migrations instead of just reinstalling on schema changes.
    exportSchema = false
)
abstract class HisabDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var INSTANCE: HisabDatabase? = null

        fun getInstance(context: Context): HisabDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HisabDatabase::class.java,
                    "hisab.db"
                ).build().also { INSTANCE = it }
            }
    }
}
