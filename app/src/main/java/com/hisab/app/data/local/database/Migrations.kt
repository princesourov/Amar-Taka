package com.hisab.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hisab.app.domain.category.CategoryCatalog

/**
 * v1 -> v2. Purely additive: nothing is dropped, rewritten or recreated, so every existing
 * account, transaction, person, category and budget survives untouched.
 *
 *  - budgets.name, budgets.isDeleted, budgets.updatedAtMillis: budget names, a real Trash state
 *    (before this, "delete" only paused a budget, so existing paused budgets stay paused), and an
 *    audit timestamp.
 *  - categories.kind: EXPENSE / INCOME / BOTH. Existing rows become BOTH so every picker keeps
 *    showing them; only the categories the app itself seeded for income-only use become INCOME.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (statement in migrationStatements1To2()) db.execSQL(statement)
    }
}

/** Exposed separately so the exact SQL can be verified (and tested) without an Android database. */
fun migrationStatements1To2(): List<String> {
    val incomeOnly = CategoryCatalog.legacyIncomeOnlyNames.joinToString(",") { "'" + it.replace("'", "''") + "'" }
    return listOf(
        "ALTER TABLE budgets ADD COLUMN name TEXT",
        "ALTER TABLE budgets ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE budgets ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE categories ADD COLUMN kind TEXT NOT NULL DEFAULT 'BOTH'",
        "UPDATE categories SET kind = 'INCOME' WHERE name IN ($incomeOnly)"
    )
}


/**
 * v2 -> v3. Purely additive again (no table is dropped, rebuilt or emptied):
 *
 *  - accounts / categories / people .deletedAtMillis: when the row was moved to Trash (nullable;
 *    rows that were already hidden keep null = "date not recorded").
 *  - savings_goals: accountId, iconKey, note, updatedAtMillis, deletedAtMillis.
 *  - goal_contributions: the new allocation ledger. Every goal that had a saved amount gets ONE
 *    carried-over row (accountId NULL) so its saved total is identical before and after the
 *    upgrade. The old savings_goals.savedAmountMinor column is left untouched (just no longer read).
 *
 * No foreign keys are declared: the rest of the schema has none either, and a mismatch between a
 * hand-written migration and Room's expected FK definition would stop the app opening at all.
 * Integrity is enforced inside Room transactions in the repositories instead (see GoalDao.purge).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (statement in migrationStatements2To3()) db.execSQL(statement)
    }
}

fun migrationStatements2To3(): List<String> = listOf(
    "ALTER TABLE accounts ADD COLUMN deletedAtMillis INTEGER",
    "ALTER TABLE categories ADD COLUMN deletedAtMillis INTEGER",
    "ALTER TABLE people ADD COLUMN deletedAtMillis INTEGER",
    "ALTER TABLE savings_goals ADD COLUMN accountId TEXT",
    "ALTER TABLE savings_goals ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'savings'",
    "ALTER TABLE savings_goals ADD COLUMN note TEXT",
    "ALTER TABLE savings_goals ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE savings_goals ADD COLUMN deletedAtMillis INTEGER",
    "CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, " +
        "`accountId` TEXT, `amountMinor` INTEGER NOT NULL, `note` TEXT, `dateMillis` INTEGER NOT NULL, " +
        "`createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)",
    "CREATE INDEX IF NOT EXISTS `index_goal_contributions_accountId` ON `goal_contributions` (`accountId`)",
    "UPDATE savings_goals SET updatedAtMillis = createdAtMillis",
    "INSERT INTO goal_contributions (id, goalId, accountId, amountMinor, note, dateMillis, createdAtMillis) " +
        "SELECT 'legacy-' || id, id, NULL, savedAmountMinor, 'Carried over from the previous version', " +
        "createdAtMillis, createdAtMillis FROM savings_goals WHERE savedAmountMinor > 0"
)
