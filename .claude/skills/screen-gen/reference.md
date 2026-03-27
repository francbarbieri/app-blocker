# Screen Generator Reference

## XML to Compose mapping

| XML | Compose |
|-----|---------|
| `LinearLayout(vertical)` | `Column` |
| `LinearLayout(horizontal)` | `Row` |
| `ConstraintLayout` | `Column`/`Row` or `Box` |
| `CoordinatorLayout + AppBarLayout` | `Scaffold + TopAppBar` |
| `RecyclerView` | `LazyColumn` / `LazyRow` |
| `TextView` | `Text` |
| `ImageView` | `Image` or `Icon` |
| `MaterialButton` | `Button` / `OutlinedButton` / `TextButton` |
| `MaterialCardView` | `Card` / `ElevatedCard` |
| `SwitchMaterial` | `Switch` |
| `FloatingActionButton` | `FloatingActionButton` |
| `ProgressBar` | `CircularProgressIndicator` |
| `AlertDialog` | `AlertDialog` composable |
| `DialogFragment` | `AlertDialog` or `ModalBottomSheet` |
| `FrameLayout` | `Box` |

## State and event extraction
- Intent extras -> screen function parameters
- ViewModel observation (`collect`, `observe`) -> `collectAsStateWithLifecycle()`
- Click listeners -> lambda parameters or ViewModel method calls
- Navigation actions -> callback lambdas

## Screen composable template

```kotlin
@Composable
fun <ScreenName>(
    // State parameters (from ViewModel or arguments)
    // Event callbacks
    modifier: Modifier = Modifier,
) {
    // UI tree using MaterialTheme tokens
}

@Preview(showBackground = true)
@Composable
private fun <ScreenName>Preview() {
    AppBlockerTheme {
        <ScreenName>(/* sample data */)
    }
}
```

## Activity thin shell template

```kotlin
class <ActivityName> : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Parse intent extras here
        setContent {
            AppBlockerTheme {
                <ScreenName>(/* pass state and callbacks */)
            }
        }
    }
}
```

## Compose best practices

### State management
- Screen composable has TWO layers:
  - **Stateful wrapper**: calls ViewModel, collects state, passes down
  - **Stateless content**: receives data + lambdas, renders UI
- Never pass ViewModel to child composables — only data and callbacks
- Use `collectAsStateWithLifecycle()` from `lifecycle-runtime-compose`

### Performance
- Use `remember` for expensive calculations inside composition
- Use `derivedStateOf` when state is derived from other state
- Use `key` parameter in `LazyColumn` `items()` for stable identity
- Mark data classes with collections as `@Immutable` if they don't change
- Never create new object instances (List, Modifier) inside composition without `remember`

### Side effects
- `LaunchedEffect(key)` for one-off side effects (showing Snackbar, loading data)
- `DisposableEffect` for cleanup-requiring effects
- Never update state directly in composition — use callbacks or side effects

## Component patterns

### Lists (replacing RecyclerView + Adapter)
```kotlin
LazyColumn {
    items(items = list, key = { it.id }) { item ->
        ItemComposable(item)
    }
}
```

### Swipe to dismiss (replacing ItemTouchHelper)
```kotlin
val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            onDelete(item)
            true
        } else false
    }
)
SwipeToDismissBox(
    state = dismissState,
    backgroundContent = { /* red delete background */ },
    content = { ItemComposable(item) },
)
```

### Bottom sheet (replacing DialogFragment)
```kotlin
var showSheet by remember { mutableStateOf(false) }
if (showSheet) {
    ModalBottomSheet(onDismissRequest = { showSheet = false }) {
        // Content with LazyColumn for list
    }
}
```

## Accessibility
- Minimum touch target: 48dp for interactive elements
- `contentDescription` on all icons and images
- Use `semantics { }` for custom accessibility descriptions
- Test with TalkBack in mind
