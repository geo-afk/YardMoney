# Donut allocation and immediate navigation — 0.3.3

Implemented in YardMoney on 2 October 2026.

## Changes

Main-menu navigation now switches immediately. Tap ripples and animated selected highlights are disabled for the bottom navigation and wide-window rail, and main-page fade/slide/scale transitions are removed. The selected icon, label weight and tonal outline still identify the current page. A static keyboard-focus outline remains. Other controls keep their normal interaction feedback, while chart/category/settings motion continues to follow the animation preference.

Plan replaces the line-style allocation bars with a thick, three-segment donut for Needs, Wants and Savings. It stays inside the merged “Your budget, balanced” card, followed by percentage controls and recorded activity. Home uses the same donut component.

The chart shows the target budget proportions, with exact currency allocations alongside the category names. Percentages remain beside ordinary slices. At large font scales or when a slice is below 8%, percentages move into the category legend to avoid crowding. Zero-share categories remain labelled. Small slices use proportional gaps rather than an artificial minimum sweep. The no-income caption still explains that the plan has no received funds yet. Transfers stay excluded from received income and spending.

## Research

[ONS pie/doughnut guidance](https://service-manual.ons.gov.uk/data-visualisation/chart-types/pie-and-doughnut-charts) supports using a small number of categories to show parts of a whole. This view has three named budget shares totalling 100%, so a donut fits the requested visual summary. Explicit percentages and currency values preserve precise reading.

[Android ripple guidance](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple) documents disabling a component's ripple with LocalRippleConfiguration set to null. This is scoped to navigation items; it is not a global removal of interaction feedback.

## Verification and update

See [APK verification](../dist/verification.json) for test/build results and checksum. Application ID jm.yardmoney and database schema 1 remain unchanged; this build uses version code 7. Install over the current pilot without uninstalling.

No phone/emulator is attached, so native visual and interaction checks remain unrun. Check immediate navigation, keyboard focus, theme contrast, large-font legends, tiny/zero shares and exact allocation values using the [device matrix](07-device-validation.md).

Final verification: 61 host tests passed, zero failures/errors. Debug, instrumented-test and optimized unsigned release builds passed. Android lint has 0 errors and 36 warnings. The 31 native tests compiled but were not executed. APK signature matches 0.3.2, and 16 KB ZIP alignment passed.
