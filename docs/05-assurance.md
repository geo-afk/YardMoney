# Privacy, security, compliance and verification

Planning document only; no claim of legal compliance or tested Android security.

## Data inventory / proposed retention

| Data | Purpose / required? | Storage / sharing | Retention and deletion |
|---|---|---|---|
| Income, balances, transactions | Core calculations; user-entered records required for meaningful results | Encrypted local DB; optional encrypted backup only by opt-in | Until user deletes; export/delete-all; audit versions subject to same deletion |
| Schedule and preferences | Payday planning; schedule optional for irregular users | DB for financial schedule, DataStore nonsensitive UI settings | Until reset/delete |
| Receipt image | Review/evidence; optional | Encrypted private files; no public sharing; optional backup images | Original/compressed/delete-after-confirmation options. Failed draft proposed 7-day cleanup with notice; user can retain. |
| Raw OCR and receipt metadata | Corrections/parsing; only relevant fields | Encrypted local DB; redact identifiers in export where appropriate | With receipt; image deletion need not delete confirmed text/expense |
| Product prices, branches | Personal comparisons; optional | Encrypted DB; never community published automatically | With receipt observation or separate explicit deletion policy; remove linked observations when evidence deleted |
| Goals/debt | Plan commitments; optional | Encrypted DB | Until deleted/archive; related ledger records independently controlled |
| Location | Optional branch hint | One-time coarse lookup; no continuous trail; manual parish works | Keep branch coordinates only if needed; strip receipt EXIF; user can clear |
| Account identity/auth | Optional backup ownership; not required locally | Version 1 auth provider and protected device tokens | Until signout/account deletion; provider retention to be contracted |
| Cloud snapshots | Recovery, explicit opt-in | Client-encrypted provider objects; metadata disclosure | Proposed latest + limited previous snapshots with explicit policy; delete includes versions. Exact policy/provider unresolved. |
| Diagnostics | No added analytics in MVP; SDK audit needed | ML Kit documented metrics may leave device | Document exact selected SDK/provider handling; never ledger/OCR in crash reports |

These retention periods are product proposals, not statutory periods. User-created exports and external backups cannot be remotely erased by wiping the app; explain that clearly.

## Threat model

| Threat / boundary | Risk | Planned controls / residual risk |
|---|---|---|
| Lost/unlocked device | Financial records exposed | App lock, Keystore-wrapped DB/image keys, auto-lock; rooted/unlocked OS can still defeat protection |
| SDK/log/network boundary | Amounts/images leak | No content telemetry, dependency/manifest/network audit, redacted logs, accurate SDK disclosures |
| Malicious image/import | Memory exhaustion or corrupt ledger | Pixel/file size bounds, background decoding, schema/content validation and staging restore |
| OCR/user edits | Wrong balances or price facts | Draft/confirmed separation, arithmetic checks, review, reversible edits and unique submission IDs |
| Migration/process death | Partial write/data loss | Atomic transactions, durable drafts, migration tests, verified restore, no destructive fallback |
| Cloud account/object boundary | Cross-user access or unreadable backup | Server ownership checks, client encryption, tested recovery, object version deletion, rate limiting |
| Export/shared-device boundary | Unintended disclosure | Preview/redaction, explicit share chooser, scoped content URI grants, private files and expiring grants |
| Notifications/screenshot | Shoulder-surfing | Hide amounts on lockscreen, configurable capture protection/app-switcher redaction; explain user tradeoff |
| Duplicate receipt/bank record | Double expense | Candidate matching + link existing transaction; exact idempotency uniqueness, no silent probabilistic deletion |

## Jamaican compliance checklist — legal verification required

