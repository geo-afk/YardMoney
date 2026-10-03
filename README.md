# YardMoney

**Understand your money before payday.** YardMoney is an Android budgeting app for managing Jamaican dollars, everyday spending, bills, savings and receipts in one place.

The current **0.3.6 local pilot** stores financial records on your device and works without an account. It uses a green Material interface with adjustable themes, spacing, corners and motion. Optional accounts and cloud backup are planned for the first public release; they are not implemented in this pilot.

[User guide](#using-yardmoney) · [Developer setup](#developer-setup) · [Testing](#testing-and-verification) · [Architecture](#architecture-and-source-map) · [Issues](https://github.com/geo-afk/YardMoney/issues) · [MIT license](LICENSE)

## Contents

- [Project status](#project-status)
- [Who it is for](#who-it-is-for)
- [Features](#features)
- [Install and start](#install-and-start)
- [Using YardMoney](#using-yardmoney)
- [Privacy, security and backup](#privacy-security-and-backup)
- [Developer setup](#developer-setup)
- [Build and install from source](#build-and-install-from-source)
- [Testing and verification](#testing-and-verification)
- [Architecture and source map](#architecture-and-source-map)
- [Financial and receipt rules](#financial-and-receipt-rules)
- [Troubleshooting](#troubleshooting)
- [Contributing and support](#contributing-and-support)
- [Release roadmap](#release-roadmap)
- [Documentation and references](#documentation-and-references)
- [License](#license)

## Project status

| Item | Current state |
| --- | --- |
| Version | 0.3.6, Android version code 10 |
| Availability | Development pilot; no production store release configured |
| Currency | Jamaican dollars (JMD), displayed as J$ where applicable |
| Android support | Android 8.0 / API 26 minimum; compile and target API 36 |
| Accounts and connectivity | Local use without sign-in; optional identity and cloud backup remain planned |
| Latest host verification | 72 tests passed |
| Latest device verification | 38 instrumentation tests passed on a Pixel 8 Pro |
| Build checks | Debug, instrumentation APK and optimized unsigned release builds passed |
| Android lint | Zero errors and 34 warnings in the latest full verification |
| License | MIT; copyright 2026 Geovanni Stewart |

Verification was recorded on **2 October 2026**. These results describe the tested source, not every Android device or every real receipt. Camera usability, real-receipt accuracy, fresh-device recovery, background reminders and a broader accessibility/device matrix still need manual validation. See [the device checklist](docs/07-device-validation.md).

Some earlier documents and files under `dist/` record historical pilot builds. Their counts and checksums belong to those original artifacts. The current source includes receipt-detector, test-library and Activity scrolling/chart updates; build it for the latest pilot.

## Who it is for

YardMoney helps people who want to:

- See what is safe to spend before their next payday.
- Keep bills and protected savings separate from everyday spending money.
- Start with a 50% needs, 30% wants and 20% savings plan, then customize it.
- Record spending manually or use receipt scanning to reduce typing.
- Review spending by category and compare shopping prices recorded from their own receipts.
- Keep control of local records without a required online account.

This pilot is a manual financial tracker. It does not connect to banks, move money, calculate full payroll or provide multi-currency accounting. Its guidance depends on the balances, transactions and commitments you enter.

## Features

| Area | What you can do |
| --- | --- |
| Home | Review safe-to-spend guidance, daily guidance, upcoming commitments and your allocation chart |
| Activity | Switch record types and date ranges to explore a column chart and matching list; record income, expenses, transfers, refunds and adjustments |
| Plan | Customize category percentages, review allocation donuts and category activity, manage bills and savings goals |
| Shop | Build checklists, use recorded product observations and manual estimates, and review price coverage and affordability |
| More | Expand grouped settings for appearance, accounts, insights, reminders, security and data controls |
| Receipts | Capture or import images, adjust crop/perspective, run bundled OCR, review extracted items and reconcile totals |
| Reports | Search local records, review merchant/category/monthly summaries, compare date periods and export CSV |
| Appearance | Choose light, dark, AMOLED or system modes; dynamic colors or custom accents; corners, spacing and motion |
| Security | Encrypted database and receipt files, optional device authentication, password-encrypted portable backups and local deletion |

### Interface details

The app uses Material calendar date selection, dollar prefixes and grouped currency input, themed navigation, anchored dropdowns, expandable settings and a scrollable money-entry sheet. Home and Plan donuts compare live category usage with allocated targets, showing used / allocated values, remaining amounts and explicit over-target figures. Their solid portions update as records change.

The money-entry form scrolls independently of its sheet. Returning to the top keeps it open; a new deliberate downward pull at the top closes it. The fixed Save action remains reachable with the keyboard open.

Main navigation changes immediately without a tap ripple or page transition. Other supported chart, category and settings motion follows the selected preference: Calm, Slide, Expressive or Off. Corner preferences are Square, Soft and Rounded; spacing preferences are Compact and Comfortable. Wider windows use a navigation rail.

The [browser design prototype](design/index.html) contains fictional examples. Its [light](design/screenshots/dashboard-light.jpg) and [dark](design/screenshots/dashboard-dark.jpg) images are **prototype previews**, not screenshots or test evidence from the installed Android app.

## Install and start

You need a device running Android 8.0 or newer. This repository contains source code; generated APKs, local SDKs and signing keys are excluded from Git.

For a development installation, follow [build and install from source](#build-and-install-from-source), or obtain a verified development APK from the maintainer. There is currently no documented public APK release or Play Store distribution. Do not assume a file linked in historical delivery notes is present in a fresh clone.

The debug build uses a development signing certificate. The optimized release build is unsigned until release signing is configured; it cannot be installed as a finished production release.

On first launch:

1. Enter your real starting balances and payday information.
2. Optionally enter typical take-home pay to guide your plan.
3. Start with the default allocation or customize it in Plan.
4. Record actual money received, bills and spending as they happen.
5. Review the Home page before deciding what to spend.

The app starts with your own empty data. It does not preload fictional money. **Entering typical pay does not add money to an account.** Record actual income when you receive it.

## Using YardMoney

### Accounts and starting balances

Create the accounts you want to track, such as cash, a current account, savings or a wallet. An account can be included in spendable money or marked as protected. Protected savings remain visible without becoming available spending money.

A starting balance records money already held. Income records money newly received. Keep these concepts separate so your spending guidance and income-based allocation stay meaningful.

### Needs, wants and savings

The default allocation divides recorded income into 50% needs, 30% wants and 20% savings. For example, J$82,000 of received income allocates J$41,000 to needs, J$24,600 to wants and J$16,400 to savings.

You can change the percentages. They must total exactly 100%; the rebalance preview helps you review the result. Category targets describe your plan, while category activity shows what you recorded. An allocation is not a bank transfer or an automatic savings deposit. Savings usage includes category spending plus net transfers into SAVINGS-type accounts for the current period; withdrawals reduce the funding measure. Ordinary transfers remain excluded from expenses.

### Safe to spend and daily guidance

Safe to spend begins with balances in included accounts, then subtracts applicable outstanding commitments due by the next payday, including commitments without a due date. Protected account balances are excluded from the starting pool.

For example, J$42,000 of included balances with J$23,580 reserved leaves J$18,420 safe to spend. With seven days until payday, daily guidance is approximately J$2,631.42. It uses whole cents and may leave a small remainder.

The app does not subtract category targets again as additional commitments. Daily guidance is unavailable when there are no days remaining or no positive safe amount. Review missing or incorrect entries before relying on a figure.

### Record money

Use the plus action and choose the appropriate transaction:

- **Income:** money you actually received.
- **Expense:** spending from an account, with a category or category splits.
- **Transfer:** movement between your own accounts; it is not new income or ordinary spending.
- **Refund:** returned spending, optionally linked to the original expense.
- **Adjustment:** a correction to reconcile a balance with reality.

Enter the amount, account and date, then review optional details before saving. The grouped form scrolls while the Save action remains reachable. Transfers need different source and destination accounts.

### Bills, commitments and savings goals

Add recurring bills and reserve money for upcoming obligations. Partial payments reduce the outstanding commitment as well as the relevant account balance. Review the remaining amount rather than treating a partially paid bill as settled.

Recurring bills appear once with the next unpaid date; expand other scheduled dates to review or pay individual occurrences. Repeated saves of one reservation request are protected against duplicate insertion.

Savings reservations earmark money still held in a spendable account. Moving that money into a protected savings account should not reserve it a second time. Track contributions and withdrawals against goals to keep goal progress aligned with your recorded movements.

### Scan and review receipts

1. Capture a photo or import an image. Use even lighting, avoid glare and keep the paper in focus.
2. Review the image, crop and perspective. Ambiguous scenes retain the full image rather than inventing a paper boundary.
3. Run OCR and inspect the merchant, date, currency, items and total.
4. Correct recognition errors and reconcile the receipt before confirming it.
5. Save the reviewed receipt and its expense relationship only when the figures are correct.

OCR suggestions do not post money automatically. Duplicate warnings need review, and an existing expense can be linked rather than recording the same purchase twice. Total-only receipts are supported when item extraction is unsuitable. Bundled recognition and tiled processing support long receipts, but real Jamaican receipt accuracy has not yet been measured against a representative corpus.

### Shopping and reports

Reviewed receipt items can supply product and price observations with branch/date and quantity information. Use them alongside manually entered estimates in a shopping checklist. Coverage indicators help show which estimates have supporting observations; an old observation is not a live retailer price.

Use local insights to search, compare spending periods and inspect category or merchant totals. CSV export creates a readable financial file for use elsewhere. CSV is not an encrypted recovery backup.

## Privacy, security and backup

Financial records are held locally. The app manifest explicitly removes the Internet permission in this pilot. This does not establish a blanket zero-telemetry claim for the device, OS or bundled third-party components, nor does it complete a privacy audit.

| Protection | Implementation and practical limit |
| --- | --- |
| Ledger storage | Room backed by SQLCipher; database encryption key protected through Android Keystore |
| Receipt images | Encrypted private storage, separate from the database |
| App access | Optional system biometric/device-credential authentication; prompt appears when authentication is required |
| Screen content | Secure-window protection restricts ordinary screenshots and screen capture |
| OS backup | Automatic app backup disabled; explicit data-extraction exclusions configured |
| Portable backup | Password-derived authenticated encryption, independent of the original device's Keystore |
| Local control | Export and deletion tools available in the app |

Portable backups use AES-GCM and PBKDF2-HMAC-SHA256 with 600,000 iterations and a fresh salt. The backup password must contain at least 12 characters. Keep both the backup file and its password somewhere you can recover them. The pilot has no online password-recovery service.

Uninstalling the app, clearing its storage or losing the device can remove local records and keys. Create a portable backup before destructive actions. Test restoration with fictional records before depending on it for recovery. Do not treat a database file copied from private storage or a CSV export as a substitute for the supported encrypted backup.

Camera access is used for receipt capture; notifications are used for bill reminders; biometric support enables optional app authentication. Device/OS permissions and notification settings can affect these features. Do not publish receipts, balances, backup passwords or exported financial files in issue reports.

## Developer setup

### Toolchain

| Component | Project configuration |
| --- | --- |
| Language | Kotlin 2.3.20; Java/Kotlin bytecode target 17 |
| Android UI | Jetpack Compose; Compose BOM 2025.10.01; Material 3 |
| Android Gradle Plugin | 9.0.1 |
| Gradle | Wrapper 9.1.0, with distribution checksum |
| Verified Gradle runtime | Java 24 |
| Android SDK | Platform API 36 and build-tools 36.0.0 |
| Data | Room 2.8.4 and SQLCipher Android 4.19.1 |
| Code generation | KSP 2.3.11 |
| Receipt tools | CameraX 1.5.2; bundled ML Kit text recognition 16.0.1 |
| Device tests | AndroidX runner 1.7.0, JUnit extension 1.3.0, Espresso 3.7.0 |

Use the checked-in wrapper and dependency lockfiles. Java 24 is the verified runtime; the bytecode target of 17 does not mean the Gradle build has been validated with every Java 17 installation. Do not upgrade dependencies solely to match an IDE suggestion without checking compatibility and rerunning verification.

### Clone and open

```powershell
git clone https://github.com/geo-afk/YardMoney.git
Set-Location YardMoney
```

Open the repository root in an Android Studio version that supports AGP 9.0.1. Select a suitable Gradle JDK and install SDK platform 36 and build-tools 36.0.0 through SDK Manager. Allow the initial Gradle dependency download.

Configure the SDK through the IDE or an ignored root `local.properties` file. For example, replace this illustrative path with your own:

```properties
sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk
```

Alternatively, the Windows helper downloads checksum-verified official SDK archives into the project:

```powershell
.\scripts\bootstrap-sdk.ps1
```

The helper writes `local.properties` and installs the platform, build tools and ADB. It does not install an emulator/system image, change global settings or write license-acceptance records. If its pinned archives are no longer available, use Android Studio's SDK Manager and configure `local.properties` yourself.

### Windows command-line environment

Set `JAVA_HOME` to your actual Java 24 installation. The path below matches the environment used for project verification:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-home'
.\gradlew.bat --version
```

The SDK, cache directories, machine-specific settings and signing keys are excluded from version control. Android SDK/build dependency downloads need connectivity during setup; installed budgeting flows operate locally.

## Build and install from source

### Debug build

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

The installable output is `app/build/outputs/apk/debug/app-debug.apk`. Connect an authorized device and install through Android Studio, or run:

```powershell
.\gradlew.bat :app:installDebug --console=plain
```

An update requires the same application ID and compatible signing certificate. A fresh machine may generate a different debug key; do not uninstall an existing installation holding real data merely to work around a signature mismatch. Back up and plan the transition first.

### Optimized release build

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`. R8 shrinking is enabled. Production signing, signed bundles, release credentials and store submission are not configured. Keep signing material outside Git.

### macOS and Linux

The wrapper can run without the Windows helper scripts. Configure a compatible Java runtime and Android SDK first, then use:

```bash
chmod +x gradlew
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease --console=plain
```

Windows is the verified development environment. These equivalent wrapper commands are provided for portability; they are not evidence of a completed macOS/Linux validation run.

## Testing and verification

### Full local verification

```powershell
.\scripts\verify.ps1
```

This runs core tests, app host tests, debug assembly, instrumentation APK assembly, debug lint and optimized unsigned release assembly. It compiles device tests but cannot execute them without a running Android device.

### Run device tests

Enable Developer options and USB debugging, use a data-capable cable, unlock the phone and approve the computer. Windows may require an OEM USB driver. For an emulator, create and start a virtual device in Android Studio's Device Manager.

With the project-local SDK:

```powershell
& '.\.tooling\android-sdk\platform-tools\adb.exe' devices
.\scripts\verify.ps1 -DeviceTests
```

If your SDK is elsewhere, use its `platform-tools/adb` instead. A connected target should show `device`; `unauthorized` means the phone still needs authorization. The script installs the app and test APK and runs the instrumentation suite. Use a dedicated test device or test profile and fictional records for manual work.

For a faster device-only rerun after configuring the environment:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

### Evidence and reports

The latest full run passed 73 host tests and 38 device tests on a Pixel 8 Pro, with no skipped device tests. Receipt regression coverage checks ambiguous uniform images as well as a recognizable paper quadrilateral. UI tests cover forms, calendar behavior, category selection and appearance; repository tests use isolated databases for ledger invariants.

| Report | Generated location |
| --- | --- |
| Core tests | `core/build/reports/tests/test/index.html` |
| App host tests | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Device tests | `app/build/reports/androidTests/connected/debug/index.html` |
| Android lint | `app/build/reports/lint-results-debug.html` |

Reports are generated locally and excluded from Git. Results are described in [testing setup and fixes](docs/14-testing-environment.md). Automated checks do not replace the [manual validation checklist](docs/07-device-validation.md), including real receipts, camera use, authentication, restore, notification delivery, large fonts and different system navigation modes.

## Architecture and source map

The project separates Android-specific storage/UI from independent financial and parsing logic. Compose screens interact with `AppModel` and the repository; the repository writes Room transactions and exposes snapshots; the core module supplies deterministic rules.

```text
YardMoney/
├── app/                       Android app, manifest, resources and device tests
│   ├── schemas/               Exported Room database schema
│   └── src/main/java/jm/yardmoney/
│       ├── ui/                Compose pages, forms, charts, appearance and motion
│       ├── data/              Entities, DAO, database and FinanceRepository
│       ├── receipts/          Image preparation and OCR processing
│       ├── security/          Private storage and portable backup
│       └── reminders/         Bill reminder scheduling
├── core/                      Money, budget, scheduling, parsing and crypto rules
├── docs/                      Research, architecture, update evidence and checklists
├── design/                    Separate browser review prototype
├── scripts/                   Windows SDK bootstrap and verification helpers
├── gradle/wrapper/            Reproducible Gradle launcher
├── app/gradle.lockfile         Android dependency locks
└── core/gradle.lockfile        Core dependency locks
```

Application ID: `jm.yardmoney`. Current Room schema version: 1. The root project and repository are named `YardMoney`; changing a folder name does not change the Android application ID.

Navigation uses saveable Compose root destinations and dialogs. The implementation does not currently use Navigation Compose or Navigation 3. The five root destinations are Home, Activity, Plan, Shop and More.

The `design/` prototype is independent of the native ledger. To view it, install Node.js and run:

```powershell
node design/serve.cjs
```

Open `http://localhost:4173`. The prototype uses fictional examples and does not persist financial records.

## Financial and receipt rules

These invariants matter when contributing changes:

- Store money as integer minor units (`Long` JMD cents), not floating-point currency.
- Store allocation percentages in basis points; values must total 10,000, representing 100%.
- Distribute allocation rounding by largest remainder with deterministic tie-breaking so allocations sum exactly to income.
- Record transfers as paired account movements; never count them as income or ordinary expense activity.
- Use atomic database operations for related ledger changes and unique submission keys for retry safety.
- Keep protected balances outside the spendable pool and avoid counting commitments twice.
- Do not credit typical pay automatically or confuse an opening balance with received income.
- Preserve receipt review and reconciliation before ledger posting; maintain duplicate acknowledgement and existing-expense linking.
- Treat price observations as historical evidence with branch/date/unit context, not guaranteed current retail prices.
- Validate imported backup data before committing restored records and retain authenticated-encryption checks.

Changes to these rules need meaningful financial/recovery tests. Schema changes require a reviewed migration and updated exported schemas; this pilot does not authorize destructive migrations to discard user records.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| `JAVA_HOME` is missing or Gradle cannot start | Verify the installed JDK path and run the wrapper's `--version` command |
| SDK location is missing | Configure SDK Manager or write the correct ignored `local.properties` |
| API 36/build tools are missing | Install platform 36 and build-tools 36.0.0; do not commit the SDK |
| No device appears | Start the emulator or check the USB data cable, debugging setting and Windows OEM driver |
| ADB reports `unauthorized` | Unlock the phone and accept the debugging authorization |
| APK update reports incompatible signing | Verify the signing certificate; back up data before considering an installation transition |
| `InputManager.getInstance` fails in UI tests | Ensure current locked Espresso 3.7.0 dependencies are used; older Espresso caused this on the tested device |
| OCR misses receipt content | Retake with even lighting and better focus; review/correct output rather than posting it unchecked |
| Reminders do not appear | Check notification permission, OS notification settings and background restrictions; manual validation remains required |
| Screenshot is blank or blocked | Secure-window protection intentionally restricts ordinary app capture |
| Backup cannot be restored | Check the password and file integrity; do not overwrite real records while diagnosing |
| Repository links to an absent historical APK | Build current source; generated APKs are excluded from Git |

For the renamed local workspace, reopen `YardMoney` in your IDE and verify the SDK path. The optional rename helper is for older folders named `Android App`; a fresh clone already has the correct name.

## Contributing and support

The repository is maintained under [geo-afk](https://github.com/geo-afk). Use [GitHub issues](https://github.com/geo-afk/YardMoney/issues) for reproducible bugs and feature requests. Include the app version, Android version, device, theme/text size if relevant, reproduction steps, expected behavior and actual behavior. Redact personal information from logs and examples.

For contributions:

1. Start from the latest `main` and create a focused branch.
2. Preserve the financial, review and encryption rules above.
3. Keep Kotlin changes consistent with the surrounding code and document user-visible behavior.
4. Run checks relevant to the change, including device tests when behavior depends on Android.
5. Open a pull request describing the problem, resulting behavior, validation and any remaining limits.

Do not commit signing keys, `.env` secrets, machine-specific SDK paths, personal databases, receipts, exported records, SDK binaries or build caches. Dependency locks, the Gradle wrapper and exported Room schemas belong in the repository.

For a security concern, avoid publishing personal data or exploitable details in a public issue. A dedicated private security-reporting channel has not yet been documented; that is a release-preparation task, not an available support promise.

## Release roadmap

The current focus is validating and refining the local pilot. Public-release work includes:

- Optional account and encrypted cloud backup with ownership checks, deletion and recovery design.
- A representative Jamaican receipt corpus and measured OCR accuracy.
- Broader Android/device testing, camera and notification checks, accessibility and lifecycle recovery.
- Fresh-device backup/restore, database migration and failure-path validation.
- Production signing, distribution packaging and store preparation.
- Privacy notice, Data safety disclosures, support process and private vulnerability reporting.
- Review of operating costs and the final product name/trademark availability.

There are no committed release dates in this repository. Bank integrations, advanced debt/card accounting, gross-payroll estimates and multiple currencies are outside the current pilot scope.

## Documentation and references

| Document | Purpose |
| --- | --- |
| [Research](docs/01-research.md) | Product and engineering research |
| [Requirements](docs/02-product.md) | Scope and release sequence |
| [Architecture proposal](docs/03-architecture.md) | Original proposal; confirm final choices against source |
| [Design system](docs/04-design.md) | Visual direction and flows |
| [Assurance](docs/05-assurance.md) | Release checks and acceptance gates |
| [Historical implementation](docs/06-implementation.md) | Earlier pilot evidence |
| [Device checklist](docs/07-device-validation.md) | Manual checks still to perform |
| [Controls and settings](docs/09-controls-and-settings.md) | Currency, calendar and settings updates |
| [Charts and motion](docs/10-charts-motion-calendar.md) | Category activity and motion choices |
| [Forms and controls](docs/11-forms-controls-allocation.md) | Scrollable entry sheet and dropdown behavior |
| [Merged Plan](docs/12-merged-plan-navigation.md) | Budget presentation and selection cues |
| [Donut and navigation](docs/13-donut-immediate-navigation.md) | Current chart and immediate-menu updates |
| [Live budget and bills](docs/16-live-budget-reservations.md) | Current used-versus-allocated charts, recurring groups and retry protection |
| [Chart colors](docs/17-vibrant-charts.md) | Shared vibrant palette and theme contrast |
| [Activity and scrolling](docs/15-activity-entry-scroll.md) | Chart filters and form gesture behavior |
| [Testing setup and fixes](docs/14-testing-environment.md) | Latest device-test evidence and Windows setup |
| [Prototype review](design/REVIEW.md) | Browser design review, distinct from Android verification |

This README follows [GitHub's README guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes) for purpose, getting started, support and relative links, and [Write the Docs guidance](https://www.writethedocs.org/guide/writing/beginners-guide-to-docs/) for audience-aware explanations and runnable examples. User tasks appear before implementation details; deeper historical evidence stays in linked documents.

For Android tooling, see [Android Studio](https://developer.android.com/studio), [physical-device setup](https://developer.android.com/studio/run/device), [virtual-device setup](https://developer.android.com/studio/run/managing-avds) and [AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test).

## License

YardMoney is licensed under the [MIT License](LICENSE), copyright 2026 Geovanni Stewart. Preserve the license notice when distributing substantial portions of the project. Third-party components retain their respective licenses and notices.
