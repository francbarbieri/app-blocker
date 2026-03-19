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
├── data/           # Room database, DAOs, repository implementations
├── domain/         # Entities, use cases, repository interfaces
├── presentation/   # Activities, ViewModels, UI components
└── service/        # AccessibilityService for app detection/blocking
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
