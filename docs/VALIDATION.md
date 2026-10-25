# Source-package validation

Version 0.3.1, prepared on 25 October 2026.

## Passed locally

- Clean-source Gradle wrapper build using ANDROID_HOME, without local.properties.
- Parser/calendar unit tests and Android lint.
- JavaScript syntax and spending-analysis/calendar tests.
- Android app and generated-receipt test APK compilation.
- Emulator integration checks: custom categories, standalone vendors, vendor defaults/overrides, aliases, duplicate references, backup round-trip/rollback, migration, generated receipt OCR, local UI and native save validation.
- Monthly subscription checks: due-date posting, missed-month catch-up, cancellation, month-end dates, duplicate prevention and backup compatibility.
- Budget schema 3-to-4 migration, per-month limits, updates/removal, backup round-trip and invalid restore rollback.
- CSV exact decimal values, UTF-8 marker, formula escaping, quoted remarks; multi-page/empty PDF generation.
- Landscape PDF tables checked for repeated headings, wrapped remarks and oversized-row continuation.
- Multi-category budget form saves and reloads several limits; invalid bulk updates roll back together.
- APK asset verification for the payment confirmation wording.
- Python-rendered README diagrams and sample preview images.
- Private-data/source-file audit and archive integrity.

The GitHub Actions workflow is included but has not been run on GitHub. Actual bank settlement is never verified by Ledger; automatic subscription debits are bookkeeping entries.
