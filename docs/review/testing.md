# Testing Review

## 1. Summary

The codebase has minimal test coverage with only three test files: one JVM ViewModel test (`BlockOverlayViewModelTest`), one JVM mapping test (`UnblockOutcomeMappingTest`), and one instrumented migration test (`AppDatabaseMigrationTest`). Where tests exist, they are high quality — `BlockOverlayViewModelTest` uses `StandardTestDispatcher` + `runTest` + `advanceTimeBy` correctly, asserts concrete state transitions (not mock interactions), and uses hand-written fakes rather than Mockito. The migration test covers all three migrations (1→2, 2→3, 3→4) end-to-end with seeded data. The gap is breadth, not depth: two of three ViewModels (`MainViewModel`, `SchedulesViewModel`), six of seven use cases, and all six DAOs have no tests at all. `IsAppBlockedUseCase` — the core decision function for the entire blocking feature — is untested despite being the most logic-heavy class in the codebase.

## 2. Coverage gap matrix

| Production class | Test exists? | Meaningful asserts? | Edge cases? | Notes |
|---|---|---|---|---|
| `domain/usecase/IsAppBlockedUseCase.kt` | No | n/a | n/a | Critical — central blocking decision, time/date branches, focus override, DAILY_LIMIT math, all untested |
| `presentation/MainViewModel.kt` | No | n/a | n/a | Critical — clock ticker, focus session, blocked-apps list orchestration |
| `presentation/SchedulesViewModel.kt` | No | n/a | n/a | Critical — editor sealed-class state machine, save/delete branches, `appPackageNames.isEmpty()` guard |
| `data/local/dao/ScheduleDao.kt` | No | n/a | n/a | Critical — `@Transaction` insertScheduleWithDaysAndApps / updateScheduleWithDaysAndApps multi-table writes |
| `data/local/dao/BlockedAppDao.kt` | No | n/a | n/a | High — REPLACE conflict strategy, blocking-enabled toggle |
| `data/local/dao/FocusSessionDao.kt` | No | n/a | n/a | High — `getActiveSession(now)` expires_at NULL/non-NULL branches |
| `data/local/dao/UnblockEventDao.kt` | No | n/a | n/a | High — `getEventCountSince` timestamp filter |
| `data/local/dao/UsageSessionDao.kt` | No | n/a | n/a | High — `duration_ms = end_time - start_time` computed update, COALESCE SUM |
| `data/local/dao/MotivationalMessageDao.kt` | No | n/a | n/a | High — `getRandomMessageForApp` (per-app OR NULL fallback) |
| `domain/usecase/RecordUsageUseCase.kt` | Partial | Yes | No | Indirectly covered through `BlockOverlayViewModelTest` outcome assertions and `UnblockOutcomeMappingTest`; no direct test for `startSession`/`endSession` |
| `domain/usecase/GetMotivationalMessageUseCase.kt` | Partial | Yes | Partial | Exercised via fake in ViewModel test; null-message fallback covered |
| `domain/usecase/AddBlockedAppUseCase.kt` | No | n/a | n/a | High — uses `System.currentTimeMillis()` directly (untestable time dep) |
| `domain/usecase/GetAllBlockedAppsUseCase.kt` | No | n/a | n/a | Medium — thin pass-through |
| `domain/usecase/RemoveBlockedAppUseCase.kt` | No | n/a | n/a | Medium — null-app early return path |
| `domain/usecase/ToggleBlockingUseCase.kt` | No | n/a | n/a | Low — single-line delegate |
| `presentation/BlockOverlayViewModel.kt` | Yes | Yes | Yes | Strong: 11 tests, countdown, skip, all three outcomes, fallback message, back-from-motivational |

## 3. Migration test coverage

| Migration | Test | Status |
|---|---|---|
| `MIGRATION_1_2` (`AppDatabase.kt:51`) | `migrate1To2_dropsAndRecreatesUnblockEvents` (`AppDatabaseMigrationTest.kt:22`) | Present — seeds v1 row, asserts table is empty post-migration, validates new outcome column |
| `MIGRATION_2_3` (`AppDatabase.kt:69`) | `migrate2To3_movesAppFkIntoScheduleAppsJunction` (`AppDatabaseMigrationTest.kt:56`) | Present — seeds schedules + days, asserts junction populated and days survive |
| `MIGRATION_3_4` (`AppDatabase.kt:112`) | `migrate3To4_createsFocusSessionsTable` (`AppDatabaseMigrationTest.kt:116`) | Present — validates new table accepts both bounded and indefinite sessions |

All migrations have matching tests. No `MISSING` rows.

## 4. Findings

### Critical

