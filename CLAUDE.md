# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

This is a Kotlin Android bookkeeping MVP. It reads Android notification text through a `NotificationListenerService`, detects money movement with deterministic offline rules, stores records locally in Room, and displays/edits records with Jetpack Compose.

The app intentionally keeps financial data on-device. There is no cloud sync, login, remote AI, OCR, analytics, or AccessibilityService in the MVP.

## Environment

Use the local Android SDK and Android Studio JBR when building on this machine. The project uses compile/target SDK 36 and AGP/Gradle 8.13.

```bash
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew <task>
```

`gradle.properties` sets `org.gradle.java.home=/home/robin/apps/android-studio/jbr` because the system Java runtime does not include `javac`.

## Common commands

```bash
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:assembleDebug
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:installDebug
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:testDebugUnitTest
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:lintDebug
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:assembleRelease
```

Run one local test class:

```bash
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:testDebugUnitTest --tests 'com.ryan.bookkeeping.domain.TransactionParserTest'
```

Final release verification command:

```bash
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

Signed release artifacts:

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

Verify APK signature:

```bash
/home/robin/Android/Sdk/build-tools/34.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

## Architecture

Single Android app module: `:app`, package/application id `com.ryan.bookkeeping`.

Key packages:

- `com.ryan.bookkeeping.domain`: framework-free parsing and money utilities. `TransactionParser.parse(raw: RawNotification)` converts notification title/content into `ParsedTransaction?`; `MoneyFormatter` formats cents for display.
- `com.ryan.bookkeeping.data`: Room `TransactionEntity`, `TransactionDao`, `AppDatabase`, `TransactionRepository`, and `calculateSummary`. `dedupeKey` is unique to ignore duplicate notifications.
- `com.ryan.bookkeeping.notification`: Android integration. `TransactionNotificationListener` extracts notification metadata and stores parsed transactions through `AppDependencies`.
- `com.ryan.bookkeeping.ui`: Compose UI and `BookkeepingViewModel`. The UI shows notification-permission guidance, income/expense/net summary, transaction list, edit dialog, and delete action.

## Data flow

1. User enables notification access in Android settings from the in-app permission card.
2. `TransactionNotificationListener` receives posted notifications.
3. The service extracts package, app label, title, text, and post time into `RawNotification`.
4. `TransactionParser` detects amount, direction, merchant hint, remark, and dedupe key.
5. `TransactionRepository.recordParsed()` inserts into Room with `OnConflictStrategy.IGNORE` for duplicate notifications.
6. `BookkeepingViewModel` observes Room via Flow and Compose renders the list/summary.

## Tests

Current JVM tests focus on framework-free business logic:

- `TransactionParserTest`: expense/income parsing, amount formats, refund wording, ignored non-financial notifications, money formatting.
- `NotificationPolicyTest`: allowed notification packages, unrelated package rejection, sanitized/bounded notification storage, and parser package filtering.
- `TransactionSummaryTest`: income/expense/net totals and dedupe key stability/change behavior.
- `TransactionRepositoryTest`: update success and duplicate-key conflict handling.

No instrumented tests are required for MVP verification; use `assembleDebug` plus manual device testing for notification permission and real notification capture.

## Release notes

Release signing reads from `/home/robin/.gradle/bookkeeping-signing.properties` by default, or from the file pointed to `BOOKKEEPING_SIGNING_PROPERTIES`. The upload keystore is stored outside the project at `/home/robin/.gradle/bookkeeping-upload.jks`; do not copy keystores or signing passwords into the project directory.

`assembleRelease` produces a signed release APK when signing properties are present, and `bundleRelease` produces a signed AAB. The AAB is the preferred artifact for Google Play-style store upload; the APK is suitable for direct distribution or stores that accept APK uploads.

Release documentation lives in `docs/release/`:

- `privacy-policy-zh.md`: privacy policy draft that must be published to a public URL for store submission.
- `store-listing-zh.md`: Chinese store listing draft.
- `release-checklist.md`: artifact paths and signing verification commands.

Store submission still requires account-specific manual steps outside this repository, such as uploading screenshots and completing the store console questionnaire.
