package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert suspend fun insert(account: AccountEntity)
    @Insert suspend fun insertAll(accounts: List<AccountEntity>)
    @Update suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE userId = :userId AND isActive = 1 ORDER BY createdAtMillis ASC")
    fun getActiveAccounts(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId AND isActive = 1 ORDER BY createdAtMillis ASC")
    suspend fun getActiveAccountsOnce(userId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    /** Counts inactive accounts too — "first run" must not be re-triggered by deleting every account. */
    @Query("SELECT COUNT(*) FROM accounts WHERE userId = :userId")
    suspend fun countAll(userId: String): Int

    @Query("UPDATE accounts SET isActive = 0, deletedAtMillis = :nowMillis WHERE id = :id")
    suspend fun deactivate(id: String, nowMillis: Long)

    @Query("UPDATE accounts SET isActive = 1, deletedAtMillis = NULL WHERE id = :id")
    suspend fun reactivate(id: String)

    @Query("SELECT * FROM accounts WHERE userId = :userId")
    fun getAllAccounts(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId AND isActive = 0 ORDER BY deletedAtMillis DESC")
    fun getDeletedAccounts(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId AND isActive = 0")
    suspend fun getDeletedAccountsOnce(userId: String): List<AccountEntity>

    /** Only ever removes an account that is already in Trash. */
    @Query("DELETE FROM accounts WHERE id = :id AND isActive = 0")
    suspend fun purgeTrashed(id: String)
}

@Dao
interface CategoryDao {
    @Insert suspend fun insert(category: CategoryEntity)
    @Insert suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    fun getActiveCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    suspend fun getActiveCategoriesOnce(userId: String): List<CategoryEntity>

    /** Active categories of the given kinds, e.g. listOf("EXPENSE", "BOTH") for the Add Expense picker. */
    @Query("SELECT * FROM categories WHERE userId = :userId AND isActive = 1 AND kind IN (:kinds) ORDER BY name ASC")
    fun getActiveCategoriesByKinds(userId: String, kinds: List<String>): Flow<List<CategoryEntity>>

    /** Including deactivated ones: history must still resolve names/colours of categories the user later deleted. */
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    fun getAllCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE userId = :userId")
    suspend fun getAllCategoriesOnce(userId: String): List<CategoryEntity>

    @Update suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET isActive = :active, deletedAtMillis = :deletedAtMillis WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, deletedAtMillis: Long?)

    @Query("SELECT * FROM categories WHERE userId = :userId AND isActive = 0 ORDER BY deletedAtMillis DESC, name ASC")
    fun getDeletedCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE userId = :userId AND isActive = 0")
    suspend fun getDeletedCategoriesOnce(userId: String): List<CategoryEntity>

    /** Only ever removes a category that is already in Trash. */
    @Query("DELETE FROM categories WHERE id = :id AND isActive = 0")
    suspend fun purgeTrashed(id: String)

    @Query("SELECT COUNT(*) FROM budgets WHERE categoryId = :id")
    suspend fun countBudgetsUsing(id: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id AND isDeleted = 0")
    suspend fun countTransactionsUsing(id: String): Int

}

@Dao
interface PersonDao {
    @Insert suspend fun insert(person: PersonEntity)
    @Update suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM people WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    fun getActivePeople(userId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM people WHERE id = :id")
    fun observeById(id: String): Flow<PersonEntity?>

    @Query("UPDATE people SET isActive = 0, deletedAtMillis = :nowMillis WHERE id = :id")
    suspend fun deactivate(id: String, nowMillis: Long)

    @Query("UPDATE people SET isActive = 1, deletedAtMillis = NULL WHERE id = :id")
    suspend fun reactivate(id: String)

    @Query("SELECT * FROM people WHERE userId = :userId")
    fun getAllPeople(userId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE userId = :userId AND isActive = 0 ORDER BY deletedAtMillis DESC")
    fun getDeletedPeople(userId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE userId = :userId AND isActive = 0")
    suspend fun getDeletedPeopleOnce(userId: String): List<PersonEntity>

    /** Only ever removes a person that is already in Trash. */
    @Query("DELETE FROM people WHERE id = :id AND isActive = 0")
    suspend fun purgeTrashed(id: String)
}
