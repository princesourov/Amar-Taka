package com.hisab.app.domain

import com.hisab.app.data.local.dao.AccountDao
import com.hisab.app.data.local.dao.RecurringTransactionDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.database.TransactionRunner
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.domain.usecase.ExecuteRecurringTransactionsUseCase
import com.hisab.app.domain.usecase.SkipReason
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.time.LocalDate

/** In-memory stand-in for the three DAOs + a runner that rolls everything back when its block throws. */
private class World {
    val schedules = mutableListOf<RecurringTransactionEntity>()
    val txns = linkedMapOf<String, TransactionEntity>()
    val accounts = mutableMapOf<String, AccountEntity>()
    var failOnInsertNumber: Int? = null
    private var insertCalls = 0

    inline fun <reified T : Any> fake(crossinline handle: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java), InvocationHandler { _, m, args ->
            val a = args ?: emptyArray()
            if (m.name == "toString") "fake" else handle(m.name, a)
        }) as T

    val recurringDao: RecurringTransactionDao = fake { name, a ->
        when (name) {
            "getDueForExecution" -> schedules.filter { it.isActive && it.nextRunEpochDay <= (a[0] as Long) }
            "update" -> { val e = a[0] as RecurringTransactionEntity; schedules.replaceAll { if (it.id == e.id) e else it }; Unit }
            else -> error("unexpected $name")
        }
    }
    val transactionDao: TransactionDao = fake { name, a ->
        when (name) {
            "insertIfAbsent" -> {
                insertCalls++
                if (failOnInsertNumber == insertCalls) throw IllegalStateException("simulated crash")
                val t = a[0] as TransactionEntity
                if (txns.containsKey(t.id)) -1L else { txns[t.id] = t; 1L }
            }
            else -> error("unexpected $name")
        }
    }
    val accountDao: AccountDao = fake { name, a ->
        when (name) { "getById" -> accounts[a[0] as String]; else -> error("unexpected $name") }
    }
    val runner = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R {
            val s = schedules.toList(); val t = LinkedHashMap(txns)
            return try { block() } catch (e: Throwable) {
                schedules.clear(); schedules.addAll(s); txns.clear(); txns.putAll(t); throw e
            }
        }
    }
    val useCase get() = ExecuteRecurringTransactionsUseCase(runner, recurringDao, transactionDao, accountDao)

    fun account(id: String = "a1", active: Boolean = true) {
        accounts[id] = AccountEntity(id, "u", "Pocket", "POCKET", "pocket", 0, null, active, 1L, "PENDING")
    }
    fun schedule(id: String = "s1", freq: String = "DAILY", next: Long, start: Long = next, active: Boolean = true, amount: Long = 500, account: String = "a1") {
        schedules += RecurringTransactionEntity(id, "u", "EXPENSE", amount, "c1", account, null, freq, start, null, next, "rent", active)
    }
}

class RecurringExecutionTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val day = today.toEpochDay()

    @Test fun catchUpCreatesOneRowPerMissedDayAndIsIdempotent() = runBlocking {
        val w = World().apply { account(); schedule(next = day - 4) }
        val first = w.useCase("u", today)
        assertEquals(5, first)
        assertEquals(5, w.txns.size)
        assertEquals(day + 1, w.schedules.single().nextRunEpochDay)
        assertEquals(0, w.useCase("u", today))
        assertEquals(5, w.txns.size)
    }

    @Test fun missedMonthsAreCaughtUpOnce() = runBlocking {
        val start = LocalDate.of(2026, 8, 4).toEpochDay()
        val w = World().apply { account(); schedule(freq = "MONTHLY", next = start, start = start) }
        assertEquals(4, w.useCase("u", today)) // Aug, Sep, Oct, Nov
        assertEquals(0, w.useCase("u", today))
        assertEquals(4, w.txns.values.map { it.id }.toSet().size)
    }

    @Test fun crashMidBatchRollsBackEverythingThenRetryCompletes() = runBlocking {
        val w = World().apply { account(); schedule(next = day - 4); failOnInsertNumber = 3 }
        try { w.useCase("u", today); org.junit.Assert.fail("should have thrown") } catch (e: IllegalStateException) { }
        assertEquals("no partial rows", 0, w.txns.size)
        assertEquals("schedule not advanced", day - 4, w.schedules.single().nextRunEpochDay)
        w.failOnInsertNumber = null
        assertEquals(5, w.useCase("u", today))
        assertEquals(5, w.txns.size)
    }

    @Test fun concurrentRunsDoNotDoubleCreate() = runBlocking {
        val w = World().apply { account(); schedule(next = day - 9) }
        val total = (1..8).map { async { w.useCase("u", today) } }.awaitAll().sum()
        assertEquals(10, total)
        assertEquals(10, w.txns.size)
    }

    @Test fun pausedScheduleIsUntouchedAndCatchesUpOnResume() = runBlocking {
        val w = World().apply { account(); schedule(next = day - 2, active = false) }
        assertEquals(0, w.useCase("u", today)); assertEquals(0, w.txns.size)
        w.schedules.replaceAll { it.copy(isActive = true) }
        assertEquals(3, w.useCase("u", today))
    }

    @Test fun archivedOrMissingAccountSkipsWithoutAdvancing() = runBlocking {
        val w = World().apply { account("a1", active = false); schedule("s1", next = day - 1); schedule("s2", next = day - 1, account = "ghost") }
        val r = w.useCase.run("u", today)
        assertEquals(0, r.created)
        assertEquals(setOf("s1" to SkipReason.ACCOUNT_MISSING_OR_ARCHIVED, "s2" to SkipReason.ACCOUNT_MISSING_OR_ARCHIVED), r.skipped.map { it.scheduleId to it.reason }.toSet())
        assertTrue(w.schedules.all { it.nextRunEpochDay == day - 1 })
        w.account("a1", active = true)
        assertEquals(2, w.useCase("u", today)) // retried once the account is back
    }

    @Test fun invalidAmountIsReportedNotGenerated() = runBlocking {
        val w = World().apply { account(); schedule(next = day - 1, amount = 0) }
        val r = w.useCase.run("u", today)
        assertEquals(0, r.created); assertEquals(SkipReason.INVALID_AMOUNT, r.skipped.single().reason)
    }

    @Test fun otherUsersSchedulesAreIgnored() = runBlocking {
        val w = World().apply { account(); schedule(next = day - 1) }
        assertEquals(0, w.useCase("someone-else", today))
    }
}
