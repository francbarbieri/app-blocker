# Architecture Review — App Blocker

## Summary

The codebase honors the high-level Clean Architecture skeleton: the `domain/` package is free of Android, Compose, and Room dependencies, and every concrete repository under `data/repository/` implements an interface defined in `domain/repository/`. The data layer correctly avoids reaching back into `presentation/` or `service/`. The two structural problems are downward: the `AppBlockerAccessibilityService` constructs repository implementations directly from `data/`, and both `MainViewModel` and `SchedulesViewModel` instantiate `*RepositoryImpl` classes inside the ViewModel — bypassing use cases and bleeding the `data/` layer into the presentation and service layers. One use case (`IsAppBlockedUseCase`) also crosses the documented "max two repositories per use case" line.

## Findings

### Domain purity

No violations. Recursive grep for `android.`, `androidx.`, `kotlinx.coroutines.android`, Room annotations, and `androidx.compose` against `app/src/main/java/com/appblocker/domain/` returns zero hits. `IsAppBlockedUseCase` uses `java.time.*`, which is JDK, not Android.

### Data layer dependencies

No violations. No file under `app/src/main/java/com/appblocker/data/` imports anything from `com.appblocker.presentation` or `com.appblocker.service`.

### Service layer dependencies

- **[High]** `app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt:6` — Imports `com.appblocker.data.local.AppDatabase`. Service should receive a `BlockedAppRepository` / `UsageRepository` / `FocusSessionRepository` via an application-level DI graph (Hilt module, manual locator, or `AppBlockerApplication` factory) rather than reaching into `data/`.
- **[High]** `app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt:7` — Imports `com.appblocker.data.repository.BlockedAppRepositoryImpl`. Depend on the domain interface; let a composition root build the impl.
- **[High]** `app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt:8` — Imports `com.appblocker.data.repository.FocusSessionRepositoryImpl`. Same fix: inject the interface.
- **[High]** `app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt:9` — Imports `com.appblocker.data.repository.UsageRepositoryImpl`. Same fix.
- **[Medium]** `app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt:39-50` — `onCreate()` performs full DI: instantiates `AppDatabase`, three repository impls, and two use cases. Move construction to an `AppContainer`/`ServiceLocator` so the service holds use case references only.

### Repository placement

No violations. All four repository interfaces (`BlockedAppRepository`, `FocusSessionRepository`, `MotivationalMessageRepository`, `UsageRepository`) live under `domain/repository/`. The six interfaces under `data/local/dao/` are Room DAOs, not repositories. No presentation file imports a `*Dao` symbol directly.

### Use case shape

- **[High]** `app/src/main/java/com/appblocker/domain/usecase/IsAppBlockedUseCase.kt:12-16` — Constructor injects three repositories (`BlockedAppRepository`, `UsageRepository`, `FocusSessionRepository`), exceeding the documented cap of two. The class fuses three policies (focus-session override, schedule lookup, daily-limit accounting). Split into smaller use cases — e.g. `IsFocusSessionActiveUseCase`, `IsAppOnActiveScheduleUseCase`, `HasReachedDailyLimitUseCase` — and compose them, or move the orchestration into a domain service that delegates to single-repo use cases.
- **[Low]** `app/src/main/java/com/appblocker/domain/usecase/RecordUsageUseCase.kt:7-23` — Three public methods (`startSession`, `endSession`, `recordUnblock`) sits at the documented ceiling. Methods are cohesive (all about usage telemetry) but the class is on the edge of becoming a `UsageService`. Watch for a fourth method; at that point split by lifecycle (`SessionUseCases`) vs. event (`RecordUnblockUseCase`).

### ViewModel boundaries

No `Context`, `Activity`, or `Service` typed fields/parameters appear in `MainViewModel`, `SchedulesViewModel`, or `BlockOverlayViewModel`. The two `AndroidViewModel` subclasses take `Application`, which is allowed. However, several violations of the spirit of the rule exist via direct data-layer imports and direct repository calls:

