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

    @Query("UPDATE accounts SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}

@Dao
interface CategoryDao {
    @Insert suspend fun insert(category: CategoryEntity)
    @Insert suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    fun getActiveCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    suspend fun getActiveCategoriesOnce(userId: String): List<CategoryEntity>
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

    @Query("UPDATE people SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
