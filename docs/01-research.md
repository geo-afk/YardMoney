# Phase 1 — Research and evidence

Checked: **2026-10-02**, using the user's date context. Primary-source desk research; no user interviews or hands-on competitor benchmarks have been conducted. Facts, design inferences and unresolved release gates are separated below. A page being available today does not establish that every paragraph is up to date.

## Executive finding

Build a manual, cash-inclusive, local-first companion around **payday → next payday**, with protected commitments and a reviewed-receipt price notebook. The differentiator is a decision before spending: “Can this shopping list fit?” rather than only a report afterward. J$ presentation, fortnightly/twice-monthly distinction, branch/parish context and remittances should be structural features.

The market gap is a hypothesis supported by the product comparisons below, not proof that no competing Jamaican product exists. Validate with Jamaican users before release. Recruit salaried, cash-heavy, irregular-income and student participants across Kingston/St. Andrew, St. Catherine, St. James and rural parishes; test inexpensive phones and unreliable connectivity.

## Jamaican financial rule register

All entries checked 2026-10-02. All can change. These are research records, **not an approved executable payroll configuration**.

| Rule | Finding and effective date | Source | Confidence / next verification |
|---|---|---|---|
| PAYE threshold | Official indexed TAJ advisory via JIS states annualised J$1,902,360 effective 2026-04-01, previously J$1,799,376. | [JIS/TAJ advisory](https://jis.gov.jm/increase-in-income-tax-threshold-now-in-effect/) | Search-index evidence; direct page fetch returned 403. Reobtain full TAJ tables before enabling an estimator. Do not confuse annualised threshold with full-calendar-year reconciliation. |
| PAYE bands | Government circular documents 25% lower-band and 30% on income exceeding J$6m annually. | [MOF payroll circular, 2022](https://www.mof.gov.jm/wp-content/uploads/2022_21.pdf), [TAJ release via JIS, 2016](https://jis.gov.jm/30-income-tax-rate-applied-aggregate-income/) | Historical basis; confirm 2026 chargeable-income basis, reliefs, cumulative operation and period rounding. Not 30% on all pay. |
| Future threshold changes | MOF describes a phased rise to J$2,003,496 over three years. | [MOF revenue measures 2025/26](https://www.mof.gov.jm/wp-content/uploads/Revenue-Measures-2025-2026.pdf) | Indexed extract; PDF rendering failed. Treat future dates/values as proposed until full enacted schedule is verified. |
| NIS | MLSS 2022 sectoral statement states employee 3% and matching employer 3%, annual insurable wage ceiling J$5m effective 2022-04-01. | [MLSS statement hosted by JIS](https://jis.gov.jm/media/2022/04/Karl-Samuda-Sectoral-Debate-26.04.22-Approved-Full.pdf) | Historical official baseline; current [MLSS overview](https://www.mlss.gov.jm/departments/national-insurance-scheme/) still exposes 2.5%, a conflicting older value. Confirm latest regulations, period ceiling and cumulative treatment; never choose by crawl date alone. |
| NHT | Employee 2%, employer 3% of gross salary on the official employer page. | [NHT employer contributions](https://www.nht.gov.jm/employer-contribution) | Current official page; commencement date not specified there. Confirm legal emoluments/exclusions and employee applicability. Employer share must not reduce employee net pay. |
| Education Tax | MOF statement gives employee/self-employed 2.25%, employer 3.5%. | [MOF tax expenditure statement](https://www.mof.gov.jm/wp-content/uploads/Jamaicas-Tax-Expenditure-Statement-22024.pdf) | Historical official rate evidence; deduction basis, allowable NIS/pension adjustments and 2026 status remain release gates. Do not compute simply as gross × rate. |
| Pension, insurance, advances, loans | Separate employer-specific deductions with taxable-basis effects only if explicitly verified. | User payslip plus reviewed rule-set evidence | No generic exemption assumed; payslip amounts override estimates. No payslip upload needed. |
| FX | BOJ publishes reference exchange-rate information. | [BOJ foreign exchange market](https://boj.org.jm/market/foreign-exchange/) | Rates change frequently. Quote original/converted currency, buy/sell basis, rate date/source and fees. Reference rates do not promise a bank's actual conversion rate. |

TAJ's precise 2026 period tables, special exemptions, self-employed treatment and statutory calculation ordering have **not** been fully verified. Exclude gross-pay estimation from MVP; accept actual take-home pay. Budgeting never requires tax-identification numbers. Model all rules with jurisdiction, employee/employer scope, currency, basis, dependency ordering, rates/bands, ceiling period, effective interval, verification status, evidence, rounding policy and immutable version. Preserve the rule-set snapshot used for each historical estimate. Stale/unverified configurations cannot silently calculate a current payslip.

## Privacy and data protection

[OIC implementation notice](https://www.oic.gov.jm/press-release/six-month-grace-period-data-controller-registration-under-data-protection-act) identifies 2023-12-01 as implementation of the Data Protection Act. [OIC obligations](https://oic.gov.jm/press-release/obligations-data-controllers-under-data-protection-act-dpa) describes controller registration, conditional DPO requirements, annual impact assessments and a 72-hour breach-reporting requirement. Check the Act and current regulations with Jamaican counsel for applicability and exact notification duties; a local-only design does not automatically settle controller status.

[OIC standards](https://www.oic.gov.jm/page/data-protection-standards) address lawful/fair processing, limited purpose, minimisation, accuracy, retention, rights, safeguards and cross-border transfers. Apply those to receipts and histories: no receipt-content telemetry, optional location, access/export/correction/deletion, disclosed retention and no cloud by default. The OIC site also lists 2024 registration/disposal regulations; current forms and obligations require legal review.

Receipt text can reveal names, card fragments, loyalty IDs, phone numbers and health purchases. Financial data is operationally sensitive; do not equate that automatically with every statutory “sensitive personal data” category. Avoid extracting unnecessary identifiers. Encrypt private storage; redact shared exports, remove EXIF location, and make image retention a separate choice. Analytics and crash reports can create processing obligations even when the ledger remains local.

## Banking and payment landscape

[BOJ's 2025 annual report](https://boj.org.jm/wp-content/uploads/2026/03/2025-BOJ-Annual-Report-final.pdf) describes JAM-DEX wallets including Lynk and JN Pay at end-2025. That historical account does not establish the full October 2026 provider list. [BOJ's sandbox](https://boj.org.jm/core-functions/financial-system/payment-systems/sandbox/) supports regulated financial innovation; sandbox admission is not unrestricted permission to operate a payment service.

No universal public Jamaican consumer account-data API was verified in this research. This is a verification limitation, not proof that none exists. Manual cash, bank, savings, wallet and credit-card records remain the reliable foundation. Later integration needs a named provider, documented authorisation/OAuth, tested Jamaican institution coverage, agreement, privacy review and clear OFFICIAL / THIRD PARTY / EXPERIMENTAL labelling. Merchant payment acceptance APIs do not imply access to consumer transaction history. Never request banking passwords or scrape sessions. The app does not hold funds or initiate payments in MVP.

## Competitor research

Features below come from product-owner documentation checked on the research date. This is pattern research, not a recommendation to subscribe, a complete country-availability audit or a reproduction of proprietary screens.

| Product | Strength / pattern | Jamaica implication |
|---|---|---|
| YNAB | [Targets](https://support.ynab.com/en_us/getting-started-with-targets-ryAEP08xC), deliberate allocation and rollover education | Adapt “assign received money”; [documented import coverage](https://support.ynab.com/en_us/my-bank-isnt-listed-HJivlLavle) excludes Jamaican institutions. Avoid requiring bank linking. |
| Monarch | [Goal/account linking](https://www.monarch.com/features/planning), broader financial overview | Model protected savings clearly. [Official availability](https://www.monarch.com/download) states US/Canada; do not assume Jamaican support. |
| Rocket Money | [Recurring bills and subscriptions](https://www.rocketmoney.com/faq), progressive budgeting | Show upcoming commitments before more complex charts. Paid tiers differentiate customisation and services; avoid copying cancellation/bill-negotiation promises. |
| PocketGuard | [Safe-to-spend and several budget methods](https://pocketguard.com/) | Make the number explainable; separate actual cash, budget targets and estimates. |
| Goodbudget | [Envelopes and goals](https://goodbudget.com/help/customize-your-goodbudget/goals-and-annuals/) | Teach small earmarks and sinking funds without accounting terms. |
| EveryDollar | [Zero-based budgeting](https://help.ramseysolutions.com/hc/en-us/articles/360047082171-What-is-a-Zero-Based-Budget) | Complement percentage planning with “assign the remainder”; avoid a compulsory monthly period. |
| Spendee | [Wallet-specific budgets and daily guidance](https://help.spendee.com/article/131-budget-my-money) | Scope accounts/currency explicitly; daily guidance is not a spending instruction. |
| Wallet / BudgetBakers | [Planned payments](https://support.budgetbakers.com/hc/en-us/articles/7149523920786-Setup-Planned-Payments), records, cash and forecast patterns | Its documented end-of-month and duplication limitations motivate explicit recurrence rules and idempotency here. |

Qualitative complaint signals include [YNAB users reporting connection/value friction](https://www.reddit.com/r/ynab/comments/1ebpl46/ynab_can_you_please_give_us_tiered_pricing/), [PocketGuard users questioning the budget versus cash number](https://www.reddit.com/r/PocketGuard_app/comments/1e5wmn6), and [Expensify users reporting scan/upload failures](https://www.reddit.com/r/Accounting/comments/1loecqe/expensify_is_horrible/). These are self-selected anecdotes, not prevalence estimates or independently reproduced defects. Use them as interview prompts. Common usability risks to test: long setup, unexplained numbers, correction effort, subscription cost, excessive charts and unreliable sync.

## Receipt capture and OCR

Recommendation: CameraX capture plus **bundled Latin ML Kit Text Recognition v2**, behind ReceiptOcrEngine. [Official Android OCR guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) distinguishes bundled immediate availability from dynamic model downloads. OCR recognises text; it does not provide a complete merchant/item/tax parser or reliable product identity. Quality warnings, crop/perspective correction, parsing, reconciliation and duplicate checks are separate components. Benchmark actual Jamaican receipt formats; no accuracy claim is available yet.

[ML Kit Document Scanner](https://developers.google.com/ml-kit/vision/doc-scanner) adds automatic detection, cropping and cleaning through Play services; useful alternative capture adapter, with initial resource downloads and a less customisable flow. Evaluate low-memory/non-GMS devices before selecting it as default. CameraX gives capture control but does not supply automatic perspective correction by itself. Tesseract is a possible fallback with native packaging/tuning effort; no local benchmark currently establishes superiority.

Use OCR confidence where supplied, nullable otherwise. Field review priority combines text confidence, parsing ambiguity and arithmetic consistency; label “Needs checking”, not invented calibrated probabilities. Never use the object's detection-confidence API as receipt-text confidence. Keep raw OCR and user-confirmed values separate; reprocessing cannot overwrite corrections.

[Expensify duplicate handling](https://help.expensify.com/articles/new-expensify/reports-and-expenses/Why-Expenses-Duplicate) motivates linking a receipt to an existing transaction rather than posting twice. [Dext line extraction](https://help.dext.com/en/articles/377044-how-to-use-line-item-extraction-in-dext) and [duplicate criteria](https://help.dext.com/en/articles/841387-how-does-dext-decide-two-cost-items-are-duplicates) illustrate separate line processing and imperfect identity matches. Borrow explicit review/recovery patterns; no cloud receipt service is added.

[ML Kit disclosure guidance](https://developers.google.com/ml-kit/android-data-disclosure) describes SDK diagnostics. On-device receipt processing is not the same claim as “no SDK data collection”. Audit the selected artifacts, merged manifest, telemetry and network behaviour; disclose accurately before release. No added analytics SDKs or ads in MVP.

## Shopping and historical pricing

[AnyList](https://www.anylist.com/) demonstrates list grouping and remembered shopping entries. [CAC's outlet enquiry](https://www.cac.gov.jm/dev/SurveyEnquiry/OutletPrices.php) separates survey date and parish; [CAC consumer surveys](https://cac.gov.jm/portal/index.php/special-features/publications/category/21-consumer-alert) support the usefulness of dated comparisons. Do not assume an ingestion API, reusable licence or live inventory feed.

Store each confirmed observation by product variant, pack size, currency, branch and purchase date. Compare J$/kg, J$/L or J$/item only across compatible dimensions. Show exact-match observations separately from substitutes, membership offers and promotions. User corrections invalidate/rebuild affected observations. Missing price is unknown, never zero. A configurable 30-day “recent” window is a proposed UX default, not a legal rule or guarantee. Shopping estimates expose recent/stale/missing coverage and user-entered assumptions; ranges are observed/assumed ranges, not statistical confidence intervals. Start with a single preferred store; travel optimisation is future scope.

## Android, Material 3 and accessibility

[Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) support Compose, repositories, UDF and lifecycle-aware state; current guidance recommends **Navigation 3** for multi-screen apps. Prefer it at implementation if its stable dependencies fit the chosen SDK; record an ADR if Navigation Compose is retained.

Use Material 3 component behaviour and semantic roles, a labelled five-destination bar on phones, rail/list-detail layouts on expanded windows, edge-to-edge inset handling and progressive forms. [Material navigation guidance](https://m3.material.io/components/navigation-bar/overview) was found but its JS page was not extractable; rely on [Android adaptive guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive) for implementation verification.

[Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility) and [Google touch targets](https://support.google.com/accessibility/android/answer/7101858) support semantic controls and at least 48dp touch targets. Target normal-text contrast 4.5:1, large-text/non-text 3:1, TalkBack traversal, 200% font scaling, Switch Access and text alternatives for every chart. Native accessibility remains untested until an Android prototype exists. The web review is not proof of Android conformance.

## Findings requiring decisions or further evidence

- User chose optional account and backup for the first release. Keep a local pilot, then offer opt-in identity/encrypted snapshots in Version 1. Automatic bill reservations remain a proposed default pending the user's response.
- Keep 50/30/20 a starting point: essential costs can exceed 50%; custom splits and a bills-first view must work without shame.
- Reserve minimum debt payments under Needs, extra repayment under Savings/debt. Do not count the same payment in both.
- Do not present a “62% remaining” ring without naming its denominator. Use safe-to-spend divided by actual included funds instead.
- Conduct consented interviews and a receipt corpus study, verify payroll/legal details, test SDK privacy and inexpensive hardware before production/release claims.
