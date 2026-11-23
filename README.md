# Atelier

An Android app for a tailor to track orders. Each order moves through the stages Accepted, Cutting, Fitting 1, Fitting 2 and Done, with payments, photos, visits, reminders and a daily Google Drive backup.

## Build

Needs JDK 17 and the Android SDK. Point `local.properties` at the SDK with `sdk.dir=...`.

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test

```
./gradlew testDebugUnitTest          # JVM tests, also run by CI
./gradlew connectedDebugAndroidTest  # device tests, needs a connected device
```

## Docs

- [Design](docs/design.md): what the app does and why
- [Architecture](docs/architecture.md): how the code is organized
- [Backup setup](docs/backup-setup.md): the Google Cloud client that Drive backup needs
- [Plan](PLAN.md): status and open items
- [AGENTS.md](AGENTS.md): rules for working in this repo
