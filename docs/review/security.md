# Security & Privacy Review — App Blocker

## 1. Summary

The privacy promise of "all data stays on-device" does not currently hold. The Room database — which contains the user's complete blocking configuration, behavioral history (unblock outcomes, usage sessions, focus sessions), and schedule contents — is auto-backed-up to Google Drive because `android:allowBackup="true"` is set in `app/src/main/AndroidManifest.xml:7` with no `fullBackupContent` or `dataExtractionRules` exclusion rules. This is a Critical leak of behavioral data off-device via the Android Auto Backup pipeline.

Secondary concerns: four `Log.w` call sites in `BlockOverlayViewModel.kt` interpolate raw package names into logcat (readable by adversarial apps on rooted devices and via ADB), the Room database is stored unencrypted on disk, and the `QUERY_ALL_PACKAGES` permission is declared but never used (`queryIntentActivities` is used instead and does not require this permission). No network code, no analytics, no crash-reporting SDKs were found — those promises hold.

Top 3 concerns: (1) backup pipeline exfiltrates the local DB to Google's servers, (2) PII (package names) in logs, (3) unused dangerous-tier permission `QUERY_ALL_PACKAGES` widens attack surface and risks Play Store policy rejection.

## 2. Findings

### Manifest exposure

- **[High]** `app/src/main/AndroidManifest.xml:12-19` — `MainActivity` is `exported="true"` with a `MAIN`/`LAUNCHER` intent filter. The export is required for the launcher icon, so the export itself is justified, but the activity reads `SharedPreferences("app_blocker_prefs", MODE_PRIVATE)` at `MainActivity.kt:16` and conditionally launches `OnboardingActivity` — no `Intent` extras are read, so there is no current injection risk. Status: justified, no action.
- **[Low]** `app/src/main/AndroidManifest.xml:6-10` — `<application>` has no `android:name` (default `Application`), no `android:enableOnBackInvokedCallback`, and uses the legacy `Theme.MaterialComponents.DayNight.DarkActionBar` despite the codebase being Compose/Material 3. Not a security finding, but the theme mismatch can hide focus indicators on certain screens. Suggested fix: align with Material 3 theme.
- **[Info]** `BlockOverlayActivity` (`AndroidManifest.xml:25-29`) and `OnboardingActivity` (`AndroidManifest.xml:21-23`) are correctly `exported="false"`. The `AccessibilityService` (`AndroidManifest.xml:31-41`) is `exported="false"` and gated by `android.permission.BIND_ACCESSIBILITY_SERVICE`, which is the required system pattern. Correct.

### Logs leaking PII

- **[High]** `app/src/main/java/com/appblocker/presentation/BlockOverlayViewModel.kt:67` — `Log.w(TAG, "Failed to load motivational message for $packageName", t)`. Logs the package name of the app the user just tried to open. Readable by any app holding `READ_LOGS` (granted to system/OEM apps and to adversarial apps on rooted devices) and over ADB. Suggested fix: remove the package name from the message or hash it before logging.
- **[High]** `BlockOverlayViewModel.kt:79` — `Log.w(TAG, "Failed to record LEGITIMATE outcome for $packageName", t)`. Same issue; additionally leaks the user's *decision* (LEGITIMATE = unblocked-with-justification). Suggested fix: log only an error code, not behavior.
- **[High]** `BlockOverlayViewModel.kt:90` — `Log.w(TAG, "Failed to record BACKED_OFF outcome for $packageName", t)`. Leaks behavior (user backed off). Suggested fix: same as above.
- **[High]** `BlockOverlayViewModel.kt:101` — `Log.w(TAG, "Failed to record BROKE_PLAN_PROCEEDED outcome for $packageName", t)`. Most sensitive of the four — leaks that the user broke their own plan, for a specific app. Suggested fix: drop the message contents; keep only the throwable.
- No `Log.d` / `Log.v` / `println` / `print(` calls were found anywhere under `app/src/main/java/`.

### Backup

