# Smart Unblock Flow — Design

**Date:** 2026-05-13
**Status:** Approved (pending spec review)
**Branch target:** new branch off `develop`

## Goal

Replace the current single-screen block overlay with a two-step "self-honesty" flow:

1. **Confirmation prompt:** "Are you disabling this before your planned time?"
   - **YES** → motivational message → Go Back or Proceed Anyway
   - **NO** → frictionless access (treated as legitimate)
2. After granting access (via NO or YES → Proceed), suppress re-blocking for **5 minutes**.
3. Record every outcome with full fidelity for future analytics.

This is the SPEC.md "Smart Unblock Flow" feature, finally implemented.

## Decisions made during brainstorming

| Decision | Choice |
|---|---|
| Flow intent | Self-honesty check (NO = legitimate, YES = breaking plan) |
| Post-message UX | Two buttons: Go Back (primary) / Proceed Anyway (text button) |
| Grace period | 5 minutes, in-memory only (lost on service restart) |
| Motivational message source | Existing fallback string; no message-management UI in this scope |
| Tracking | New enum column on `unblock_events` (`LEGITIMATE`, `BROKE_PLAN_PROCEEDED`, `BACKED_OFF`) |
| Back-from-Confirmation | Untracked (different signal from `BACKED_OFF`) |
| Back-from-Motivational | Returns to Confirmation (not home) |
| Implementation pattern | Single Composable host + sealed `BlockOverlayUiState` + ViewModel |
| Grace mechanism | Static `ConcurrentHashMap` on `AppBlockerAccessibilityService` companion object |

## Architecture

No layer boundary changes. All existing Clean Architecture rules hold (domain has no Android deps, data depends on domain, service depends on domain).

### Files changed

**Domain layer**
- `domain/model/UnblockOutcome.kt` *(new)* — `enum class UnblockOutcome { LEGITIMATE, BROKE_PLAN_PROCEEDED, BACKED_OFF }`
- `domain/model/UnblockEvent.kt` — replace `userProceeded: Boolean` with `outcome: UnblockOutcome`
- `domain/usecase/RecordUsageUseCase.kt` — `recordUnblock(packageName, outcome: UnblockOutcome)`

**Data layer**
- `data/local/entity/UnblockEventEntity.kt` — replace `user_proceeded` column with `outcome` (TEXT, stores enum `name`)
- `data/local/dao/UnblockEventDao.kt` — query/insert reflects new column
- `data/local/AppDatabase.kt` — bump version `1 → 2`, add `Migration(1, 2)` that drops + recreates `unblock_events` (acceptable since there is no production data; document this in the migration)
- `data/repository/UsageRepositoryImpl.kt` — adapt to entity change

**Presentation layer**
- `presentation/screen/BlockOverlayUiState.kt` *(new)* — sealed interface with `Confirmation(appName)` and `Motivational(appName, message)` variants
- `presentation/screen/BlockOverlayScreen.kt` — rewritten to render either variant
  - `ConfirmationContent` — title "Are you disabling this before your planned time?", subtitle = app name, buttons YES / NO
  - `MotivationalContent` — message text, buttons Go Back (primary) / Proceed Anyway (text)
