# Validation

## Local checks

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
node --check app/src/main/assets/ui/ui.js
node --check app/src/main/assets/ui/subscriptions.js
node tests/model.test.cjs
```

On macOS/Linux, use `./gradlew` instead of `gradlew.bat`.

## Device integration checks

Start an emulator first. These checks use isolated test databases and generated receipts. They do not require private screenshots, bank access or a real payment.

```sh
adb devices
adb -s EMULATOR_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s EMULATOR_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s EMULATOR_SERIAL shell am instrument -w in.upiledger.app.test/in.upiledger.app.LedgerChecks
```

Replace `EMULATOR_SERIAL` with the emulator listed by `adb devices`. The custom instrumentation runner returns a `PASS:` or `FAIL:` stream. An `adb` exit code alone does not determine whether its assertions passed.

The tests check custom category creation, duplicate names, independent vendor creation, vendor defaults versus overrides, aliases, duplicate IDs, remarks, backup compatibility/rollback, migration from schema 1, OCR of generated Latin-script receipts, local interface loading and native save validation.

## Manual checks

- Share a receipt image from BHIM/GPay to the app and review all extracted fields.
- Try an ambiguous currency glyph and verify the amount is not silently inflated.
- Create a category from a payment form and confirm that amount, vendor and remarks remain entered.
- Override one payment category and confirm that the vendor default is unchanged.
- Select historical vendor months/weeks and inspect both totals and history.
- Export a backup, restore it on a test installation and compare records.
- Use system Back to close the editor and navigate home.

Actual share formats, image compression, themes, OCR quality and Android/HyperOS versions vary. Generated-receipt tests cannot establish compatibility with every payment-app release.

## Browser design preview

```sh
python -m http.server 8766 --bind 127.0.0.1 --directory app/src/main/assets/ui
```

Open `http://127.0.0.1:8766/index.html?demo=1`. Sample edits are temporary; Android receipt import and backup operations are unavailable in the browser. Omit `?demo=1` to inspect empty states.

Subscription checks cover monthly posting, catch-up, cancellation, month-end/leap-year dates, duplicate renewal prevention, category/vendor fields, and backup compatibility.

## Budget and report checks

ReportChecks uses an isolated database to verify monthly budget update/removal, schema 3 upgrade, version 4 backup round-trip, version 3 compatibility and restore rollback. It checks CSV quoting/formula escaping/exact decimals and generates multi-page and empty PDFs on Android. LedgerChecks also opens the budget editor, saves a limit and verifies remaining amounts and both export formats in the offline WebView.

## Split checks

SplitChecks verifies schema 4 migration, older backups, zero own share, partial/named repayments, overpayment rejection, repayment date bounds, protected edits, corrections, cascade deletion and backup rollback. LedgerChecks exercises the actual split-payment form, two separate repayment submissions, Home totals and reopening the full paid amount. Sample PDF/CSV exports reconcile INR 900 paid, INR 300 personal spending and INR 600 received.
