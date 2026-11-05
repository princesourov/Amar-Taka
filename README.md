# Hisab

An offline-first personal finance manager for Bangladesh, built with native Android Kotlin, XML layouts, ViewBinding, Room, and Firebase scaffolding.

## Included features

- Dashboard with account balances, daily totals, receivable/payable summaries, recent transactions, budgets, and savings goals.
- Income, expenses, transfers, lending, borrowing, repayments, transaction editing, and soft-delete.
- Accounts and people management.
- Budgets with create/edit/pause/delete/restore flows and Trash.
- Category management and type-aware category selection.
- Recurring transactions with catch-up execution and deterministic transaction IDs.
- Reports with period filters, comparisons, category breakdowns, charts, insights, and CSV/text sharing.
- Savings goals as an allocation inside accounts (add / withdraw / history), debts with receivable, payable and net position, Trash for six item types, encrypted backup & restore.

See `CHANGES.md`, `TEST_REPORT.md`, `MIGRATION.md`, `BACKUP_RESTORE.md`.

## Tech stack

- Kotlin 2.2.20, Android Gradle Plugin 8.10.0, Gradle 8.11.1, KSP 2.2.20-2.0.4.
- Room 2.8.5, Coroutines, AndroidX Lifecycle, Navigation, Material Components.
- Firebase Auth/Firestore dependencies and rules are included as scaffolding; live sign-in/sync is not fully wired into the UI.

## Open and build

1. Open the `Hisab/` directory in Android Studio.
2. Use JDK 17 (recommended) and install Android SDK Platform 36.
3. Allow Gradle to download dependencies, then run `./gradlew assembleDebug testDebugUnitTest`.
4. For device migration testing, restore the original Room v1 schema JSON at `app/schemas/com.hisab.app.data.local.database.HisabDatabase/1.json`; only v2 was present in the uploaded project.

## Important database/build note

The Room schema exporter is temporarily disabled to avoid the `FieldBundle$$serializer` / `GeneratedSerializer.typeParametersSerializers()` classloader ABI crash during KSP. The existing v2 schema JSON is retained as a test asset, and database version 2 plus the additive v1→v2 migration are unchanged. Re-enable `exportSchema` and the KSP `room.schemaLocation` argument only after the Room compiler fixes this serializer compatibility issue.

## UI redesign delivery

The UI now uses a shared premium fintech visual system based on the supplied reference: forest-green branding, cream backgrounds, white elevated cards, rounded controls, consistent spacing, themed forms, redesigned reusable list cards, and a floating rounded bottom navigation shell. Existing ViewBinding IDs, navigation, MVVM, Room, Firebase scaffolding, and business logic were preserved.

## Verification limitation

All project XML files parse successfully and the packaged project extracts cleanly. A full Android build was attempted, but the current environment only provides Java 27; the project's Kotlin/Gradle toolchain fails before compilation with a Java-version parsing error. Build locally with JDK 17 and Android SDK Platform 36.
