package com.hisab.app.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hisab.app.HisabApplication

class RecurringTransactionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as HisabApplication
            app.container.executeRecurringTransactionsUseCase(app.container.userId)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
