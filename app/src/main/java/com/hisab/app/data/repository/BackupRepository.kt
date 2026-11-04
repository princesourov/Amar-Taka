package com.hisab.app.data.repository

import com.hisab.app.data.backup.BackupCodec
import com.hisab.app.data.backup.BackupCrypto
import com.hisab.app.data.backup.BackupData
import com.hisab.app.data.backup.BackupPreferences

/** What the repository needs from the database (BackupDao implements it; tests use an in-memory fake). */
interface BackupStore {
    suspend fun readAll(): BackupData
    suspend fun replaceAll(data: BackupData)
}

/** Where the few non-database settings live; implemented over SharedPreferences in the app, faked in tests. */
interface BackupPreferenceStore {
    fun read(): BackupPreferences
    fun apply(preferences: BackupPreferences)
}

sealed interface InspectResult {
    /** The file is password protected and no (or an empty) password was given. */
    data object NeedsPassword : InspectResult
    data object WrongPassword : InspectResult
    data class Invalid(val message: String) : InspectResult
    data class Ready(val parsed: BackupCodec.Parsed, val warnings: List<String>, val currentCounts: Map<String, Int>) : InspectResult
}

sealed interface RestoreResult {
    data class Restored(val counts: Map<String, Int>) : RestoreResult
    /** Nothing was changed: the database transaction rolled back (or the safety copy could not be written). */
    data class Failed(val message: String) : RestoreResult
}

/**
 * Backup and restore as one use-case-sized repository.
 *
 * Restore strategy (explicit, predictable): REPLACE. The database is emptied and filled from the file in
 * ONE transaction, so there are no merge surprises and no duplicate ids. Before anything is touched, a
 * safety copy of the current data is handed to [safetyCopyWriter]; if that cannot be written the restore
 * is refused. If any step fails the transaction rolls back and the current data is left exactly as it was.
 */
class BackupRepository(
    private val backupDao: BackupStore,
    private val preferenceStore: BackupPreferenceStore,
    private val currentUserId: () -> String,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** The backup file's text. A non-empty [password] encrypts it (AES-256-GCM). */
    suspend fun createBackup(appVersion: String, password: CharArray? = null): String {
        val data = backupDao.readAll().copy(preferences = preferenceStore.read())
        val plain = BackupCodec.encode(data, now(), appVersion)
        return if (password != null && password.isNotEmpty()) BackupCrypto.encrypt(plain, password) else plain
    }

    /** Reads and validates a backup WITHOUT changing anything - this is the "preview" step. */
    suspend fun inspect(fileText: String, password: CharArray? = null): InspectResult {
        var text = fileText.removePrefix("﻿")
        if (BackupCrypto.isEncrypted(text)) {
            if (password == null || password.isEmpty()) return InspectResult.NeedsPassword
            text = try { BackupCrypto.decrypt(text, password) } catch (e: BackupCrypto.WrongPasswordException) { return InspectResult.WrongPassword }
        }
        return when (val r = BackupCodec.decode(text)) {
            is BackupCodec.Result.Invalid -> InspectResult.Invalid(r.message)
            is BackupCodec.Result.Ok -> InspectResult.Ready(r.parsed, r.warnings, backupDao.readAll().counts())
        }
    }

    suspend fun restore(parsed: BackupCodec.Parsed, appVersion: String, safetyCopyWriter: suspend (String) -> Unit): RestoreResult {
        val current = backupDao.readAll().copy(preferences = preferenceStore.read())
        try {
            safetyCopyWriter(BackupCodec.encode(current, now(), appVersion))
        } catch (e: Exception) {
            return RestoreResult.Failed("Could not save a safety copy of your current data, so nothing was changed.")
        }
        val incoming: BackupData = parsed.data.withUserId(currentUserId())
        try {
            backupDao.replaceAll(incoming)
        } catch (e: Exception) {
            return RestoreResult.Failed("The restore failed and was rolled back. Your existing data is unchanged. (${e.message})")
        }
        preferenceStore.apply(incoming.preferences)
        return RestoreResult.Restored(incoming.counts())
    }
}
