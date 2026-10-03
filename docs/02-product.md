# Phase 2–3 — Product requirements and information architecture

Status: design and implementation approved, 2026-10-02. User preference: **optional account and backup for first public release**. Core budgeting works without registration. Visible, editable bill reservations are included in the approved implementation. This document retains the release requirements; pilot scope and outstanding public-release gates are recorded in [implementation status](06-implementation.md).

## Product outcome and assumptions

Working name YardMoney. The core question is “Will my money last until payday?” followed by “Can I afford this list?” All displayed prices in the prototype are fictional, including named-store observations. Audience: adults initially; student support includes adult students. Serving minors is a separate legal/product gate. No payment initiation, regulated advice, bank credential capture, advertising or mandatory cloud.

Cash is a first-class account. Jamaica uses JMD with J$ display; monetary storage preserves cents even when a whole-dollar display is clearer. Optional USD/CAD/GBP/EUR accounts never silently add to JMD totals. Primary date display is unambiguous, e.g. 9 Oct 2026; pay schedules default to America/Jamaica. Location and profile name are optional.

## Personas and journeys — hypotheses for interviews

| Persona | Context | Main journey | Risk to address |
|---|---|---|---|
| Geovanni, salaried worker | Fortnightly take-home, taxi and household bills | Receive pay → allocate → protect bills → check daily guidance | Confusing fortnightly with twice monthly |
| Alana, hospitality worker | Tips and variable weekly pay | Enter received income → choose conservative plan → revise after tips arrive | Treating expected income as spendable |
| Marcia, household shopper | Cash/bank mix and supermarket receipts | Make list → inspect coverage → shop → review receipt → remember branch price | Stale or mismatched product comparisons |
| Daniel, adult student | Remittances, irregular support, tuition goal | Record support/cash → protect fees → plan transport → track goal | Too many onboarding questions |

Proposed usability success criteria: at least 4/5 recruited users can explain safe-to-spend and find receipt capture without assistance; first usable setup within three minutes in moderated sessions; no participant mistakes historical prices for current quotes. These are targets, not measured results.

## Feature grouping and release sequence

MVP means an internal local-first pilot; **Version 1 means first public release**. This staging accommodates the user's optional-account request without introducing an unselected cloud provider into the pilot.

| Group | MVP pilot | Version 1 / first public release | Version 2 | Future |
|---|---|---|---|---|
| Payday and budgets | All pay-frequency choices, manual net income, estimates separated, 50/30/20/custom, safe-to-spend | Period history/rollover and recurring irregular-income presets | Cash-flow forecasts | Advanced allocation assistance |
| Records | Cash/bank/savings/wallet, manual expense/income/transfer/refund/adjustment, splits, corrections | Credit-card accounting, loans/investments, reviewed CSV import | Rich reconciliation | Verified bank/wallet feeds |
| Bills and goals | Due occurrences, reminders, reserved commitments, simple goals, minimum debt obligation | Debt tracking with documented interest assumptions | Payoff scenarios | Investments and household goals |
| Receipts and prices | Bundled OCR, manual crop, review, duplicate warning, confirmed exact-product prices | Better long-receipt capture, tested perspective correction, more parser formats | Barcode/matching assistance | Opt-in community/live merchant data |
| Shopping | Local lists, quantities, recent/stale/unknown coverage, preferred-branch estimates | Alternatives and richer list management | Optional list sharing | Travel-aware optimisation |
| Data and security | App lock, encrypted local store, CSV export, encrypted local backup/restore, delete controls | **Optional account and encrypted cloud backup**, recovery and deletion | Optional multi-device sync | Household collaboration |
| Reports | Labelled donut/rings, category bars, product line history, light/dark | More period comparisons and accessible summaries | Forecast visualisation | Personalisation/AI explanation |

