package com.hisab.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.hisab.app.data.local.database.HisabDatabase
import com.hisab.app.data.local.database.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * v1 -> v2 must keep every row. NOT YET EXECUTED: needs an emulator/device and BOTH exported schemas
 * (app/schemas/.../1.json from building the original v1 project, 2.json from this build).
 * See CHANGES.md "Manual steps".
 */
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HisabDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_keepsExistingBudgetsAndCategories() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO budgets (id, userId, scope, categoryId, amountMinor, startDateEpochDay, isActive) VALUES ('b1','u','MONTHLY','c1',500000,NULL,0)")
            execSQL("INSERT INTO categories (id, userId, name, iconKey, colorHex, monthlyBudgetMinor, isCustom, isActive) VALUES ('c1','u','Food','food',NULL,NULL,0,1)")
            execSQL("INSERT INTO categories (id, userId, name, iconKey, colorHex, monthlyBudgetMinor, isCustom, isActive) VALUES ('c2','u','Business Income','x',NULL,NULL,0,1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        db.query("SELECT amountMinor, isActive, isDeleted, name FROM budgets WHERE id='b1'").use {
            assertTrue(it.moveToFirst())
            assertEquals(500000L, it.getLong(0))
            assertEquals(0, it.getInt(1))          // a paused budget stays paused...
            assertEquals(0, it.getInt(2))          // ...and is not suddenly in Trash
            assertTrue(it.isNull(3))
        }
        db.query("SELECT kind FROM categories WHERE id='c1'").use { it.moveToFirst(); assertEquals("BOTH", it.getString(0)) }
        db.query("SELECT kind FROM categories WHERE id='c2'").use { it.moveToFirst(); assertEquals("INCOME", it.getString(0)) }
    }

    private companion object { const val DB = "migration-test" }
}
