> Historical 0.1.0 delivery evidence. The current 0.3.3 implementation and verification are in [the update notes](13-donut-immediate-navigation.md).

# Implementation and verification — 0.1.0 pilot

The user approved the Palm green design and implementation on 2 October 2026. The native local pilot is implemented. The first public release must still include the requested optional account and cloud backup; it has not been declared complete or published.

## Delivered scope

| Area | Pilot implementation |
|---|---|
| Budgeting | Exact JMD cents, accounts, income/expense/refund/transfer/adjustment, category splits, allocation preview, safe-to-spend, payday schedules and category limits |
| Commitments | Editable reservations, recurring bills, partial payments, protected savings, goals and linked withdrawals |
| Receipts | Camera/import, rotation/crop, bundled OCR, encrypted drafts, mandatory review, reconciliation, total-only save, duplicate acknowledgement and existing-expense linking |
| Shopping | Reviewed branch/date/product observations, pack/unit prices, checklists, manual estimates, coverage and affordability checks |
| Reports | Search, spending summaries, equal-length date comparisons and CSV export |
| Privacy | SQLCipher, Keystore-wrapped key, encrypted images, app lock, secure windows, password-encrypted portable backup/restore and local deletion |
| Design | Approved green light/dark palettes, phone navigation and wider-window rail; real empty starting data |

Typical pay never credits an account. Receipt suggestions never post money automatically. Cloud authentication is deferred to the public-release phase; the pilot has encrypted portable backup instead. Gross payroll estimates, bank integrations, advanced debt/card accounting and multiple currencies are outside this pilot.

## Architecture and reproducibility

The independent Kotlin core module owns money, allocation, scheduling, reconciliation, quantity, CSV and backup crypto. Room stores the ledger and derives consistent snapshots within a database transaction. Transfers have paired movements; retries use unique submission keys. Receipt duplicate detection compares merchant names without case sensitivity, including accented text, while preserving exact fingerprint detection.

SQLCipher encrypts the database with a random key wrapped by Android Keystore. Receipt images are encrypted separately. OS automatic backup is excluded; portable backup uses password-derived AES-GCM and validates restored records before committing. Device verification remains necessary for Keystore, restore and recovery behavior.

Navigation uses saveable Compose root destinations and dialogs. Navigation 3 and expanded two-pane scenes remain future work. This corrects the earlier proposal to use Navigation Compose; that library is not part of the implementation.

Build configuration: API 26 minimum, compile/target API 36, AGP 9.0.1, Gradle 9.1.0, Kotlin/Compose compiler 2.3.20, Compose BOM 2025.10.01, Room 2.8.4 and SQLCipher 4.19.1. Java 24 runs Gradle; compilation targets Java 17. Dependency lockfiles and the Gradle distribution checksum are project artifacts. The SDK bootstrap downloads checksum-verified official archives; local SDKs and caches are ignored.

## Verification evidence

scripts/verify.ps1 runs:

- 35 host unit tests for exact allocation, money boundaries, pay schedules, reservations, receipt reconciliation, quantities, backup round trips/tampering and CSV escaping.
- Debug APK assembly and optimized unsigned release assembly.
- Instrumentation APK compilation: 14 tests covering atomic ledger operations, retries, bill/goal/refund invariants, reviewed receipts, duplicate acknowledgement, recurrence and encrypted database reopen.
- Android lint; generated report: app/build/reports/lint-results-debug.html.

Host tests passed. Both app variants and the instrumentation APK built successfully. Android lint reported zero errors and 26 warnings. APK signature and ZIP alignment checks passed; ZIP alignment alone does not establish native library/device compatibility. **Instrumentation tests were compiled but not run:** no Android device or emulator was attached. Lint warnings and the retained backward-compatible credential API deprecation do not establish runtime correctness.

The installable pilot is development-signed; the optimized release is unsigned. Neither store signing nor publication is configured. The delivery checksum and final verification counts are in dist/verification.json.

## Remaining release gates

Run [the device validation checklist](07-device-validation.md) before using the app as a dependable financial record. Camera/OCR accuracy, TalkBack/font scaling, rotation/process death, fresh-device restore, biometric/credential fallback, notifications, offline behavior, memory and native library/device compatibility are unverified on hardware. Draft OCR text survives restarts; unsaved review edits are not yet a durable editing session.

For the first public release, implement the optional identity/backup provider, encrypted cloud storage and key recovery, ownership authorization, retention/deletion, transfer disclosures and operating costs. Validate the receipt corpus, representative Jamaican devices, migrations, recovery failures and performance. Complete privacy/Data safety, legal/trademark checks, support contact, production signing and store requirements. ML Kit diagnostic disclosures require device auditing and an accurate privacy notice; no blanket zero-telemetry claim is made.

Prototype screenshots and browser checks are design evidence only, recorded in design/REVIEW.md; they are not native runtime evidence.