Cloud backup is a snapshot, not concurrent synchronisation. The first-release account/backup feature requires provider, key recovery, ownership, server-side deletion, privacy/transfer review and cost approval if paid. No external accounts or paid dependencies have been created.

## Functional requirements and acceptance

| ID | Requirement | Acceptance condition |
|---|---|---|
| F01 | Progressive onboarding | Currency → pay schedule → next payday → typical net pay → budget method; bills/balances prompted next with skip. Optional profile/account comes later. Never imply income equals current balance. |
| F02 | Pay scheduling | Weekly +7d; fortnightly +14d; twice monthly two explicit anchors; monthly handles end-of-month policy; freelance/irregular uses manually confirmed planning date. Holiday adjustment is user-defined. |
| F03 | Budget allocations | Basis-point percentages in [0,10000], sum exactly 10000. J$82,000 → J$41,000 / J$24,600 / J$16,400. Auto-rebalance previews its changes before acceptance. Largest-remainder cent allocation preserves total. |
| F04 | Income | Record salary/tips/overtime/remittance/rental/benefit/other received amounts separately from expected income. Net is default; gross estimates later. Zero/negative regular income invalid; opening empty budget still allowed. |
| F05 | Safe-to-spend | Included liquid balances minus unique active commitments; show source balances, last confirmation and reservations. Exclude credit limits, investments and protected accounts. Negative result shows shortfall; never clamp into a misleading healthy state. |
| F06 | Daily guidance | Divide positive safe-to-spend by whole days until planning payday; label guidance. On payday today or overdue, ask to confirm income/new date; do not divide by zero or assume income arrived. |
| F07 | Ledger records | Positive expense/income/refund values; signed balance adjustments allowed with explanation. Transfers paired atomically. Refund references original spending when possible. Category split sum equals transaction amount. |
| F08 | Bills | Recurrence template produces uniquely identified due occurrences. Reserve only unpaid/unfunded amounts; paying links ledger entry and releases corresponding reserve atomically. Estimated variable bills visibly labelled. |
| F09 | User control | Can reclassify categories, override warnings and move earmarks. Overspending is a financial state, not an invalid expense. Destructive operations disclose consequences and require confirmation. |
| F10 | Receipt capture | Contextual camera permission, photo-picker import/manual fallback, retake/crop; clear quality warnings; cancellation retains or discards draft explicitly. Offline first-use OCR works with bundled model. |
| F11 | Review/save | Raw text preserved; editable merchant/date/currency/items/tax/discount/total; uncertain fields flagged. Resolve or acknowledge discrepancies; allow total-only expense when items cannot be trusted. No unverified price observation. |
| F12 | Duplicate handling | Submission idempotency prevents double taps. Probable duplicate offers view/link existing, save anyway or cancel. Receipt linked to existing expense adds no second expense. |
| F13 | Price notebook | Confirmed line → observation with product variant/pack, quantity, final paid amount, currency, branch and date. Editing/deletion refreshes stats. Unknown branch remains unknown. |
| F14 | Unit pricing | Compatible mass/volume/count units only. Multi-packs and discounts allocated deterministically with rounding conservation. Deposit/fees distinct from comparable product cost. |
| F15 | Shopping | Required/optional, quantity, brand/size, notes. Estimate explains branch policy, price age and coverage. Unknown items block “complete estimate”; user can supply assumptions. Check against grocery remaining and safe-to-spend separately. |
| F16 | Goals/debt | Protected savings ownership explicit. Physical savings transfer is not an expense; minimum debt already reserved under Needs cannot also be reserved under Goals. Extra payment is a separate commitment. |
| F17 | Search/reporting | Search records, receipts/products/branches/lists locally, filter by date/pay period/account/currency. Transfers excluded from expense totals. Unequal-period comparisons show length and daily rate. |
| F18 | Data controls | Export preview, private sharing warning, JSON backup schema/version, encrypted restore with integrity/validation and recovery on failure. Image cleanup retains confirmed records. Delete receipt offers keep expense/delete linked expense, and explains price consequences. |
| F19 | Settings | Currency/theme/pay schedule/budget/notifications/privacy/security/data/receipt/location/backup/accessibility/about. Every notification type independently configurable and privacy-preserving on lock screen. |
| F20 | Optional identity/backup | Continue without account remains available. Opt-in shows upload scope, location of processing, retention, costs, encryption/recovery. Local data is usable when signed out or offline; account deletion and local wipe are distinct. |

