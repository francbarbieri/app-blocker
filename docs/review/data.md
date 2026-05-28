# Data Layer Review

## Summary

The data layer is at schema version 4 with three production migrations (1->2, 2->3, 3->4), all wired into the builder at `AppDatabase.kt:134` and each covered by an instrumented test in `AppDatabaseMigrationTest.kt`. Entities declare foreign keys with explicit `CASCADE`, index foreign-key columns either explicitly or via the leftmost column of a composite primary key, and repositories map entity types to domain models before exposing them. The persistence layer is correct but not optimally indexed: ordered/range queries on `unblock_events` and `usage_sessions` (timestamp dimension) currently fall back to filesorts because indexes only cover the package_name prefix, and the schema v1 JSON is absent so the 1->2 migration test cannot create a v1 baseline. Writes consistently use `suspend`; reactive reads consistently use `Flow`. `fallbackToDestructiveMigration()` is not in use.

## Findings

### Entities, foreign keys, indexes

- **[Critical]** `app/schemas/com.appblocker.data.local.AppDatabase/` — schema `1.json` is missing; only `2.json`, `3.json`, `4.json` are exported. `AppDatabaseMigrationTest.migrate1To2_dropsAndRecreatesUnblockEvents` (`AppDatabaseMigrationTest.kt:24`) calls `helper.createDatabase(testDbName, 1)`, which requires `1.json` and will throw `IllegalStateException` at test run time. Fresh-install users of v1 are still covered because Room replays the migration chain, but any tester or contributor running `connectedAndroidTest` cannot validate the 1->2 path. Restore `1.json` (recoverable from commit `38f9c4d` history of the v1 entity definitions) or pin the project's minimum installed version above v1 and remove the 1->2 test.
- **[High]** `app/src/main/java/com/appblocker/data/local/entity/UnblockEventEntity.kt:19` — `Index("app_package_name")` covers `WHERE app_package_name = ?` but not `ORDER BY timestamp DESC` (`UnblockEventDao.kt:16-19`) nor `AND timestamp >= ?` (`UnblockEventDao.kt:22-26`). Replace with `Index("app_package_name", "timestamp")` so both queries become index-covered without a filesort/scan.
- **[Medium]** `app/src/main/java/com/appblocker/data/local/entity/UsageSessionEntity.kt:19` — the compound `Index("app_package_name", "start_time")` covers range scans by `start_time` but not the column projection `duration_ms` used by `getTodayTotalDurationMs` and `getTotalDurationForRange` (`UsageSessionDao.kt:30-46`). For an aggregation hot path, a covering index `Index("app_package_name", "start_time", "duration_ms")` removes the row lookup; consider only if profiling shows it matters.
- **[Medium]** `app/src/main/java/com/appblocker/data/local/dao/BlockedAppDao.kt:23` — `SELECT * FROM blocked_apps ORDER BY app_name ASC` has no index on `app_name`, so every observation re-sorts the table. Set size is small (one row per user-tracked app), so leave as-is until the list grows; add `Index("app_name")` if scroll latency surfaces.
- **[Low]** `app/src/main/java/com/appblocker/data/local/entity/MotivationalMessageEntity.kt:26` — `app_package_name` is nullable to model "general" messages, and an FK to `blocked_apps(package_name)` exists. NULL is correctly unenforced by SQLite, but the design conflates two row types in one table. Acceptable; document the convention in the entity KDoc.

### Migrations

