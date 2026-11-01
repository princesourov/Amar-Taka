# Hisab — repair & improvement notes

## Build/test status (honest)
- **Gradle/Android build: NOT executed.** The sandbox has no Android SDK and cannot reach dl.google.com / services.gradle.org / Maven Central. Please run `./gradlew assembleDebug testDebugUnitTest` locally.
- **Executed:** 107 pure-Kotlin unit tests (app/src/test/.../domain) compiled with a standalone kotlinc and run through a JUnit shim: 107 passed, 0 failed.
- **Executed:** SQL and v1→v2 migration verified in sqlite3 (user data preserved); all XML files pass xmllint; a resource/ViewBinding cross-check finds no missing strings, ids or binding fields.
- Not compiled against the Android SDK: every file that imports Android/Room/Material classes (fragments, adapters, ViewModels, DAOs). Expect to fix a few compile errors on first build.

## Reports
Rebuilt (ReportsViewModel/Fragment, domain/report/*): period filters (Today … This year, Custom), income/expense/net, savings rate, previous-period comparison, donut + bar charts (custom Canvas views), category drill-down, account balances, budget utilisation, insights, filters (type/category/account/keyword), CSV export and text share. Transfers/loans are excluded; refunds reduce spending.

## Budgets
Add / Edit / Delete (confirm + Trash + Undo + Restore) / Pause-Enable, daily/weekly/monthly scopes, duplicate protection, month navigation, status & type filters, summary card. Trash screen (transactions + budgets) with restore, delete forever, empty trash.

## Data safety
Room v1→v2 additive migration (no destructive fallback), schema export enabled, idempotent default-category seeding.

## Not done
Recurring-transaction execution catch-up (RecurrenceSchedule is written and tested but not yet wired into the worker), category management UI, androidTest migration test, PDF export.
