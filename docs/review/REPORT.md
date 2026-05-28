# App Blocker — Codebase Quality Review

_Diagnostic pass. No source files were modified. Five reviewer appendices live alongside this file._

| Appendix | File |
|---|---|
| Architecture | [architecture.md](architecture.md) |
| Android / Kotlin | [android.md](android.md) |
| Data layer | [data.md](data.md) |
| Testing | [testing.md](testing.md) |
| Security & Privacy | [security.md](security.md) |

## Executive summary

The codebase is structurally sound and the high-level Clean Architecture skeleton holds: `domain/` is genuinely free of Android, Compose, and Room dependencies; repository interfaces sit in `domain/` and impls in `data/`; nullability is disciplined (zero `!!` in production); Compose state collection consistently uses `collectAsStateWithLifecycle`; and the codebase ships zero network calls, zero analytics, and zero crash-reporting SDKs. The most consequential problems are not architectural drift — they are **(1) a privacy regression that is one manifest attribute deep, (2) a hand-built DI graph that leaks the data layer into presentation and service, and (3) a critical-mass test gap on the very logic that decides whether to block.** Three reviewers independently flagged the AccessibilityService hot path — concurrent state mutation, per-event DB hammering, no test coverage — making it the single biggest correctness risk on top of the privacy finding. The recommended next-sprint focus is (a) close the backup leak, (b) introduce a composition root and inject a `Clock`, (c) cover `IsAppBlockedUseCase` and the multi-table `ScheduleDao` writes with tests.

## Critical findings

### C1. Room database is auto-backed-up to Google Drive
**Source:** security · `app/src/main/AndroidManifest.xml:7`
`android:allowBackup="true"` is set with no `fullBackupContent` and no `dataExtractionRules`. Android Auto Backup uploads the `databases/` directory to Drive under the user's Google account by default. `app_blocker.db` contains `blocked_apps`, `schedules`, `schedule_apps`, `usage_sessions`, `unblock_events`, `focus_sessions`, and `motivational_messages` — the user's full behavioral history. This directly breaks the stated "all data stays on-device" promise.
**Fix:** set `allowBackup="false"`, or add `android:fullBackupContent="@xml/backup_rules"` and `android:dataExtractionRules="@xml/data_extraction_rules"` excluding the databases dir and `app_blocker_prefs`.

### C2. `IsAppBlockedUseCase` has zero test coverage
**Source:** testing · `app/src/main/java/com/appblocker/domain/usecase/IsAppBlockedUseCase.kt`
This is the central decision function: focus-session override, schedule lookup, day-of-week and time-of-day windows, daily-limit math. Bugs here silently disable the entire product. The class is also flagged by the architecture reviewer for injecting three repositories.
**Fix:** add a JVM test with hand-written fake repositories and an injected `Clock` so `LocalTime.now()` / `LocalDate.now()` are controllable.

### C3. `ScheduleDao` multi-table transactional writes are untested
**Source:** testing · `app/src/main/java/com/appblocker/data/local/dao/ScheduleDao.kt:64,80`
`insertScheduleWithDaysAndApps` and `updateScheduleWithDaysAndApps` are `@Transaction` methods writing to three tables. No instrumented test verifies rollback behavior or row preservation on the delete-then-insert update path. A partial-write bug here corrupts schedules silently.
**Fix:** add an `androidTest` exercising both methods, including a failure-injection case for the transactional rollback.

### C4. Schema v1 JSON missing — migration 1→2 test cannot run
**Source:** data · `app/schemas/com.appblocker.data.local.AppDatabase/1.json`
The file is absent while `AppDatabaseMigrationTest.migrate1To2_dropsAndRecreatesUnblockEvents` (`AppDatabaseMigrationTest.kt:24`) calls `helper.createDatabase(testDbName, 1)`, which requires the v1 schema export. The test throws at run time. Production migration chains still replay correctly on legacy installs, but the 1→2 path is unverifiable by any contributor.
**Fix:** restore `1.json` from git history (recoverable from the commit that introduced v2) or drop the v1 baseline if the project no longer supports v1 installs.

### C5. `MainViewModel` and `SchedulesViewModel` have zero tests
**Source:** testing · `presentation/MainViewModel.kt`, `presentation/SchedulesViewModel.kt`
`MainViewModel` runs an unbounded `while (true) { delay(1_000) }` ticker and orchestrates focus session + blocked-apps list. `SchedulesViewModel.saveEditor` silently no-ops on `appPackageNames.isEmpty()` (`:130`); `deleteEditing` silently no-ops on unknown id (`:153`); `EditorState.Creating`/`Editing` selects create-vs-update at `:142`. None of these branches are asserted. The model for testing them already exists in `BlockOverlayViewModelTest`.
**Fix:** mirror `BlockOverlayViewModelTest`'s pattern (`StandardTestDispatcher` + fakes + `advanceTimeBy`) for both ViewModels, with explicit tests on the silent no-op branches.

