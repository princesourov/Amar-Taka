package com.hisab.app.data

import com.hisab.app.data.backup.BackupCodec
import com.hisab.app.data.backup.BackupCrypto
import com.hisab.app.data.backup.BackupData
import com.hisab.app.data.backup.BackupPreferences
import com.hisab.app.data.backup.MiniJson
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.GoalContributionEntity
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.repository.BackupPreferenceStore
import com.hisab.app.data.repository.BackupRepository
import com.hisab.app.data.repository.BackupStore
import com.hisab.app.data.repository.InspectResult
import com.hisab.app.data.repository.RestoreResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

private fun sample(userId: String = "u1"): BackupData = BackupData(
    accounts = listOf(
        AccountEntity("a1", userId, "Pocket", "POCKET", "pocket", 100_000, "n", true, 1L, "PENDING"),
        AccountEntity("a2", userId, "Old \"Bank\" ৳", "BANK", "bank", 0, null, false, 2L, "PENDING", deletedAtMillis = 99L)
    ),
    categories = listOf(CategoryEntity("c1", userId, "Food", "food", "#fff", null, false, true, "EXPENSE")),
    people = listOf(PersonEntity("p1", userId, "Rahim", "017", null, null, null, "line1\nline2", true, 3L, "PENDING")),
    transactions = listOf(
        TransactionEntity("t1", userId, "EXPENSE", 5_000, "c1", "a1", null, null, "lunch", 10L, null, false, "PENDING", 10L, 10L, false),
        TransactionEntity("t2", userId, "LENDING", 20_000, null, "a1", null, "p1", null, 11L, 77L, false, "PENDING", 11L, 11L, false),
        TransactionEntity("t3", userId, "EXPENSE", 1, null, "a1", null, null, null, 12L, null, false, "PENDING", 12L, 13L, true)
    ),
    budgets = listOf(BudgetEntity("b1", userId, "MONTHLY", "c1", 300_000, null, true, "Food", false, 5L)),
    goals = listOf(SavingsGoalEntity("g1", userId, "Phone", 5_000_000, 0, 123L, true, 4L, "a1", "📱", "note", 4L, null)),
    contributions = listOf(GoalContributionEntity("gc1", "g1", "a1", 1_000_000, null, 5L, 5L)),
    recurring = listOf(RecurringTransactionEntity("r1", userId, "EXPENSE", 100, "c1", "a1", null, "MONTHLY", 1L, null, 2L, null, true)),
    preferences = BackupPreferences("৳", 2, false)
)

class BackupCodecTest {
    @Test fun roundTripPreservesEveryRowIncludingTrashedOnes() {
        val d = sample()
        val text = BackupCodec.encode(d, 1_700_000_000_000, "1.0")
        val r = BackupCodec.decode(text) as BackupCodec.Result.Ok
        assertEquals(d, r.parsed.data)
        assertEquals(3L, r.parsed.header.schemaVersion)
        assertEquals(1_700_000_000_000, r.parsed.header.createdAtMillis)
        assertEquals(3L, r.parsed.header.counts["transactions"])
        assertTrue(r.parsed.data.transactions.single { it.id == "t3" }.isDeleted)
        assertEquals(99L, r.parsed.data.accounts.single { it.id == "a2" }.deletedAtMillis)
    }

    @Test fun emptyDatabaseBacksUpAndRestores() {
        val text = BackupCodec.encode(BackupData(), 1L, "1")
        assertEquals(BackupData(), (BackupCodec.decode(text) as BackupCodec.Result.Ok).parsed.data)
    }

    @Test fun truncatedFileIsRejected() {
        val text = BackupCodec.encode(sample(), 1L, "1")
        assertTrue(BackupCodec.decode(text.substring(0, text.length / 2)) is BackupCodec.Result.Invalid)
        assertTrue(BackupCodec.decode("") is BackupCodec.Result.Invalid)
        assertTrue(BackupCodec.decode("not json at all") is BackupCodec.Result.Invalid)
    }

    @Test fun editedFileFailsTheChecksum() {
        val text = BackupCodec.encode(sample(), 1L, "1")
        val tampered = text.replace("\"amountMinor\":5000", "\"amountMinor\":9000")
        assertFalse(tampered == text)
        val r = BackupCodec.decode(tampered) as BackupCodec.Result.Invalid
        assertTrue(r.message.contains("checksum", ignoreCase = true))
    }

    private fun rewrite(text: String, edit: (MutableMap<String, Any?>) -> Unit): String {
        @Suppress("UNCHECKED_CAST") val m = (MiniJson.parse(text) as Map<String, Any?>).toMutableMap()
        edit(m); return MiniJson.write(m)
    }

