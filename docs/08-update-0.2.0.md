> Historical 0.2.0 update. See [current controls and settings](09-controls-and-settings.md) for 0.2.1.

# Android update — 0.2.0

Implemented directly in the existing Kotlin/Compose application on 2 October 2026.

## Themes and UI

The old palette specified only a few roles and inherited mismatched container/content colors. YardTheme now supplies paired roles, neutral light surfaces, dark surfaces, true-black AMOLED backgrounds, Android 12+ wallpaper colors, six accent presets and arbitrary #RRGGBB accents. Preference listeners update onboarding, the lock screen and all app components immediately and persist across launches. System mode follows the phone. Manual accents turn off wallpaper coloring, with an explicit explanation.

User accents and dynamic foregrounds are adjusted using sRGB contrast calculations. Main body text is 16–17sp; supporting text is 14sp with 21sp line height. Shapes, cards, center-aligned app bars, theme-aware chart tracks, multiline fields and whole-row 48dp checkbox targets use the shared system. Pages crossfade in 180ms and chart progress animates; Compose follows platform animation scaling. Onboarding settings are collapsible. Loading labels explain startup and OCR processing; data-open retry re-subscribes without resetting storage.

Material 3 1.4.0 is already the project's stable version, so no speculative dependency upgrade was required. Reference: [official Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3). Rounded hierarchy, tonal controls and restrained transitions follow Material 3 design. The stable artifact keeps the named Expressive motion API internal, so the implementation uses public stable components and Compose animations; no alpha library or internal API bypass was added.

## Receipt pipeline

Image loading preserves tall receipts instead of imposing the former 3,000-pixel long-edge limit. EXIF orientation is applied. Decoding is bounded to 8 megapixels with a 12,000-pixel height ceiling. OCR section height adapts to width to keep working sections near 2 megapixels; it uses overlapping sections, keeping repeated purchases on separate rows. Recognition uses line geometry to join corresponding columns, probes orientation/skew, and compares original and illumination-normalized recognition. Thermal-paper processing uses local brightness normalization, contrast enhancement, isolated-noise suppression and restrained sharpening. This is an attempt to improve recognition, not an accuracy guarantee.

The preparation screen offers conservative paper-boundary detection and four-point perspective correction, a preview, original-image restoration, rotation and manual edge cropping. Ambiguous boundaries leave the image intact. Brightness/focus heuristics warn about dark, washed-out, blurred or faded photos. Camera capture has alignment guides, tap-to-focus and sampled live lighting warnings.

The parser now handles wrapped item names, prices on following lines, quantity/unit-price annotations, extended-price columns, receipt totals on following lines, day-first/year-first/named dates, time, location, receipt/transaction identifiers, visible payment methods, subtotal, taxes and discounts. Numeric glyph correction is confined to amount tokens. Mathematical mismatch and ambiguous date warnings require review. Taxes printed as inclusive may need a manual adjustment; the app does not silently decide the tax basis.

Review provides editable details, item matching notes, add/remove items, individual verification, live reconciliation and rescan. Save edits as draft persists corrections inside the encrypted existing draft record. Rotation restores editor state; reopening a saved draft asks for verification again. Receipt metadata is stored alongside original text, while verified item records and prices keep their existing schema. Raw OCR remains available. No financial record is created until confirmation; idempotence, reconciliation and duplicate guards remain active.

Reference: [ML Kit input-image guidance](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Bundled recognition remains offline-capable; privacy disclosures from the original implementation still apply.

## Dashboard and reports

The dashboard and Plan have a labeled allocation donut and budget-progress bars based on received income and the user's actual percentages. Reports add income versus net expenses, merchant/category bars, a six-month spending trend and savings-goal progress. Values and labels remain visible outside charts. Charts use theme roles rather than fixed green colors. Refunds reduce expenses and retain their original merchant grouping; transfers do not inflate income or expenses. Negative remaining amounts are displayed, and empty/incomplete records are explained.

## Data and verification

Application ID remains jm.yardmoney. Version code advances from 1 to 2. Room schema stays at version 1; no destructive migration, database clearing or account replacement was introduced. New review metadata fits existing encrypted text columns and portable backups. Install the new development APK over the earlier pilot; do not uninstall to update.

52 host unit tests pass: 50 in core and 2 theme-role/mode tests in app. These include 12 receipt-layout regressions and contrast repair across accent/background combinations. All prior financial and crypto tests continue to pass. The verification script now includes app host tests.

24 instrumentation tests compile: 16 isolated repository/encryption tests, 5 image/OCR tests and 3 Compose theme/accessibility tests. **They have not run.** No device/emulator is attached. Camera capture, real OCR accuracy, dynamic wallpaper colors, actual 200% font rendering, low-end memory/performance and API 26–36 device behavior remain unverified. Synthetic text fixtures are not a measured Jamaican receipt corpus.

Build/lint/signature/checksum results are recorded in dist/verification.json after final verification. The hardware matrix is in [device validation](07-device-validation.md). Optional account/cloud backup and production release gates from the previous pilot remain outstanding.