### C6. `FocusSessionDao.getActiveSession(now)` time-branch logic untested
**Source:** testing · `app/src/main/java/com/appblocker/data/local/dao/FocusSessionDao.kt:15`
Filters by `ended_at IS NULL AND (expires_at IS NULL OR expires_at > :nowEpochMs)`. The expires-at-passed branch and the indefinite-session branch are both untested. A regression here either silently extends focus sessions past their expiry or terminates indefinite sessions immediately.
**Fix:** add an `androidTest` exercising all three states (active+bounded, expired+bounded, active+indefinite).

## High findings

### H1. AccessibilityService cross-thread race on session-tracking state
**Source:** android · `service/AppBlockerAccessibilityService.kt:62-75`
`currentTrackedPackage` and `currentSessionId` are read/written on the system event thread by `endCurrentSession()` while the same fields are written from a `Dispatchers.IO` coroutine. Two consecutive events for different packages can interleave, leaving an orphaned open `usage_sessions` row.
**Fix:** confine mutations to a single thread (a `Channel<AccessibilityEvent>` consumed by one collector), or convert to `@Volatile` + `AtomicReference` with compare-and-set.

### H2. AccessibilityService hits Room on every window-state event
**Source:** android · `service/AppBlockerAccessibilityService.kt:66-75`
`isAppBlocked` performs 3–4 DAO calls per event. Window-state events fire at high rates during scrolling and IME transitions; there is no in-memory cache and no debounce.
**Fix:** maintain a `StateFlow<Set<String>>` of currently-blocked package names populated at service start; the hot path becomes an O(1) `Set.contains` check, falling back to DB only when a package is in the blocked set.

### H3. AccessibilityService and ViewModels reach into `data/` directly (no composition root)
**Source:** architecture · `service/AppBlockerAccessibilityService.kt:6-9`, `presentation/MainViewModel.kt:6-8,23-28`, `presentation/SchedulesViewModel.kt:6-7,49-53`, `presentation/BlockOverlayActivity.kt:14-16`
Three constructors each independently instantiate `AppDatabase` and concrete `*RepositoryImpl` classes. Presentation and service layers thereby import `com.appblocker.data.*`, violating the documented layering rule, and any change to the repository graph requires edits in four places.
**Fix:** introduce an `AppContainer` on `Application` (or Hilt) that builds repositories and use cases once; pass use case references via `ViewModelProvider.Factory` (the pattern `BlockOverlayActivity` already uses).

### H4. ViewModels invoke repositories directly, skipping use cases
**Source:** architecture · `presentation/MainViewModel.kt:66,101,107`, `presentation/SchedulesViewModel.kt:61-62,143-144,155`
Eight call sites bypass the domain use-case layer entirely. The documented architecture says presentation depends on `domain/` only.
**Fix:** introduce `ObserveFocusSessionUseCase`, `StartFocusSessionUseCase`, `EndFocusSessionUseCase`, `GetSchedulesUseCase`, `SaveScheduleUseCase`, `DeleteScheduleUseCase`; the ViewModels stop importing `data/`.

### H5. `IsAppBlockedUseCase` injects three repositories
**Source:** architecture · `domain/usecase/IsAppBlockedUseCase.kt:12-16`
Exceeds the documented two-repo cap and fuses three policies (focus-session override, schedule lookup, daily-limit accounting) into one class.
**Fix:** split into `IsFocusSessionActiveUseCase`, `IsAppOnActiveScheduleUseCase`, `HasReachedDailyLimitUseCase`; compose them in a thin orchestrator. (Pairs with C2 — splitting makes coverage easier.)

### H6. PII (package names + behavior outcomes) in logcat
**Source:** security · `presentation/BlockOverlayViewModel.kt:67,79,90,101`
Four `Log.w` calls interpolate the blocked package name and the user's chosen outcome (`LEGITIMATE`, `BACKED_OFF`, `BROKE_PLAN_PROCEEDED`) into messages readable via ADB and by any process with `READ_LOGS`. The fourth (`BROKE_PLAN_PROCEEDED`) leaks a user's plan failure for a named app.
**Fix:** log only the throwable (no message), or substitute the package name with a hash. Apply ProGuard rules to strip the log strings in release.

### H7. `UnblockEventEntity` index does not cover `ORDER BY timestamp`
**Source:** data · `data/local/entity/UnblockEventEntity.kt:19`
Single-column `Index("app_package_name")` covers the `WHERE` clause but `UnblockEventDao` also `ORDER BY timestamp DESC` (`:16-19`) and ranges by timestamp (`:22-26`), forcing a filesort each call.
**Fix:** promote to `Index("app_package_name", "timestamp")` so the index covers both queries.

### H8. `BlockedAppRepositoryImpl.hydrate()` re-fetches per emission without `@Transaction`
**Source:** data · `data/repository/BlockedAppRepositoryImpl.kt:77-87`
Two extra DAO calls (`getDaysForSchedules`, `getAppsForSchedules`) run on every Flow emission of `getAllSchedules`, none under `@Transaction`. A concurrent write can surface days/apps for a schedule that no longer exists.
**Fix:** model the join as `@Relation`-backed `ScheduleWithDaysAndApps` with `@Transaction` on the query; Room handles atomicity and observability.

