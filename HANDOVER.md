# HQ Fit handover

Owner: Mahesh Sharma. Written 11 Nov 2026. Personal system, single user. The repo is public; nothing secret is in it. Companion to the HQ handover (HQ is the dashboard app; HQ Fit is the training and nutrition app).

## 1. Goal

One native Android app that replaces Everfit (training) and Cronometer (nutrition), keeps data on the phone, and feeds HQ. Plan and screen spec live in the Claude Doc "HQ Fit" (https://claude.ai/artifact/BkCFSDBQZRfmNYso315BEy): Stages 1 and 2 (audit, architecture), Stage 3 (screen spec), Stage 4 (build plan, 11 milestones). Read Stage 4 before changing anything.

## 2. Pieces and where they live

| Piece | Where | Notes |
|---|---|---|
| App | GitHub `Woozypictures/hq-fit` (public), branch `main` | Kotlin, Jetpack Compose, package `uk.woozy.fit`, AGPL-3.0 |
| Builds and releases | `.github/workflows/build.yml` | Tests, lint, screenshots, signed APK, release `v0.<run>`; log and screenshots pushed to orphan branch `build-log` |
| Updates on phone | Obtainium pointed at the repo | Public repo, so no token needed |
| Signing key | GitHub Actions secrets `HQ_KEYSTORE_B64`, `HQ_KEYSTORE_PASS` (alias `hq`) | Same key as HQ. Not in the repo |
| Companion app | `Woozypictures/hq`, package `uk.woozy.hq` | See the HQ handover |

## 3. Data ownership (planned, see Stage 2)

- Samsung Health owns steps, sleep, heart rate and active calories, shared through Health Connect.
- HQ Fit owns workouts, sets, programmes, PRs, food diary, nutrients, water, targets and bodyweight.
- Bodyweight: HQ Fit pairs directly with the Kamtron CS20M scale (a Qingniu-family model that openScale decodes) and writes weight to Health Connect. Feelfit and Google Fit are not needed after milestone 7.
- HQ reads a read-only summary from HQ Fit through a signature-protected provider (milestone 8).
- Nothing in this list is built yet beyond the shell. See section 7.

## 4. Code structure

- `app`: screens in `ui/`, navigation in `ui/HqFitRoot.kt` (Today, Train, +, Diary, Progress; the active workout is a full-screen route over the tab bar), manual dependency container in `HqFitApplication.kt`.
- `core/model`: plain Kotlin (`Targets`, `ThemeMode`), with JUnit tests.
- `core/data`: storage. Preferences only for now (`ThemeSettings`); the encrypted database arrives in milestone 2.
- `core/designsystem`: theme, colour schemes (`Schemes.kt`), Google Sans, components, the component gallery, Paparazzi screenshot tests.
- Look: Everfit by default (page `#F6F7FC`, cards white, blue `#2355FB`), plus HQ's 17 colour schemes. Appearance (light, dark, system) is separate from scheme. One font, Google Sans (OFL).

## 5. Build, signing, updates

- CI runs on every push to `main`. Version code is the run number.
- It runs `:core:model:test`, `:core:designsystem:recordPaparazziDebug`, `:app:lintDebug`, `:app:assembleRelease`, then checks the APK certificate and publishes the release with `app-release.apk` and `screenshots.zip`.
- Debug a failed build: `git fetch origin build-log && git show origin/build-log:build.log`. Screenshots are under `shots/` on the same branch.
- Signing key risk: the same as HQ's. If the secret and Mahesh's copy are both lost, updates stop installing in place.
- The Claude session cannot compile locally (no Android SDK, no access to Google Maven). CI is the compiler; expect an occasional fix-up build.

## 6. Rules carried over from HQ

- A failing source never wipes cached data. A failed write stays queued and retries.
- No tokens or personal data in logs. Credentials, if any are added later, stay in private app storage.
- Do not break Obtainium updates: same package ID (`uk.woozy.fit`) and same signing key.
- Never claim on-device testing. Anything that needs the phone is listed in section 8.
- Style: no em-dashes, UK English, letter-spaced titles only in the Everfit-style workout headers.

## 7. Status

| Milestone | State |
|---|---|
| 1 Foundation | Built and released. Colour schemes added after |
| 2 Database and encryption | Built; CI green (run 5, JVM tests passed). Phone checks 8 to 11 below still open. Foundation tables only; each later milestone adds its tables by migration |
| 3 to 11 | Not started. Order and scope are in Stage 4 of the Doc |

Done in milestone 1: Gradle modules, design system, five-slot navigation, Settings skeleton with appearance and colour scheme switches, component gallery, CI with signed releases, screenshots.

Done in milestone 2: Room on SQLCipher (`core/data/db`), random 32-byte database key wrapped by an Android Keystore key and by a recovery key (PBKDF2 200k + AES-GCM, `core/data/crypto`), first-run recovery key screen (typed back to confirm), recovery screen for a phone that cannot open the data, seed of 37 nutrients with Health Connect field names and the starting targets. Tables so far: nutrient, target, exercise, weight_entry, water_entry, meta. Not yet seeded: the exercise library (arrives with milestone 3, where it is first used). Tests: recovery crypto on the JVM, DAOs and seed on an in-memory Room database through Robolectric. Encryption on disk is not covered by tests because SQLCipher needs the phone's native library.

Not done and not faked: every screen shows empty values with an "arrives in milestone N" caption. Nothing logs data yet.

## 8. Needs checking on the phone (never tested on a device by Claude)

1. The APK installs next to HQ and opens.
2. Five tabs open; the + opens the Quick add sheet; Start workout opens the full-screen placeholder and Close returns.
3. Settings (your initial on Today): Appearance Light, Dark and System switch the look; the choice survives closing the app.
4. Colour scheme picker: all 18 swatches scroll and apply; dark-native schemes (Digital, Noir, Athletic, Arcade) stay dark in Light appearance, as in HQ.
5. Status bar and gesture bar icons stay readable in light and dark schemes.
6. Obtainium finds the repo and updates in place.
7. Settings > Design components renders without crashing and scrolls.
8. First launch shows "Protect your data"; the recovery key appears, a wrong retype is refused, the right one opens Today. Write the key down.
9. Close and reopen: no prompt, straight to Today. Clear nothing; this proves the Keystore path.
10. Optional proof of encryption: with a file manager on a rooted device or `adb run-as uk.woozy.fit`, `databases/hqfit.db` is not readable as SQLite.
11. If the app is force-closed while the recovery key is on screen, the next launch makes a new key (the unconfirmed one held no data).

## 9. Open items

1. Programme details before milestone 3 (current Everfit programme: Fullbody A, B, C).
2. Cronometer full export (Servings, Daily Nutrition, Biometrics) and any Everfit history before milestone 9.
3. Frosted glass style, pull to refresh, drag-to-reorder cards: planned (see "Carried over from HQ" in Stage 4).
4. `allowBackup` is off until the backup design lands in milestone 9.
5. Exercise media: no licensed animations; text from OpenGym's catalogue, stills from free-exercise-db, own clips later.