- **[Critical]** `app/src/main/AndroidManifest.xml:7` — `android:allowBackup="true"` with no `android:fullBackupContent` and no `android:dataExtractionRules` attribute. The Room file `app_blocker.db` (`AppDatabase.kt:132`) lives in the default databases directory, which is included in Android Auto Backup by default. The DB contains `blocked_apps`, `schedules`, `schedule_apps`, `usage_sessions`, `unblock_events`, `focus_sessions`, and `motivational_messages` — i.e. the user's full behavioral history. On any device with Backup enabled (default on most Android setups), this data is uploaded to Google Drive under the user's Google account, breaking the "all data stays on-device" promise. Suggested fix: either set `android:allowBackup="false"`, or add an `android:fullBackupContent="@xml/backup_rules"` plus `android:dataExtractionRules="@xml/data_extraction_rules"` that excludes the `database` directory and `app_blocker_prefs` SharedPreferences.

### Network

- No findings. Grep for `okhttp`, `retrofit`, `httpurl`, `java.net.URL`, `http://`, `https://`, `WebView`, `loadUrl`, `ktor`, `volley`, `ConnectivityManager` returned zero source matches under `app/src/main/java/`. The only `http://` hits were the XML namespace URI `http://schemas.android.com/apk/res/android` in `AndroidManifest.xml:2` and `res/xml/accessibility_service_config.xml:3` — these are XML schema identifiers, not network calls. Build files (`app/build.gradle.kts`, root `build.gradle.kts`) declare no networking libraries; only AndroidX, Compose BOM, Room, and coroutines.

### Permissions

- **[Medium]** `app/src/main/AndroidManifest.xml:4` — `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`. The only app-listing code is `MainScreen.kt:179`, which calls `pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)` with `ACTION_MAIN` + `CATEGORY_LAUNCHER`. That call does not require `QUERY_ALL_PACKAGES` on API 30+ — visibility to launcher apps is allowed by default. The permission is unused and triggers Play Console's "Sensitive App permissions" policy review (target SDK 35 is well past the policy gate of API 30). Suggested fix: remove `QUERY_ALL_PACKAGES` entirely; if a future feature legitimately needs to see non-launcher apps, add a `<queries>` element instead.

### AccessibilityService description

- **[Low]** `app/src/main/res/values/strings.xml:13` — `"App Blocker needs this permission to detect when blocked apps are opened and show a blocking screen."` is honest and accurate, matching the actual behavior in `AppBlockerAccessibilityService.kt:53-76`. The service uses only `TYPE_WINDOW_STATE_CHANGED` with `canRetrieveWindowContent="false"` (`accessibility_service_config.xml:4,7`), which matches the description's narrow scope. Suggested fix: optionally clarify that no window content is read — strengthens Play Store policy compliance.
- **[Low]** `accessibility_service_config.xml` has no `android:settingsActivity` and no `android:summary` string, so the system Accessibility settings entry shows only the description. Not a security issue, but a UX/transparency improvement.
- No localized `values-*/strings.xml` exists, so there are no divergent translations to audit.

### DB encryption

- **[Medium]** `app/src/main/java/com/appblocker/data/local/AppDatabase.kt:129-136` — Room DB is built with `Room.databaseBuilder(...).addMigrations(...).build()`, no `openHelperFactory(SupportFactory(...))`, no SQLCipher. The DB file `app_blocker.db` is stored in plaintext in app-private storage. For a privacy-first app whose DB contains the user's behavioral history (which apps they tried to open, when they backed off, when they broke their plan), this is a conscious decision worth documenting. Threat model: a rooted device, a physical-access adversary running `adb backup` (if backup is enabled — see Critical above), or a malicious recovery image can read the DB. Suggested fix: wire SQLCipher via `androidx.sqlite:sqlite-ktx` + `net.zetetic:android-database-sqlcipher`, with a passphrase stored in Android Keystore-wrapped EncryptedSharedPreferences. If the team decides plaintext is acceptable, document the threat model in `README.md`.

### Crash / analytics reporting

