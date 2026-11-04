package com.hisab.app.data.backup

import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.AttachmentEntity
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.GoalContributionEntity
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.ReminderEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.local.entity.UserProfileEntity

/*
 * Entity <-> JSON row mapping for backups. GENERATED-STYLE, explicit code (no reflection) so a renamed
 * column is a compile error here instead of a silently missing backup field.
 *
 * Reading is tolerant in one direction only: a column that is absent from the file but has a default
 * (columns added in later database versions) takes that default, so older backups stay restorable.
 * A REQUIRED column that is missing, or any column of the wrong type, is a format error.
 */

class BackupFormatException(message: String) : Exception(message)

/** One JSON object being read, with errors that say which table/row/field is wrong. */
class BackupRow(private val m: Map<String, Any?>, private val table: String, private val index: Int) {
    private fun fail(field: String, why: String): Nothing =
        throw BackupFormatException("$table[$index].$field: $why")

    fun string(f: String): String = when (val v = m[f]) { is String -> v; null -> fail(f, "is missing"); else -> fail(f, "must be text") }
    fun stringOr(f: String, d: String): String = when (val v = m[f]) { is String -> v; null -> d; else -> fail(f, "must be text") }
    fun stringOrNull(f: String): String? = when (val v = m[f]) { is String -> v; null -> null; else -> fail(f, "must be text") }
    fun long(f: String): Long = when (val v = m[f]) { is Long -> v; null -> fail(f, "is missing"); else -> fail(f, "must be a whole number") }
    fun longOr(f: String, d: Long): Long = when (val v = m[f]) { is Long -> v; null -> d; else -> fail(f, "must be a whole number") }
    fun longOrNull(f: String): Long? = when (val v = m[f]) { is Long -> v; null -> null; else -> fail(f, "must be a whole number") }
    fun intOrNull(f: String): Int? = when (val v = m[f]) { is Long -> v.toInt(); null -> null; else -> fail(f, "must be a whole number") }
    fun boolean(f: String): Boolean = when (val v = m[f]) { is Boolean -> v; null -> fail(f, "is missing"); else -> fail(f, "must be true/false") }
    fun booleanOr(f: String, d: Boolean): Boolean = when (val v = m[f]) { is Boolean -> v; null -> d; else -> fail(f, "must be true/false") }
}


fun UserProfileEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "userId" to userId,
    "displayName" to displayName,
    "email" to email,
    "currencyCode" to currencyCode,
    "dailySpendingTargetMinor" to dailySpendingTargetMinor,
    "monthlyBudgetMinor" to monthlyBudgetMinor,
    "onboardingCompleted" to onboardingCompleted,
    "createdAtMillis" to createdAtMillis
)

fun userprofileFromBackup(r: BackupRow) = UserProfileEntity(
    userId = r.string("userId"),
    displayName = r.string("displayName"),
    email = r.stringOrNull("email"),
    currencyCode = r.stringOr("currencyCode", "BDT"),
    dailySpendingTargetMinor = r.longOrNull("dailySpendingTargetMinor"),
    monthlyBudgetMinor = r.longOrNull("monthlyBudgetMinor"),
    onboardingCompleted = r.booleanOr("onboardingCompleted", false),
    createdAtMillis = r.long("createdAtMillis")
)


fun AccountEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "name" to name,
    "type" to type,
    "iconKey" to iconKey,
    "openingBalanceMinor" to openingBalanceMinor,
    "notes" to notes,
    "isActive" to isActive,
    "createdAtMillis" to createdAtMillis,
    "syncStatus" to syncStatus,
    "deletedAtMillis" to deletedAtMillis
)

fun accountFromBackup(r: BackupRow) = AccountEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    name = r.string("name"),
    type = r.string("type"),
    iconKey = r.string("iconKey"),
    openingBalanceMinor = r.long("openingBalanceMinor"),
    notes = r.stringOrNull("notes"),
    isActive = r.booleanOr("isActive", true),
    createdAtMillis = r.long("createdAtMillis"),
    syncStatus = r.stringOr("syncStatus", "PENDING"),
    deletedAtMillis = r.longOrNull("deletedAtMillis")
)


