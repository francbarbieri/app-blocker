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

## Skills (`.claude/skills/`)

Each skill is a directory with `SKILL.md` (lightweight, loaded on invocation) + `reference.md` (detailed, loaded lazily when needed).

| Skill | Invocation | Runs as |
|---|---|---|
| `/ui-orchestrate` | `/ui-orchestrate migrate <Activity>` | Main context (orchestrates others) |
| `/theme-setup` | `/theme-setup check` or auto-spawned | Forked subagent |
| `/screen-gen` | `/screen-gen <Activity>` or auto-spawned | Forked subagent |
| `/build-check` | `/build-check` or auto-spawned | Forked subagent |

The orchestrator spawns theme-setup, screen-gen, and build-check as parallel subagents. Each runs in its own context window to keep the main context clean.
