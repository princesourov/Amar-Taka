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
- Savings goals and contributions.

## Tech stack

- Kotlin 2.2.20, Android Gradle Plugin 8.10.0, Gradle 8.11.1, KSP 2.2.20-2.0.4.
- Room 2.8.5, Coroutines, AndroidX Lifecycle, Navigation, Material Components.
- Firebase Auth/Firestore dependencies and rules are included as scaffolding; live sign-in/sync is not fully wired into the UI.

## Open and build

1. Open the `Hisab/` directory in Android Studio.
2. Use JDK 17 or newer and install Android SDK Platform 36.
3. Allow Gradle to download dependencies, then run `./gradlew assembleDebug testDebugUnitTest`.
4. For device migration testing, restore the original Room v1 schema JSON at `app/schemas/com.hisab.app.data.local.database.HisabDatabase/1.json`; only v2 was present in the uploaded project.

## Important database/build note

The Room schema exporter is temporarily disabled to avoid the `FieldBundle$$serializer` / `GeneratedSerializer.typeParametersSerializers()` classloader ABI crash during KSP. The existing v2 schema JSON is retained as a test asset, and database version 2 plus the additive v1→v2 migration are unchanged. Re-enable `exportSchema` and the KSP `room.schemaLocation` argument only after the Room compiler fixes this serializer compatibility issue.

## Verification limitation

This delivery was statically reviewed, but a full Android build and emulator/device smoke test could not be run in the current environment because the Android SDK is absent and Gradle distribution downloads are unavailable.
