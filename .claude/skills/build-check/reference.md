# Build Check Reference

## Build command
```bash
cd /home/barbax/projects/app-blocker && ./gradlew assembleDebug 2>&1
```
Timeout: 5 minutes. Use `assembleDebug` (faster than full `build`).

## Common Compose import fixes

| Unresolved reference | Import to add |
|---------------------|---------------|
| `remember` | `import androidx.compose.runtime.remember` |
| `mutableStateOf` | `import androidx.compose.runtime.mutableStateOf` |
| `getValue` / `setValue` | `import androidx.compose.runtime.getValue` / `setValue` |
| `LaunchedEffect` | `import androidx.compose.runtime.LaunchedEffect` |
| `collectAsStateWithLifecycle` | `import androidx.lifecycle.compose.collectAsStateWithLifecycle` |
| `Modifier` | `import androidx.compose.ui.Modifier` |
| `dp` / `sp` | `import androidx.compose.ui.unit.dp` / `sp` |
| `painterResource` | `import androidx.compose.ui.res.painterResource` |
| `stringResource` | `import androidx.compose.ui.res.stringResource` |
| `Column` / `Row` / `Box` | `import androidx.compose.foundation.layout.*` |
| `Text` | `import androidx.compose.material3.Text` |
| `Button` | `import androidx.compose.material3.Button` |
| `Scaffold` | `import androidx.compose.material3.Scaffold` |
| `TopAppBar` | `import androidx.compose.material3.TopAppBar` |
| `MaterialTheme` | `import androidx.compose.material3.MaterialTheme` |
| `Icon` | `import androidx.compose.material3.Icon` |
| `FloatingActionButton` | `import androidx.compose.material3.FloatingActionButton` |
| `Card` | `import androidx.compose.material3.Card` |
| `Switch` | `import androidx.compose.material3.Switch` |
| `CircularProgressIndicator` | `import androidx.compose.material3.CircularProgressIndicator` |
| `AlertDialog` | `import androidx.compose.material3.AlertDialog` |
| `SwipeToDismissBox` | `import androidx.compose.material3.SwipeToDismissBox` |
| `ModalBottomSheet` | `import androidx.compose.material3.ModalBottomSheet` |

## Common Compose compiler errors

| Error | Fix |
|-------|-----|
| `@Composable invocations can only happen from the context of a @Composable function` | Add `@Composable` annotation |
| `Composable calls are not allowed inside the calculation parameter of inline fun` | Move composable call outside `remember {}` |
| `None of the following candidates is applicable` | Wrong overload — check parameter types |
| `Type mismatch: inferred type Unit but X was expected` | Missing return in lambda or wrong lambda structure |

## Rules
- Never suppress errors with `@Suppress` — fix root cause
- If error is in code you didn't generate, report but don't modify
- Always run from project root
- Max 3 retry cycles
