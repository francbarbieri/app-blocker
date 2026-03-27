# Orchestrator Reference

## Project paths
- Project root: `/home/barbax/projects/app-blocker`
- Source: `app/src/main/java/com/appblocker/`
- Layouts: `app/src/main/res/layout/`
- Theme: `app/src/main/java/com/appblocker/presentation/theme/`
- Screens: `app/src/main/java/com/appblocker/presentation/screen/`
- Components: `app/src/main/java/com/appblocker/presentation/components/`

## Current screens to migrate
| Screen | Activity | XML | ViewModel | Complexity |
|--------|----------|-----|-----------|------------|
| Main | MainActivity.kt | activity_main.xml, item_blocked_app.xml | MainViewModel.kt | High (list, swipe, FAB) |
| Overlay | BlockOverlayActivity.kt | activity_block_overlay.xml | None | Low |
| App Picker | AppPickerDialog.kt | dialog_app_picker.xml, item_installed_app.xml | None | Medium (dialog + list) |

## Agent spawn template
```
Agent(
  subagent_type="general-purpose",
  description="<short task>",
  prompt="<reference.md contents>\n\n## Your task\n<specific instructions>"
)
```