### H9. `QUERY_ALL_PACKAGES` declared but never used
**Source:** security · `app/src/main/AndroidManifest.xml:4`
The only listing code (`MainScreen.kt:179`) uses `pm.queryIntentActivities` with a launcher intent filter, which does not require this permission on API 30+. The permission triggers Play Console's sensitive-permission policy review.
**Fix:** remove the permission. If a future feature needs broader visibility, declare a `<queries>` element instead.

### H10. `AddBlockedAppUseCase` / `RecordUsageUseCase` / `FocusSessionRepositoryImpl` use `System.currentTimeMillis()` directly
**Source:** testing + data · `AddBlockedAppUseCase.kt:12`, `RecordUsageUseCase.kt:9`, `FocusSessionRepositoryImpl.kt:15`
No clock injection means future tests are time-dependent. This is a prerequisite for closing the test gaps in C2/C3/C5/C6, not a follow-up.
**Fix:** inject a `Clock` (or `() -> Long`) into each; the production wiring passes `Clock.systemDefaultZone()` from the composition root.

### H11. AccessibilityService can re-launch overlay on repeated events for the same package
**Source:** android · `service/AppBlockerAccessibilityService.kt:69-74`
The `currentTrackedPackage != packageName` guard prevents some duplication but not re-entry from the user dismissing the overlay back to the blocked app.
**Fix:** debounce identical-package events within ~500 ms, or check that the overlay activity is not already foreground before calling `startActivity`.

### H12. `MainActivity` is exported (justified, flagged for awareness)
**Source:** security · `app/src/main/AndroidManifest.xml:12-19`
Export is required for the launcher icon; the activity reads no `Intent` extras (`MainActivity.kt:16`) so there is no current injection surface. Listed so future contributors don't add extras handling without re-evaluating the export.
**Fix:** none required today; document the constraint at the top of `MainActivity.kt`.

## Medium & Low — counts per appendix

| Appendix | Critical | High | Medium | Low |
|---|---:|---:|---:|---:|
| Architecture | 0 | 12 | 2 | 1 |
| Android / Kotlin | 0 | 5 | 5 | 5 |
| Data | 1 | 4 | 4 | 4 |
| Testing | 5 | 5 | 2 | 1 |
| Security | 1 | 5 | 2 | 3 |

Full detail for each item lives in the linked appendix.

## Cross-cutting themes

1. **DI by hand is the root of several findings.** Architecture (H3, H4), Android (theme drift / repeated graph construction), and Testing (impossible to swap fakes without a clock + composition root) all converge here. A single `AppContainer` on `AppBlockerApplication` resolves the architecture violations and unblocks testability simultaneously.
2. **Time is uninjected.** Direct `System.currentTimeMillis()` and `LocalTime.now()` calls appear in three use cases, one repository, and one ViewModel ticker. This is a prerequisite for closing the test-coverage gaps — it cannot be done lazily after writing tests, because the tests cannot exist without it.
3. **The AccessibilityService is doing too much on the wrong thread.** Three independent reviewers landed on this file: cross-thread state race, per-event DB queries, untested. Whoever owns the next change here should also add an in-memory cache + single-thread confinement; the change is small and removes all three risks at once.
4. **"Local only" has soft spots beyond network code.** Security flagged Auto Backup (Critical) and plaintext DB (Medium); Android flagged `allowBackup` defaulting open. The privacy story is consistent in *code* but inconsistent in *configuration* — the next privacy claim in marketing copy should be backed by a `dataExtractionRules` audit, not just a grep for `OkHttp`.
5. **Where tests exist, they are excellent — but coverage is one mile deep, three feet wide.** `BlockOverlayViewModelTest` uses fakes (not mocks), `runTest` + `advanceTimeBy`, and asserts state transitions. All three Room migrations have matching `MigrationTestHelper` tests. The team clearly knows how to write good tests; the gap is investment, not skill.

## Out of scope for this review

- **Compose UI tests** under `androidTest` — none exist for `HomeScreen`, `OnboardingScreen`, `SchedulesScreen`, `BlockOverlayScreen`. Not requested; worth tracking.
- **Release-build hardening** — `app/build.gradle.kts:21-28` has `isMinifyEnabled = false`. Enabling R8 + ProGuard would shrink the APK and (relevantly to H6) strip log strings.
- **Localization** — only `values/strings.xml` exists; no localized variants to audit for the AccessibilityService description.
- **Performance under load** — static review only; no profiling of the AccessibilityService hot path or Compose recomposition behavior.
- **Lint / Detekt / Spotless configuration** — not inspected; the team may already have these or may benefit from them.
- **Schedule midnight wrap-around** (`IsAppBlockedUseCase.kt:50`) — flagged by android reviewer as a long-tail item; `now in start..end` does not match windows like 22:00–06:00. Treated as a logic bug discovered during review, not part of the architectural pass.
- **Wider OEM testing of the accessibility ignore-list** (`AppBlockerAccessibilityService.kt:29-35`) — hardcoded set is brittle across Samsung/MIUI launchers.
