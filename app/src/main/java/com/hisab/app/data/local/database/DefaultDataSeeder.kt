package com.hisab.app.data.local.database

import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.domain.category.CategoryCatalog
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
        // "First run" means the user has never had any account/category — counting INACTIVE rows too,
        // otherwise deleting every account would make the next launch re-seed duplicates.
        val seedAccounts = database.accountDao().countAll(userId) == 0
        val seedCategories = database.categoryDao().getAllCategoriesOnce(userId).isEmpty()
        if (!seedAccounts && !seedCategories) return

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
        if (seedAccounts) database.accountDao().insertAll(defaultAccounts)

        val defaultCategoryNames = listOf(
            "Food",
            "Snacks",
            "Transport",
            "University Transport",
            "University Adda",
            "Education",
            "Shopping",
            "Clothing",
            "Mobile & Internet",
            "Bills",
            "Home",
            "Medical",
            "Personal Care",
            "Entertainment",
            "Relationship",
            "Gift",
            "Family",
            "Salary",
            "Freelancing",
            "Business",
            "Development",
            "Family Support",
            "Loan",
            "Savings",
            "Other"
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
        if (seedCategories) database.categoryDao().insertAll(defaultCategories)
    }

    /**
     * Adds any default categories (see CategoryCatalog) the user doesn't already have. Purely
     * additive and idempotent: it compares against EVERY existing category (including deactivated
     * ones, so a default the user deleted is not resurrected) by normalised name + aliases, and
     * never updates or removes an existing row.
     */
    suspend fun seedMissingDefaultCategories(userId: String) {
        val existingNames = database.categoryDao().getAllCategoriesOnce(userId).map { it.name }
        val toAdd = CategoryCatalog.missing(existingNames)
        if (toAdd.isEmpty()) return
        database.categoryDao().insertAll(
            toAdd.map { c ->
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    name = c.name,
                    iconKey = c.iconKey,
                    colorHex = c.colorHex,
                    monthlyBudgetMinor = null,
                    isCustom = false,
                    isActive = true,
                    kind = c.kind.name
                )
            }
        )
    }
}
