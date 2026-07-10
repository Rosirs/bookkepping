# Android Bookkeeping MVP Design

## Goal

Build a first-release Android bookkeeping app that automatically records money movement from Android notification text. The app runs fully on-device, stores records locally, lets the user review and edit detected entries, and provides enough tests/build verification to be release-ready as an MVP.

## MVP scope

- Android native app written in Kotlin.
- Notification listener is the only automatic capture mechanism in v1.
- No AccessibilityService in v1.
- No cloud sync, account login, OCR, remote AI, or external analytics.
- All transaction detection happens offline with deterministic rules.
- All financial data stays in local app storage.

## Architecture

The app is organized as a small single-module Android project with clear package boundaries:

- `data`: Room entities, DAO, database, repository, and duplicate handling.
- `domain`: transaction direction, parsed transaction model, parsing rules, and remark generation.
- `notification`: `NotificationListenerService` adapter that converts Android notifications into raw parse input.
- `ui`: Compose screens and state management for records, editing, and permission guidance.

The core parser does not depend on Android framework classes, Room, or Compose. This keeps the most important behavior testable with local JVM unit tests.

## Data flow

1. User enables notification access for the app in Android settings.
2. `TransactionNotificationListener` receives posted notifications.
3. The service extracts package name, app label fallback, title, text, and post time.
4. `TransactionParser` attempts to parse amount, direction, counterparty/merchant hint, and remark from the text.
5. If parsing succeeds, `TransactionRepository` checks a deterministic duplicate key and inserts the record into Room.
6. Compose UI observes repository flow and displays latest transactions.
7. User can edit amount, direction, remark, merchant, and deletion state from the UI.

## Transaction detection rules

The parser detects common Chinese financial notification language. It supports:

- Expense signals such as `支付`, `付款`, `消费`, `扣款`, `支出`, `转出`.
- Income signals such as `收款`, `到账`, `收入`, `转入`, `退款`, `返现`.
- Amount formats such as `¥12.34`, `￥12.34`, `12.34元`, `人民币12.34元`.
- A generated remark made from source app + sanitized notification title/text excerpt.

When both income and expense words are present, refund/income words win only when they are clearly present as `退款`, `返现`, `到账`, `收入`, or `收款`; otherwise expense words win.

## Persistence

Room stores a `transactions` table with:

- `id`
- `amountCents`
- `direction`
- `sourcePackage`
- `sourceApp`
- `title`
- `content`
- `merchant`
- `remark`
- `postedAt`
- `createdAt`
- `dedupeKey`

The dedupe key is based on source package, amount, direction, normalized title/content, and a one-minute timestamp bucket. Duplicate notifications are ignored.

## UI

The first UI is intentionally simple:

- Top permission card explaining notification access and linking to Android notification listener settings.
- Summary row showing total income, total expense, and net amount for records currently stored.
- Transaction list sorted newest first.
- Edit dialog for correcting parsed fields and remarks.
- Empty state telling the user to enable permission and wait for supported payment/bank notifications.

## Error handling

- Notification parsing failures are ignored and do not create records.
- Database insertion uses `OnConflictStrategy.IGNORE` on `dedupeKey`.
- UI shows an empty state instead of crashing when no records exist.
- Invalid edit amount is rejected in the dialog by disabling save until a positive amount is entered.

## Testing

Local JVM tests cover:

- Expense parsing.
- Income parsing.
- Amount format variants.
- Ambiguous income/expense wording.
- Duplicate key stability.
- Summary calculations.

Build verification covers:

- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`

## Release readiness definition

The MVP is considered ready for user testing or app-store preparation when:

- Debug and release APK builds succeed.
- Local unit tests pass.
- Android lint has no fatal errors.
- App has a launcher icon/name, notification listener service declaration, and release build configuration.
- `CLAUDE.md` documents actual commands and structure.

Signing keys, store listing text, screenshots, privacy policy hosting, and formal Play/App Store submission are outside this repository's automated scope unless the user provides those assets and accounts.