fun CategoryEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "name" to name,
    "iconKey" to iconKey,
    "colorHex" to colorHex,
    "monthlyBudgetMinor" to monthlyBudgetMinor,
    "isCustom" to isCustom,
    "isActive" to isActive,
    "kind" to kind,
    "deletedAtMillis" to deletedAtMillis
)

fun categoryFromBackup(r: BackupRow) = CategoryEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    name = r.string("name"),
    iconKey = r.string("iconKey"),
    colorHex = r.stringOrNull("colorHex"),
    monthlyBudgetMinor = r.longOrNull("monthlyBudgetMinor"),
    isCustom = r.booleanOr("isCustom", false),
    isActive = r.booleanOr("isActive", true),
    kind = r.stringOr("kind", "BOTH"),
    deletedAtMillis = r.longOrNull("deletedAtMillis")
)


fun PersonEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "name" to name,
    "phone" to phone,
    "email" to email,
    "address" to address,
    "photoUri" to photoUri,
    "notes" to notes,
    "isActive" to isActive,
    "createdAtMillis" to createdAtMillis,
    "syncStatus" to syncStatus,
    "deletedAtMillis" to deletedAtMillis
)

fun personFromBackup(r: BackupRow) = PersonEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    name = r.string("name"),
    phone = r.stringOrNull("phone"),
    email = r.stringOrNull("email"),
    address = r.stringOrNull("address"),
    photoUri = r.stringOrNull("photoUri"),
    notes = r.stringOrNull("notes"),
    isActive = r.booleanOr("isActive", true),
    createdAtMillis = r.long("createdAtMillis"),
    syncStatus = r.stringOr("syncStatus", "PENDING"),
    deletedAtMillis = r.longOrNull("deletedAtMillis")
)


fun TransactionEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "type" to type,
    "amountMinor" to amountMinor,
    "categoryId" to categoryId,
    "sourceAccountId" to sourceAccountId,
    "destinationAccountId" to destinationAccountId,
    "personId" to personId,
    "note" to note,
    "transactionDateMillis" to transactionDateMillis,
    "dueDateEpochDay" to dueDateEpochDay,
    "isSampleData" to isSampleData,
    "syncStatus" to syncStatus,
    "createdAtMillis" to createdAtMillis,
    "updatedAtMillis" to updatedAtMillis,
    "isDeleted" to isDeleted
)

fun transactionFromBackup(r: BackupRow) = TransactionEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    type = r.string("type"),
    amountMinor = r.long("amountMinor"),
    categoryId = r.stringOrNull("categoryId"),
    sourceAccountId = r.stringOrNull("sourceAccountId"),
    destinationAccountId = r.stringOrNull("destinationAccountId"),
    personId = r.stringOrNull("personId"),
    note = r.stringOrNull("note"),
    transactionDateMillis = r.long("transactionDateMillis"),
    dueDateEpochDay = r.longOrNull("dueDateEpochDay"),
    isSampleData = r.booleanOr("isSampleData", false),
    syncStatus = r.stringOr("syncStatus", "PENDING"),
    createdAtMillis = r.long("createdAtMillis"),
    updatedAtMillis = r.long("updatedAtMillis"),
    isDeleted = r.booleanOr("isDeleted", false)
)


fun BudgetEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "scope" to scope,
    "categoryId" to categoryId,
    "amountMinor" to amountMinor,
    "startDateEpochDay" to startDateEpochDay,
    "isActive" to isActive,
    "name" to name,
    "isDeleted" to isDeleted,
    "updatedAtMillis" to updatedAtMillis
)

fun budgetFromBackup(r: BackupRow) = BudgetEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    scope = r.string("scope"),
    categoryId = r.stringOrNull("categoryId"),
    amountMinor = r.long("amountMinor"),
    startDateEpochDay = r.longOrNull("startDateEpochDay"),
    isActive = r.booleanOr("isActive", true),
    name = r.stringOrNull("name"),
    isDeleted = r.booleanOr("isDeleted", false),
    updatedAtMillis = r.longOr("updatedAtMillis", 0L)
)


