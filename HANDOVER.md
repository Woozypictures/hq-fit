# HQ Fit handover (merge document)

Owner: Mahesh Sharma. Rewritten 11 Nov 2026 to start the merged HQ + HQ Fit chat. Personal system, single user. Repo is public and holds nothing secret. Companion: `claude/hq-handover.md` in the DASHBOARD Project (the HQ dashboard app). Plan: Claude Doc "HQ Fit" https://claude.ai/artifact/BkCFSDBQZRfmNYso315BEy (Stage 3 = screen spec, Stage 4 = build plan).

## 1. What HQ Fit is

A native Android app (`uk.woozy.fit`, Kotlin, Jetpack Compose, AGPL-3.0, repo `Woozypictures/hq-fit`) that replaces Everfit (training) and Cronometer (nutrition). Data stays on the phone, encrypted. It writes food, water, weight and workouts to Health Connect and exposes a read-only summary to HQ (`uk.woozy.hq`) through a signature-protected ContentProvider.

Status in one line: every planned feature except the Everfit history import and a JSON export is built and compiles (CI run 44 green). Almost nothing has been verified on a real phone; Mahesh has installed it and begun testing (section 10).

## 2. Pieces

| Piece | Where |
|---|---|
| App | GitHub `Woozypictures/hq-fit`, branch `main`; modules `:app`, `:core:model` (JVM), `:core:data` (Room 2.6.1 + SQLCipher 4.6.1), `:core:designsystem` (Paparazzi) |
| CI | `.github/workflows/build.yml`: tests, lint, screenshots, signed APK, release `v0.<run>`; log and screenshots on orphan branch `build-log` |
| Updates | Obtainium pointed at the repo (public, no token) |
| Signing | GitHub Actions secrets `HQ_KEYSTORE_B64`, `HQ_KEYSTORE_PASS` (alias `hq`), same key as HQ. Never put it in a file in the repo or this doc. If the secret and Mahesh's copy are both lost, updates stop installing in place |
| HQ app | `Woozypictures/hq` (private), see HQ handover |
| Project docs | DASHBOARD Project: `claude/hq-fit-handover.md` (this), `claude/hq-handover.md` |

## 3. Data ownership

- Samsung Health owns steps, sleep, resting HR, active calories (read via Health Connect).
- HQ Fit owns workouts, sets, programmes, PRs, food diary, nutrients, water, targets, bodyweight, check-ins.
- Bodyweight comes from the Kamtron CS20M scale paired directly (Qingniu/QN protocol, ported from openScale, credited in NOTICE), plus weights other apps put in Health Connect.
- Health Connect writes: weights, workouts, food (`NutritionRecord`), water. Records carry `clientRecordId` `hqfit-weight|session|meal|water-<rowId>`; meals and workouts also a rising `clientRecordVersion` so edits update. Reads skip the app's own package. Imported Cronometer day totals are not re-sent.
- Provider for HQ: `content://uk.woozy.fit.summary/today`, permission `uk.woozy.fit.permission.READ_SUMMARY` (signature), read-only. Columns: date, kcal, protein_g, net_carbs_g, fat_g, water_ml, kcal_target, protein_target_g, net_carbs_target_g, fat_target_g, weight_kg, weight_at_ms, workout_today, workout_today_done, workouts_done_week, workouts_planned_week, checkin_date, checkin_mood, checkin_metrics, checkin_photo, version(=1). Empty cursor when the database cannot be opened.
- Targets: 2,297 kcal, 150 g protein, 260 g net carbs, 70 g fat, 8,500 steps, 8 x 250 ml water, goal 87.4 to 80 kg. Net carbs = carbs minus fibre.

## 4. Code map

