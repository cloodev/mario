# Cartoon Quest — Android platformer starter

A native Android 2D platform game prototype built with Kotlin Canvas.

## Included
- Landscape Android game, touch controls (left, right, jump) and pause/level-map button.
- Animated cartoon hero, scrolling camera, clouds, platforms, coins, patrol/flying enemies, spikes, checkpoints and goal flag.
- 12 themed worlds × 20 levels (240 level slots), procedural level layouts with increasingly varied gaps/platforms.
- Lobby, world selection, level selection, completion screen and saved unlock/coin progress.

## Build an APK
1. Install Android Studio (Hedgehog or newer) on a computer.
2. Open this folder as an existing Gradle project.
3. Allow Gradle sync to finish (JDK 17 recommended).
4. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Install the generated APK from `app/build/outputs/apk/debug/app-debug.apk`.

The project targets Android 15 (API 35), minimum Android 6.0 (API 23).

## Notes
This is an initial playable foundation, not a finished commercial release. Art is drawn with Canvas primitives so it has no external asset/license dependency. Background music and polished sound effects are not bundled yet. The 240 stages are generated from deterministic world/level seeds, so they are distinct procedural layouts rather than 240 hand-authored campaigns.


## Build APK automatically with GitHub

1. Create a new repository on GitHub.
2. Upload **all files and folders inside this project** to the repository root. Keep the `.github/workflows/android.yml` file; GitHub uses it to build the APK.
3. Open the repository's **Actions** tab. If GitHub asks, enable workflows.
4. Select **Build Android APK** in the left panel, then tap **Run workflow** → **Run workflow**.
   - A build also starts automatically when you push changes to the `main` or `master` branch.
5. Open the completed workflow run, scroll to **Artifacts**, and download `CartoonQuest-debug-apk`.
6. Extract the downloaded ZIP. The APK inside is `app-debug.apk`; install it on your Android device.

The workflow uses JDK 17 and Gradle 8.9, then runs `assembleDebug`. It creates a debug APK for testing, not a Play Store release-signed APK.
