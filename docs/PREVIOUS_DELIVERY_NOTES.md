# Hisab — repair & improvement report

## Build repair applied in this delivery (2026-11-03)
- Fixed the Room KSP `AbstractMethodError` path by disabling Room schema export in `@Database` and removing the `room.schemaLocation` KSP argument. The crash occurs in Room's schema serializer under the Kotlin compiler classloader; changing app dependencies alone does not repair that classloader ABI mismatch. The committed v2 schema JSON is retained as an Android-test asset. Re-enable schema export only after a Room release fixes the serializer ABI issue.
- Updated Room runtime/ktx/compiler/testing together from 2.8.1 to 2.8.5.
- Aligned the toolchain: Kotlin 2.2.20 + KSP 2.2.20-2.0.4, and Gradle 8.11.1 for AGP 8.10.0. AGP 8.10 requires Gradle 8.11.1.
- Made the Linux Gradle wrapper executable and made a LongArray increment explicit (`1L`) for compiler portability.
- Preserved database version 2, the additive `MIGRATION_1_2`, all application source, and the existing v2 schema JSON. No destructive migration fallback was added.

## Verification in this delivery
- Static review completed for Gradle configuration, database/migration setup, manifest, and source tree.
- Full Android Gradle build was **not verifiable in this environment**: there is no Android SDK and Gradle distribution download is blocked by unavailable network/DNS. Do not treat this ZIP as a device-tested APK.
- The existing migration test still requires the original v1 schema JSON (`app/schemas/.../1.json`), which was not included in the uploaded project. I did not fabricate it.

Legend: **Tested** = executed here (offline harness). **Implemented, unverified** = code written and statically checked, but never compiled/run on Android. **Not done** = absent.

## Build/test environment (exact limits)
- **No Android/Gradle build was possible.** The sandbox proxy returns 403 for dl.google.com, services.gradle.org, repo.maven.apache.org and maven.google.com (rechecked at the end), and there is no Android SDK. Nothing that imports Android, Room, Material or Navigation has been compiled.
- **Executed:** 119 pure-Kotlin unit tests (`app/src/test/.../domain`, compiled with standalone kotlinc + a JUnit shim): **119 passed, 0 failed**.
- **Executed:** SQL queries and the v1→v2 migration statements in sqlite3 (existing rows preserved); xmllint on every XML file (OK); resource/ViewBinding cross-checker (no missing strings/ids/binding fields; the 11 `simple_list_item_1` hits are Android framework layouts, a checker false positive); bracket-balance check on all Kotlin files.
- **Written but NOT executed:** `app/src/androidTest/.../MigrationTest.kt` (needs a device/emulator and the exported schema JSONs).

## Requirement matrix
| Area | Status |
|---|---|
| Reports: period filters, totals, savings rate, expense ratio, previous-period comparison, charts (custom Canvas), category drill-down, filters, CSV/text share | Calculation, period and CSV logic **tested** (domain tests). Screens/charts **implemented, unverified** on device |
| Reports accuracy rules (transfers/loans excluded, refunds reduce spending, one source of truth) | **Tested** (ReportCalculator tests; SQL mirrored in sqlite3) |
| Budgets: add / edit / delete→Trash / undo / restore / pause-enable, cards, month nav, filters, duplicate guard | Validation, evaluation and money parsing **tested**; DB conflict-safe writes checked in sqlite3 only; UI **implemented, unverified** |
| Trash screen (transactions + budgets, restore, purge, empty) | **Implemented, unverified** |
| Categories: expanded idempotent defaults, kind (expense/income/both), seeding that never overwrites | Catalog/seeding logic **tested**; seeder/DAO **unverified** |
| Category management UI (add, rename, change kind, hide/restore, duplicate + length checks, usage warning; history never touched) | Rules **tested**; screen **implemented, unverified** |
| Edit-transaction category picker filtered by type (current category always kept) | Rule **tested**; wiring **unverified** |
| Recurring: catch-up of missed runs, no month-end drift, idempotent deterministic IDs, atomic write, mutex, uncategorised expenses no longer dropped, pause/resume/delete, runs immediately after creating | Schedule/plan logic **tested**; Room/use-case/UI **implemented, unverified** |
| DB migration v1→v2 (additive, no destructive fallback) | SQL **verified in sqlite3**; Room-level `MigrationTest` **written, not run** |
| Premium UI across ALL screens | **Partial.** New/rebuilt screens (Reports, Budgets, Trash, Categories, recurring item) use Material 3 with light/dark colours and text-based status. Existing screens (Accounts, People, Savings goals, Add/Edit forms, Transfer, Calendar) were **not redesigned** |
| General bug audit of the rest of the app (accounts deletion rules, transfer edit/delete, people/loan repayments, savings goals, dashboard) | **Not re-audited in this pass.** Only APIs touched by my changes were checked for consistency |
| PDF export | **Not done** (CSV + text share only) |
| Budget limit notifications, app lock changes | **Not done** |

## Manual steps
1. Open in Android Studio and run `./gradlew assembleDebug testDebugUnitTest`; fix any compile errors (expect some — see above).
2. For `MigrationTest`: build the ORIGINAL v1 project once to export `app/schemas/com.hisab.app.data.local.database.HisabDatabase/1.json`, copy it into this project's `schemas` folder next to the generated `2.json`, then run `./gradlew connectedDebugAndroidTest`.
3. Smoke-test on a device: create/edit/pause/delete/restore a budget; create a recurring entry with a past start date (expect catch-up transactions, no duplicates after restart); hide/restore a category; check Reports totals against the Transactions list.
