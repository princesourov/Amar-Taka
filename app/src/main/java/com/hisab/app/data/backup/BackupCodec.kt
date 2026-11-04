package com.hisab.app.data.backup

import java.security.MessageDigest
import java.time.Instant

/**
 * The Hisab backup file: one UTF-8 JSON document.
 *
 * {
 *   "app": "Hisab", "formatVersion": 1, "schemaVersion": 3,
 *   "createdAtMillis": ..., "createdAt": "2026-11-03T12:00:00Z", "appVersion": "...",
 *   "counts": {"accounts": 5, ...},
 *   "preferences": {...},
 *   "checksum": "<sha-256 hex of the compact JSON of \"tables\">",
 *   "tables": {"accounts": [...], "transactions": [...], ...}
 * }
 *
 * The checksum catches truncated or hand-edited files before anything is restored.
 */
object BackupCodec {
    const val APP = "Hisab"
    const val FORMAT_VERSION = 1L

    /** The database schema version this build writes (and the newest one it can restore). */
    const val SCHEMA_VERSION = 3L

    data class Header(
        val formatVersion: Long, val schemaVersion: Long, val createdAtMillis: Long, val appVersion: String,
        val counts: Map<String, Long>
    )

    data class Parsed(val header: Header, val data: BackupData)

    sealed interface Result {
        data class Ok(val parsed: Parsed, val warnings: List<String>) : Result
        data class Invalid(val message: String) : Result
    }

    fun encode(data: BackupData, createdAtMillis: Long, appVersion: String): String {
        val tables = tablesJson(data)
        val doc = linkedMapOf<String, Any?>(
            "app" to APP,
            "formatVersion" to FORMAT_VERSION,
            "schemaVersion" to SCHEMA_VERSION,
            "createdAtMillis" to createdAtMillis,
            "createdAt" to Instant.ofEpochMilli(createdAtMillis).toString(),
            "appVersion" to appVersion,
            "counts" to data.counts().mapValues { it.value.toLong() },
            "preferences" to linkedMapOf(
                "currencySymbol" to data.preferences.currencySymbol,
                "themeMode" to data.preferences.themeMode.toLong(),
                "notificationsEnabled" to data.preferences.notificationsEnabled
            ),
            "checksum" to sha256(MiniJson.write(tables)),
            "tables" to tables
        )
        return MiniJson.write(doc)
    }

    private fun tablesJson(d: BackupData): LinkedHashMap<String, Any?> = linkedMapOf(
        "user_profile" to d.userProfiles.map { it.toBackupJson() },
        "accounts" to d.accounts.map { it.toBackupJson() },
        "categories" to d.categories.map { it.toBackupJson() },
        "people" to d.people.map { it.toBackupJson() },
        "transactions" to d.transactions.map { it.toBackupJson() },
        "budgets" to d.budgets.map { it.toBackupJson() },
        "savings_goals" to d.goals.map { it.toBackupJson() },
        "goal_contributions" to d.contributions.map { it.toBackupJson() },
        "recurring_transactions" to d.recurring.map { it.toBackupJson() },
        "reminders" to d.reminders.map { it.toBackupJson() },
        "attachments" to d.attachments.map { it.toBackupJson() }
    )

    /** Parses and fully validates. Nothing is written anywhere; the caller decides what to do with the result. */
    fun decode(text: String): Result {
        val root = try { MiniJson.parse(text) } catch (e: MiniJson.JsonException) {
            return Result.Invalid("This is not a valid Hisab backup file (${e.message}).")
        }
        if (root !is Map<*, *>) return Result.Invalid("This is not a Hisab backup file.")
        @Suppress("UNCHECKED_CAST") val doc = root as Map<String, Any?>
        if (doc["app"] != APP) return Result.Invalid("This file was not created by Hisab.")
        val format = doc["formatVersion"] as? Long ?: return Result.Invalid("The backup has no format version.")
        if (format > FORMAT_VERSION) return Result.Invalid("This backup uses a newer file format ($format). Update Hisab and try again.")
        val schema = doc["schemaVersion"] as? Long ?: return Result.Invalid("The backup has no database version.")
        if (schema > SCHEMA_VERSION) return Result.Invalid("This backup was made by a newer version of Hisab (database v$schema). Update the app first.")
        if (schema < 1) return Result.Invalid("The backup has an invalid database version.")
        val tablesAny = doc["tables"] as? Map<*, *> ?: return Result.Invalid("The backup contains no data section.")
        @Suppress("UNCHECKED_CAST") val tables = tablesAny as Map<String, Any?>

        val stored = doc["checksum"] as? String ?: return Result.Invalid("The backup has no checksum, so it cannot be verified.")
        if (!stored.equals(sha256(MiniJson.write(tables)), ignoreCase = true)) {
            return Result.Invalid("The backup is damaged or was modified (checksum mismatch). Nothing was restored.")
        }

        val data = try {
            BackupData(
                userProfiles = rows(tables, "user_profile").map(::userprofileFromBackup),
                accounts = rows(tables, "accounts").map(::accountFromBackup),
                categories = rows(tables, "categories").map(::categoryFromBackup),
                people = rows(tables, "people").map(::personFromBackup),
                transactions = rows(tables, "transactions").map(::transactionFromBackup),
                budgets = rows(tables, "budgets").map(::budgetFromBackup),
                goals = rows(tables, "savings_goals").map(::savingsgoalFromBackup),
                contributions = rows(tables, "goal_contributions").map(::goalcontributionFromBackup),
                recurring = rows(tables, "recurring_transactions").map(::recurringtransactionFromBackup),
                reminders = rows(tables, "reminders").map(::reminderFromBackup),
                attachments = rows(tables, "attachments").map(::attachmentFromBackup),
                preferences = prefs(doc["preferences"])
            )
        } catch (e: BackupFormatException) {
            return Result.Invalid("The backup contains invalid data: ${e.message}")
        }

        val problems = BackupValidator.validate(data)
        if (problems.errors.isNotEmpty()) return Result.Invalid("The backup is inconsistent: " + problems.errors.first())

        @Suppress("UNCHECKED_CAST") val counts = (doc["counts"] as? Map<String, Any?>).orEmpty()
            .mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
        val header = Header(format, schema, doc["createdAtMillis"] as? Long ?: 0L, doc["appVersion"] as? String ?: "", counts)
        return Result.Ok(Parsed(header, data), problems.warnings)
    }

