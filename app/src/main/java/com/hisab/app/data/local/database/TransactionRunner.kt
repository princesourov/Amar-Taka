package com.hisab.app.data.local.database

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Runs a block as ONE atomic database transaction (all writes commit together or none do).
 * An interface so repositories and use cases can be unit-tested with a fake that emulates rollback.
 */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = database.withTransaction(block)
}
