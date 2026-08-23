package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.SavingsGoalDao
import com.hisab.app.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Phase 2 keeps goal contributions as their own soft-tracked total
 * (SavingsGoalEntity.savedAmountMinor), NOT a transaction against a real
 * account. That's a deliberate scope decision: tying a contribution to a
 * specific account would mean deciding whether it counts as an expense in
 * reports, which the spec doesn't settle either way. A goal here is a target
 * you track, not money that's automatically locked away — see the README for
 * how to evolve this into real money movement if you want stricter tracking.
 */
class SavingsGoalRepository(private val savingsGoalDao: SavingsGoalDao) {

    fun getActiveGoals(userId: String): Flow<List<SavingsGoalEntity>> = savingsGoalDao.getActiveGoals(userId)

    suspend fun createGoal(
        userId: String,
        name: String,
        targetAmountMinor: Long,
        targetDateEpochDay: Long? = null
    ): String {
        val id = UUID.randomUUID().toString()
        savingsGoalDao.insert(
            SavingsGoalEntity(
                id = id, userId = userId, name = name, targetAmountMinor = targetAmountMinor,
                savedAmountMinor = 0L, targetDateEpochDay = targetDateEpochDay, isActive = true,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun contribute(goalId: String, amountMinor: Long) {
        require(amountMinor > 0) { "Contribution must be greater than zero" }
        savingsGoalDao.addToSaved(goalId, amountMinor)
    }
}
