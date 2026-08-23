# Hisab — Phases 1 & 2, plus Add Money / Transactions / Accounts / Transfer

A personal money manager for Bangladesh: cash, bank, bKash/Nagad/Rocket,
lending & borrowing, budgets, and reports — built from a full product spec.
It was generated outside Android Studio, so it has not been compiled or
run — see "Before you build" below.

## What's real and working right now

- **Full Room database** — every entity from the spec, with DAOs and a
  working schema. No migration needed for anything below — every addition
  used existing fields or added new *query methods*, never new columns or
  tables.
- **The accounting engine** — all 7 money-flow use cases (expense, income,
  transfer, lend, borrow, repayment received, repayment made), each
  implementing the exact ledger rules from the spec, with duplicate and
  overpayment guards.
- **Add Expense** and **Add Money** — fast bottom-sheet entry for both
  directions, both going through `RecordExpenseUseCase`/`RecordIncomeUseCase`.
- **Transaction History** — real screen, replacing the placeholder. Search
  by note/category/account, filter by type, newest-first, empty state.
- **Edit and Delete, for every transaction type** — one type-adaptive edit
  screen (fields change based on whether it's an expense, transfer, a loan,
  a repayment, etc.), reachable via a 3-dot menu on each row. Delete is a
  soft delete with a confirmation dialog — the row is flagged `isDeleted`,
  never destroyed, and every balance query already excludes it.
- **Accounts** — real screen, replacing the placeholder. Add, edit
  (name/opening balance/notes), and deactivate ("delete") accounts, each
  showing its live derived balance.
- **Transfer Money** — account-to-account, reachable from the Accounts
  screen. Never counted as income or expense.
- **Budgets, Savings Goals, Recurring Transactions** (Phase 2) — see below.
- **Dashboard** — total balance, per-account breakdown, You Will Receive /
  You Will Pay / Net, today's income & expense, Recent Transactions,
  Budgets and Savings Goals summary cards, Add Money + Add Expense FABs.
  All live from Room, nothing hardcoded.
- **Navigation shell** — 5-tab bottom nav, Material 3 light/dark theming.
- **People, Lending & Borrowing** — real screen, replacing the placeholder.
  Add/edit/deactivate people, search, live receivable/payable balance per
  person. Person detail shows full history (reusing the same transaction
  list/edit/delete UI as the Transactions screen) plus Give Money, Take
  Money, Receive Repayment, and Make Repayment — each going through the
  exact same `LendMoneyUseCase`/`BorrowMoneyUseCase`/repayment use cases
  built in Phase 1, including the overpayment guard. This closes the gap
  flagged earlier: the Dashboard's "You Will Receive"/"You Will Pay" cards
  could only ever read ৳0.00 with no way to create a person-linked
  transaction — now they can show real figures.
- **Firebase scaffold** — Auth wrapper and a Firestore push function, plus
  `firestore.rules`. Extended this round, but still **not wired into the
  live app** — see "Firebase" below for exactly why and what that means.

### A bug fixed along the way, not introduced by this round

Every ViewModel in the app was being created via `Factory.create(...)`
called directly, instead of through `ViewModelProvider` (the `by viewModels
{}` delegate). That meant `onCleared()` never fired and `viewModelScope` was
never cancelled — every screen visit left a ViewModel running in the
background permanently. This is fixed everywhere now, old screens and new.

## What's NOT built yet

Reports & Analytics, date-range/account/person filtering beyond the search
already in Transactions and People, sign-in screens, live Firebase sync,
reminders/notifications, a Trash/Restore screen (soft-delete works,
nothing shows you the deleted list yet), Settings, and onboarding.

## Firebase — extended, still not live, and why that's deliberate

`FirestoreSyncService` now has the shape ready for create/update/delete, but
nothing in the app *calls* it yet. There's no `google-services.json` and no
sign-in screen, so `FirebaseFirestore.getInstance()` could plausibly throw
if actually invoked right now — wiring it in without those two pieces first
would trade a working offline app for a real crash risk, for zero benefit
(nobody can be signed in yet regardless). Once sign-in exists, calling these
methods after a local save is a small, safe addition.

