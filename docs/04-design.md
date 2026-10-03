> Design baseline approved for 0.1.0. The implemented 0.2.0 theme customization, contrast, scanner and chart changes are documented in [the update notes](08-update-0.2.0.md).

# Phase 5–6 — User flows and UI/UX direction

## Flows

1. **First use:** welcome → JMD/default local mode → pay frequency → next payday → net income → 50/30/20 or custom → optional bills → add current account balances → Home. No account is required; optional account/backup is offered later in More. Freelance/irregular users set a planning date and labelled income basis rather than a fictional recurring payday.
2. **Receive pay:** add received income → select account → confirm date → preview period allocations and existing reserves → create/refresh period once → Home. Estimated income becoming received links the existing plan; it is not added twice. Missed payday asks for correction, never auto-credits a bank balance.
3. **Spend:** amount → account/category → optional split/receipt → preview impact → save → undo/edit. Warn if a bucket is exceeded; permit continue, move earmark or cancel. Transfers/refunds are distinct types.
4. **Scan:** visible Scan receipt → contextual camera permission or import/manual fallback → capture quality → crop/rotate/retake → local OCR → review flagged fields and image → reconcile total → duplicate check → link existing or save new. Failed save preserves draft. Total-only save does not generate line prices.
5. **Shop:** new list → items/quantities/required flag → choose branch policy → inspect recent/stale/unknown coverage → estimate versus grocery budget and safe-to-spend → revise optional items → shop → receipt review. A price chart or branch result opens dated evidence, not a live-store claim.
6. **Bills:** Plan → occurrence → reserve explanation → mark paid → select account/existing transaction → atomic save/release → updated safe-to-spend. Autopay flag does not assume payment occurred.
7. **Goal:** choose amount/target → reserve contribution → optionally record transfer into protected savings → update progress. Allocating money does not move bank funds; no external payment initiated.
8. **Backup:** More → optional account → clear upload/encryption/recovery disclosure → opt in → verify recovery secret → snapshot → status. Continue locally/sign out always available. Restore previews data scope before replacement; local wipe and account deletion are separate choices.
9. **Delete/export:** preview included data → receipt image inclusion/redaction → user chooses destination → warning about external copies. Delete selected receipt explains price/expense effects; delete-all requires typed confirmation and separate backup deletion choice.

## Design assessment and direction

Home leads with payday and the safe amount, followed by the protected-money explanation, scan action, budget and upcoming bills. Avoid a dense bank-style dashboard. “Saved” is fulfilled contributions; “set aside” is earmarking; neither is the same as an account balance. Budget donut describes allocation, category rings describe usage, and the safe ring names its denominator.

Two proposed colour directions share the same hierarchy: **Palm** (forest/teal with warm ochre accents), and **Harbour** (navy/blue with ochre). Palm is the default recommendation. Neither is final branding. Light mode uses warm near-white surfaces; dark mode uses deep neutral greens/navy with lighter text and deliberately adjusted chart colours.

## Design system

| Token | Proposal |
|---|---|
| Typography | Android system Roboto in production; system sans-serif fallback in browser mockups; 16sp body, 14sp supporting text, 24sp titles, 36–40sp money. Tabular numerals. |
| Spacing | 4/8/12/16/24/32dp rhythm, 20dp phone gutters |
| Shapes | 24dp primary surfaces, 16dp secondary surfaces, 12dp inputs, pill actions |
| Elevation | Mostly tonal surfaces/borders; modest elevation for navigation/FAB only |
| Primary | Palm #145D4D; Harbour #245477; white on primary |
| Text/surface | #182F29 / #F7F8F4; dark #E7EFE9 / #101D19 |
| Positive | Teal with “On track” label; spending itself is neutral |
| Warning | Ochre with text and alert icon; errors dark red with label |
| Charts | Needs teal, Wants ochre, Savings/debt slate-blue; visible legend/amounts always |
| Fields | Persistent label, sample/help text, associated inline validation, no placeholder-only labels |
| Buttons | Minimum 48dp targets; primary verb, outlined secondary, text tertiary; visible keyboard focus |

## Chart contracts

- Allocation donut: 50/30/20 and J$82,000 with separate labelled bucket amounts; segments navigate to detail in Android.
- Safe ring: J$18,420 / J$42,000 = 44% of included funds; detail lists all reserves.
- Category ring: groceries J$13,600 / J$20,000 = 68% used.
- Distribution donut: expenses only, excluding transfers/goal transfers; text totals match segments.
- Planned versus actual: paired labelled bars for each bucket with explicit time/basis, not unexplained different donuts.
- Product history: date/value points plus a text table/list. Branch comparisons include unit prices and dates. Never join unlike sizes/branches into an apparently continuous current-price trend.
- Goal ring: J$126,000 / J$300,000 = 42%; forecasts only when contributions/date assumptions are shown.
- Shopping ring: J$5,800 / J$6,400 = 91% of grocery money; range and coverage remain visible.

## State and responsive behaviour

The prototype includes normal, empty, processing, error, over-budget, low-confidence, offline and dark states. Every asynchronous Android screen will follow the loading/success/empty/error/retry pattern where meaningful; offline core data is usable, not a fatal screen.

Phone design uses a scrollable single column and five labelled destinations. Tablet/foldable design will use a rail and two panes rather than simply stretching cards. The web review frame is responsive at 320px and expands to a review workspace on desktop; it demonstrates screens, not an implemented Android adaptive layout.

No essential colour-only cues. Charts have readable text; icon buttons have labels; sample forms use native controls. Avoid clipping J$1,250,000 or long branch names; abbreviations are optional with full-value access. Font scaling changes layout, not money meaning. Motion is limited to immediate colour/selection feedback and respects reduced-motion preferences. Camera view is simulated and labelled; no real image is captured.

## Review gate

After showing the prototype, ask the user which design/colour direction they prefer; whether dashboard, scan access, charts and navigation are clear; what to change/remove/add; and whether they explicitly approve moving to implementation. Approval applies to design, not unresolved legal/payroll/backup-provider gates.
