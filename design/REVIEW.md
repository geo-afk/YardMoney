# Phase 7–8 — Design review and approval gate

The requested pre-implementation materials are prepared. **Production implementation has not started.** Working name: YardMoney. User selected optional account/backup for first release; pilot remains local, Version 1 includes the optional service subject to provider/privacy/cost/recovery decisions. Automatic bill reservation is a disclosed proposal; the second product question has not been answered yet.

## Open the review

Open `index.html` directly or run `node design/serve.cjs` from the project root, then visit http://127.0.0.1:4173. Use the screen picker/sidebar for all **36 screen concepts**, or Screen gallery for the **11 prioritised screens**. Palm/Harbour controls offer green/blue directions; dark mode changes surfaces and chart colours. The seven scenario options include normal, empty, loading, error, over-budget, low-confidence and offline.

Screenshots are in `design/screenshots/`: dashboard-light.jpg, dashboard-dark.jpg and priority-gallery.jpg. They show the browser review, not Android execution.

## Design assessment and changes

- Safe-to-spend leads with payday and an explanation of unique commitments. Ring 44% names included funds as its denominator.
- Scan receipt appears on Home and Transactions. Receipt review makes uncertain content explicit and blocks the sample confirm control until checked.
- Custom percentages reject an invalid total; auto-rebalance previews 57/24/19 before acceptance for the 60/25/20 example.
- Historical prices carry branch/date/unit price; shopping estimates expose stale/missing prices and differentiate grocery remainder from safe-to-spend.
- Optional backup/account appears in Settings and Data; local use remains available without sign-in.

## Responsive behaviour / accessibility / motion

Responsive browser review uses a sidebar on desktop and native screen selector on narrow widths. The phone frame preserves single-column reading. Narrow safe-to-spend now places its ring beneath the amount to keep the number intact. App account rows wrap rather than overflow. The planned native tablet UI uses a rail and two panes but has not been built.

Native HTML controls, persistent input labels, chart text alternatives, labelled navigation, visible focus and ≥48px main controls are in the prototype. Gallery controls are disabled examples. Contrast and target sizes need a complete accessibility audit; 12px supporting prototype labels will be revisited during native large-font tests. There are no blocking animations; reduced-motion CSS is included. No TalkBack/conformance claim.

## Validation performed (2026-10-02)

**Tested:** Node syntax checks passed for prototype.js and serve.cjs. In-app browser rendered all 36 screens at 320, 390, 768 and 1280px: **144 screen/width checks**, no horizontal page or phone-content overflow after fixes. Initial 320px testing found a wrapped headline amount and an account-row overflow; both were corrected and rerun.

**Tested:** 105% allocation blocked save; preview generated 57/24/19; accepting enabled save. Receipt confirmation initially disabled and enabled after the review checkbox. Light/dark screens rendered; gallery contained 11 priority frames. All seven scenarios rendered with no phone-content overflow at 1280px. Keyboard Tab moved from Needs % to Wants %. The safe-to-spend breakdown expanded correctly. Browser log inspection returned no warning/error entries. Final screenshots saved; temporary viewport override reset and review tab retained.

**Reviewed:** Sample financial arithmetic, navigation mapping, no persistence/network requests, typed confirmation/deletion proposals and raw-versus-confirmed OCR contract. Node/HTML only; no Android build/unit/migration tests apply yet.

**Remaining limitations:** No real camera/OCR, authentication, backup, tax calculator or ledger. Form demos do not model every transaction type, recurring schedule or changed-receipt total. Scenario selector uses reusable state examples rather than bespoke states for every screen. Large-font/zoom, forced colours, automated axe and screen-reader testing have not been performed. Real Jamaican interviews, current legal/payroll verification, receipt benchmarks, native tablet/foldable and device-security tests remain gates. This prototype approves layout/flows, not production behaviour.

## Questions for approval

1. Which design do you prefer: Palm or Harbour?
2. What should change?
3. Is there another colour direction you prefer?
4. Is the dashboard easy to understand?
5. Is receipt scanning easy to find?
6. Are the charts useful?
7. Is navigation clear?
8. Should anything be removed?
9. Should anything be added?
10. Do you explicitly approve moving into MVP implementation, or want revisions first?

Your written approval in chat is the phase-9 gate. No permission has been requested for external services or spending because none have been created.