## Safe-to-spend contract and coherent sample

At 2 Oct 2026, payday 9 Oct (7 days): confirmed cash/current/wallet funds **J$42,000**. Unpaid bills J$12,000 + unfulfilled savings commitment J$8,000 + minimum debt payment J$3,580 = protected **J$23,580**. Safe-to-spend **J$18,420**, guidance approximately **J$2,631/day**. Ring: 44% of J$42,000 is safe, not “62% remaining”. Account savings J$126,000 is excluded from the J$42,000 pool. A savings payment already moved into that excluded account must not be subtracted again.

Period allocations use received net income J$82,000, not current balance. Needs spent J$27,400 / J$41,000; Wants spent J$10,000 / J$24,600; Savings/debt fulfilled J$8,000 / J$16,400. Category groceries spent J$13,600 / J$20,000 → J$6,400 remaining. Period income and category totals are illustrative subsets, not a full reconciled ledger; never infer current balance from their difference.

Shopping example: rice J$980 + chicken J$1,950 + eggs J$780 + milk J$450 + bread J$500 + oil J$690 + tissue J$450 = **J$5,800**. Six recent prices plus one stale oil price; all fictional. Indicative range J$5,450–J$6,350. Grocery remainder J$600 at central estimate; safe-to-spend after purchase J$12,620. Required products with no price show a partial subtotal instead.

## Non-functional requirements — proposed measurable gates

- Core use without internet, mandatory login or location. Zero recurring infrastructure for pilot; first-release backup cost to be evaluated.
- Financial invariants at UI/domain/database layers; atomic save and no destructive migration fallback.
- Android candidate minSdk 26 for manageable security/device support; validate Jamaican device distribution before finalising. Target SDK/dependency versions pinned when implementation starts.
- On representative 4GB Android hardware: cold-start target <2s, usable dashboard <1s after local read, 10k records scroll without persistent frame stalls; single receipt OCR target <5s p95 after warm-up. Benchmark gates, not results.
- Bound image resolution/memory, cancellable background work, drafts survive process death, low-storage save errors preserve input.
- 48dp targets, 200% font scale, TalkBack/Switch Access, labelled chart data, no colour-only status; WCAG-aligned contrast targets without a conformance claim.
- Encrypted local structured data/images; audited SDK telemetry; explicit encrypted backups and user-controlled recovery. No logs containing OCR, amounts or account identifiers.

## Navigation and screen inventory

Phone destinations: **Home / Transactions / Plan / Shop / More**. Scan is a Home quick action and prominent Transactions action, not a sixth destination disguised as navigation. Plan contains budgets, bills, goals and debt; Shop contains lists, product history and branch comparisons. More contains accounts, reports, insights, search and settings. Expanded windows use a rail and list/detail panes; fold hinges must not split money cards.

All 36 brief screens are in the review prototype: Splash, Welcome, Initial onboarding, Payday frequency, Next payday, Income setup, 50/30/20 setup, Custom allocation, Bills onboarding, Dashboard, Budget overview, Category detail, Transaction list, Add transaction, Receipt camera, Receipt processing, Receipt review, Receipt details, Product history, Store comparison, Shopping lists, Shopping list detail, Shopping cost estimate, Accounts, Savings goals, Savings detail, Debt list, Debt detail, Reports, Insights, Search, Notification settings, Privacy, Security, General settings, Data export/delete. Debt details and advanced accounts are labelled Version 1 concepts.
