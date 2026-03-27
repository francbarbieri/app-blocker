---
name: ui-orchestrate
description: Orchestrates XML-to-Compose migration and new screen creation. Spawns parallel subagents for theme, screen generation, and build validation. Use when migrating screens, creating new Compose UI, or running the full migration pipeline.
disable-model-invocation: true
user-invocable: true
allowed-tools: Read, Grep, Glob, Bash, Agent, Edit, Write
effort: high
argument-hint: [migrate <Activity> | create <ScreenName> | status]
---

# UI Orchestrator

You coordinate UI development by spawning parallel subagents. You do NOT generate screens yourself.

## On invocation

Parse `$ARGUMENTS`:
- `migrate <ActivityName>` — migrate an existing XML screen to Compose
- `create <ScreenName>` — create a new Compose screen from description
- `status` — report migration progress

## Migration workflow

### Phase 1: Analyze (main context — quick reads only)
1. Read the target Activity, its XML layout, and ViewModel (if any)
2. Check if `presentation/theme/Theme.kt` exists

### Phase 2: Spawn parallel agents

Spawn **two agents in parallel** using the Agent tool:

**Theme agent:**
- Read `.claude/skills/theme-setup/reference.md` first
- Pass its full contents + the task into the agent prompt
- Task: check theme completeness for the target screen, add tokens if needed

**Screen-gen agent:**
- Read `.claude/skills/screen-gen/reference.md` first
- Pass its full contents + the target Activity/XML/ViewModel code into the agent prompt
- Task: generate the Compose screen, update the Activity shell

Both agents WRITE code directly — they don't return plans.

### Phase 3: Build validation (sequential — after Phase 2)

Spawn a **build-check agent:**
- Read `.claude/skills/build-check/reference.md` first
- Pass its full contents into the agent prompt
- Task: run `./gradlew assembleDebug`, fix errors, retry up to 3x

### Phase 4: Cleanup (main context)
1. Delete old XML layout file(s)
2. Report summary to user

## Migration order for this project
1. `BlockOverlayActivity` (simplest)
2. `AppPickerDialog` -> bottom sheet (dialog with list)
3. `MainActivity` (most complex)
4. Final cleanup: remove XML-only deps

## Rules
- ALWAYS spawn agents via the Agent tool — never generate screens in main context
- ALWAYS read reference.md files and include contents in agent prompts
- Agents write code directly
- Keep main context clean: orchestration + cleanup only
