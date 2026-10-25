# Architecture

![Architecture rendered with Python](images/architecture.png)

## Android host

`MainActivity` accepts `ACTION_SEND` with an image MIME type and document-picker results. Receipt images are read in memory; a shared URI is not copied into permanent app storage. OCR-derived fields are sent to the local editor for review. A successful save persists structured fields only.

The WebView serves five allowlisted assets from a local intercepted origin. Remote navigation, network loads, arbitrary file access and content access are blocked. Its JavaScript bridge validates input before changing the database. Dynamic strings are escaped before being inserted into the interface.

## Storage

`LedgerDb` schema version 3 contains categories, vendors, aliases, subscriptions and entries. A payment stores its category independently of its vendor's default. Changing the default affects future selections, not past classifications. Categories can exist without payments; vendors can exist before their first payment. Unique nonempty transaction references prevent duplicate saves.

Version 1 databases upgrade in place. Backup version 3 includes categories and subscriptions; restore also accepts version 1 and 2 backups. Restores run inside a transaction so malformed input can roll back without replacing existing records.

## Amount parsing

Explicit readable currency values take priority. Otherwise, the parser compares a header amount with debit/send lines. A leading 7 is not removed solely because it looks like a mistaken rupee glyph. Character-size evidence may normalize it; unresolved or conflicting readings leave the amount blank.

## Analysis

`model.js` uses local calendar dates and amounts represented as integer paise. Weeks run Monday to Sunday. Incomplete current periods compare against the same elapsed portion of the previous period. Vendor filters and global insights share these date helpers. The browser preview uses in-memory synthetic records and never represents actual user spending.

## Monthly renewal bookkeeping

Schema version 3 adds subscriptions and links each generated payment to its plan and scheduled due date. The unique plan/date index and a transaction around posting/advancing the date prevent duplicate renewals. Opening/resuming the app posts active due schedules using the stored monthly amount, service-name vendor and Subscription category. There is no scheduler service or bank integration. Cancellation deactivates the plan without deleting historical debits. Backups include schedules and links and still accept versions 1 and 2.

## Budgets and reports (0.3.0)

SQLite schema 4 adds a budgets table keyed by calendar month and category. Limits and expenses use integer paise. Migration creates the table without rewriting existing records. Backup version 4 includes budgets; restore continues to accept versions 1-3.

Reports use a user-selected inclusive date range. CSV is UTF-8 with a BOM, quoted fields and escaping of formula-leading text. Native Android PdfDocument renders landscape spending tables with repeated column headers and wrapped cells. The PDF excludes budget information and transaction IDs. Multi-category budget changes commit in one database transaction. Both exports use Android Create Document so the user chooses the destination without broad storage permissions. Reports contain saved structured fields; no receipt images or raw OCR text are included.

## Split payments and repayments (0.4.0)

Schema 5 adds entries.own_paise (-1 for an unsplit payment) and a repayments table linked to the original entry with delete cascade. The original entries.paise amount remains the full vendor payment. Entry.spent() returns the personal share or full amount for older unsplit records. The bridge supplies personal spending as paise and the full payment as paidPaise, so existing summaries, charts and budgets consistently use personal spending.

Repayment creation runs in a transaction and checks that the total received cannot exceed the friends' share. Payment edits cannot invalidate existing repayment amounts or dates. Backup version 5 includes repayments; older versions retain their full original spending. PDF/CSV distinguish payment dates and repayment receipt dates. Incoming repayments do not subtract from personal spending again.
