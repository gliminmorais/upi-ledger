# Contributing

Open the project in Android Studio and run the checks in [docs/TESTING.md](docs/TESTING.md).

Keep these behaviors intact:

- Represent money as integer paise for storage and analysis.
- Preserve transaction categories when vendor defaults change.
- Validate category/vendor names and transaction fields in Android as well as in the UI.
- Leave uncertain OCR values for review; never strip a leading digit without evidence.
- Preserve records through database upgrades and keep backup restore atomic.
- Bundle interface assets locally and keep the bridge unavailable to remote content.
- Use generated or fully fabricated receipts in tests and screenshots.

Do not commit local SDK settings, signing keys, payment backups, databases or personal receipt images. APKs belong in intentional releases/build artifacts rather than source control. The repository owner has not selected a project license yet.
