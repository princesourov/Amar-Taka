package com.hisab.app.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.hisab.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.tasks.await

/** users/{userId}/... — matches firestore.rules at the project root exactly. */
object FirestorePaths {
    private const val USERS = "users"
    fun accounts(userId: String) = "$USERS/$userId/accounts"
    fun transactions(userId: String) = "$USERS/$userId/transactions"
    fun people(userId: String) = "$USERS/$userId/people"
    fun categories(userId: String) = "$USERS/$userId/categories"
    fun budgets(userId: String) = "$USERS/$userId/budgets"
    fun savingsGoals(userId: String) = "$USERS/$userId/savingsGoals"
    fun recurringTransactions(userId: String) = "$USERS/$userId/recurringTransactions"
    fun reminders(userId: String) = "$USERS/$userId/reminders"
}

/**
 * Phase 1 scaffold only — not called anywhere yet. Sketches the shape of
 * spec Section 50 (offline sync) for Phase 2/3:
 *  - On each local write, mark the row syncStatus = PENDING (already done —
 *    every use case in TransactionUseCases.kt sets this).
 *  - A WorkManager job pushes PENDING rows to Firestore when connectivity
 *    returns, then flips them to SYNCED.
 *  - A Firestore snapshot listener pulls remote changes and upserts into Room.
 *  - Conflict rule: last-write-wins by updatedAtMillis.
 * None of that loop is implemented yet — this class only has the one-way
 * push, as a starting point.
 */
class FirestoreSyncService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun pushTransaction(userId: String, transaction: TransactionEntity) {
        firestore.collection(FirestorePaths.transactions(userId))
            .document(transaction.id)
            .set(transaction)
            .await()
    }
}
