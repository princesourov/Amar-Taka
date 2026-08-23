package com.hisab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String? = null,
    val currencyCode: String = "BDT",
    val dailySpendingTargetMinor: Long? = null,
    val monthlyBudgetMinor: Long? = null,
    val onboardingCompleted: Boolean = false,
    val createdAtMillis: Long
)

/**
 * openingBalanceMinor is the only balance value actually stored here. Current
 * balance is never written to a column — it's always derived live from the
 * transaction ledger (see AccountRepository.getCurrentBalance), matching spec
 * Section 48: "Do not store unnecessary calculated balances as the primary
 * source of truth."
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val type: String, // AccountType enum name
    val iconKey: String,
    val openingBalanceMinor: Long,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAtMillis: Long,
    val syncStatus: String
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val iconKey: String,
    val colorHex: String? = null,
    val monthlyBudgetMinor: Long? = null,
    val isCustom: Boolean = false,
    val isActive: Boolean = true
)

/**
 * Totals (given/received/borrowed/repaid) are intentionally NOT columns here —
 * same reasoning as AccountEntity's balance. They're computed from the
 * transaction ledger by personId (see TransactionDao.getNetBalanceForPerson).
 */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val photoUri: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAtMillis: Long,
    val syncStatus: String
)
