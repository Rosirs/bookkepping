# Android Bookkeeping MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a release-ready Android MVP that records financial transactions from Android notifications, stores them locally, and lets the user review and edit records.

**Architecture:** A single Kotlin Android app module with clean package boundaries: Android notification integration feeds framework-free parser/domain logic, which writes through a Room repository and is displayed by Jetpack Compose. Core parsing and summary behavior are JVM-testable without Android framework dependencies.

**Tech Stack:** Kotlin, Android Gradle Plugin 8.2.0, Jetpack Compose Material 3, Room 2.6.1, Kotlin coroutines/Flow, JUnit 4.

## Global Constraints

- MVP capture method is `NotificationListenerService`; no AccessibilityService in v1.
- Financial data remains local; no cloud sync, account login, remote AI, or analytics.
- Parser is deterministic, offline, and testable with local JVM tests.
- Minimum SDK is 26; target/compile SDK is 34 for available local toolchain stability.
- Use package/application id `com.ryan.bookkeeping`.
- Release readiness means tests pass, lint has no fatal errors, debug build succeeds, release build succeeds, and `CLAUDE.md` documents actual commands.

---

## File Structure

- `settings.gradle.kts`: Gradle plugin repositories and `:app` module registration.
- `build.gradle.kts`: root plugin aliases.
- `gradle/libs.versions.toml`: dependency versions and aliases.
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat`: Gradle wrapper copied from a known local Android project.
- `local.properties`: local Android SDK path.
- `app/build.gradle.kts`: Android app configuration and dependencies.
- `app/src/main/AndroidManifest.xml`: app, activity, and notification listener declarations.
- `app/src/main/java/com/ryan/bookkeeping/MainActivity.kt`: activity entry point and dependency bootstrap.
- `app/src/main/java/com/ryan/bookkeeping/domain/*`: parser, models, money formatting, summary logic.
- `app/src/main/java/com/ryan/bookkeeping/data/*`: Room database, DAO, entity, repository.
- `app/src/main/java/com/ryan/bookkeeping/notification/*`: notification listener and service dependency locator.
- `app/src/main/java/com/ryan/bookkeeping/ui/*`: Compose view model and screens.
- `app/src/test/java/com/ryan/bookkeeping/domain/*`: parser and summary tests.
- `CLAUDE.md`: actual commands and structure after implementation.

---

### Task 1: Scaffold Android project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `local.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/xml/notification_listener_service.xml`

**Interfaces:**
- Produces: buildable app module `:app`, namespace `com.ryan.bookkeeping`, min SDK 26, compile SDK 34.

- [ ] Copy Gradle wrapper files from `/home/robin/apps/Developed_App/SoSimpleLedger`.
- [ ] Write Gradle settings, dependency catalog, app build file, manifest, strings, theme, and notification listener XML.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:tasks --all` and expect Gradle to list app tasks.

### Task 2: Implement domain parser and tests

**Files:**
- Create: `app/src/main/java/com/ryan/bookkeeping/domain/TransactionDirection.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/domain/ParsedTransaction.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/domain/RawNotification.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/domain/TransactionParser.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/domain/MoneyFormatter.kt`
- Test: `app/src/test/java/com/ryan/bookkeeping/domain/TransactionParserTest.kt`

**Interfaces:**
- Produces: `TransactionParser.parse(raw: RawNotification): ParsedTransaction?`.
- Produces: `MoneyFormatter.format(amountCents: Long): String`.

- [ ] Write parser tests for expense, income, amount formats, ambiguous wording, and ignored non-financial text.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:testDebugUnitTest --tests 'com.ryan.bookkeeping.domain.TransactionParserTest'` and expect failures before implementation.
- [ ] Implement parser and money formatter.
- [ ] Re-run the same test and expect pass.

### Task 3: Implement Room persistence and repository

**Files:**
- Create: `app/src/main/java/com/ryan/bookkeeping/data/TransactionEntity.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/data/TransactionDao.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/data/AppDatabase.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/data/TransactionRepository.kt`
- Test: `app/src/test/java/com/ryan/bookkeeping/domain/TransactionSummaryTest.kt`

**Interfaces:**
- Consumes: `ParsedTransaction` from Task 2.
- Produces: `TransactionRepository.recordParsed(parsed: ParsedTransaction): Boolean`.
- Produces: `TransactionRepository.observeTransactions(): Flow<List<TransactionEntity>>`.
- Produces: `calculateSummary(transactions: List<TransactionEntity>): TransactionSummary`.

- [ ] Write summary tests for income, expense, and net amount.
- [ ] Implement Room entity, DAO, database, repository duplicate insert, and summary calculation.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:testDebugUnitTest` and expect pass.

### Task 4: Implement notification listener integration

**Files:**
- Create: `app/src/main/java/com/ryan/bookkeeping/notification/AppDependencies.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/notification/TransactionNotificationListener.kt`

**Interfaces:**
- Consumes: `TransactionParser.parse(raw)`.
- Consumes: `TransactionRepository.recordParsed(parsed)`.
- Produces: Android service `com.ryan.bookkeeping.notification.TransactionNotificationListener` declared in manifest.

- [ ] Implement dependency locator with lazy Room database and repository.
- [ ] Implement notification extraction from package name, app label, title, text, and post time.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:assembleDebug` and expect success.

### Task 5: Implement Compose UI

**Files:**
- Modify: `app/src/main/java/com/ryan/bookkeeping/MainActivity.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/ui/BookkeepingViewModel.kt`
- Create: `app/src/main/java/com/ryan/bookkeeping/ui/BookkeepingApp.kt`

**Interfaces:**
- Consumes: `TransactionRepository.observeTransactions()`.
- Consumes: `TransactionRepository.updateTransaction(...)` and `deleteTransaction(id: Long)`.
- Produces: a UI with permission card, summary, transaction list, edit dialog, and empty state.

- [ ] Implement Activity bootstrap and Compose theme.
- [ ] Implement ViewModel state collection and edit/delete actions.
- [ ] Implement Compose screen and edit dialog.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:assembleDebug` and expect success.

### Task 6: Verify release readiness and documentation

**Files:**
- Modify: `CLAUDE.md`
- Build outputs: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release-unsigned.apk` or AGP equivalent.

**Interfaces:**
- Produces: documented build/test commands and final verification status.

- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:testDebugUnitTest` and expect pass.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:lintDebug` and expect no fatal lint errors.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:assembleDebug` and expect pass.
- [ ] Run `ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:assembleRelease` and expect pass.
- [ ] Update `CLAUDE.md` with actual app architecture and verified commands.

## Self-Review

- Spec coverage: notification listener, offline parsing, Room persistence, Compose UI, duplicate prevention, editing, tests, lint, debug build, release build, and documentation are covered by Tasks 1-6.
- Placeholder scan: the plan has no unresolved placeholders.
- Type consistency: parser, repository, summary, and UI interfaces are named once and reused consistently across tasks.