- **[High]** `app/src/main/java/com/appblocker/presentation/MainViewModel.kt:6-8` — Imports `com.appblocker.data.local.AppDatabase`, `BlockedAppRepositoryImpl`, `FocusSessionRepositoryImpl`. Presentation should not know about `data/`. Inject the use cases (and the one `FocusSessionRepository` it observes) from an application-level factory.
- **[High]** `app/src/main/java/com/appblocker/presentation/MainViewModel.kt:23-28` — ViewModel constructs `AppDatabase` and two `*RepositoryImpl` instances inline. Move construction to `AppBlockerApplication` (a service locator or Hilt graph) and pass collaborators into a `ViewModelProvider.Factory` like `BlockOverlayActivity` already does.
- **[High]** `app/src/main/java/com/appblocker/presentation/MainViewModel.kt:66` — Calls `focusSessionRepository.observeLatestOpenSession()` directly. Wrap in an `ObserveFocusSessionUseCase` so presentation depends only on the domain layer.
- **[High]** `app/src/main/java/com/appblocker/presentation/MainViewModel.kt:101` — Calls `focusSessionRepository.startSession(durationMs)` directly; should go through a `StartFocusSessionUseCase`.
- **[High]** `app/src/main/java/com/appblocker/presentation/MainViewModel.kt:107` — Calls `focusSessionRepository.endActiveSession()` directly; should go through an `EndFocusSessionUseCase`.
- **[High]** `app/src/main/java/com/appblocker/presentation/SchedulesViewModel.kt:6-7` — Imports `com.appblocker.data.local.AppDatabase` and `BlockedAppRepositoryImpl`. Same fix as `MainViewModel`.
- **[High]** `app/src/main/java/com/appblocker/presentation/SchedulesViewModel.kt:49-53` — ViewModel constructs `AppDatabase` and the repository impl. Move to composition root.
- **[High]** `app/src/main/java/com/appblocker/presentation/SchedulesViewModel.kt:61-62,143-144,155` — Five direct `repository.*` calls (`getAllSchedules`, `getAllBlockedApps`, `addSchedule`, `updateSchedule`, `deleteSchedule`). Introduce use cases (`GetSchedulesUseCase`, `SaveScheduleUseCase`, `DeleteScheduleUseCase`) so the ViewModel depends on domain only.
- **[High]** `app/src/main/java/com/appblocker/presentation/BlockOverlayActivity.kt:14-16` — Activity imports `AppDatabase`, `MotivationalMessageRepositoryImpl`, `UsageRepositoryImpl` and wires them at line 36-42. The factory pattern here is correct; the wrong layer is doing it. Move repository construction into an `AppBlockerApplication` container and expose ready-built use cases.
- **[Medium]** `app/src/main/java/com/appblocker/presentation/MainViewModel.kt:24,28` and `SchedulesViewModel.kt:50` — Field type is inferred to the concrete `BlockedAppRepositoryImpl` / `FocusSessionRepositoryImpl` instead of the domain interface. Even after wiring is fixed, annotate `private val repository: BlockedAppRepository = ...` so the ViewModel can only invoke interface methods.

## Calibration examples

- **Critical** — None found. No privacy regression, data loss, or correctness break in scope.
- **High** — `service/AppBlockerAccessibilityService.kt:7` (service imports `BlockedAppRepositoryImpl`) and `presentation/MainViewModel.kt:101` (ViewModel calls repository directly). Both break the layering rule the CLAUDE.md explicitly states.
- **Medium** — `service/AppBlockerAccessibilityService.kt:39-50` (DI happens inside the service) and `presentation/MainViewModel.kt:24` (impl-type inference instead of interface type). Maintainability/clarity rather than a hard rule break.
- **Low** — `domain/usecase/RecordUsageUseCase.kt:7-23` (three public methods, at the ceiling but cohesive).

## Not detailed

- `MainViewModel.startClockTick` polls `System.currentTimeMillis()` every second in a `while (true)` (`MainViewModel.kt:73-78`) — leak-safe under `viewModelScope` but pure presentation timer logic belongs in a UI-state mapper or a `kotlinx.coroutines.flow.tickerFlow`.
- `BlockOverlayViewModel.kt:67,79,89,101` log via `android.util.Log` referenced through fully qualified name; not an import, so domain purity is unaffected, but inconsistent with the rest of the file.
- `AppBlockerAccessibilityService.kt:12` imports `presentation.BlockOverlayActivity` to launch it. Acceptable for the Android intent boundary, but a `BlockOverlayLauncher` interface in `domain/` (implemented in `presentation/`) would invert the dependency cleanly.
- `BlockedAppRepository` (`domain/repository/BlockedAppRepository.kt:7-21`) bundles "blocked apps" and "schedules" responsibilities. Splitting into `BlockedAppRepository` and `ScheduleRepository` would let `SchedulesViewModel` depend on a narrower interface.
- `data/local/AppDatabase` is referenced as `AppDatabase.getInstance(...)` from presentation and service; a single instance is fine, but accessing it from anywhere outside `data/` is the root cause of the High findings above.
