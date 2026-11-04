# Hisab 0.2.0 — what changed

Priority order followed: financial correctness → data safety → features → polish.
**Important:** the Android/Gradle build could not be run in the authoring environment (no Android SDK, Gradle and Maven hosts blocked). See `TEST_REPORT.md` for exactly what was and was not executed. Open the project in Android Studio and run `./gradlew assembleDebug testDebugUnitTest lint` before trusting it on a device.

## Data safety (nothing destroyed, ever)
- Database **v2 → v3** by a proper additive Room migration (`MIGRATION_2_3`): only `ADD COLUMN` and `CREATE TABLE`/`INDEX`. No `fallbackToDestructiveMigration`, no table drops, no data reset. Details in `MIGRATION.md`.
- Every multi-step write (repayments, goal allocation, trash purge, account/person deletion, recurring catch-up, restore) runs inside one Room transaction (`TransactionRunner`), so it either fully happens or not at all.
- Money is parsed with exact integer arithmetic (`MoneyParser`) everywhere; the old `Double` rounding path was removed from all amount-entry screens.

## Debts (receivable / payable / net)
- New pure `DebtLedger`: each person's lending and borrowing are tracked as separate records; repayments are allocated oldest-first (FIFO). Statuses: Active, Partially paid, Settled.
- Dashboard shows **Receivable**, **Payable** and **Net position = receivable − payable**. No double counting, settled debts are zero, history is kept.
- Repayments above what is owed are rejected; editing a loan or repayment is validated against the position excluding the edited row.
- Person screen: net position, both directions, per-record cards with progress, filters (All / Open / Settled) and the list of payments against each record.

## Goals (allocation model)
- A goal **earmarks money inside an account**; no transaction is created, so nothing is counted twice. Every add/withdraw is an append-only `goal_contributions` row, so it is traceable.
- Account "free to spend" = balance − money set aside for goals; adding more than is free (or withdrawing more than the goal holds in that account) is rejected inside the same transaction.
- Create, edit (name, target, date, icon, note, default account), delete (to Trash, money becomes free; undo), detail screen with progress, remaining, per-account allocation and history. Old saved amounts are carried over as an "Earlier balance" contribution.

## Trash
- Six tabs: Transactions, Budgets, Goals, Categories, Accounts, People. Each row shows name, type, amount (where relevant), original date and deleted date.
- Restore is referentially safe (a transaction cannot be restored into an archived account/person — you are told which to restore first; budgets re-check duplicates). Delete-forever and Empty Trash have confirmations and keep anything still referenced (and say so).
- Accounts, categories and people are now soft-deleted (with a deleted timestamp) instead of silently archived.

## Backup & restore
- Versioned JSON file (`.hisab`), deterministic, with SHA-256 checksum; optional AES-256-GCM encryption (PBKDF2-HMAC-SHA256, 210,000 iterations).
- Save through the system file picker (SAF) or share through a non-exported `FileProvider` (private cache, one file at a time).
- Restore: file is validated (format, version — newer-than-app files are refused —, duplicates, bad values, orphans), a preview with counts of "backup vs. current" is shown, then **explicit confirmation**. A safety copy of current data is written first; the replace happens in one transaction and rolls back on any failure. Nothing is merged or silently overwritten. Backups from another phone are re-owned to the current install.

## Dashboard (rebuilt)
- Hero balance with "Free to spend" and "In goals", period selector (default This month), summary (income, expense, net cash flow, savings rate), debt card, accounts strip (shows free amount when goals hold some), budget overview, goals overview, recent transactions, quick actions (Income, Expense, Transfer, Lend/borrow, New goal, Reports, Budgets), overflow menu (Categories, Recurring, Trash, Backup & restore). Every card navigates; empty states for accounts, budgets, goals and transactions.

## Recurring
- Edit (amount, category, account, frequency, note), pause/resume, delete, **history** of generated transactions, and a "needs attention" line when a schedule is skipped (archived/missing account, invalid amount) instead of silently doing nothing. Skipped schedules are not advanced, so nothing is lost.
- Catch-up is atomic and idempotent (deterministic transaction ids + `insertIfAbsent` + mutex); covered by automated tests with crash-rollback and concurrency cases.

## Accounts / people deletion
- Deleting an account is blocked while active recurring schedules or goal allocations depend on it, and otherwise moves it to Trash with history intact. People with an open debt cannot be deleted.

## Files added/changed (high level)
`data/backup/*`, `data/local/dao/BackupDao.kt`, `data/local/database/{Migrations,TransactionRunner,HisabDatabase}.kt`, `domain/{debt,goal,trash,dashboard}/*`, repositories, view models, `presentation/{dashboard,savings,trash,backup,people,recurring}/*`, layouts, `nav_graph.xml`, manifest (FileProvider), `res/xml/file_paths.xml`, `strings_v3*.xml`, `tools/*`.

## Completed vs. not completed
**Completed (code written; non-UI logic executed in tests, UI unverified on device):** debt model, goals, trash, backup/restore, dashboard, person debt records, recurring edit/history, safe deletion, DB migration, money parsing, tests.
**Not done / limited:**
- Recurring "custom interval" (every N days/weeks) — needs a new column; daily/weekly/monthly/yearly are supported.
- Reports PDF export (CSV and text share exist from the earlier delivery).
- Reports, Budgets and Category screens were not rewritten in this pass (they already had logic and tests); they were not re-audited screen-by-screen.
- Room schema JSON for v3 is not generated (schema export is disabled in this project because of a Room/KSP classloader crash noted in `docs/PREVIOUS_DELIVERY_NOTES.md`). An instrumented Room migration test therefore could not be added; the migration SQL was verified against the v2 schema in SQLite instead.
- No SavingsGoalRepository/BackupDao tests against a real Room database (needs Android).
- Android auto-backup (`allowBackup=true`) was left as it was; the app's own backup file is separate.

## Known risks to check first in Android Studio
1. UI code and the Room-generated parts have never been compiled — expect a few compile fixes.
2. Room validates the migrated schema at first open; if it complains, compare `MIGRATION_2_3` to the generated v3 schema JSON (see `MIGRATION.md`).
3. Smoke test list is at the end of `TEST_REPORT.md`.
