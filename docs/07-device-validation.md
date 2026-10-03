# Native validation checklist — pilot 0.3.3

Status on 2 October 2026: the fixed instrumentation suite passed all 31 tests on the connected Pixel 8 Pro. See [testing setup and fixes](14-testing-environment.md). The manual checks below are **instructions to perform**, not passed results.

Use a fresh test profile with fictional data. Instrumented tests use isolated databases; the manual steps alter your app's test records. Keep an encrypted backup if your installation already holds real data.

## Financial fixture

1. Set typical take-home pay to J$82,000, next payday seven days from today and opening cash to J$42,000. Typical pay must not increase Cash or count as received income.
2. Add a protected Savings account with J$126,000 opening balance. It must not increase the available pool.
3. Reserve a bill of J$12,000 due within the week, savings of J$8,000 with no due date, and debt of J$3,580 due on payday.
4. Verify available J$42,000, commitments J$23,580, safe-to-spend J$18,420 and daily guidance J$2,631.42 for seven days.
5. Pay J$6,000 of the bill from Cash. Cash and the unpaid reservation should both decrease by J$6,000; safe-to-spend should stay J$18,420.
6. Transfer the J$8,000 savings reservation to protected Savings. Its reserve should become zero and safe-to-spend stay J$18,420. A spendable destination must be rejected.
7. Record a separate J$100 expense with rapid double taps. Only one transaction should save. Overspending must remain recordable, with the resulting shortfall visible.
8. Link two refunds to an expense. Combined refunds must not exceed the expense; use the original budget group. Original deletion must be blocked while refunds remain.
9. In a separate clean allocation test, start with zero cash and record received J$82,000. Verify Needs J$41,000, Wants J$24,600, Savings J$16,400. Transfers must not increase income.
10. Try 60/25/20. Save must reject 105%; rebalance must preview exactly 100% before acceptance.

## Bills, goals and periods

- Create weekly, fortnightly and month-end recurring bills. Reopen repeatedly; no duplicate occurrences. Include February/leap years.
- Test due today, overdue, payday today, irregular planning, partial payments and cancellation of an unpaid series. No automatic income credit or divide by zero.
- Link savings deposits and withdrawals to a goal. Net contributions should change once; excessive withdrawal or deletion causing negative goal savings must reject atomically.
- Account inclusion changes the plan without changing balances. Goal progress is recorded contributions, not a verified bank balance.
- Verify manual new-period confirmation. Category limits are reusable targets; full archived period history/rollover is a Version 1 gate.

## Receipt capture and review

- On a fresh offline installation, recognise a receipt without downloading a model. Test declined camera permission, photo import, rotation and all crop edges.
- Use consented/redacted Jamaican receipts: faded ink, glare, folds, long receipts, weighted items, tax, discounts, cash/change and repeated totals. Measure total/date/merchant accuracy separately from item accuracy. No accuracy score is claimed.
- OCR must never save an expense/price automatically. Keep a draft, kill/reopen and find it in Activity. The original OCR draft survives; unsaved review edits are not yet a durable edited draft.
- Edit merchant/date/total, quantity, line totals and package sizes. Future dates, unverified lines and unreconciled totals must reject. Total-only save emits no product prices.
- Test exact and merchant/date/total duplicates, save-anyway acknowledgement and linking an existing matching expense without a second account movement.
- Test no text, unreadable image, crop cancellation and storage-full failures. Temporary captures should be cleaned after processing; retained photos should be encrypted.
- Remove a receipt keeping its expense, then one with its expense. Price observations must disappear. Item corrections currently require removal/re-review.

## Shopping, reports and accessibility

- Test no prices, partial coverage, stale prices, optional items and fractional quantities. Missing prices block a complete verdict. Estimates identify historical sources.
- Compare compatible exact product/pack/unit observations. No barcode aliases, cross-pack matching, live quotes, branch optimisation or prediction range are implemented.
- Set a Groceries limit, spend/refund against Groceries/Needs and check both grocery remainder and safe-to-spend independently.
- Search records/products/branches; compare explicit equal-length windows. Transfers stay outside spending totals.
- Test 320dp phones, 600dp+ windows, 200% font scale, long names/amounts, TalkBack, keyboard focus and dark mode. Wide windows have a rail; fold-hinge/two-pane handling remains a gate.
- Test background app lock, biometric rejection and device-credential fallback on API 26, 29 and 36+. No brief data exposure while locked.

