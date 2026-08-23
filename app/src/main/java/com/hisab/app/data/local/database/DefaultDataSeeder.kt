package com.hisab.app.data.local.database

import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.domain.model.AccountType
import com.hisab.app.domain.model.SyncStatus
import java.util.UUID

/**
 * Runs once per user, the first time the app opens with no accounts yet.
 * Creates the default accounts (spec Section 15) and default categories
 * (spec Section 13) with zero opening balances so Dashboard and Add Expense
 * are usable immediately instead of showing an empty shell.
 */
class DefaultDataSeeder(private val database: HisabDatabase) {

    suspend fun seedIfNeeded(userId: String) {
        val existing = database.accountDao().getActiveAccountsOnce(userId)
        if (existing.isNotEmpty()) return

        val now = System.currentTimeMillis()

        val defaultAccounts = listOf(
            "Pocket" to AccountType.POCKET,
            "Bank" to AccountType.BANK,
            "bKash" to AccountType.BKASH,
            "Nagad" to AccountType.NAGAD,
            "Rocket" to AccountType.ROCKET
        ).map { (name, type) ->
            AccountEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                name = name,
                type = type.name,
                iconKey = type.name.lowercase(),
                openingBalanceMinor = 0L,
                notes = null,
                isActive = true,
                createdAtMillis = now,
                syncStatus = SyncStatus.PENDING.name
            )
        }
        database.accountDao().insertAll(defaultAccounts)

        val defaultCategoryNames = listOf(
            "Food", "Transport", "Shopping", "Home", "Mobile", "Bills", "Education",
            "Medical", "Entertainment", "Snacks", "Clothing", "Technology", "Gift", "Loan", "Other"
        )
        val defaultCategories = defaultCategoryNames.map { name ->
            CategoryEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                name = name,
                iconKey = name.lowercase(),
                colorHex = null,
                monthlyBudgetMinor = null,
                isCustom = false,
                isActive = true
            )
        }
        database.categoryDao().insertAll(defaultCategories)
    }

    /**
     * Income-oriented categories didn't exist before the Add Money feature. Unlike
     * [seedIfNeeded], this runs on EVERY app start and is purely additive: it checks
     * existing category names first and only inserts ones that are missing, so it's
     * safe to call for both brand-new installs and existing ones with real transaction
     * history already in place — nothing existing is touched.
     */
    suspend fun seedIncomeCategoriesIfMissing(userId: String) {
        val existingNames = database.categoryDao().getActiveCategoriesOnce(userId)
            .map { it.name.lowercase() }
            .toSet()

        val incomeCategoryNames = listOf("Salary", "Business Income", "Gift Received", "Refund", "Other Income")
        val toAdd = incomeCategoryNames.filter { it.lowercase() !in existingNames }
        if (toAdd.isEmpty()) return

        database.categoryDao().insertAll(
            toAdd.map { name ->
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    name = name,
                    iconKey = name.lowercase().replace(" ", "_"),
                    colorHex = null,
                    monthlyBudgetMinor = null,
                    isCustom = false,
                    isActive = true
                )
            }
        )
    }
}