- **[Critical]** `domain/usecase/IsAppBlockedUseCase.kt:17` — Core blocking decision has zero tests. The function combines four data sources (blocked-app enabled flag, focus session, schedules, today's usage) and branches on day-of-week, time-of-day windows, and `DAILY_LIMIT` minute math. Bugs here silently break the entire product feature. Add a JVM test with fake repositories and inject a `Clock` to control `LocalTime.now()` / `LocalDate.now()`.
- **[Critical]** `presentation/MainViewModel.kt:1` — No test exists. The `startClockTick` `while (true) { delay(1_000) }` loop and the focus-session observer are core to the home screen. Add a test using `StandardTestDispatcher` mirroring `BlockOverlayViewModelTest`.
- **[Critical]** `presentation/SchedulesViewModel.kt:1` — No test exists. `saveEditor` silently returns when `appPackageNames.isEmpty()` (line 130); `deleteEditing` silently no-ops when the editing id is not found (line 153); the `EditorState.Creating`/`Editing` distinction in `saveEditor` (line 142) drives create-vs-update. None of these branches is asserted.
- **[Critical]** `data/local/dao/ScheduleDao.kt:64` — `@Transaction insertScheduleWithDaysAndApps` and `updateScheduleWithDaysAndApps` (line 80) write to three tables. No instrumented test verifies transactional rollback or that delete-then-insert preserves rows on failure. Add an `androidTest` covering both methods.
- **[Critical]** `data/local/dao/FocusSessionDao.kt:15` — `getActiveSession(now)` filters by `ended_at IS NULL AND (expires_at IS NULL OR expires_at > :nowEpochMs)`. The expires-at-passed branch and the indefinite-session branch are both untested.

### High

- **[High]** `data/local/dao/UsageSessionDao.kt:15` — `updateEndTime` computes `duration_ms = end_time - start_time` in SQL. This is the only place the duration is set; if the column expression is wrong, `getTodayTotalDurationMs` silently returns 0. No instrumented test.
- **[High]** `data/local/dao/MotivationalMessageDao.kt:29` — `getRandomMessageForApp` falls back to `app_package_name IS NULL` general messages. No test asserts the fallback path triggers when no per-app message exists.
- **[High]** `domain/usecase/AddBlockedAppUseCase.kt:12` — Uses `System.currentTimeMillis()` directly with no injection, so any future test will be time-dependent. Inject a `Clock` (or `() -> Long`) before adding a test.
- **[High]** `domain/usecase/RecordUsageUseCase.kt:9` — `startSession` / `endSession` / `recordUnblock` all call `System.currentTimeMillis()` directly. Same fix.
- **[High]** `data/repository/FocusSessionRepositoryImpl.kt:15` — All three methods call `System.currentTimeMillis()` directly. Untestable without injection.

### Medium

- **[Medium]** `app/src/test/java/com/appblocker/presentation/BlockOverlayViewModelTest.kt:85` — `advanceUntilIdle()` after countdown init relies on the implicit `ZERO_HOLD_MS = 800L` from `BlockOverlayViewModel.kt:114`. If `ZERO_HOLD_MS` increases to a delay that the `runTest` virtual scheduler still drains, the test passes silently regardless of value. Asserting `advanceTimeBy(5_000 + 800)` then `runCurrent()` would pin the timing contract.
- **[Medium]** `app/src/test/java/com/appblocker/data/UnblockOutcomeMappingTest.kt:22` — `UnblockOutcome.values()` is deprecated in Kotlin 1.9+. Prefer `entries`. Style-only.

### Low

- **[Low]** `app/src/test/java/com/appblocker/data/UnblockOutcomeMappingTest.kt:43` — `dao.events.value.first().outcome` reads a private field of a hand-rolled fake from outside the fake's class (it's `internal val` on a private class in the same file, so this works, but it couples the test to fake internals). Expose via a method on the fake.

## 5. Calibration examples

- **Critical example** (zero coverage on production logic): `IsAppBlockedUseCase` (above) — orchestrates focus session + schedules + daily-limit math, no test.
- **Critical example** (zero coverage on multi-table transactional DAO): `ScheduleDao.insertScheduleWithDaysAndApps` — three-table write annotated `@Transaction`, no test.
- **High example** (test exists but asserts nothing meaningfully): None found. Where tests exist, they assert concrete state.
- **Medium example** (brittle/time-dependent): `AddBlockedAppUseCase.kt:12` direct `System.currentTimeMillis()` makes any future test order-dependent on wall clock; same pattern in `RecordUsageUseCase` and `FocusSessionRepositoryImpl`.
- **Low example**: `UnblockOutcome.values()` deprecation in `UnblockOutcomeMappingTest.kt:22`.

No findings of the form "verify(mock) with no state assertion" were observed — tests do not use Mockito at all, which is itself a positive signal.

## 6. Not detailed

- `service/AppBlockerAccessibilityService.kt` — system-API integration, instrumented test would require an emulator with accessibility permissions; out of scope for unit-test posture but worth tracking.
- `data/repository/BlockedAppRepositoryImpl.kt` and `MotivationalMessageRepositoryImpl.kt` — repository implementations covered indirectly if/when DAO tests are added, since they are thin entity-to-domain mappers.
- Compose-screen UI tests under `androidTest` — none exist for `HomeScreen`, `OnboardingScreen`, `SchedulesScreen`, `BlockOverlayScreen`. Not requested for this review but a gap worth noting.
- `BlockOverlayViewModel.Factory` (line 118) — `ViewModelProvider.Factory` shim, low value to test.
- Entity classes under `data/local/entity/` — POKOs, no test needed.
- Domain models under `domain/model/` — data classes / enums, no test needed.