- No findings. Grep for `firebase`, `crashlytics`, `sentry`, `bugsnag`, `analytics`, `mixpanel`, `amplitude` returned zero matches in source and build files. No `google-services.json` is referenced; no `com.google.gms` or `com.google.firebase.crashlytics` plugins are applied in `app/build.gradle.kts` or `build.gradle.kts`. The promise holds.

## 3. Calibration examples

- **Critical** — `AndroidManifest.xml:7` (Auto Backup exfiltrates the DB to Google Drive). One example used.
- **High** — `BlockOverlayViewModel.kt:67,79,90,101` (PII in logs); `AndroidManifest.xml:12-19` (`MainActivity` exported, but justified by launcher requirement — flagged for the reader's awareness only).
- **Medium** — `AndroidManifest.xml:4` (unused `QUERY_ALL_PACKAGES`); `AppDatabase.kt:129-136` (plaintext DB).
- **Low** — `strings.xml:13` (AS description is accurate but could mention "no content reading"); `AndroidManifest.xml:6-10` (legacy theme attribute).

## 4. Inventory

### Exported components

| Component | Type | exported | Justification |
|---|---|---|---|
| `com.appblocker.presentation.MainActivity` | activity | `true` | Required: `MAIN`/`LAUNCHER` intent filter for launcher icon. No extras read. OK. |
| `com.appblocker.presentation.OnboardingActivity` | activity | `false` | Internal navigation only. OK. |
| `com.appblocker.presentation.BlockOverlayActivity` | activity | `false` | Launched only by the internal AccessibilityService with explicit `Intent`. OK. |
| `com.appblocker.service.AppBlockerAccessibilityService` | service | `false` | System-bound via `BIND_ACCESSIBILITY_SERVICE`. Correct. |

No `<receiver>` or `<provider>` is declared in `AndroidManifest.xml`. No grep matches for `BroadcastReceiver` subclasses in source.

### Permissions

| Permission | Used? | Where |
|---|---|---|
| `android.permission.QUERY_ALL_PACKAGES` (`AndroidManifest.xml:4`) | **No** | App-listing code at `MainScreen.kt:179` uses `queryIntentActivities` with a `MAIN`/`LAUNCHER` filter, which does not require this permission. Remove. |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` (`AndroidManifest.xml:34`) | Yes | Declared as a `<service>` permission attribute (required by Android for accessibility services). Correct. |

No runtime-dangerous permissions are declared, so no `ActivityResultContracts.RequestPermission` flow is needed. Accessibility consent is handled via system-settings navigation in `MainScreen.kt:161-169` (`checkAccessibilityService`), which is the correct pattern.

## 5. Not detailed (long tail)

- `MainActivity.kt:16` reads `SharedPreferences` named `"app_blocker_prefs"` with `MODE_PRIVATE`. Also subject to Auto Backup; same Critical fix as the DB.
- `AppBlockerAccessibilityService.kt:29-35` hardcodes `ignoredPackages` — fine for now but brittle across OEMs (e.g. Samsung/MIUI launchers); consider deriving from `PackageManager`.
- `AppBlockerAccessibilityService.kt:123` uses a process-wide `ConcurrentHashMap` (`graceUntilMs`) for grace periods. Survives service restarts only while the process lives; not a security finding, but worth noting that grace state is not persisted (also avoids growing the DB footprint — actually a privacy plus).
- `BlockOverlayActivity.kt:54` reads `EXTRA_PACKAGE_NAME` without validation. Since the activity is `exported="false"` and the only caller is the in-process service, this is safe today; if export ever changes, validate that the package exists.
- `app/build.gradle.kts:21-28` — release build has `isMinifyEnabled = false`. Not a security finding per se, but enabling R8 + ProGuard reduces the attack surface from reverse-engineering and would strip the leaky log strings via `proguard-rules.pro`.
- Schema JSONs under `app/schemas/` (referenced at `app/build.gradle.kts:45`) are checked in; they contain table/column names only, no user data. OK.
- No `network_security_config.xml` exists. Not required given the no-network finding, but adding one with `cleartextTrafficPermitted="false"` would harden against future regressions.
