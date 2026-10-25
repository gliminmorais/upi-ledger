# Source-package validation

Version 0.2.2, prepared on 25 October 2026.

## Passed locally

- Clean-source Gradle wrapper build using ANDROID_HOME, without local.properties.
- Parser/calendar unit tests and Android lint.
- JavaScript syntax and spending-analysis/calendar tests.
- Android app and generated-receipt test APK compilation.
- Emulator integration checks: custom categories, standalone vendors, vendor defaults/overrides, aliases, duplicate references, backup round-trip/rollback, migration, generated receipt OCR, local UI and native save validation.
- Monthly subscription checks: due-date posting, missed-month catch-up, cancellation, month-end dates, duplicate prevention and backup compatibility.
- APK asset verification for the payment confirmation wording.
- Python-rendered README diagrams and sample preview images.
- Private-data/source-file audit and archive integrity.

The GitHub Actions workflow is included but has not been run on GitHub. No repository was created or uploaded by this task. Actual bank settlement is never verified by Ledger; automatic subscription debits are bookkeeping entries.
