> Historical 0.3.2 update. See [current donut and immediate navigation](13-donut-immediate-navigation.md) for 0.3.3.

# Plan and navigation refinement — 0.3.2

Implemented on 2 October 2026 in the existing Android app.

## Plan

The previous update split the allocation summary and recorded activity into separate cards. That changed the order and repeated the same received-income context. Plan now has one “Your budget, balanced” card with a single income caption, the three allocation bars, Change percentages and the Needs/Wants/Savings recorded-activity selector. There is no separate “Allocated from received income” heading/card. Home retains its allocation ring and category activity in the same shared component.

Income and allocations are calculated once for the card. Bars preserve exact-cent allocation and the common income scale. Recorded spending still subtracts refunds, transfers stay excluded, and goal balances remain explicitly separate from spending.

## Navigation

The selected destination combines a filled icon, semibold label, and a restrained tonal background with a fine outline. Inactive destinations use outlined icons and normal labels. The background follows the selected Control corners preference instead of imposing a full pill. Its color/outline transition uses the animation preference. The icon box keeps a fixed size so selection does not move the touch target or adjacent items.

The bottom bar and wide-window rail use the same treatment. Existing Material NavigationBarItem/NavigationRailItem selection semantics, labels, focus behavior, ripples and touch handling remain in use. Theme contrast pairs continue to supply foreground and background colors.

Research: [Android navigation-bar guidance](https://developer.android.com/develop/ui/compose/components/navigation-bar) supports consistent labelled destinations with explicit selected state; the visual treatment here is YardMoney's adaptation of the [Material navigation pattern](https://m3.material.io/components/navigation-bar/guidelines). Selection has icon/weight/outline cues in addition to color. This is a design choice, not a claim that it universally outperforms the stock indicator.

## Verification

See [APK verification](../dist/verification.json) for exact build/test results and checksum. The compiled category-switching test now exercises the Plan allocation-bar variant. No phone/emulator is attached, so visual contrast, selected-state accessibility and narrow/large-font layouts still need device checks.

Version code 6, application ID jm.yardmoney, database schema 1 and development signer are preserved for an in-place update. Install over the current pilot without uninstalling.

Final verification: 61 host tests passed, zero failures/errors. Debug, instrumented-test and optimized unsigned release builds passed. Android lint has 0 errors and 36 warnings. The 31 native tests compiled but were not executed. APK signature matches 0.3.1, and 16 KB ZIP alignment passed.