    @Test fun newerSchemaOrFormatOrOtherAppIsRefused() {
        val text = BackupCodec.encode(sample(), 1L, "1")
        val newer = rewrite(text) { it["schemaVersion"] = 99L }
        assertTrue((BackupCodec.decode(newer) as BackupCodec.Result.Invalid).message.contains("newer"))
        val fmt = rewrite(text) { it["formatVersion"] = 99L }
        assertTrue(BackupCodec.decode(fmt) is BackupCodec.Result.Invalid)
        val other = rewrite(text) { it["app"] = "Other" }
        assertTrue(BackupCodec.decode(other) is BackupCodec.Result.Invalid)
    }

    @Test fun olderSchemaBackupWithoutNewColumnsStillRestoresWithDefaults() {
        // A v2-era goal row: no accountId / iconKey / note / updatedAtMillis / deletedAtMillis.
        val tables = linkedMapOf<String, Any?>(
            "savings_goals" to listOf(linkedMapOf<String, Any?>(
                "id" to "g", "userId" to "old", "name" to "Bike", "targetAmountMinor" to 1000L,
                "savedAmountMinor" to 400L, "isActive" to true, "createdAtMillis" to 5L))
        )
        val doc = linkedMapOf<String, Any?>(
            "app" to "Hisab", "formatVersion" to 1L, "schemaVersion" to 2L, "createdAtMillis" to 1L,
            "checksum" to BackupCodec.sha256(MiniJson.write(tables)), "tables" to tables
        )
        val ok = BackupCodec.decode(MiniJson.write(doc)) as BackupCodec.Result.Ok
        val g = ok.parsed.data.goals.single()
        assertEquals("savings", g.iconKey); assertEquals(null, g.accountId); assertEquals(0L, g.updatedAtMillis)
        assertEquals(400L, g.savedAmountMinor)
    }

    @Test fun duplicateIdsAreAnError() {
        val d = sample().let { it.copy(accounts = it.accounts + it.accounts.first()) }
        val r = BackupCodec.decode(BackupCodec.encode(d, 1L, "1"))
        assertTrue((r as BackupCodec.Result.Invalid).message.contains("twice"))
    }

    @Test fun danglingReferencesAreWarningsNotErrors() {
        val d = sample().let { it.copy(transactions = it.transactions + it.transactions.first().copy(id = "t9", sourceAccountId = "gone")) }
        val r = BackupCodec.decode(BackupCodec.encode(d, 1L, "1")) as BackupCodec.Result.Ok
        assertEquals(1, r.warnings.size)
    }

    @Test fun contributionWithoutItsGoalIsAnError() {
        val d = sample().copy(goals = emptyList())
        assertTrue(BackupCodec.decode(BackupCodec.encode(d, 1L, "1")) is BackupCodec.Result.Invalid)
    }

    @Test fun wrongTypeInARowIsAFormatError() {
        val text = BackupCodec.encode(sample(), 1L, "1")
        @Suppress("UNCHECKED_CAST") val m = MiniJson.parse(text) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST") val tables = (m["tables"] as Map<String, Any?>).toMutableMap()
        @Suppress("UNCHECKED_CAST") val txs = (tables["transactions"] as List<Map<String, Any?>>).map { it.toMutableMap() }
        txs[0]["amountMinor"] = "lots"; tables["transactions"] = txs
        val doc = linkedMapOf<String, Any?>("app" to "Hisab", "formatVersion" to 1L, "schemaVersion" to 3L,
            "checksum" to BackupCodec.sha256(MiniJson.write(tables)), "tables" to tables)
        val r = BackupCodec.decode(MiniJson.write(doc)) as BackupCodec.Result.Invalid
        assertTrue(r.message.contains("amountMinor"))
    }

    @Test fun userIdIsRemappedOnRestore() {
        val remapped = sample("phone-A").withUserId("phone-B")
        assertTrue(remapped.accounts.all { it.userId == "phone-B" } && remapped.transactions.all { it.userId == "phone-B" })
        assertEquals(sample("phone-A").transactions.map { it.id }, remapped.transactions.map { it.id })
    }
}

class BackupCryptoTest {
    @Test fun encryptDecryptRoundTrip() {
        val plain = BackupCodec.encode(sample(), 1L, "1")
        val enc = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(enc)); assertFalse(BackupCrypto.isEncrypted(plain))
        assertFalse(enc.contains("Rahim"))
        assertEquals(plain, BackupCrypto.decrypt(enc, "correct horse".toCharArray()))
    }

    @Test fun wrongPasswordAndTamperingAreDetected() {
        val enc = BackupCrypto.encrypt("secret", "pw1".toCharArray())
        try { BackupCrypto.decrypt(enc, "pw2".toCharArray()); org.junit.Assert.fail() } catch (e: BackupCrypto.WrongPasswordException) { }
        val broken = enc.replace("\"data\":\"", "\"data\":\"AAAA")
        try { BackupCrypto.decrypt(broken, "pw1".toCharArray()); org.junit.Assert.fail() } catch (e: BackupCrypto.WrongPasswordException) { }
    }

    @Test fun twoEncryptionsOfTheSameTextDiffer() {
        assertFalse(BackupCrypto.encrypt("x", "p".toCharArray()) == BackupCrypto.encrypt("x", "p".toCharArray()))
    }
}