- **[High]** `app/src/main/java/com/appblocker/data/local/AppDatabase.kt:69-110` — `MIGRATION_2_3` recreates the `schedules` table via `schedules_new` rename but does not recreate `schedule_days` or rewrite its FK target. SQLite preserves FKs across `RENAME TO`, so existing `schedule_days` rows still reference the renamed `schedules` table — the migration test confirms this. No fix required, but add a comment at line 107-108 noting that FK preservation is intentional, because the pattern is non-obvious.
- **[High]** `app/src/main/java/com/appblocker/data/local/AppDatabase.kt:112-125` — `MIGRATION_3_4` creates `focus_sessions` but does not declare the indexes a future query pattern may need. The current `FocusSessionDao` queries (`FocusSessionDao.kt:15-33`) filter on `ended_at IS NULL`, `expires_at`, and `id` — `id` is the PK, the others are full-table conditions that are cheap only while session count stays small. Add `Index("ended_at")` when historical sessions accumulate.
- **[Medium]** `AppDatabase.kt:51-67` — `MIGRATION_1_2` drops and recreates `unblock_events`, discarding all prior rows. This was the documented intent (the old `user_proceeded` boolean is replaced by an `outcome` enum) but constitutes silent data loss. Acceptable per the design note in commit `38f9c4d`; future migrations should prefer `ALTER TABLE`/data-mapping over `DROP`+`CREATE` unless the data is truly disposable.

### DAO query quality

- **[Medium]** `app/src/main/java/com/appblocker/data/local/dao/ScheduleDao.kt:38-46` — `getAllSchedules` and `getSchedulesForApp` return `Flow<List<ScheduleEntity>>` but consumers always re-fetch days + apps via `hydrate()` (`BlockedAppRepositoryImpl.kt:77-87`). The repository runs two extra DAO queries per emission, then groups in memory. Both helper queries (`getDaysForSchedules`, `getAppsForSchedules`, `ScheduleDao.kt:58-62`) are filtered by `schedule_id IN (...)`, which is bounded — but the hydrate path is not annotated `@Transaction`, so a concurrent write could surface days/apps for a schedule that no longer exists, leaving the returned `Schedule` with stale child rows. Add `@Transaction` to `getAllSchedules`/`getSchedulesForApp` or annotate `hydrate` to run under a Room transaction.
- **[Low]** `app/src/main/java/com/appblocker/data/local/dao/UsageSessionDao.kt:15` — the update statement computes `duration_ms = :endTime - start_time` server-side. Fine; flagged only because the column duplicates derived state. A computed projection on read would normalize it, but the current shape is simpler.
- **[Low]** `app/src/main/java/com/appblocker/data/local/dao/MotivationalMessageDao.kt:29-34` — `ORDER BY RANDOM() LIMIT 1` over the partition `(app_package_name = ? OR IS NULL)` is acceptable at current sizes; if the table grows large, switch to "count then offset" to avoid the full-partition shuffle.

### Repository implementations

- **[High]** `app/src/main/java/com/appblocker/data/repository/BlockedAppRepositoryImpl.kt:77-87` — `hydrate()` issues two suspending DAO calls per Flow emission of `getAllSchedules` / `getSchedulesForApp`. On every schedule mutation the entire schedule list re-emits and `getDaysForSchedules` + `getAppsForSchedules` re-run. This is not a per-row N+1 (the queries use `IN (:ids)`), but it is a per-emission re-fetch that ignores Room's relation support. Replace with `@Relation`-backed DTOs and a `@Transaction`-annotated query that returns `Flow<List<ScheduleWithDaysAndApps>>` — Room will keep the join atomic and observable.
- **[Medium]** `app/src/main/java/com/appblocker/data/repository/BlockedAppRepositoryImpl.kt:14-17` — the repository class mixes two domains: `BlockedApp` CRUD and `Schedule` CRUD. The `BlockedAppRepository` interface (`BlockedAppRepository.kt:7-21`) reflects this split. Extract `ScheduleRepository` so each repository targets a single aggregate root; the `IsAppBlockedUseCase` only consumes the schedule slice anyway.
- **[Low]** `app/src/main/java/com/appblocker/data/repository/FocusSessionRepositoryImpl.kt:14-30` — `startSession` and `endActiveSession` call `System.currentTimeMillis()` directly. The use case is fine in production, but it complicates testing in `IsAppBlockedUseCase` and elsewhere. Inject a `Clock` (or take `now: Long` as a parameter) so the repository is deterministic in tests.

