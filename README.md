# App Blocker

A privacy-first Android app that puts a mindful pause in front of the apps that
distract you. Everything runs on the phone: no accounts, no network code, no
cloud backup.

## Install it on your phone

You need Android 8.0 or newer.

**Option A: build and install over USB**

1. On the phone, enable Developer options, then turn on USB debugging.
2. Plug the phone in and accept the "Allow USB debugging" prompt.
3. From this folder:

   ```bash
   ./gradlew installDebug
   ```

**Option B: download a build from GitHub**

Every push runs the *Android CI* workflow. Open the run under the repository's
Actions tab, download the `app-blocker-debug-apk` artifact, and install the APK
on the phone (for example with `adb install app-debug.apk`, or by opening the
file on the phone).

Builds from different machines are signed with different debug keys. To switch
between a local build and a CI build, uninstall the app first.

## First run

1. Swipe through the introduction and tap **Start blocking**.
2. On Home, tap **Turn on blocking**, then **Open settings**, find App Blocker
   in the Accessibility list and turn it on.
3. Open the **Apps** tab and add the apps you want to block.

**"Restricted setting" or a greyed-out switch?** Android 13 and newer restrict
accessibility for apps installed from a file. Open *Settings → Apps → App
Blocker*, tap **⋮** in the top corner, choose **Allow restricted settings**,
then turn the service on again. Installing over USB with `adb` or
`./gradlew installDebug` usually avoids this, because Android only restricts
APKs opened from a file manager or browser.

## How blocking works

- An app on the list with **no schedules** is blocked all the time.
- A **time window** blocks it between two times on the chosen days. Windows can
  run overnight (22:00–06:00), and equal start and end times block the whole day.
- A **daily limit** blocks it once today's use reaches the limit, until midnight.
  A limit on several apps is one shared budget: their time adds up.
- A **focus session** blocks every enabled app on the list, whatever the schedules say.
- Schedules can be paused from the Schedules tab without deleting them.

When a blocked app opens, App Blocker shows which attempt today this is and how
much time you've already spent in the app, one line at a time, and says why the
app is blocked. The choices stay locked for a pause that grows with every attempt
(5 s at first, 15 s on the fifth, up to 30 s), and there is no skip.
"This is planned" and "Open anyway" give a 5-minute pass;
after that the check-in comes back, even if you stayed in the app. Going back
counts as walking away, and the **Activity** tab shows how often you did.

## Development

```bash
./gradlew assembleDebug              # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest          # JVM tests, incl. architecture/privacy invariants
./gradlew connectedDebugAndroidTest  # Room DAO + migration tests on a device/emulator
```

Architecture, invariants and conventions are documented in [CLAUDE.md](CLAUDE.md).
