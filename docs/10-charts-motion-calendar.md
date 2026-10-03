> Historical 0.3.0 update. See [current forms, controls and allocation](11-forms-controls-allocation.md) for 0.3.1.

# Android UI update — 0.3.0

Implemented directly in YardMoney on 2 October 2026.

## Interaction and visual changes

Home and Plan share a thicker, density-aware 24dp allocation ring with percentages beside the segments. A three-way Needs/Wants/Savings segmented control reveals recorded category spending, refunds, remaining allocation or an explicit over-target amount. Savings also shows goal balances separately from expenses. The charts use only the current pay period's transaction splits; refunds retain their signed amounts. Goal balances are clearly identified as all-period totals. Empty states explain what to add next.

The Safe to spend card uses a compact, thicker meter in place of a second small ring. Category bars are 12dp thick. Navigation-bar system insets now sit outside the rounded themed panel, keeping its visible top/bottom spacing balanced while protecting the gesture/button navigation area.

Appearance settings includes persistent Calm, Slide, Expressive and Off styles. The preference updates page transitions, settings expansion/chevrons, allocation chart entry, spending/progress bars and the safe-balance meter. Expressive uses restrained scaling and non-bouncy chart springs; Off snaps app motion. Built-in Material controls retain their platform/component behavior. Compose motion follows Android's animation duration scale.

Date fields now open the Material calendar dialog with month navigation, year selection and optional text entry. Changes commit only on confirmation, Cancel preserves the previous date and optional dates can be cleared. Calendar milliseconds are converted through UTC in both directions to avoid a one-day timezone shift.

App lock immediately presents the system authentication method on launch/return. There is no app Unlock button. A retained session prevents duplicate prompts during rotation, and callbacks reconnect when the activity is recreated. Cancelling authentication closes the locked activity; biometric lockout falls back to device credentials. Existing secure-device prerequisites remain.

Checkbox rows have a 12dp label gap, including the camera flash row. Existing whole-row settings checkbox touch targets remain intact.

## Design research

- [Material date pickers](https://m3.material.io/components/date-pickers/overview) and [Compose implementation](https://developer.android.com/develop/ui/compose/components/datepickers): use a focused calendar dialog with explicit confirm/cancel and text-entry fallback.
- [Compose segmented buttons](https://developer.android.com/develop/ui/compose/components/segmented-button): use a single-choice group for three related category views rather than hiding choices inside a menu.
- [Compose animation guidance](https://developer.android.com/develop/ui/compose/animation/quick-guide): animate state changes and content transitions with restrained duration, and keep an Off preference.
- [Android biometric guidance](https://developer.android.com/identity/sign-in/biometric-auth): keep authentication in the system-provided prompt with credential fallback for supported Android versions.

No dependency upgrade, external account setup or database migration was needed. Application ID and schema remain unchanged. APK version code is 4.

## Verification

Build and test totals are recorded in [APK verification](../dist/verification.json). Host tests cover refunds, bucket isolation, exclusion of transfers/income/out-of-period rows and refund-only periods. Native calendar tests cover opening, clearing and cancelling.

Device UI/authentication tests have not run: no phone or emulator is attached. Check the [device matrix](07-device-validation.md), especially large fonts, very small allocation shares, system navigation modes, motion preferences and credential/biometric lifecycle. The pilot remains a development build.

Final verification: 61 host tests passed, zero failures/errors. Debug, instrumented-test and optimized unsigned release builds passed. Android lint has 0 errors and 34 warnings. The 29 native tests compiled but were not executed. APK signature matches 0.2.1, and 16 KB ZIP alignment passed.