## Backup, privacy and reliability

- Export records/photos and restore onto a fresh installation from setup. Compare every balance, split, bill payment, goal, receipt, price and shopping item.
- Wrong password, modified ciphertext, unsupported version, malformed rows, missing photos and interrupted restore must preserve the current ledger on validation failure. Test the bounded 50 MB format and low-memory behaviour.
- Device-key loss must fail visibly. In-app recovery of an unreadable existing Keystore database is unfinished; retain portable backups and test fresh-install recovery.
- Verify encrypted reopen, WAL/temp files, OEM backup exclusions and logs. Run the compiled encrypted-reopen test; compilation is not device encryption evidence.
- Inspect actual SDK/network behaviour and complete ML Kit disclosures. INTERNET is removed from the merged manifest; this is not a full SDK/IPC audit. Google's [ML Kit disclosure](https://developers.google.com/ml-kit/android-data-disclosure) describes diagnostics/usage collection, so no blanket zero-telemetry claim is made.
- Test CSV quoting/formula neutralisation and explicit local deletion. External backups remain; flash secure erasure is not guaranteed.
- Test private reminders under battery restrictions and after reboot. WorkManager scheduling is approximate.
- Benchmark startup, 10k+ records, OCR p50/p95 and peak image/backup memory on representative lower-end hardware. Snapshot reads and lists are currently not paged; optimise after measurement.

## Public-release gates

Optional identity/cloud backup, key recovery, provider/data-location/cost review, period history, advanced debt/credit-card/multi-currency behaviour, device acceptance, OCR corpus results, SDK privacy review, legal/store disclosures and production signing remain outstanding. This pilot is not store-ready.

## Update 0.2.0 theme/scanner matrix

- Check a fresh launch and existing-profile upgrade on API 26, 30, 31 and 36. Existing balances, receipts, backups and preferences must survive an in-place update.
- Test Light, Dark, AMOLED and System in day/night system modes. On API 31+, exercise wallpaper colors on/off. Try Palm/Ocean/Plum/Terracotta/Gold/Rose and custom white, black, yellow and saturated colors. Check text on every surface, outline, dialog, lock screen, chart and navigation item.
- Test 320dp, landscape, 600dp and larger windows with 100%, 150% and 200% fonts, TalkBack, keyboard open and platform animation scale zero. Confirm full-row checkbox touch behavior and scrolling to every onboarding action.
- Run the new isolated image/OCR and theme UI instrumentation tests. Use real consented/redacted receipts from supermarkets, restaurants, pharmacies, hardware/wholesale stores and gas stations; record total/item recall separately.
- Test 100+ items, repeat purchases, tilted/perspective paper, glare, shadows, blur and faded thermal text. Compare original/enhanced output; detection may abstain. Correct crop mistakes using Restore original photo. Verify no clipped first/last item after tiling.
- Check quantity/unit-price columns, wrapped names, following-line amounts, discounts and inclusive versus additive GCT. Confirm the live reconciliation and unresolved-date warnings.
- Edit metadata/items, save as draft, relaunch and reopen. Verify all edits, including total and quantity, survived and must be rechecked. Rescan failure must preserve the old draft; successful rescan must not create an expense.
- Record processing time and peak memory on low-end hardware. Recognition scores select a candidate rather than proving correctness; review must always remain mandatory.

## Update 0.2.1 controls and settings