fun SavingsGoalEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "name" to name,
    "targetAmountMinor" to targetAmountMinor,
    "savedAmountMinor" to savedAmountMinor,
    "targetDateEpochDay" to targetDateEpochDay,
    "isActive" to isActive,
    "createdAtMillis" to createdAtMillis,
    "accountId" to accountId,
    "iconKey" to iconKey,
    "note" to note,
    "updatedAtMillis" to updatedAtMillis,
    "deletedAtMillis" to deletedAtMillis
)

fun savingsgoalFromBackup(r: BackupRow) = SavingsGoalEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    name = r.string("name"),
    targetAmountMinor = r.long("targetAmountMinor"),
    savedAmountMinor = r.longOr("savedAmountMinor", 0L),
    targetDateEpochDay = r.longOrNull("targetDateEpochDay"),
    isActive = r.booleanOr("isActive", true),
    createdAtMillis = r.long("createdAtMillis"),
    accountId = r.stringOrNull("accountId"),
    iconKey = r.stringOr("iconKey", "savings"),
    note = r.stringOrNull("note"),
    updatedAtMillis = r.longOr("updatedAtMillis", 0L),
    deletedAtMillis = r.longOrNull("deletedAtMillis")
)


fun GoalContributionEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "goalId" to goalId,
    "accountId" to accountId,
    "amountMinor" to amountMinor,
    "note" to note,
    "dateMillis" to dateMillis,
    "createdAtMillis" to createdAtMillis
)

fun goalcontributionFromBackup(r: BackupRow) = GoalContributionEntity(
    id = r.string("id"),
    goalId = r.string("goalId"),
    accountId = r.stringOrNull("accountId"),
    amountMinor = r.long("amountMinor"),
    note = r.stringOrNull("note"),
    dateMillis = r.long("dateMillis"),
    createdAtMillis = r.long("createdAtMillis")
)


fun RecurringTransactionEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "type" to type,
    "amountMinor" to amountMinor,
    "categoryId" to categoryId,
    "accountId" to accountId,
    "personId" to personId,
    "frequency" to frequency,
    "startDateEpochDay" to startDateEpochDay,
    "endDateEpochDay" to endDateEpochDay,
    "nextRunEpochDay" to nextRunEpochDay,
    "note" to note,
    "isActive" to isActive
)

fun recurringtransactionFromBackup(r: BackupRow) = RecurringTransactionEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    type = r.string("type"),
    amountMinor = r.long("amountMinor"),
    categoryId = r.stringOrNull("categoryId"),
    accountId = r.string("accountId"),
    personId = r.stringOrNull("personId"),
    frequency = r.string("frequency"),
    startDateEpochDay = r.long("startDateEpochDay"),
    endDateEpochDay = r.longOrNull("endDateEpochDay"),
    nextRunEpochDay = r.long("nextRunEpochDay"),
    note = r.stringOrNull("note"),
    isActive = r.booleanOr("isActive", true)
)


fun ReminderEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "userId" to userId,
    "relatedTransactionId" to relatedTransactionId,
    "relatedPersonId" to relatedPersonId,
    "title" to title,
    "message" to message,
    "triggerAtMillis" to triggerAtMillis,
    "daysBeforeDue" to daysBeforeDue,
    "isEnabled" to isEnabled,
    "isSent" to isSent
)

fun reminderFromBackup(r: BackupRow) = ReminderEntity(
    id = r.string("id"),
    userId = r.string("userId"),
    relatedTransactionId = r.stringOrNull("relatedTransactionId"),
    relatedPersonId = r.stringOrNull("relatedPersonId"),
    title = r.string("title"),
    message = r.string("message"),
    triggerAtMillis = r.long("triggerAtMillis"),
    daysBeforeDue = r.intOrNull("daysBeforeDue"),
    isEnabled = r.booleanOr("isEnabled", true),
    isSent = r.booleanOr("isSent", false)
)


fun AttachmentEntity.toBackupJson(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "transactionId" to transactionId,
    "uriString" to uriString,
    "type" to type,
    "createdAtMillis" to createdAtMillis
)

fun attachmentFromBackup(r: BackupRow) = AttachmentEntity(
    id = r.string("id"),
    transactionId = r.string("transactionId"),
    uriString = r.string("uriString"),
    type = r.string("type"),
    createdAtMillis = r.long("createdAtMillis")
)
