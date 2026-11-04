# Database migration v2 → v3

Class: `data/local/database/Migrations.kt` (`MIGRATION_2_3`, statements also exposed as `migrationStatements2To3()`).
Registered in `HisabDatabase` together with `MIGRATION_1_2`. There is **no** destructive fallback.

| Step | SQL (summary) | Effect on existing data |
|---|---|---|
| 1 | `ALTER TABLE accounts/categories/people ADD COLUMN deletedAtMillis INTEGER` | Adds a nullable column; all rows keep their values |
| 2 | `ALTER TABLE savings_goals ADD COLUMN accountId, iconKey (default 'savings'), note, updatedAtMillis (default 0), deletedAtMillis` | Adds columns; goals keep name/target/date |
| 3 | `CREATE TABLE goal_contributions` + 2 indices | New table |
| 4 | `UPDATE savings_goals SET updatedAtMillis = createdAtMillis` | Fills the new timestamp |
| 5 | `INSERT INTO goal_contributions … 'legacy-'||id … WHERE savedAmountMinor > 0` | Every goal that had a saved amount keeps it as one "carried over" contribution with no account (shown as "Earlier balance") |

`savings_goals.savedAmountMinor` stays in the table (legacy, no longer read) so nothing is lost and a downgrade of data is not needed.

## How it was verified
`python3 tools/verify_migration_2_3.py app/schemas/com.hisab.app.data.local.database.HisabDatabase/2.json`
builds the real v2 schema from the exported JSON in SQLite, inserts sample rows, runs the migration statements parsed from `Migrations.kt`, and checks rows, the legacy contribution and `PRAGMA integrity_check` (executed; output "ok").
**Not verified:** Room's own schema-identity check on a device (needs Android).

## If Room complains on first launch
Build once with schema export temporarily enabled (see `docs/PREVIOUS_DELIVERY_NOTES.md` for why it is off), diff the generated `3.json` against the statements above, and adjust column types/nullability. Users' data is not touched by a failed validation — the app simply will not open the database until the migration matches.