    private fun prefs(any: Any?): BackupPreferences {
        val m = any as? Map<*, *> ?: return BackupPreferences()
        return BackupPreferences(
            currencySymbol = (m["currencySymbol"] as? String)?.takeIf { it.isNotBlank() && it.length <= 4 } ?: "৳",
            themeMode = (m["themeMode"] as? Long)?.toInt() ?: -1,
            notificationsEnabled = m["notificationsEnabled"] as? Boolean ?: true
        )
    }

    /** A missing table is treated as empty (older backups); a table that is not a list of objects is an error. */
    private fun rows(tables: Map<String, Any?>, name: String): List<BackupRow> {
        val v = tables[name] ?: return emptyList()
        if (v !is List<*>) throw BackupFormatException("$name must be a list")
        return v.mapIndexed { i, item ->
            @Suppress("UNCHECKED_CAST")
            BackupRow(item as? Map<String, Any?> ?: throw BackupFormatException("$name[$i] must be an object"), name, i)
        }
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/**
 * Structural checks on a decoded backup.
 *  - ERRORS stop the restore: duplicate ids inside a table (the insert would fail halfway), amounts that
 *    are not positive on money rows, unknown enum-like values the app cannot interpret.
 *  - WARNINGS do not: a row that points at something not in the file (e.g. an old transaction whose
 *    account was removed long ago). History is kept as it was rather than refusing the whole restore.
 */
object BackupValidator {
    data class Problems(val errors: List<String>, val warnings: List<String>)

    private val txTypes = setOf("INCOME", "EXPENSE", "TRANSFER", "LENDING", "BORROWING", "REPAYMENT_RECEIVED", "REPAYMENT_MADE", "REFUND", "GIFT", "OTHER")

    fun validate(d: BackupData): Problems {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        fun dupes(table: String, ids: List<String>) {
            val seen = HashSet<String>()
            ids.firstOrNull { !seen.add(it) }?.let { errors += "$table has the same id twice ($it)" }
            ids.firstOrNull { it.isBlank() }?.let { errors += "$table has a row with an empty id" }
        }
        dupes("accounts", d.accounts.map { it.id })
        dupes("categories", d.categories.map { it.id })
        dupes("people", d.people.map { it.id })
        dupes("transactions", d.transactions.map { it.id })
        dupes("budgets", d.budgets.map { it.id })
        dupes("savings_goals", d.goals.map { it.id })
        dupes("goal_contributions", d.contributions.map { it.id })
        dupes("recurring_transactions", d.recurring.map { it.id })
        dupes("reminders", d.reminders.map { it.id })
        dupes("attachments", d.attachments.map { it.id })
        dupes("user_profile", d.userProfiles.map { it.userId })

        d.transactions.firstOrNull { it.type !in txTypes }?.let { errors += "a transaction has an unknown type '${it.type}'" }
        d.transactions.firstOrNull { it.amountMinor <= 0 }?.let { errors += "a transaction has a zero or negative amount (${it.id})" }
        d.goals.firstOrNull { it.targetAmountMinor <= 0 }?.let { errors += "a goal has a zero or negative target (${it.id})" }
        d.budgets.firstOrNull { it.amountMinor <= 0 }?.let { errors += "a budget has a zero or negative amount (${it.id})" }

        val accountIds = d.accounts.map { it.id }.toHashSet()
        val categoryIds = d.categories.map { it.id }.toHashSet()
        val personIds = d.people.map { it.id }.toHashSet()
        val goalIds = d.goals.map { it.id }.toHashSet()
        val txIds = d.transactions.map { it.id }.toHashSet()

        val danglingTx = d.transactions.count {
            (it.sourceAccountId != null && it.sourceAccountId !in accountIds) ||
                (it.destinationAccountId != null && it.destinationAccountId !in accountIds) ||
                (it.personId != null && it.personId !in personIds) ||
                (it.categoryId != null && it.categoryId !in categoryIds)
        }
        if (danglingTx > 0) warnings += "$danglingTx transaction(s) refer to an account, person or category that is not in the backup; they are kept as they are."
        val danglingContrib = d.contributions.count { it.goalId !in goalIds }
        if (danglingContrib > 0) errors += "$danglingContrib goal contribution(s) belong to a goal that is not in the backup"
        val danglingAttach = d.attachments.count { it.transactionId !in txIds }
        if (danglingAttach > 0) warnings += "$danglingAttach attachment(s) belong to a transaction that is not in the backup."
        return Problems(errors, warnings)
    }
}