- [ ] Determine controller/processor roles for local app, support, SDK metrics and optional backup; confirm registration applicability/current process.
- [ ] Check Data Protection Act 2020 and current 2024+ regulations; assess DPO criteria and annual DPIA requirements with counsel.
- [ ] Map lawful bases and notices to each purpose; separate optional location/backup/analytics choices.
- [ ] Implement access, correction, export, relevant processing restrictions and deletion requests for remote data.
- [ ] Assess statutory sensitive-data categories (e.g. health purchases), minors, processor agreements and vendor/subprocessor obligations.
- [ ] Establish justified retention/disposal and recoverability; no unsupported seven-year personal-ledger requirement.
- [ ] Review cross-border processing/transfer basis, region and disclosures before any cloud/SDK collection.
- [ ] Incident owner, evidence preservation and 72-hour OIC reporting workflow per official guidance; counsel confirms exact duties.
- [ ] Review app wording and financial-service boundary with BOJ/legal expertise if payment/integration scope changes.
- [ ] Verify current payroll tables and payslip edge cases with qualified Jamaican practitioner before estimates; “estimate” label alone is insufficient.

## Security checklist

- [ ] Current Android guidance and dependency audit; no secrets, hardcoded credentials, ads or unnecessary exported components.
- [ ] HTTPS/certificate validation for any later network use; no bypassing TLS validation; tokens scoped and protected.
- [ ] Verify Room encryption integration, WAL/temporary files, image storage and key lifecycle; Room/DataStore are not assumed encrypted.
- [ ] Exclude sensitive data from Android automatic cloud/device-transfer backup as required by the explicit backup design; test actual OS/OEM behaviour.
- [ ] Scoped camera/photo-picker/location permissions; denied permissions retain useful manual workflows.
- [ ] Biometric/app-lock fallback and retry controls; PIN design resists trivial local brute force, no plaintext PIN.
- [ ] Redact logs, notification content and exported identifiers; never promise complete automatic receipt redaction.
- [ ] Auth ownership tests and client-encrypted cloud snapshot/recovery tests before optional accounts ship.
- [ ] Restore staging, schema version/integrity checks and bounded parsing; reject unsupported future schemas safely.
- [ ] Deletion scope covers local files, images, drafts, audit history and selected remote versions; flash storage secure erasure is not guaranteed.

## Phase 10 testing plan and release gates

| Layer | Critical cases / expected evidence |
|---|---|
| Domain unit/property tests | 50/30/20, 60/20/20, below/above 100%, cents/large values/overflow; allocation sum conservation, deterministic rebalance; safe-to-spend no double reserve |
| Schedule tests | Weekly/fortnightly vs two anchors, leap year, 28/29/30/31-day month, year/zone change, today/missed payday, duplicate occurrence IDs |
| Ledger/repository tests | Paired transfers, cash withdrawal, card purchase/payment, refund reversal, partial bill payment, double tap/retry, rollback at every save stage |
| Room/migration tests | Every supported schema upgrade, FK/index constraints, encrypted reopen, corrupted/low-storage database, preserved balances and no destructive fallback |
| OCR corpus | Consented/redacted Jamaican receipts across formats: faded/glare/crumpled/angled/long/cut-off, inclusive tax, discounts/returns, weighted items, duplicates; report merchant/date/total and line-level accuracy separately |
| Receipt integration | Denied camera/import, rotation/process kill, empty OCR, unavailable model, retake, duplicate link/save anyway, total-only save, corrected values survive rerun |
| Price/shopping | None/one/many observations, stale/missing, outliers/promotions, kg/g versus L/ml incompatibility, packs/quantity, branch/currency mismatch, edits/deletion invalidate summaries |
| Compose UI/device tests | First-use completion, scan findability, custom validation, over-budget override, chart text/TalkBack focus, 200% font scaling, 320dp to tablet/foldable, long currency values |
| Backup/security | Wrong passphrase/key, interrupted upload/restore, cross-user object denial, expired token, fresh-device recovery, signout/offline, deletion of retained versions, malicious JSON/CSV formula-injection protection |
| Performance | Startup, 10k+ ledger rows, image peak memory, OCR p50/p95, no heavy main-thread work; representative low-end hardware and no-network first use |

Phase 11: tune parser quality, startup/memory and chart clarity only after measurements. Phase 12: Play target SDK/Data safety/privacy notice, store disclosures, accessibility/device matrix, signing/release-key custody, crash recovery, support/deletion contact and professional verification gates. Publishing costs are distinct from infrastructure costs and require later consideration.

Current build and device evidence is summarized in the [README](../README.md#testing-and-verification). The release checklist remains in [device validation](07-device-validation.md).