### Suspend vs Flow

- No findings. All writes (`@Insert`, `@Update`, `@Delete`, write `@Query`) across `BlockedAppDao`, `ScheduleDao`, `UsageSessionDao`, `UnblockEventDao`, `MotivationalMessageDao`, `FocusSessionDao` are `suspend`. Reactive UI reads (`getAllBlockedApps`, `getAllSchedules`, `getSchedulesForApp`, `getSessionsForApp`, `getEventsForApp`, `getMessagesForApp`, `getGeneralMessages`, `observeLatestOpenSession`) return `Flow`. One-shot reads inside the accessibility service hot path (`isBlockingEnabled`, `getActiveSchedulesForApp`, `getTodayTotalDurationMs`, `getActiveSession`) are `suspend` — correct.

## Calibration examples

- **Critical**: Missing `schema/1.json` (above). Test infrastructure cannot validate the 1->2 path, and any future bug fix to `MIGRATION_1_2` becomes unverifiable.
- **High**: `UnblockEventEntity.kt:19` index does not cover `ORDER BY timestamp` — a correctness-adjacent performance footgun on a query the unblock-history UI is likely to hit per-app.
- **Medium**: `ScheduleDao.kt:38-46` returns `Flow<List<ScheduleEntity>>` without `@Transaction`, allowing torn reads of schedule + days + apps.
- **Low**: `MotivationalMessageEntity.kt:26` nullable FK column is a documented convention but coexistence of "scoped" and "general" rows in one table is a maintainability nit.

## Schema version and migration coverage

| Version | Migration file (in `AppDatabase.kt`)        | Migration test (in `AppDatabaseMigrationTest.kt`)        | Schema JSON                  |
|---------|---------------------------------------------|----------------------------------------------------------|------------------------------|
| 1       | n/a (initial)                               | n/a                                                      | **missing** (`1.json`)        |
| 2       | `MIGRATION_1_2` at `AppDatabase.kt:51-67`   | `migrate1To2_dropsAndRecreatesUnblockEvents` (`:21-53`) | `2.json` present              |
| 3       | `MIGRATION_2_3` at `AppDatabase.kt:69-110`  | `migrate2To3_movesAppFkIntoScheduleAppsJunction` (`:55-113`) | `3.json` present          |
| 4       | `MIGRATION_3_4` at `AppDatabase.kt:112-125` | `migrate3To4_createsFocusSessionsTable` (`:115-145`)    | `4.json` present              |

`@Database(version = 4)` is declared at `AppDatabase.kt:35`. All three migrations are registered at `AppDatabase.kt:134`. `fallbackToDestructiveMigration()` is not in use.

## Not detailed

- Coroutine dispatcher choice: DAO writes run on Room's IO executor implicitly; service-side `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` at `AppBlockerAccessibilityService.kt:22` is correct.
- Database singleton (`AppDatabase.kt:127-138`) is the standard double-checked-locking pattern; acceptable.
- No `@Embedded` or `@Relation` types exist in the codebase, so the "`@Transaction` required on relation methods" rule has no applicable sites today (but see the `ScheduleDao` recommendation above to introduce one).
- Repository classes are constructed ad-hoc in `MainViewModel`, `SchedulesViewModel`, `AppBlockerAccessibilityService`, `BlockOverlayActivity`. That is a DI smell, not a data-layer concern, so it is left to the architecture review.
- `UsageSessionEntity.endTime` and `durationMs` are both nullable — the live session row has neither until `endSession`. Reasonable, but `getTodayTotalDurationMs` sums `duration_ms` and so silently skips active sessions; consumers should know.
- `BlockedAppEntity.packageName` is the primary key, intentionally non-autogenerated and stable across app reinstalls.
