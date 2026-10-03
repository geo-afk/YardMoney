# Pilot 0.3.4 — stable money entry and Activity explorer

## Money-entry scrolling

The entry sheet previously combined Material sheet dragging/nested fling handling with a scrolling form body. A drag returning to the top could also hide the sheet, and edge flings could move the sheet rather than only the form.

Money entry now disables the sheet's built-in drag gesture. Its content has one remembered scroll state, no stretch overscroll, and consumes leftover edge scroll/fling deltas. The header and Save action remain outside the scrolling content. Keyboard insets continue to reserve space for the fixed action.

A dismissal gate checks the position at the beginning of each touch gesture. A gesture beginning below the top only scrolls; reaching the top during that gesture cannot make it eligible to close. A new downward pull beginning at the top closes only after at least 120dp of deliberate drag and release. Short pulls and direction reversals do not close. Fling velocity never counts toward the threshold. Busy saves disable dismissal; Close and Android Back remain available when idle.

## Activity explorer

Activity includes a single chart card with wrapping record-type chips: Expenses, Income, Transfers, Refunds and Adjustments. Expenses are selected initially. Date-range options are This pay period, Last six months and All recorded dates. Records dated after today are excluded from these historical views; the visible range is labelled.

The chart and transaction list use the same filtered set. Data is grouped into up to six consecutive date intervals spanning the selected range, including intervals with no records. Column heights use a common currency scale, shown explicitly. Tap a column to see its exact interval, total and record count. Columns have at least 48dp-wide touch areas; compact layouts permit horizontal chart scrolling rather than squeezing targets. Text dates and accessibility descriptions remain available independently of color.

Column charts were selected to compare discrete recorded amounts across time. No line interpolation implies spending happened between entries, and a pie/donut cannot represent signed balance corrections reliably. This is a native Compose chart; it does not load a web chart service.

Expenses and refunds are shown separately. Transfers use each ledger transaction once and never become income/spending. Income excludes typical-pay hints and opening balances. Adjustments retain their signed values, with reductions below zero; opposite corrections within an interval can net to zero, which is explained in the UI. Receipt review/draft sections stay separate from financial record totals.

## Validation

New host tests cover type/date isolation, calendar ranges, transfer counting, signed totals, continuous interval boundaries, empty data, and the two-gesture dismissal gate. New device tests exercise actual form swipes, short/long top pulls, fixed header position, keyboard entry persistence, fixed Save visibility, type switches, negative corrections and empty type states.

The focused run of three new device tests passed on the Pixel 8 Pro. Full verification also passed on 2 October 2026: 67 host tests, all 34 device tests (zero failures/errors/skips), debug and optimized unsigned release builds, and lint with zero errors and 34 warnings. APK signature verification matched the previous 0.3.3 development signer; 16KB ZIP alignment passed. Broader real-device/accessibility/manual receipt validation remains necessary.

Application ID remains `jm.yardmoney`; database schema remains version 1. Version is 0.3.4 (code 8). Update with the compatible development signer without uninstalling to preserve local records.

## Research

- [Compose Material 3 modal sheet implementation](https://android.googlesource.com/platform/frameworks/support/+/e8405530ed56fdfb4dc887185c2cf67265824801/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ModalBottomSheet.kt): independent control of sheet gestures and nested scrolling.
- [Compose gesture handling](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures): observing pointer passes while keeping standard scroll/click behavior.
- [Google column-chart guide](https://developers.google.com/chart/interactive/docs/gallery/columnchart): columns, labels and signed baselines. Used as visualization guidance, not as an app dependency.
