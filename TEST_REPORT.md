# Test report — Hisab 0.2.0

Only things that were **actually executed** are listed as passed.

## Gradle / Android build — NOT RUN
`./gradlew assembleDebug testDebugUnitTest lint` could **not** be executed: there is no Android SDK in the authoring environment and the Gradle, Google and Maven hosts are unreachable (re-checked at the end: services.gradle.org, dl.google.com, repo.maven.apache.org returned no response). Consequently:
- Nothing that imports Android, Room, Material, Navigation or ViewBinding was compiled (all Fragments, Adapters, Room DAOs/entities, `AppContainer`).
- Android lint was not run.
- No instrumented/migration test was run on a device or emulator.
Treat this as source code that has been statically checked, not as a build.

## Executed: unit tests — 179 passed, 0 failed
Method: all non-UI Kotlin sources (domain, repositories, view models, backup codec) were compiled with the Kotlin 2.0.21 compiler bundled with Gradle against small hand-written stubs of the Room/AndroidX APIs, then run with JUnit 4.13.2. (The project itself targets Kotlin 2.2.20, so a few newer-compiler differences cannot be excluded.)

| Test class | Tests | Covers |
|---|---|---|
| ReportCalculatorTest | 32 | report totals, exclusions, comparisons |
| ReportPeriodsTest | 25 | period ranges |
| BackupTest (codec, crypto, repository) | 21 | round trip, tamper/checksum, newer version, old schema defaults, duplicates, dangling refs, encryption + wrong password, restore rollback on failure, safety-copy failure, repeat restore (no duplicates), BOM |
| BudgetLogicTest | 15 | budget periods, evaluation, conflicts, validation |
| DebtLedgerTest | 14 | FIFO allocation, partial/settled, net position, no double counting, surplus |
| RecurrenceScheduleTest | 13 | schedule planning, month-end |
| GoalRulesTest | 11 | allocate/withdraw rules, percent, status, trash/delete rules, dashboard maths |
| CategoryCatalogTest / CategoryRulesTest | 10 / 7 | categories |
| RecurringExecutionTest | 8 | catch-up, idempotency, crash mid-batch rolled back then retried, 8 concurrent runs, pause/resume, archived/missing account, invalid amount |
| InsightEngineTest / MiniJsonAndMoneyParserTest / MoneyParserTest / CsvExporterTest / MoneyFormatTest | 7 / 6 / 5 / 4 / 1 | misc |

Note: "restore rollback" is tested with an in-memory store that emulates Room's rollback; the real `@Transaction` behaviour is untested without Android.

## Executed: other checks
- **Migration v2→v3** (`tools/verify_migration_2_3.py`): real v2 schema from the exported JSON built in SQLite, migration statements run, existing rows preserved, legacy goal contribution created, `PRAGMA integrity_check` = ok.
- **XML**: `xmllint` well-formed on all 78 XML files.
- **Resources/ViewBinding** (`tools/check_resources.py`): no missing strings/ids/colors/dimens/layouts/nav ids. Remaining reports are false positives (Android framework layouts and Material styles, data-class fields named like views).
- **Fragment → ViewModel member check**: 0 unknown members.
- **Bracket balance** of Kotlin files: all balanced (one false positive from character literals in `MiniJson.kt`, which compiled and is unit-tested).

## Not executed / not covered
Room DAOs and the real database, `SavingsGoalRepository` against a database, all UI (rendering, navigation, SAF pickers, FileProvider share, dialogs), Android lint, performance, dark-mode visual check, accessibility pass, PDF export (not implemented), custom recurrence intervals (not implemented).

## Manual smoke test (please run on a device)
1. Install over an existing v2 install: all accounts, transactions, goals still there; old goal amounts show as "Earlier balance".
2. Lend 5,000, receive 2,000, receive 3,000 → record goes Active → Partially paid → Settled; dashboard receivable/payable/net correct; a repayment above the outstanding is rejected.
3. Goal: add money from an account → account "free" drops, total balance unchanged; withdraw; delete → Trash → restore.
4. Trash: delete one item of each type, check dates, restore, delete forever, empty trash.
5. Backup (with and without password) → wipe app data → restore → compare counts; try a tampered/older/newer file.
6. Recurring: past start date → catch-up once, rerun app → no duplicates; edit, pause, resume, history.
7. Delete an account with an active recurring schedule → blocked with a message.
