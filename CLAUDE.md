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
- UI: Jetpack Compose with Material 3 (migration in progress from XML)
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

## AccessibilityService hot-path discipline

`onAccessibilityEvent` runs on the system event thread, fires at high rates during scrolling and IME transitions, and shares a process with every other foreground activity. The hot path must stay cheap.

- **No DB calls on the event thread.** The slow path (`IsAppBlockedUseCase` and friends) only runs inside `serviceScope.launch { ... }`. The event thread reads `blockedPackages` (an in-memory `@Volatile Set<String>` populated by `ObserveEnabledBlockedPackagesUseCase`) and returns early when the package is not tracked.
- **Single-thread confinement for session state.** `currentTrackedPackage` and `currentSessionId` are mutated exclusively inside `serviceScope`, which is built on `Dispatchers.IO.limitedParallelism(1)`. Never read or write those fields from the event thread.
- **Debounce overlay launches.** Track `lastOverlayPackage` + `lastOverlayAtMs` on the event thread; suppress launches for the same package within `OVERLAY_DEBOUNCE_MS`.
- **No allocation-heavy work or JSON parsing inside `onAccessibilityEvent`.** New work should follow the fast-path → slow-path split.

## Skills (`.claude/skills/`)

Each skill is a directory with `SKILL.md` (lightweight, loaded on invocation) + `reference.md` (detailed, loaded lazily when needed).

| Skill | Invocation | Runs as |
|---|---|---|
| `/ui-orchestrate` | `/ui-orchestrate migrate <Activity>` | Main context (orchestrates others) |
| `/theme-setup` | `/theme-setup check` or auto-spawned | Forked subagent |
| `/screen-gen` | `/screen-gen <Activity>` or auto-spawned | Forked subagent |
| `/build-check` | `/build-check` or auto-spawned | Forked subagent |

The orchestrator spawns theme-setup, screen-gen, and build-check as parallel subagents. Each runs in its own context window to keep the main context clean.
