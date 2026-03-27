# Theme Reference

## Theme file structure
```
presentation/theme/
├── Color.kt   — All color tokens (light + dark)
├── Type.kt    — Typography scale
├── Shape.kt   — Shape tokens (optional, create if needed)
└── Theme.kt   — AppBlockerTheme composable
```

## Check completeness
1. Read all files in `presentation/theme/`
2. Verify: Color.kt has both light + dark tokens
3. Verify: Theme.kt has `AppBlockerTheme` with dynamic color support
4. Verify: Type.kt has complete typography scale
5. Report what exists and what's missing

## Add screen-specific tokens
When a screen needs colors not in the palette:
1. Add values to `Color.kt` with descriptive names
2. Add to color schemes in `Theme.kt` if semantic
3. Comment the purpose

## Update theme
Apply changes while maintaining:
- Both light and dark schemes
- Dynamic color support (`dynamicLightColorScheme`/`dynamicDarkColorScheme`)
- Fallback to static scheme on older devices

## Design principles for this app
- Clean, modern, minimal — M3 baseline
- Dark theme is primary (focus/productivity app)
- Dynamic color on Android 12+ for personalization
- Stay close to M3 defaults — avoid heavy customization
- Purple-toned primary (default M3) unless specified otherwise

## Contrast verification
For key color pairs (primary/onPrimary, surface/onSurface, error/onError):
- 4.5:1 minimum for normal text (WCAG AA)
- 3:1 minimum for large text and UI components

## Rules
- Color names describe role, not value (`Primary` not `Purple`)
- Keep theme close to M3 defaults
- Always test both light and dark schemes
- `AppBlockerTheme` must be the root wrapper in all `setContent {}` calls
