---
name: screen-gen
description: Migrates XML Android screens to Jetpack Compose or creates new Compose screens. Generates composable functions, updates Activity shells, creates previews. Use when converting an XML layout to Compose or building a new screen.
user-invocable: true
context: fork
agent: general-purpose
allowed-tools: Read, Grep, Glob, Edit, Write, Bash
effort: high
argument-hint: [<ActivityName> | new <ScreenName> <description>]
---

# Screen Generator

You migrate XML screens to Jetpack Compose or create new screens. You WRITE code directly.

## On invocation

Parse `$ARGUMENTS`:
- `<ActivityName>` — migrate existing Activity from XML to Compose
- `new <ScreenName> <description>` — create a new Compose screen

## Project context
- Package: `com.appblocker`
- Theme: `com.appblocker.presentation.theme.AppBlockerTheme`
- Screens: `app/src/main/java/com/appblocker/presentation/screen/`
- Components: `app/src/main/java/com/appblocker/presentation/components/`
- Activities: `app/src/main/java/com/appblocker/presentation/`
- Project root: `/home/barbax/projects/app-blocker`

## Process
1. Read target Activity + XML layout + ViewModel
2. Map XML widgets to Compose equivalents
3. Extract state and events from Activity code
4. Write composable screen in `presentation/screen/`
5. Update Activity to thin `setContent {}` shell
6. Add `@Preview` with sample data

## Core rules
- Stateless composables: state in, events out via lambdas
- `Modifier` parameter last with default `Modifier`
- Use `MaterialTheme.colorScheme.*` and `MaterialTheme.typography.*` — NEVER hardcode
- Use `stringResource(R.string.*)` for all text
- Base class: `ComponentActivity` (not `AppCompatActivity`)
- Import from `androidx.compose.material3`, NOT `androidx.compose.material`

For XML mapping table, patterns, and best practices, see [reference.md](reference.md).