## Key design decisions (and why)

- **Balances are never stored, only derived**, from opening balance + the
  transaction ledger — for accounts, for what each person owes/is owed,
  and for budget progress. Editing or deleting a transaction needs no
  separate "recalculate" step anywhere in the app, by construction: every
  downstream number is a live query, not a cache.
- **Delete = soft delete**, everywhere. `isDeleted` is already excluded by
  every balance/total query, so nothing needed to change to make delete
  safe — it already was.
- **One universal transaction table**, not separate tables per feature.
  Lending, borrowing, repayments, transfers, and recurring executions are
  all just rows with a `type` — the reason the derived-balance formula
  works uniformly everywhere, including the new edit/delete/search paths.
- **Editing a repayment re-validates against a ledger that excludes
  itself** (`getNetBalanceForPersonExcluding`) — otherwise an edited
  repayment would be checked against a total that already includes its own
  old value, which would be wrong.
- **Account type is fixed after creation**; name, opening balance, and
  notes are editable. A deliberate scope line, not an oversight.
- **Savings goal contributions are soft-tracked, not tied to a real
  account** — unchanged from Phase 2; see the code comment in
  `SavingsGoalRepository` for the reasoning.
- **No sign-in required yet.** A random local ID is generated once per
  device so the app is fully usable offline before Firebase Auth exists.
- **Manual DI, not Hilt** — see the comment in `AppContainer.kt`.

## Before you build

This project was written without access to Android Studio, an emulator, or
network access to resolve Gradle/Firebase dependencies — so **it has not
been compiled**. Every cross-reference between Kotlin and XML (view-binding
field names — 88 checked this round, string resources — 116 checked,
navigation action IDs, drawable/color/menu references) was checked by
script against the actual files each time something was added, but that's
not the same as a real compile. When you open it:

1. **Open the `Hisab/` folder in Android Studio** and let Gradle sync
   (needs internet — this is the first real compile check it'll get).
2. **Create a Firebase project** at console.firebase.google.com, add an
   Android app with package name `com.hisab.app`, download
   `google-services.json`, and place it in `app/`. The build will not
   complete without this file present — the `google-services` Gradle
   plugin requires it.
3. In the Firebase console, go to Firestore Database → Rules and paste in
   the contents of `firestore.rules` (or deploy via the Firebase CLI).
4. Enable Email/Password and Google sign-in methods in Firebase Auth
   (needed once a later phase wires up login screens).
5. Fix whatever Gradle sync/compile turns up. Two spots worth double-checking
   first: the KSP version in the root `build.gradle.kts` (`2.3.20-1.0.29`
   was my best estimate — check github.com/google/ksp/releases for the
   exact build matching Kotlin 2.3.20 if sync fails there), and the
   `applicationId`/`namespace` (currently generic `com.hisab.app`).
6. Swap the placeholder nav/launcher icons for a real icon set — Android
   Studio's Resource Manager → Vector Asset makes this quick.

AGP 9.x now has built-in Kotlin support and can drop the separate
`kotlin-android` plugin — I stayed on the more thoroughly-documented AGP
8.10 / Kotlin 2.3.20 pairing since it's the path I could be most confident
about writing correctly without a compiler to check it.

## No Gradle or Room migration changes this round

Every addition in this round used fields and DAO methods that already
existed, or added new `@Query` methods — never a new column, table, or
dependency. Room only requires a version bump for actual schema changes, so
`HisabDatabase`'s `version = 1` is untouched, and there is nothing to
migrate.

## Roadmap

People & Lending/Borrowing, Reports & Analytics, Search refinements
(date-range and person/account filters), Auth screens + live Firebase sync,
Reminders/Notifications, Settings, Onboarding, and a visible Trash screen.


