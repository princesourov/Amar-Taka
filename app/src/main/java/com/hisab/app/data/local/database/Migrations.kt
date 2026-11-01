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