- `app/ui/`: `HqFitRoot.kt` (navigation, tabs Today/Train/+/Diary/Progress, routes), `TodayScreen`, `TrainScreens`, `WorkoutScreen` (section cards, keypad, set types, RPE), `EditorScreens` (workout editor, custom exercise), `PastWorkouts`, `FoodScreens`, `FoodExtras` (recipes, nutrient summary, targets editor), `TabScreens` (Progress) + `Charts.kt` (Canvas), `ScaleScreen`, `HealthScreen`, `BackupScreen`, `SettingsScreens`/`SettingsExtras` (Profile, Workout, HQ connection), `ExerciseMedia` (pictures).
- `app/`: `SummaryProvider`, `BackupRunner` (+ nightly alarm 03:30), `WeighInReminder`, `CheckInReminder` (Sunday 18:00), `RestTimer`, `health/HealthGateway` + `HealthSync`, `scale/ScaleSession` (GATT).
- `core/data`: `db/` (entities, DAOs, `HqFitDatabase`, `DB_VERSION`, `Seed`, `DatabaseProvider`), `crypto/` (KeyVault, recovery key), `scale/QnProtocol` (pure, tested), `backup/`, `imports/CronometerImport`, `share/HqSummary`, `off/` (Open Food Facts), assets `exercises.tsv` (about 5,600, OpenGym text), `cofid.tsv`.
- `core/model`: pure Kotlin (Targets, Nutrients, Trend, Scheduler, programme, NumberEntry).
- `tools/`: `build_cofid.py`, `build_exercise_images.py` (regenerate assets from source files).
- Look: Everfit by default (page #F6F7FC, white cards, blue #2355FB) plus HQ's 17 schemes; appearance separate from scheme; optional Frosted glass (dark #2E2E2E, translucent cards). Font Google Sans (OFL).
- Encryption: random 32-byte DB key wrapped by the Android Keystore and by a recovery key (PBKDF2 200k + AES-GCM). Backup file `.hqfitbak` = encrypted DB + recovery-wrapped key; opens only with the recovery key.
- Database version 6 with AutoMigrations 1 to 6. After bumping `DB_VERSION`, copy the generated schema: `git show origin/build-log:shots/schemas/uk.woozy.fit.data.db.HqFitDatabase/N.json > core/data/schemas/.../N.json` (a test fails if it is missing). New entity fields go last with defaults (BackupTest uses positional constructors).

## 5. Working rules for the next session

- CI is the compiler (no Android SDK or Google Maven in the sandbox). Loop: push to `main`; `until git fetch -q origin build-log:refs/remotes/origin/build-log -f && git show origin/build-log:status.txt | grep -q "$sha"; do sleep 20; done`; then print `status.txt` and `grep -E "^e: |FAILED"` on `build.log`. Use a 600000 ms timeout; bare `sleep` is blocked. Expect an occasional fix-up build.
- Commit trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and the Claude-Session line.
- Keep this file current in the repo and in the Project (`Projects project_write` with `local_path` as an absolute path).
- Never claim on-device testing. Style: UK English, no em-dashes.
- Kotlin/Compose gotchas hit: cross-module smart casts (copy to a local val); named arguments for entity constructors; icon `EventRepeat` needs its extension import (use `CalendarMonth`); `combinedClickable`/`SwipeToDismissBox` need `@OptIn`; local functions that call composables must be `@Composable`; do not read `LocalContext` inside a click lambda.
- Obtainium: same package id and signing key, never change either.
- A failing source never wipes cached data; no tokens or personal data in logs.

## 6. Feature status (all built unless stated)

- Train: Full body A/B/C programme seeded (Mon/Wed/Fri, 10 weeks), week strip, move a day, past workouts, workout and programme editor (rename, sets/reps/weight/rest, reorder, supersets, add via picker, schedule), custom exercises.
- Live workout (Everfit flow, from his screen recording, run 50): top bar shows Total Duration and one dot per section; each section opens on an intro page (name, N exercises, Start Section, exercise list). After Start Section each exercise is its own full-screen page (start-position picture, set table, Add set, Mark all complete); supersets share a page with Mark all supersets. When an exercise's sets are all ticked it slides to the next page; the last page has Complete section, which shows a badge page ("N exercises, COMPLETED") and then moves to the next section's intro. After the last section the badge page says "Swipe to complete workout"; swiping finishes and opens the summary (asks first if sets are unticked). Edit on the badge page reopens the section; the X on an exercise page goes back to the intro. Also: keypad with step setting, previous column, swipe to delete with Undo, set types, RPE, replace/add exercise or superset, rest timer with notification, edit after saving, records and history, pictures and steps (tap the picture). Finish now and Discard are in the three-dot menu; the chevron closes the screen and keeps the session running.
- Food: diary, quick add, repeating foods, recipes, barcode scanner (CameraX + ML Kit) with Open Food Facts, custom foods, about 2,850 bundled CoFID foods, full nutrient summary, targets editor, water with undo, Cronometer daily-totals import (about 400 days, one entry per day).
- Body: scale pairing and live weight, typed weight, weekly weigh-in reminder, Sunday check-in tick boxes with reminder.
- Today: workout card, energy ring, water, steps and active kcal, check-in; pull to refresh; reorderable cards.
- Progress: weight vs 80 kg with trend and forecast date, 14-day calories, 8-week sessions and volume.
- Health Connect: permissions, steps/sleep/HR/kcal read, weights imported from other apps, food/water/weights/workouts written (last 3 days on each open, full 90 days weekly).
- Backups: manual and nightly encrypted backups (last 7 local plus optional folder), restore with recovery key, CSV exports (weights, diary, sets).
- Settings: appearance, colour scheme (18), Frosted glass, Targets, Scale, Health Connect, Backup, Profile, Workout, HQ connection (live preview of the provider output), Import and export (opens Backup).

## 7. Not built, and decisions

1. Everfit history import. Everfit does not export; screenshots only. Mahesh will prompt a browser session from his personal PC (MAHESH-PC); Claude should read the exercise metrics for his client account `https://app.everfit.io/home/client/6593ff40354fc4001f737a63/metrics?tab=exercise` and import PRs/history. No browser was reachable from the earlier session.
2. JSON export (CSV and encrypted backup exist).
3. Custom exercise photos or clips: not wanted for now.
4. HQ app changes: on hold until the chats are merged (section 9).
5. Exercise pictures cover 239 names (all but Machine-Assisted Pull-Up of the programme); most of OpenGym's names differ from free-exercise-db. Extend `tools/build_exercise_images.py` overrides to add more. Pictures load from raw.githubusercontent.com once, then cache.
6. Cronometer servings file has no nutrients, so food-level history cannot be rebuilt; day totals only. Cronometer vitamin D IU is converted /40 to mcg; its "Water (g)" is not imported.
7. Decided 11 Nov: Complete section leaves unticked sets unticked (they do not count). Each exercise has a "Mark all complete" button that ticks its remaining sets (fills target reps, no rest timer).

## 8. Known risks and limits

- Everything below runs on assumptions until tested on the phone: the QN packet decoder (hand-built frames only), Health Connect writes and permission flow, backup/restore, nightly alarm, provider, Cronometer import, section cards, pictures.
- A restore that fails after the old data is deleted cannot roll back.
- GATT status 133 on first scale connect is logged but not retried.
- Food search ranks by name only; with CoFID loaded generic searches return many near matches (limit 60).
- Carbs for CoFID foods are stored as CoFID carbohydrate plus fibre so net carbs equal CoFID's figure.

## 9. Merge plan with HQ

Both apps share a signing key, so the provider permission works. Work to do in the merged chat:
1. HQ reads `content://uk.woozy.fit.summary/today` (needs `<uses-permission android:name="uk.woozy.fit.permission.READ_SUMMARY"/>` and a `<queries>` entry for `uk.woozy.fit`), shows kcal/macros against targets, water, weight, today's workout and weekly count, and the Sunday check-in boxes in the Health/Fitness tab; ignore rows whose `version` it does not know.
2. HQ stops reading nutrition and weight from Health Connect once HQ Fit is the source (avoid double counting).
3. Decide whether HQ's Fitness sub-page needs anything else from HQ Fit (extend the provider, bump `version`).
4. Then run the switch-over checklist (section 11).

## 10. Phone checks (renumbered; none confirmed by Claude)

Foundation: 1 APK installs next to HQ and opens; 2 five tabs, + menu, Settings; 3 light/dark/system persists; 4 all 18 colour schemes, dark-native stay dark; 5 status/gesture bar icons readable; 6 Obtainium updates in place; 7 Settings > Design components opens.
Data and security: 8 first launch shows "Protect your data", wrong retype refused, right one opens Today (write the key down); 9 reopen goes straight to Today; 10 force-closing while the key is on screen makes a new key next launch; 11 update from an older build keeps data.
Train and workout: 12 Train shows Mon/Wed/Fri dots and sections/supersets match Everfit; 13 Move to another day; 14 Today shows the workout; 15 exercise library search; 16 start Fullbody B, tick a set (reps fill, rest timer, notification, vibration at zero), leave and return, Resume workout appears; 17 keypad and step setting; 18 swipe set to delete with Undo, long-press set for type and RPE; 19 section cards: Start, Complete section, last section finishes; 20 tap an exercise for pictures and steps (needs connection once); 21 Finish shows time, sets, volume, records; Discard keeps the day planned; edit a finished workout; 22 replace/add exercise; Records and history screens; 23 New workout, Edit workout, New exercise, Past workouts.
Food: 24 add food, quick add, change amount, water + Undo (also gone from Health Connect), repeating foods; 25 barcode scan, search, offline cache; 26 search "bread" (CoFID) and log 100 g; 27 Create a recipe; All nutrients summary; Targets editor.
Body and sync: 28 Scale: Bluetooth permission, Find scale after stepping on, live weight saves once settled; if it fails send the packet log; 29 typed weight and delete; weekly reminder; 30 Health Connect grant, steps/sleep/HR/kcal, Sync now imports weights, food/water/weights/workouts appear and are not duplicated, Cronometer-era days not re-sent; 31 delete a weight or diary entry removes it from Health Connect; 32 Today steps and kcal; Sunday check-in and 18:00 reminder.
Backup: 33 Back up now, folder copy, nightly status after 03:30; 34 Restore on a spare phone (asks for the recovery key); 35 Cronometer import adds about 400 days, running twice adds nothing; 36 CSV export opens in a spreadsheet; 37 Progress charts fill as you log.
Polish: 38 pull to refresh, reorder cards; 39 Frosted glass toggle; 40 Profile name on Today; HQ connection preview matches the day.
HQ link: needs the HQ change first (section 9).

## 11. Switch-over checklist

1. Run both apps side by side for a week (same food in Cronometer and HQ Fit, compare totals).
2. Sync Health Connect to bring in Feelfit weight history before removing Feelfit.
3. Take a backup and restore it once.
4. Turn on the nightly backup folder.
5. Change HQ (section 9), confirm no double counting.
6. Only then stop using Cronometer and Everfit (after the Everfit history import).
