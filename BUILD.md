# pause-pd — build recipe (proven on this PC)

Single-module Kotlin Android app, **zero external dependencies** (no AndroidX, no Compose).
minSdk 29, compileSdk/targetSdk 36. Everything below was executed on this machine, not assumed.

## Versions (all already on disk — no downloads needed)

| Thing | Version | Why this one |
|---|---|---|
| Gradle | **9.8.0** | Already unpacked at `~/.gradle/wrapper/dists/gradle-9.8.0-bin/3m7h6ceboy5k31n8kzwzuxssm/` — the wrapper resolves to this exact dist, so no download |
| AGP | **9.4.1** | Stable, cached, and its minimum is Gradle 9.6.0 ≤ 9.8.0 |
| Kotlin | **2.2.10** | Bundled inside AGP 9.4.1 (built-in Kotlin). Cached. No `org.jetbrains.kotlin.android` plugin to apply |
| JDK | **21.0.10** (Temurin) | `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\` |
| build-tools | **36.0.0** | Present; used for `aapt2` verification |
| platform | **android-36** | Present (`platforms/android-36/android.jar`) |
| SDK license | accepted | `sdk/licenses/android-sdk-license` = `24333f8a63b6825ea9c5514f83c2829b004d1fee` |

AGP 9.x ships Kotlin support itself. **Do not apply `id("org.jetbrains.kotlin.android")`** — that is the old
path and conflicts. `android.builtInKotlin` is on by default in 9.4.1 (verified: builds fine with the flag
absent), so it is not set in `gradle.properties`.

Fallback if AGP 9.4.1 ever misbehaves: **AGP 9.0.1** is also cached (same bundled KGP 2.2.10, needs
Gradle ≥ 9.1.0, so 9.8.0 is still fine) — change one line in `build.gradle.kts`.

## Commands

```bash
cd /c/Users/A/Projects/pause-pd
./build.sh                                  # debug APK -> dist/pause-pd-debug.apk
# or, directly:
ANDROID_HOME="C:/Users/A/AppData/Local/Android/Sdk" ./gradlew assembleDebug
```

`build.sh` does three things: resolve the SDK (`ANDROID_HOME`, else the default path, else a clear error),
run `./gradlew assembleDebug`, copy the APK to `dist/pause-pd-debug.apk`.

No `local.properties` is required or committed — `build.sh` exports `ANDROID_HOME`. Android Studio will
create its own `local.properties` on first open, which `.gitignore` covers.

From `cmd.exe` use `gradlew.bat assembleDebug` (the `./gradlew` script needs bash; this machine's shell is git-bash).

## Proof

### Probe — `/c/Users/A/AppData/Local/hermes/cache/scratch/build-probe`

Plain `android.app.Activity` + one `TextView`, no dependencies, same plugin/Gradle/JDK.

```
$ ./gradlew clean assembleDebug --no-daemon        # ANDROID_HOME set, no local.properties
BUILD SUCCESSFUL in 17s
35 actionable tasks: 14 executed, 21 from cache

$ ls -l app/build/outputs/apk/debug/app-debug.apk
-rw-r--r-- 1 A 197121 2525838 Nov 10 16:43 app-debug.apk
```

```
$ aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
package: name='com.example.probe' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
minSdkVersion:'29'
targetSdkVersion:'36'
application: label='Probe' icon=''
application-debuggable
launchable-activity: name='com.example.probe.MainActivity'  label='' icon=''
```

### Real build files — `/c/Users/A/AppData/Local/hermes/cache/scratch/pause-pd-proof`

A scratch clone of **the exact files in this repo** (same `settings.gradle.kts`, `build.gradle.kts`,
`gradle.properties`, `app/build.gradle.kts`, wrapper, `build.sh`) plus a throwaway hello-world source in the
real package `dev.pausepd`. `app/src/**` was not touched in the real project.

```
$ ./build.sh
BUILD SUCCESSFUL in 5s
34 actionable tasks: 25 executed, 9 from cache
Configuration cache entry stored.
APK -> dist/pause-pd-debug.apk

$ ls -l dist/
-rw-r--r-- 1 A 197121 2525810 Nov 10 16:48 pause-pd-debug.apk

$ aapt2 dump badging dist/pause-pd-debug.apk
package: name='dev.pausepd' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
minSdkVersion:'29'
targetSdkVersion:'36'
application: label='Pause' icon=''
application-debuggable
launchable-activity: name='dev.pausepd.MainActivity'  label='' icon=''
```

A cold run of that script (fresh `build/`, `.gradle/`, `dist/`) took 27 s; a repeat run reuses the
configuration cache and finishes in ~1 s. Debug signing was verified with `apksigner verify --print-certs`:
`CN=Android Debug`.

## Hard constraint for whoever writes `app/src/**`

**No AndroidX.** `gradle.properties` does not set `android.useAndroidX=false` (deprecated in AGP 9, and there
are no AndroidX artifacts on the classpath), so anything importing `androidx.*` fails to compile. The sources
already in `app/src/main/java/dev/pausepd/` comply — every import is `android.*`, `java.*` or `kotlin.*`.

- `android.app.Activity` (not `AppCompatActivity` / `ComponentActivity`), `android.app.Service`
- `android.widget.*`, `android.view.*`, `android.service.quicksettings.TileService`
- Themes from the framework: `@android:style/Theme.Material.Light`, `Theme.Material.NoActionBar`, `Theme.DeviceDefault.*`
- No Compose, no ViewBinding/DataBinding setup, no material components

If a dependency later becomes genuinely necessary, the usual `implementation("...")` works — `google()` and
`mavenCentral()` are configured and the network is reachable — but that breaks the zero-dependency rule this
recipe is sized for.

Compile target: Java 17 (`compileOptions`), desugared by D8. minSdk 29 means no multidex concerns.

## Not done yet (owned by the sources agent)

`assembleDebug` on the real project will fail until these exist — the current Kotlin files reference them:

- `app/src/main/AndroidManifest.xml` (activity names are relative to namespace `dev.pausepd`)
- `app/src/main/res/layout/activity_main.xml`, `res/values/strings.xml`, `res/values/colors.xml`
  (the code uses `R.layout.activity_main`, `R.string.*`, `R.color.accent|warn|danger|muted`)

The build files need no change when those land.

## Files

```
settings.gradle.kts                  plugin + dependency repos, rootProject.name = "pause-pd"
build.gradle.kts                     AGP 9.4.1, apply false
gradle.properties                    jvmargs, parallel, build cache, configuration cache
app/build.gradle.kts                 namespace dev.pausepd, minSdk 29, compile/target 36
gradle/wrapper/gradle-wrapper.*      Gradle 9.8.0 (matches the cached dist)
gradlew, gradlew.bat                 generated by Gradle 9.8.0
build.sh                             assembleDebug + copy to dist/pause-pd-debug.apk
.gitignore                           build dirs, .gradle, .kotlin, local.properties, dist, logs
```

`versionCode 1`, `versionName "1.0"`, `applicationId dev.pausepd` — matches the package already used by the
Kotlin sources in `app/src/main/java/dev/pausepd/`.
