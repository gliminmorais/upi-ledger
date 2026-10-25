# Changelog

## 0.4.1

- Display dates as DD-MM-YY throughout the interface and PDF/CSV reports.
- Keep ISO storage and native calendar pickers compatible with existing records.

## 0.4.0

- Split full vendor payments into your own spending and friends' share.
- Record separate named and dated repayments, including partial repayments.
- Home shows received amounts and outstanding friend shares.
- Charts, category budgets, vendor totals and spending use only your share.
- PDF and CSV separate spending from money received, without double counting.
- Schema/backup 5 preserves older records and includes split and repayment history.

## 0.3.1

- Edit and save limits for multiple categories in one monthly budget form.
- Save all edited budgets atomically; validation errors retain existing values.
- Landscape PDF spending tables with wrapped cells and repeated headers.
- PDF excludes budget limits, remaining amounts and transaction IDs.

## 0.3.0

- Monthly category budgets with remaining and overspend indicators.
- Date-range CSV export for Excel, with UTF-8 and quoted fields.
- Local PDF reports with category totals, budgets and paginated payments.
- Schema 4 migration retains payments/subscriptions; backups include budgets and accept versions 1-3.
- Emulator checks cover budget persistence, migration, restore rollback, CSV escaping and multi-page PDF output.

## 0.2.2

- Restrict date extraction to actual month names so receipt status text cannot hide the payment date.

## 0.2.1

- Change payment-save confirmation to "Your payment is up to date."

## 0.2.0

- Add a Subscriptions navigation tab and manual monthly plan creation.
- Automatically post due debit entries on app open/resume, with the service name as vendor and Subscription as category.
- Catch up missed months without duplicating renewal entries.
- Preserve renewal-day anchors through short months and leap years.
- Cancel future Ledger entries while retaining historical payments.
- Upgrade database and JSON backups to version 3, retaining older-backup compatibility.

## 0.1.2

- Extend ambiguous-currency handling to GPay and unknown receipt sources.
- Compare header and debit/send amounts before accepting OCR-derived spending.
- Leave unresolved amounts blank instead of guessing.

## 0.1.1

- Move the Ledger header lower and remove its status badge.
- Add the initial BHIM currency-glyph safeguard.

## 0.1.0

- Introduce image receipt import, review and manual entry.
- Add vendor defaults, per-payment category overrides, aliases and optional remarks.
- Add custom categories and standalone vendor creation.
- Add glass-style screens, search, weekly/monthly analysis and vendor period filters.
- Add JSON backups, schema migration and local validation checks.