- `presentation/BlockOverlayViewModel.kt` *(new)* — holds `StateFlow<BlockOverlayUiState>`, exposes `SharedFlow<BlockOverlayEvent>`, owns use cases. Events: `GrantGraceAndClose`, `Close`, `GoHome`. **VM does not reference `AppBlockerAccessibilityService`**; the Activity is responsible for calling `grantGrace` on receipt of `GrantGraceAndClose` (keeps VM unit-testable without Android deps).
- `presentation/BlockOverlayActivity.kt` — slimmed: build VM via manual factory (matching the existing pattern from today's `BlockOverlayActivity`), collect state + events. On `GrantGraceAndClose` → call `AppBlockerAccessibilityService.grantGrace(...)` then `finish()`. On `Close` → `finish()`. On `GoHome` → home intent + `finish()`.

**Service layer**
- `service/AppBlockerAccessibilityService.kt` — adds:
  - `companion object { const val GRACE_PERIOD_MS = 5 * 60_000L; private val graceUntilMs = ConcurrentHashMap<String, Long>(); fun grantGrace(pkg: String) { graceUntilMs[pkg] = System.currentTimeMillis() + GRACE_PERIOD_MS } }`
  - `onAccessibilityEvent` checks `graceUntilMs[pkg]` before invoking `isAppBlocked`; expired entries are removed lazily on read.

## Data flow

### Path A: app launch → overlay
1. User opens blocked app.
2. `AppBlockerAccessibilityService.onAccessibilityEvent` fires on `TYPE_WINDOW_STATE_CHANGED`.
3. Service checks `graceUntilMs[pkg]` — if `System.currentTimeMillis() < expiry`, return (no overlay).
4. Otherwise `IsAppBlockedUseCase(pkg)` → if true, launch `BlockOverlayActivity`.
5. Activity builds VM, VM emits `Confirmation(appName)`.

### Path B: NO → legitimate access
1. Composable invokes `vm.onLegitimate()`.
2. VM coroutine: `recordUnblock(pkg, LEGITIMATE)`.
3. VM emits `GrantGraceAndClose`.
4. Activity calls `AppBlockerAccessibilityService.grantGrace(pkg)` then `finish()`. User lands back in blocked app; service grace check prevents re-block.

### Path C: YES → motivational
1. Composable invokes `vm.onBreakingPlan()`.
2. VM coroutine: `message = getMotivationalMessage(pkg)?.message ?: "You've got this! Stay focused."`
3. State becomes `Motivational(appName, message)`.

### Path D: Motivational → Go Back
1. `vm.onGoBack()` → `recordUnblock(pkg, BACKED_OFF)` → emit `GoHome`. No grace.
2. Activity launches `Intent.ACTION_MAIN / CATEGORY_HOME` + `finish()`.

### Path E: Motivational → Proceed Anyway
1. `vm.onProceed()` → `recordUnblock(pkg, BROKE_PLAN_PROCEEDED)` → emit `GrantGraceAndClose`.
2. Activity calls `grantGrace(pkg)` then `finish()`; user lands back in blocked app, grace check suppresses re-block.

### System back button
- On `Confirmation`: treated as "exit the flow" → goes home, **untracked** (different signal from BACKED_OFF).
- On `Motivational`: returns to `Confirmation` (user can re-decide). No event recorded for the transition.

## Error handling

| Failure mode | Behavior |
|---|---|
| `getMotivationalMessage` returns null or throws | Use fallback string `"You've got this! Stay focused."` |
| `recordUnblock` throws | Fire-and-forget in VM coroutine; log via `Log.w`, swallow. UX must not block on DB writes. |
| AccessibilityService killed mid-flow | Activity continues working independently. Grace map is lost on service restart; user re-blocks on next event. Acceptable. |
| Activity recreated (rotation / config change) | VM survives via `ViewModelProvider`; state machine resumes. |
| Missing `EXTRA_PACKAGE_NAME` | Existing early `finish()` retained. |

## Testing

### Unit tests (JVM — `app/src/test/`)
- `BlockOverlayViewModelTest` using fake repositories:
  - Initial state is `Confirmation`
  - `onBreakingPlan()` → loads message → state becomes `Motivational(message)`
  - `onBreakingPlan()` with null repo result → state uses fallback string
  - `onLegitimate()` → records `LEGITIMATE` + emits `GrantGraceAndClose`
  - `onGoBack()` from Motivational → records `BACKED_OFF` + emits `GoHome`
  - `onProceed()` from Motivational → records `BROKE_PLAN_PROCEEDED` + emits `GrantGraceAndClose`
- `UnblockOutcomeMappingTest` — round-trip enum through `UnblockEventEntity`.

### Instrumented tests (`app/src/androidTest/`)
- `UnblockEventDaoMigrationTest` — verify `Migration(1, 2)` runs without throwing on a v1 database. Use Room's `MigrationTestHelper`.

### Manual smoke test (pre-merge)
- [ ] Open a blocked app → Confirmation appears
- [ ] NO → app opens; wait 5 min; reopen → Confirmation appears again
- [ ] YES → motivational message renders with fallback when no per-app messages exist
- [ ] Motivational → Go Back → home
- [ ] Motivational → Proceed Anyway → app opens; reopen within 5 min → no re-block
- [ ] System back from Confirmation → home (no event recorded)
- [ ] System back from Motivational → returns to Confirmation
- [ ] Rotate device on Confirmation and on Motivational → state preserved

### Not tested
- Composable rendering (Previews cover visual verification)
- `AppBlockerAccessibilityService` itself (already untested; out of scope for this feature)

## Out of scope

- Motivational message management UI (separate future feature)
- Persisting grace period across service restarts
- Schedules screen redesign
- Analytics/usage views surfacing the new `outcome` data