private class FakeStore(var data: BackupData, var failOnReplace: Boolean = false) : BackupStore {
    var replaceCalls = 0
    override suspend fun readAll(): BackupData = data
    override suspend fun replaceAll(data: BackupData) {
        replaceCalls++
        val before = this.data
        this.data = data
        if (failOnReplace) { this.data = before; throw IllegalStateException("disk full") } // emulates Room rolling back
    }
}

private class FakePrefs(var value: BackupPreferences = BackupPreferences()) : BackupPreferenceStore {
    override fun read() = value
    override fun apply(preferences: BackupPreferences) { value = preferences }
}

class BackupRepositoryTest {
    private fun repo(store: FakeStore, prefs: FakePrefs = FakePrefs()) = BackupRepository(store, prefs, { "me" }, { 42L })

    @Test fun createThenRestoreOnAFreshInstallRebuildsEverythingUnderTheNewUser() = runBlocking {
        val oldPhone = FakeStore(sample("old-phone"))
        val file = repo(oldPhone, FakePrefs(BackupPreferences("$", 1, true))).createBackup("1.0")

        val newPhone = FakeStore(BackupData()); val newPrefs = FakePrefs()
        val r = repo(newPhone, newPrefs)
        val ready = r.inspect(file) as InspectResult.Ready
        assertEquals(0, ready.currentCounts.getValue("transactions"))
        var safety = ""
        val res = r.restore(ready.parsed, "1.0") { safety = it } as RestoreResult.Restored
        assertEquals(3, res.counts["transactions"])
        assertTrue(newPhone.data.transactions.all { it.userId == "me" })
        assertEquals(sample("x").transactions.map { it.id }, newPhone.data.transactions.map { it.id })
        assertEquals(BackupPreferences("$", 1, true), newPrefs.value)
        assertTrue(safety.isNotEmpty())
    }

    @Test fun failedRestoreLeavesExistingDataAndPreferencesUntouched() = runBlocking {
        val existing = sample("me")
        val store = FakeStore(existing, failOnReplace = true); val prefs = FakePrefs(BackupPreferences("৳", 9, true))
        val file = repo(FakeStore(sample("other")), FakePrefs(BackupPreferences("$", 1, false))).createBackup("1")
        val r = repo(store, prefs)
        val ready = r.inspect(file) as InspectResult.Ready
        val res = r.restore(ready.parsed, "1") { }
        assertTrue(res is RestoreResult.Failed)
        assertEquals(existing, store.data)
        assertEquals(BackupPreferences("৳", 9, true), prefs.value)
    }

    @Test fun restoreIsRefusedIfTheSafetyCopyCannotBeWritten() = runBlocking {
        val store = FakeStore(sample("me")); val r = repo(store)
        val ready = r.inspect(r.createBackup("1")) as InspectResult.Ready
        val res = r.restore(ready.parsed, "1") { throw java.io.IOException("no space") }
        assertTrue(res is RestoreResult.Failed); assertEquals(0, store.replaceCalls)
    }

    @Test fun restoringTheSameBackupTwiceDoesNotDuplicateAnything() = runBlocking {
        val store = FakeStore(sample("me")); val r = repo(store)
        val file = r.createBackup("1")
        repeat(2) { val ready = r.inspect(file) as InspectResult.Ready; r.restore(ready.parsed, "1") { } }
        assertEquals(sample("me").transactions.size, store.data.transactions.size)
        assertEquals(sample("me").accounts.size, store.data.accounts.size)
    }

    @Test fun encryptedBackupNeedsTheRightPassword() = runBlocking {
        val r = repo(FakeStore(sample("me")))
        val file = r.createBackup("1", "pw".toCharArray())
        assertEquals(InspectResult.NeedsPassword, r.inspect(file))
        assertEquals(InspectResult.WrongPassword, r.inspect(file, "nope".toCharArray()))
        assertTrue(r.inspect(file, "pw".toCharArray()) is InspectResult.Ready)
    }

    @Test fun invalidFileNeverReachesTheDatabase() = runBlocking {
        val store = FakeStore(sample("me")); val r = repo(store)
        assertTrue(r.inspect("{\"hello\":1}") is InspectResult.Invalid)
        assertEquals(0, store.replaceCalls)
    }

    @Test fun byteOrderMarkIsTolerated() = runBlocking {
        val r = repo(FakeStore(sample("me")))
        assertTrue(r.inspect("﻿" + r.createBackup("1")) is InspectResult.Ready)
    }
}
