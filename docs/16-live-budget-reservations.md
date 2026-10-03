# Pilot 0.3.5 — live budget usage and grouped reservations

## Budget charts

Home and Plan previously displayed the fixed target split in their main ring. Recorded spending was only reflected in the separate category section, so the primary chart looked unchanged after an expense.

Both pages now share a live used-versus-allocated donut. Each category segment retains its target share: a solid portion represents usage and a lighter portion represents remaining allocation. The legend shows exact used / allocated amounts and remaining amounts, with an explicit over-budget amount when usage exceeds a target. The center shows total usage. Over-budget arcs stop at the target extent without hiding the excess in the text values.

Allocation remains based on actual income recorded in the current pay period. Typical pay and opening balances do not silently become received income. Net expense splits, including negative refunds, determine Needs/Wants/Savings spending. Income changes update allocations; expense/refund edits and deletions update usage. Empty-income and refund-only states preserve their real values rather than inventing funds.

Savings usage additionally includes net transfer entries into accounts whose type is SAVINGS, within the current period. Withdrawals reduce that funding; transfers between savings accounts cancel out. Other transfers remain excluded from spending and target usage. This is a budget-progress view, not an assertion that savings transfers are expenses. Goal balances remain a separate all-period measure.

Existing account-entry rows are read in the same committed snapshot as the ledger and splits. No database schema change or destructive migration is needed. Chart motion follows the existing motion preference.

## Recurring bills and reservation retries

Recurring bills already materialize several dated payment occurrences ahead of time. Plan previously displayed every date as another full bill, making one recurring bill appear to have been added repeatedly.

Plan now groups occurrences by their existing series identifier. A recurring bill has one primary row for its earliest unpaid occurrence, including partially paid/overdue amounts. Other dates and payment history are available through an expand/collapse action, retaining their individual pay/edit operations. Fully paid series use their most recent occurrence as the primary row. Home's Coming up section also shows one next unpaid occurrence per series.

Grouping never merges unrelated reservations merely because their names match. One-time reservations remain individually identifiable. Existing dated obligations and payment history are retained; grouping does not erase or change their balances, and safe-to-spend continues to count applicable outstanding commitments.

New reservation forms carry one stable submission key across retries. The database transaction checks both one-time commitments and recurring templates for that key before inserting. Repeated submission of the same request cannot create an extra one-time reservation or a second recurring series. Separately initiated reservations receive separate keys.

## Validation

Five new host tests cover income allocation versus usage, refunds, no-income/over-budget values, net savings transfers, recurring primary selection and unrelated same-name bills. New database tests cover retried one-time and recurring reservations. Device UI tests observe a real isolated Room/SQLCipher snapshot stream while posting income, expenses, refunds, deleting records and transferring savings; the same composed chart updates without switching screens. Another device test expands and collapses recurring payment rows.

The focused 19-test device run passed on the Pixel 8 Pro. Full verification also passed on 2 October 2026: all 72 host tests and all 38 device tests (zero failures/errors/skips), debug and optimized unsigned release builds, and lint with zero errors and 34 warnings. APK signature verification matched 0.3.4; 16KB ZIP alignment passed. Broader manual/device/OCR validation remains pending.

Version: 0.3.5, code 9. Application ID `jm.yardmoney`, Room schema version 1 and development signing identity are retained. Earlier delivery verification files remain historical records of their original APKs.
