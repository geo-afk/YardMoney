> Historical 0.2.1 update. See [current charts, motion and calendar](10-charts-motion-calendar.md) for 0.3.0.

# Android controls and settings — 0.2.1

Implemented in the existing Kotlin/Compose application on 2 October 2026.

## Changes

- Dates use an expandable selector with day, month and year dropdowns. Display dates use an unambiguous month name; storage stays ISO. Month/year changes clamp invalid days, including leap years. Optional dates can be cleared.
- Setup uses safe drawing insets. The main title has system-bar insets and additional top spacing to keep it below notifications/status indicators.
- Monetary fields show a J$ prefix, comma grouping and two decimal places when unfocused. Editing preserves partial cents and maps caret positions around separators. The keyboard requests decimal input, with an additional cents-separator button. Pasted dollar prefixes and valid thousands separators normalize to exact-cent input; ambiguous decimal commas remain errors. A trailing decimal marker saves as whole dollars without rounding.
- Bottom navigation uses an inset rounded container and explicit theme foreground/container roles for selected and unselected items. The wider navigation rail follows the same theme roles.
- The allocation card says “Your budget, balanced”; Needs, Wants and Savings carry their individual percentages. Percentage displays omit unnecessary decimal zeros while retaining values such as 12.5% or 33.33%.
- More starts with collapsed categories: Appearance, Accounts, Insights & search, Receipts, Notifications, Security & privacy, Backup & data, and About. Tapping a category reveals its existing controls and closes the previous category. Rounded tonal headers, icons, animation and accessibility state descriptions make the hierarchy explicit.

The visual reference was [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer), particularly its dark rounded surfaces, tonal actions and inset navigation. Its code and assets were not copied. This update uses public stable Material 3 and Compose components with YardMoney's existing theme system.

## Verification

- 58 host tests passed, with zero failures/errors. New tests cover percentage precision, exact currency normalization, caret offsets, invalid decimal commas and leap-day/month transitions.
- Debug APK, instrumented test APK and optimized unsigned release all built successfully.
- 27 instrumented tests compiled; none ran because no phone/emulator is connected. New native tests cover dropdown dates, formatted currency editing and expandable settings.
- Android lint: zero errors and 33 warnings. The lint report remains under app/build/reports.
- Development signature verified and matches the 0.2.0 APK. ZIP alignment passes the 16 KB check. Native ELF/runtime compatibility still needs device validation.
- Application ID jm.yardmoney and database schema version 1 are unchanged; version code is 3. Install over the earlier pilot without uninstalling to retain local data. Production signing and optional cloud accounts remain outstanding.

Phone checks for title placement, keyboard/caret behavior, navigation contrast and large-font settings are still required. See [device validation](07-device-validation.md) and [APK verification](../dist/verification.json).
