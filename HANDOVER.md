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
| 3 Library, programmes, calendar | First slice built, CI green (run 10, tests passed): library, your Full body A/B/C seeded and scheduled, Train calendar, move a day, Today hero. Editors for workouts and programmes not yet built |
| 4 Active workout | First slice built, CI green (run 13, tests passed): log sets with the keypad, previous column, rest timer, records, summary. Not yet: swipe actions, replace or add exercise, rest-timer service and notification, edit after saving, history and PR screens |
| 4b Active workout, rest of it | Built, CI green (run 15): rest timer notification and alarm, replace and add exercise, records screen, exercise history |
| 5 Food diary | First slice built, CI green (run 17): diary, add food, quick add, water, repeating foods, nutrient snapshots. Not yet: recipes and meals, targets editor, full nutrient summary |
| 6 Barcode and food data | Built, CI green (run 18): CameraX and ML Kit scanner, Open Food Facts lookup and search, cached foods, custom foods. No bundled CoFID or USDA database yet (needs your export or a file) |
| 7 Scale and Health Connect | Built, CI green (runs 20 and 22): Qingniu packet decoder (tested on hand-built frames, not real ones), Bluetooth session, Scale screen with manual entry, packet log and weekly reminder; Health Connect permissions, today's steps, sleep, resting HR and calories, weight import, optional send of weights and workouts. Not yet: nutrition and water writing, mirroring deletes, steps on Today |
| 8 Provider for HQ | Provider built, CI green (run 23): `content://uk.woozy.fit.summary/today`, signature permission `uk.woozy.fit.permission.READ_SUMMARY`, read-only. HQ side not changed yet |
| Added after milestone 10 | CI green (runs 28, 29): Cronometer daily-summary importer (one entry per logged day, totals only, about 400 days; the servings file has no nutrient figures so food-level history cannot be rebuilt), food and water sync to Health Connect on from launch (imported Cronometer days are not re-sent), automatic sync each time the app opens, deleted weights and food entries mirrored, steps and active calories on Today, Sunday check-in tick boxes with a Sunday 18:00 reminder and fields in the HQ provider (`checkin_*`). Database is version 5 |
| 9 Backups and export | Built, CI green (run 25): encrypted backup zip (database plus recovery-wrapped key), nightly alarm around 03:30, optional folder copy, restore with the recovery key, CSV export of weights, diary and sets. Not yet: Everfit importer (the Everfit page could not be read in this session; needs an export or a browser), JSON export |
| 10 Progress | Charts built, CI green (run 26): weight against 80 kg with trend and forecast date, 14-day calories, 8-week sessions and volume, link to records. Drawn with Canvas, no chart library. Not yet: frosted style, pull to refresh, card reordering, accessibility pass |
| 11 Switch-over | Checklist below, nothing to build until the phone checks pass |

Done in milestone 1: Gradle modules, design system, five-slot navigation, Settings skeleton with appearance and colour scheme switches, component gallery, CI with signed releases, screenshots.

Done in milestone 2: Room on SQLCipher (`core/data/db`), random 32-byte database key wrapped by an Android Keystore key and by a recovery key (PBKDF2 200k + AES-GCM, `core/data/crypto`), first-run recovery key screen (typed back to confirm), recovery screen for a phone that cannot open the data, seed of 37 nutrients with Health Connect field names and the starting targets. Tables so far: nutrient, target, exercise, weight_entry, water_entry, meta. Not yet seeded: the exercise library (arrives with milestone 3, where it is first used). Tests: recovery crypto on the JVM, DAOs and seed on an in-memory Room database through Robolectric. Encryption on disk is not covered by tests because SQLCipher needs the phone's native library.

Done in milestone 3 (first slice, database version 2): bundled exercise library (about 5,600 entries, text from the openGym catalogue, no pictures yet), search with muscle and equipment filters, and your Full body A / B / C programme from the Everfit handover seeded exactly (warm up, prehab, five movements, arm superset, cool down, rest times, notes), scheduled Mon / Wed / Fri for 10 weeks from the first run. Train shows the week strip and coming-up list; tap a day to see the workout by section; Move to another day keeps the original date; Today shows today's workout. Starting weights for Romanian deadlift (10 kg per dumbbell) and hip thrust (40 kg) are guesses, as in the handover.

Not in this slice (still milestone 3): workout editor, programme editor (copy day or week, duplicate), custom exercises with photo or clip, drag to reschedule, exercise stills, percent-of-1RM and RIR targets. The Start workout button opens the placeholder until milestone 4.

Done in milestone 4 (first slice, database version 3): start a workout from Train or Today (Resume workout if one is running); sections, supersets and notes as in your programme; set table with a PREVIOUS column (same set number last time) and weight starting from last time's weight; our keypad with plus/minus 2.5 kg or 1 rep; ticking a set fills the target reps if you did not type any and starts the rest timer (vibrates at zero); Add set and Delete last set; screen stays on; every change is saved straight away, so closing the app leaves the session as it was; Finish (warns about unticked sets) or Discard; summary with time, sets, volume, new records (heaviest, estimated 1RM, best set volume, judged against earlier sessions only, so the first time is a baseline) and a 1 to 5 feeling. Finishing marks the calendar day done. Estimated 1RM uses Epley.