- Check setup and main titles below status/notification indicators on notch/cutout devices, landscape and 200% font scale. Check bottom navigation above gesture and three-button system navigation.
- Open dates in transaction, payday, bill, goal and receipt forms. Choose 31 January then February, and switch leap-day years. Verify cleared optional dates stay blank and saved dates remain correct after reopening.
- Enter and paste J$1,234.50 and $1,234.50, edit around commas, remove cents, enter whole dollars with a trailing dot and add cents using the explicit separator. Check exact saved cents. Invalid 12,50 must show an error rather than become 1,250.
- Check bottom navigation and rail in every theme/accent, including AMOLED, with selected/unselected items visible. Whole percentages should show 50%, while fractional values retain only necessary precision.
- More categories must begin collapsed, expand only when tapped, announce their state to TalkBack and preserve all prior account/report/receipt/privacy/backup controls. Test one-category expansion, long text and large fonts.

## Update 0.3.0 charts, motion and calendar

- Home and Plan: switch Needs/Wants/Savings and reconcile exact category totals, refunds, remaining target and over-target values. Verify savings transfers never count as expenses, and all-period goal balances are labelled separately. Exercise empty periods, refund-only periods and fractional/tiny allocation shares.
- Test the thicker ring, percentage labels and meters on 320dp phones, large screens and 200% fonts. Check long category names and large monetary amounts. Verify navigation-panel top/bottom spacing with gesture and three-button system navigation.
- Try Calm, Slide, Expressive and Off, relaunch to check persistence, and set Android animator duration scale to zero. Check tab changes, settings expansion, charts, saved-data updates, keyboard and dialog transitions. No excessive bouncing or lost input focus.
- Calendar: navigate months/years, choose leap days, use text entry, cancel without changes and clear optional dates. Test Jamaica/Bogota and positive-UTC timezones, compact landscape, large fonts and TalkBack.
- Enable app lock: system prompt must appear immediately. Test biometric success/failure/lockout, credential fallback, cancellation, rotation during both prompt types, background/relaunch and process recreation. Confirm no private-content flash, duplicate prompt or authentication loop.
- Check label/checkbox spacing, full-row toggles and camera flash on small screens and with TalkBack.

## Update 0.3.1 forms, controls and allocation

- Bottom panel: check its lower position and reduced padding on gesture/three-button navigation, cutouts, landscape and 200% font scale. All five destinations remain readable and tappable.
- Plan: verify shared-scale allocation bars against exact received income, custom percentages and cent rounding. No-income states must not imply money exists; transfers remain excluded. Recorded spending/refunds remain in the separate category selector.
- Money entry: test expense, income, transfer, linked/unlinked refund, positive/negative adjustment and bill payment. Scroll with the keyboard open, ensure Save stays reachable, test closing/swiping/back, and prevent dismissal/duplicate saves while busy. Exercise category splits, invalid amounts, one-account transfers and conflicting source/destination choices.
- Dropdowns: verify field-width anchoring, selected checkmark, scroll limits, keyboard avoidance, long labels, disabled/empty options and preserved account/refund IDs in all themes.
- Preferences: change Square/Soft/Rounded and Compact/Comfortable, relaunch and verify persistence. Buttons, input fields and menus update immediately. Font scale and 48dp touch targets remain usable.

## Update 0.3.2 merged Plan and selection cues

- Plan has one budget card: one title/income caption, allocation bars, percentage-edit action and category activity. Check zero income, custom allocations, refunds and savings, and compare every value with the ledger.
- Switch all five navigation destinations on compact and wide screens. Verify selected semantics/TalkBack, filled vs outlined icons, label weight, outlined tonal background, and fixed icon/touch positions.
- Try all themes, wallpaper/custom colors, Square/Soft/Rounded corners and animation styles, including Off and system reduced motion. Check 320dp and 200% font scale; no clipped labels or doubled selection backgrounds.

## Update 0.3.3 donut and immediate navigation

- Switch and re-tap all five main destinations: no press ripple, animated highlight or page transition. Selected cues remain immediate. Test keyboard/D-pad focus and TalkBack on both bottom bar and wide rail.
- Plan's allocation is a donut in one merged card. Verify category proportions, exact currency allocations, cent rounding, zero income, zero shares and tiny custom shares. Confirm small/large-font percentages move into the legend without overlapping.
- Test all themes and animation styles. Main navigation remains immediate in every style; charts/settings/category transitions still follow the selected motion setting and Android duration scale.
