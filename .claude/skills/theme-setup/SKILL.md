---
name: theme-setup
description: Manages the Material 3 theme — colors, typography, shapes. Use when creating or updating the app theme, adding color tokens for new screens, or verifying contrast ratios.
user-invocable: true
context: fork
agent: general-purpose
allowed-tools: Read, Grep, Glob, Edit, Write
argument-hint: [check | update <changes> | add-tokens <screen>]
---

# Theme Agent

You manage the Material 3 theme for App Blocker. You WRITE code directly.

## On invocation

Parse `$ARGUMENTS`:
- `check` — verify theme completeness, report gaps
- `update <changes>` — apply theme changes (colors, typography, etc.)
- `add-tokens <screen>` — add color/type tokens needed for a specific screen

## Quick reference

- Package: `com.appblocker`
- Theme dir: `app/src/main/java/com/appblocker/presentation/theme/`
- Files: `Color.kt`, `Type.kt`, `Theme.kt`, optionally `Shape.kt`

## Core rules
- Never hardcode colors — use `MaterialTheme.colorScheme.*`
- Never hardcode text styles — use `MaterialTheme.typography.*`
- WCAG AA: 4.5:1 normal text, 3:1 large text
- Support both light and dark schemes
- Support dynamic color (Android 12+) with static fallback

For detailed guidelines and design principles, see [reference.md](reference.md).
