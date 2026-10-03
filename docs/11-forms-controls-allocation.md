> Historical 0.3.1 update. See [current merged Plan and navigation](12-merged-plan-navigation.md) for 0.3.2.

# Android UI update — 0.3.1

Implemented directly in the existing application on 2 October 2026.

## Changes

- Bottom navigation has a 4dp outer bottom gap rather than 8dp, and a 76dp visible height rather than 80dp. This lowers its edge slightly and reduces internal spare space. Large-font configurations use 96dp to protect labels. Android navigation insets remain outside the panel.
- Plan replaces the repeated allocation figures with three 16dp horizontal bars on a shared total-income scale. Each row retains its exact currency value and target percentage. Empty income gives an explanatory empty state. The separate recorded-activity card retains Needs/Wants/Savings switching without repeating the allocation ring.
- Record money uses an expanded modal sheet with a scrollable body and a fixed Save footer. Type, Amount and date, Account, and Details are grouped into tonal sections with clear headings. Optional category splits appear only when enabled. Transfer account selection repairs a conflicting destination, and Save waits for valid amount/date/account inputs. Existing repository validation, exact-cent handling and submission keys remain in place.
- All shared choices use Material exposed dropdown fields. Their menus are anchored to the field, match its width, have bounded scroll height and mark the selected option. Account and refund selectors retain IDs separately from display labels. Disabled/empty choices cannot open menus.
- Appearance includes Control corners (Square, Soft, Rounded) and Layout spacing (Compact, Comfortable). Soft is the default: 12dp corners without full pill shapes. Choices persist and update live. The corner preference applies to buttons, outlined inputs, menus and category segment ends; section spacing applies to pages, settings groups and the new form. Compact retains existing touch targets. Theme, color and animation preferences remain available.

## Research and rationale

[Material exposed dropdown APIs](https://developer.android.com/reference/kotlin/androidx/compose/material3/ExposedDropdownMenuBoxScope) provide a non-editable field anchor and anchor-width menu behavior. This replaces the previous unrelated button/popup sizing. [Android menu guidance](https://developer.android.com/develop/ui/compose/components/menu) supports scrollable option menus with selected-state cues.

The entry flow follows the [Compose modal-sheet pattern](https://developer.android.com/develop/ui/compose/components/bottom-sheets), with explicit close, grouped fields, scrolling and a persistent action. [Material text-field guidance](https://m3.material.io/components/text-fields/guidelines) informs labels and input hierarchy. This is an implementation choice for the long form, not a claim that all forms must use sheets.

[ONS chart guidance](https://service-manual.ons.gov.uk/data-visualisation/chart-types/choosing-a-chart-type) recommends consistent baselines for comparison. The Plan chart uses a common zero-to-total-income scale, direct category/amount labels and consistent Needs/Wants/Savings colors. Budget targets remain separate from recorded spending and account balances.

## Validation and update

The exact build/test totals and APK checksum are in [verification](../dist/verification.json). Device test coverage includes dropdown width/ID selection and long-form scrolling with a visible Save action. These tests are compiled only until an Android device is available.

Application ID jm.yardmoney and database schema 1 remain unchanged; version code is 5. Install the update over the existing pilot without uninstalling. No accounts, transactions or receipts are migrated or reset by this update. This remains a development APK.

Phone checks are still needed for compact/large-font navigation, dropdown placement with the keyboard, sheet scrolling/focus, all transaction types and live preference changes. See [device validation](07-device-validation.md).

Final verification: 61 host tests passed, zero failures/errors. Debug, instrumented-test and optimized unsigned release builds passed. Android lint has 0 errors and 36 warnings. The 31 native tests compiled but were not executed. APK signature matches 0.3.0, and 16 KB ZIP alignment passed.
