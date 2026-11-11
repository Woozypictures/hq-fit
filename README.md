# HQ Fit

A personal Android app for training and nutrition, replacing Everfit and Cronometer. It works with
[HQ](https://github.com/Woozypictures/hq) through Health Connect and a read-only summary provider.

- Native Kotlin and Jetpack Compose, light by default in Everfit's style, with a dark variant.
- Your data stays on the phone, in an encrypted database (from milestone 2).
- Licence: AGPL-3.0. See `NOTICE` for credits.

## Status

Milestone 1 of 11: foundation. The app installs, shows the five-slot layout (Today, Train, +, Diary,
Progress), switches light and dark, and has a Settings skeleton. Nothing logs data yet.

## Build

CI builds and signs every push to `main` and publishes a release with the APK and screenshots of the
design system in light and dark. Install and update through Obtainium, pointed at this repo.

The signing key is the same as HQ's and lives only in GitHub Actions secrets
(`HQ_KEYSTORE_B64`, `HQ_KEYSTORE_PASS`). It is never in the repo.

Locally, with JDK 17 and the Android SDK:

    gradle :core:model:test :core:designsystem:recordPaparazziDebug :app:assembleDebug

## Layout

- `app` - screens, navigation, manual dependency container
- `core/model` - plain Kotlin types and their tests
- `core/data` - storage (preferences now; the encrypted database from milestone 2)
- `core/designsystem` - theme, Google Sans, components and the component gallery