Not in this slice (rest of milestone 4): swipe to delete with Undo, Replace, Add exercise before or after, Add superset, Mark all, section-complete celebration, set types in the UI (drop, failure, warm-up) and RPE or RIR entry, the rest timer as a foreground service with a notification (for now it only runs while the app is open), editing a finished workout, exercise history and personal records screens.

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
12. Train: the week strip shows dots on Mon, Wed, Fri; tap a day opens the workout with sections, supersets and notes matching Everfit.
13. Workout screen: Move to another day changes the date; the Train list shows "moved from".
14. Today shows today's workout (or the next one on a rest day).
15. Exercise library: search "squat", filter by muscle and equipment; scrolling 100 rows is smooth.
17. Workout: start Fullbody B (Train or Today). Tick a set: the reps fill from the target and the rest timer shows in the header; leaving the app and coming back keeps everything; "Resume workout" appears on Today.
18. Keypad: tap weight or reps, type, plus and minus step by 2.5 kg or 1 rep, Done moves from weight to reps. The set table is not covered by the keypad.
19. Finish: summary shows time, sets and volume; a second session with heavier weights shows records; the day on Train turns green.
20. Discard: removes the session and the day stays planned.
16. First launch after updating from the previous version keeps your data (database upgrade 1 to 2) and adds the library and programme.
21. Rest notification: tick a set, leave the app; a countdown notification appears, and "Rest over" with a vibration when it ends. Allow notifications when asked.
22. Replace and add exercise in a workout; Records and exercise history screens show finished sessions.
23. Diary: add food, quick add, change an amount, add water, repeating foods appear once per day.
24. Scanner: the camera permission prompt appears; a barcode looks the food up on Open Food Facts; search works; a found food is cached and works offline.
25. Scale (Settings > Scale and weight): allow Bluetooth when asked; Find scale lists the scale after you step on it; tap it; the weight appears and saves once it settles. If it does not pair, open the packet log on that screen and send it to Claude. Body composition is not read.
26. Typed weight saves and can be deleted. Weekly reminder: turn it on, set a day and hour, allow notifications; it appears on that day.
27. Health Connect (Settings): Grant access; today's steps, sleep, resting HR and calories show; "Sync now" imports Feelfit weights and, with the switch on, sends weights and workouts without duplicating on a second sync.
28. HQ provider: needs HQ changed to read it. Until then nothing can be checked on the phone.

29. Backup (Settings > Backup and restore): Back up now reports success; choose a folder and run again, the file appears there. Nightly switch on; next morning the status line shows a 03:30 backup.
30. Restore: copy a backup file, tap Restore, the app restarts and asks for the recovery key; the right key brings back your data. Try this on a spare phone or accept it replaces the current data.
31. CSV export of weights, diary and sets opens in a spreadsheet.
32. Progress: weight chart shows your weigh-ins and the 80 kg line; calories and training charts fill as you log.

11. If the app is force-closed while the recovery key is on screen, the next launch makes a new key (the unconfirmed one held no data).

## 9. Open items

1. Programme details: received 11 Nov 2026 and seeded. Still to confirm on the phone: starting weights, and that the Sunday check-in tasks (mood, metrics, photo) are wanted as reminders in HQ Fit (not built; still unanswered).
2. A food database: send a CoFID xlsx or your Cronometer export so a bundled database and the importer can be built. Cronometer full export (Servings, Daily Nutrition, Biometrics) and any Everfit history before milestone 9.
3. Frosted glass style, pull to refresh, drag-to-reorder cards: planned (see "Carried over from HQ" in Stage 4).
4. `allowBackup` is off until the backup design lands in milestone 9.
6. Leftovers: workout and programme editors, custom exercises with photos, swipe actions, set types and RPE, recipes and meals, targets editor, steps on Today, Vico charts.
7. HQ needs a small change to read the summary provider and drop its own nutrition reads once you switch over.
5. Exercise media: no licensed animations; text from OpenGym's catalogue, stills from free-exercise-db, own clips later.

## 10. Switch-over checklist (milestone 11)

Do these in order, only after the phone checks above pass.

1. Run both apps side by side for a week: log the same food in Cronometer and HQ Fit and compare totals.
2. Import weight history from Health Connect (Settings > Health Connect > Sync now) before removing Feelfit.
3. Take a backup and restore it once, so you know the recovery key works.
4. Turn on the HQ Fit nightly backup folder.
5. Change HQ to read `content://uk.woozy.fit.summary/today` and stop reading nutrition from Health Connect.
6. Only then stop using Cronometer and Everfit. Nutrition and water writing to Health Connect is not built; HQ gets food totals from the provider instead.

## 11. Added late (check on the phone)

33. Settings > Backup and restore > Import from Cronometer: choose the daily summary CSV; about 400 days appear in the Diary and the Progress charts. Running it again adds nothing.
34. Health Connect: after granting access, opening the app sends food, water, weights and workouts. Check they show in Health Connect and that HQ does not double count Cronometer-era days (imported days are not re-sent).
35. Today shows steps and active calories from Health Connect. On a Sunday the check-in tick boxes appear; the reminder fires at 18:00.
36. Deleting a weight or a diary entry removes it from Health Connect too. Undoing water does not yet.
