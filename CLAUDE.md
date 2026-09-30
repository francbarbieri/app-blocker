# App Blocker

A privacy-first Android productivity app that helps users manage screen time by blocking distracting apps. All core functionality runs locally on-device.

## Tech Stack

- **Language**: Kotlin
- **Platform**: Native Android (min SDK 26, target SDK 35)
- **Database**: Room (local SQLite)
- **Architecture**: Clean Architecture (data / domain / presentation / service layers)
- **Build**: Gradle with Kotlin DSL, Android Gradle Plugin 8.7.3

## Project Structure

```
app/src/main/java/com/appblocker/
├── data/               # Room database, DAOs, repository implementations
├── domain/             # Entities, use cases, repository interfaces
├── presentation/
│   ├── theme/          # Material 3 theme (Color, Type, Theme)
│   ├── screen/         # Full-screen Composables
│   ├── components/     # Reusable Compose components
│   └── *.kt            # Activities (thin shells), ViewModels
└── service/            # AccessibilityService for app detection/blocking
```

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
./gradlew clean                  # Clean build outputs
```

## Key Conventions

- Package: `com.appblocker`
- Layer boundaries: domain layer has no Android dependencies; data and service layers depend on domain, not on each other
- Repository pattern: interfaces in `domain/`, implementations in `data/`
- Accessibility Service in `service/` is the only component that interacts with Android system APIs for app detection
- UI: Jetpack Compose with Material 3. The app always renders dark; activities call `enableDarkEdgeToEdge()` so system-bar icons stay light even when the phone is in light mode.
- Activities are thin shells: `setContent { AppBlockerTheme { ScreenComposable() } }`
- Compose state: collect ViewModel StateFlows with `collectAsStateWithLifecycle()`

## Architecture invariants

These rules are mechanically enforced by `app/src/test/java/com/appblocker/LayerBoundariesTest.kt`. Drift fails CI.

- **`domain/` is pure.** Zero imports of `android.*`, `androidx.*`, `androidx.compose.*`, `androidx.room.*`, or `kotlinx.coroutines.android`. Domain models and use cases run on the JVM.
- **`presentation/` does not import `com.appblocker.data.*`.** ViewModels receive use cases via constructor injection from `MainViewModel.Factory` / `SchedulesViewModel.Factory`, which read from [`AppContainer`](app/src/main/java/com/appblocker/AppContainer.kt). Compose screens fetch the Factory via `LocalContext.current.appContainer`.
- **`service/` does not import `com.appblocker.data.*`.** The AccessibilityService reads use cases from `applicationContext.appContainer` in `onCreate`.
- **`AppContainer` is the single composition root.** It is the only file outside `data/` that builds repository implementations. New repositories / use cases are wired here; no other DI graphs.
- **Inject `Clock` for time.** No production code under `domain/` or `data/` may call `System.currentTimeMillis()` / `LocalTime.now()` / `LocalDate.now()` directly — use the `Clock` exposed by the container. Tests pass `Clock { 0L }` or a controllable fake.
- **Use cases stay small.** Inject at most two repositories per use case; orchestrators compose other use cases. `IsAppBlockedUseCase` is the worked example (one repo + two sub-use-cases).
- **Pure logic lives in `domain/model`.** Calculations that need no repository (time windows, activity summaries) are plain functions there, so they are unit-tested without fakes.

## Data-layer invariants

Enforced by `app/src/test/java/com/appblocker/DataInvariantsTest.kt`.

- **Every schema version ships an exported JSON.** Every bump to `@Database(version = N)` produces a `app/schemas/com.appblocker.data.local.AppDatabase/N.json` via KSP `room.schemaLocation`. The 1.json is permanently missing — earlier in the project a bump shipped without the export — so `migrate1To2_dropsAndRecreatesUnblockEvents` is `@Ignore`d with a documented reason. This rule prevents a recurrence.
- **No `fallbackToDestructiveMigration`.** Silent data loss is unacceptable for a privacy-first app. Write a real `Migration(N, N+1)` instead. Enforced.
- **Every migration has a test.** Add an `androidTest` case in `AppDatabaseMigrationTest` for each new `Migration(N, N+1)`. Seed v(N) data, run the migration, assert the resulting shape and that data survived. Not mechanically enforced — caught in review.
- **Joins use `@Relation` + `@Transaction`.** Multi-table reads (schedules with their days and apps) go through `ScheduleWithDaysAndApps`, not hand-rolled `hydrate()` helpers. Room handles atomicity and observability.

## Privacy invariants

This app's value proposition is "all data stays on-device." The following rules are guardrails, not aspirations — most are mechanically enforced by `app/src/test/java/com/appblocker/PrivacyInvariantsTest.kt`. Run `./gradlew test` before merging.

- **No network code.** No `okhttp`, `retrofit`, `java.net.URL`, `HttpURLConnection`, `WebView`, `ktor`, `volley`, or analytics / crash-reporting SDKs anywhere under `app/src/main/`. Enforced.
- **No cloud backup.** `AndroidManifest.xml` declares `android:allowBackup="false"` and `android:dataExtractionRules="@xml/data_extraction_rules"`. The `data_extraction_rules.xml` excludes every backup/transfer domain. Enforced.
- **No `QUERY_ALL_PACKAGES`.** App-listing happens via the manifest `<queries>` block plus `PackageManager.queryIntentActivities(...)` with an `ACTION_MAIN` + `CATEGORY_LAUNCHER` filter — visible by default on API 30+. Enforced.
- **No PII in logs.** Log messages MUST NOT interpolate package names, app names, schedule contents, timestamps tied to user activity, or unblock outcomes. Log the `Throwable`; skip the context. Logcat is readable via ADB and by any app holding `READ_LOGS`. Not mechanically enforced — caught in code review.

## Blocking semantics

`IsAppBlockedUseCase` returns a `BlockReason?` (null = not blocked); the overlay shows the reason to the user.

- A disabled app (`isBlockingEnabled = false`) is never blocked.
- A running focus session blocks every enabled app → `FocusSession`.
- An app with **no schedules at all** is always blocked → `AlwaysBlocked`. An app whose schedules are all **paused** is not blocked by schedules.
- `TIME_WINDOW` schedules are half-open `[start, end)`. `start > end` is an overnight window whose after-midnight part belongs to the day it started; `start == end` covers the whole day. The pure math lives in `domain/model/TimeWindowRules.kt`.
- `DAILY_LIMIT` schedules compare the limit to today's usage from local midnight, including the running session. A matching time window wins over a reached limit.

## Usage sessions

- The tracker opens a session when a tracked app comes to the front and **extends it with a heartbeat** every `ForegroundTracker.CHECK_INTERVAL_MS`. `end_time` is therefore always set, and a killed process loses at most one heartbeat instead of leaving an open-ended row. The session closes when another app, the block overlay or screen-off takes over.
- Range queries clip sessions to the range (`UsageSessionDao.getUsageMs`, `ActivitySummary.build`), so a session that crosses midnight is split between the two days.
- A `DAILY_LIMIT` schedule on several apps is **one shared budget**: `getUsageMs` sums the usage of all of the schedule's apps.

## AccessibilityService design and hot-path discipline

The blocking logic lives in `service/ForegroundTracker.kt`, a plain Kotlin state machine with no Android types, unit-tested with virtual time in `ForegroundTrackerTest`. `AppBlockerAccessibilityService` is a thin shell that filters events and forwards them.

- **Atomic transitions.** Every tracker method runs on `serviceScope` (`Dispatchers.IO.limitedParallelism(1)`) and **none of them suspend**. Single-thread confinement alone is not enough: coroutines interleave at suspension points, so a suspending transition let two handlers both start tracking and orphan an enforcement loop. All suspending work (database access) happens inside the per-app tracking job, which opens its session, closes it in `finally` under `NonCancellable`, and exits once it is no longer the current job. Keep it that way.
- **Enforcement loop.** While a tracked app is in front, its job re-checks `IsAppBlockedUseCase` every `CHECK_INTERVAL_MS` (first check immediately), so blocks land even when nothing changes on screen: a schedule starting, a daily limit being reached, or a 5-minute pass (`GRACE_PERIOD_MS`) expiring.
- **The overlay reports itself.** `BlockOverlayActivity` calls `AppBlockerAccessibilityService.onOverlayInFront` from `onResume`/`onPause`. While it is in front the tracker closes the app's session, ignores late events from tracked apps and never relaunches (and so never resets) the overlay. Do not rely on seeing the overlay's own accessibility event.
- **What counts as an app switch.** Only full-screen windows (`event.isFullScreen`) of any app. Ignored: dialogs, sheets, chat heads and floating activities; keyboards (enabled IMEs, refreshed on `INPUT_METHOD_CHANGED`); `TRANSIENT_PACKAGES` (system UI, system dialogs/share sheet, permission prompts); and every event while the screen is off (always-on display, lock screen). When a floating window closes, the app underneath re-announces itself with a new window event.
- **`notificationTimeout` stays 0.** With coalescing the framework delivers only the last event of a burst, so a keyboard or dialog event could replace the one full-screen event that marks an app switch.
- **Screen off and unlock.** Screen off pauses tracking (a second screen-off before unlock keeps the paused app). Unlock (`USER_PRESENT`, or `SCREEN_ON` without a keyguard) resumes the paused app, with the first check delayed by `RESUME_SETTLE_MS` so an app opened from a lock-screen notification wins. The receiver is registered `RECEIVER_EXPORTED` on purpose: `USER_PRESENT` comes from System UI, which a non-exported receiver rejects; all its actions are protected broadcasts.
- **No DB calls on the event thread.** It only does set lookups plus the `@Volatile ForegroundTracker.isBusy` snapshot, and returns early for untracked apps when nothing is tracked.
- **Debounce overlay launches** inside the tracker: no relaunch for the same package within `OVERLAY_DEBOUNCE_MS`.
- **The overlay runs in its own task** (`android:taskAffinity=""`), so finishing it returns to the blocked app instead of App Blocker's main screen. The app theme (`Theme.AppBlocker`) is dark so the task animation never flashes white.
- **Failures are logged and swallowed**, never thrown (an uncaught exception would crash the process that also hosts the user's UI), and logs never include package names.
- **No allocation-heavy work or JSON parsing inside `onAccessibilityEvent`.**

## Tests

- `./gradlew testDebugUnitTest` — JVM tests: invariants, the service state machine (`ForegroundTrackerTest`), domain rules (`TimeWindowRulesTest`, `IsAppOnActiveScheduleUseCaseTest`, `ActivitySummaryTest`, …) and ViewModels. Shared in-memory fakes live in `app/src/test/.../presentation/fakes/`.
- `./gradlew connectedDebugAndroidTest` — Room DAO tests and `AppDatabaseMigrationTest` on a device or emulator. The schema JSONs are packaged as androidTest assets for `MigrationTestHelper`.

## Skills (`.claude/skills/`)

Each skill is a directory with `SKILL.md` (lightweight, loaded on invocation) + `reference.md` (detailed, loaded lazily when needed).

| Skill | Invocation | Runs as |
|---|---|---|
| `/ui-orchestrate` | `/ui-orchestrate migrate <Activity>` | Main context (orchestrates others) |
| `/theme-setup` | `/theme-setup check` or auto-spawned | Forked subagent |
| `/screen-gen` | `/screen-gen <Activity>` or auto-spawned | Forked subagent |
| `/build-check` | `/build-check` or auto-spawned | Forked subagent |

The orchestrator spawns theme-setup, screen-gen, and build-check as parallel subagents. Each runs in its own context window to keep the main context clean.
