# Put this project on GitHub

## Upload through the website

1. Sign in to GitHub and create a new repository, for example **upi-ledger**.
2. Choose its visibility. Leave GitHub's initial README, license and `.gitignore` options unchecked: this folder already contains project documentation and ignore rules.
3. Extract the source ZIP if you downloaded it.
4. In the empty repository, choose **uploading an existing file**.
5. Drag the **contents of the project folder**, including `.github`, `.gitignore`, `app`, `gradle`, both Gradle launchers and the documentation, into the upload area.
6. Use a message such as `Initial UPI Ledger project` and commit the files.

Upload extracted source files, not just the ZIP or APK. The root of the repository should contain `settings.gradle`, `build.gradle`, `gradlew`, `app/` and `README.md` directly.

GitHub Actions starts after the source is uploaded if Actions is enabled for the repository. Its first remote run is the check that confirms the hosted runner's setup. The workflow saves a debug APK and reports as temporary artifacts; it does not publish an app release.

## Git command-line alternative

From the project folder:

```sh
git init -b main
git add .
git commit -m "Initial UPI Ledger project"
git remote add origin https://github.com/YOUR_USERNAME/upi-ledger.git
git push -u origin main
```

Replace the example URL with the repository you created. Git handles authentication with your own GitHub sign-in; no credentials are included in this project.

## What's included

Portable Android source, Gradle wrapper with distribution checksum, generated test receipts, workflow configuration and documentation. The dashboard image and browser demo use sample data. This archive contains version 0.2.2 with monthly subscriptions.

## What's excluded

Personal receipt images, transaction databases/backups, signing keys, local SDK paths, installed APKs, caches and build outputs. The `.gitignore` continues to exclude these if you build locally.

The source package has no `.git` history or remote configured and no license has been chosen on the owner's behalf.

Official browser-upload guide: https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository
