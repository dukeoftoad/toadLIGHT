# toadLIGHT 🌇

[![toadLIGHT CI](https://github.com/dukeoftoad/toadLIGHT/actions/workflows/ci.yml/badge.svg)](https://github.com/dukeoftoad/toadLIGHT/actions/workflows/ci.yml)

**Gentle light for late nights** – a modern, open, fully-free blue-light filter for Android, inspired by the classic *Twilight* app but rebuilt from scratch with an original Material 3 design, Kotlin and Jetpack Compose.

After sunset, toadLIGHT warms your screen with a translucent amber veil and can dim it a little, reducing blue light so your eyes can rest. At sunrise everything fades back to normal – gradually, like a real dawn.

## Features

- **Sun-synced filter** – switches on at your local sunset and off at sunrise (computed on-device with the classic NOAA/USNO sunrise equation; no network needed). Optional civil-twilight times and ±120 min sunrise/sunset offsets.
- **Custom or always-on schedules** – fixed time windows that wrap midnight, or 24/7 filtering.
- **Full control** – color temperature (1000K–6500K), warmth intensity and extra screen dimming, all with a live preview. Changes apply to the real overlay as you drag.
- **Profiles** – Evening, Bed reading, and anything you create. Each profile keeps its own filter values *and* its own schedule.
- **Per-app behavior** – turn the filter off inside chosen apps (games, galleries) or give an app its own settings. Powered by a privacy-first accessibility service that reads only the foreground package name – never screen content.
- **Material You** – dynamic colors from your wallpaper, dark/AMOLED/light themes.
- **Play/pause hero button** – one big button that always does the right thing: pause a running filter, resume a paused one, or turn the warmth on now.
- **Quick controls everywhere** – a draggable floating bubble menu over the screen (play/pause, warmer, cooler, dim, stop), a quick-settings tile (with an in-app "Add" button), notification actions and quick pause chips (1 min / 30 min / 1 hour).
- **Gentle fades** – configurable transition duration (0–120 min) so sunset and sunrise fade in and out smoothly; interrupted fades continue seamlessly.
- **Starts on boot**, survives app updates, reacts to clock/time-zone changes.
- **On now** – need the warmth before sunset? Turn the filter on immediately for two hours; the schedule takes over automatically afterwards. Dragging any slider during the day starts a short live preview on the real screen.
- **No paywall, no ads, no network** – everything is free and all data stays on the device.

## Building

### Android Studio
Open the project folder and press **Run**. Studio will install the right SDK automatically (compileSdk 36, minSdk 34, Android 14+).

### Command line
```bash
./gradlew assembleRelease        # -> app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest    # runs the JVM unit tests
```

The repository does not ship signing secrets (they are git-ignored). For a local release build, create `keystore.properties` at the repo root and a keystore:

```bash
keytool -genkeypair -keystore keystore/toadlight-release.jks -alias toadlight \
  -keyalg RSA -keysize 2048 -validity 10950
```

```properties
storeFile=keystore/toadlight-release.jks
storePassword=<your store password>
keyAlias=toadlight
keyPassword=<your key password>
```

Without it, `assembleDebug` still works out of the box. CI signs releases from the `TOADLIGHT_KEYSTORE_BASE64` / `TOADLIGHT_STORE_PASSWORD` / `TOADLIGHT_KEY_PASSWORD` repo secrets (tag a `v*` release to trigger it).

Toolchain used for development:

| Component | Version |
| --- | --- |
| AGP | 8.10.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.0.20 |
| Compose BOM | 2024.09.03 (Material 3 1.3.0) |
| compileSdk / targetSdk | 36 (Android 16) |
| minSdk | 34 (Android 14) |
| JDK | 21 |

## Architecture

```
com.toadlight.filter
├── ToadlightApp / MainActivity      # app entry, single-activity Compose UI
├── core/
│   ├── model/Models.kt              # Profile, Schedule, AppOverride, AppSettings (kotlinx.serialization)
│   ├── color/ColorTemperature.kt    # Kelvin -> RGB (black-body approximation)
│   ├── solar/SolarCalculator.kt     # sunrise equation, pure Kotlin, offline
│   ├── schedule/ScheduleEvaluator.kt# pure window logic (SUN / CUSTOM / ALWAYS + offsets)
│   └── util/LocationGrabber.kt      # one-shot LocationManager fix (no Play Services)
├── data/AppRepository.kt            # Preferences DataStore + JSON persistence
├── filter/
│   ├── FilterEngine.kt              # state machine: inputs -> tint/dim + fades
│   ├── FilterService.kt             # foreground service, overlay window, notification
│   ├── FilterOverlayView.kt         # the actual full-screen tint view
│   ├── FilterTileService.kt         # quick-settings tile
│   ├── ForegroundAppWatcher.kt      # accessibility watcher (package names only)
│   └── BootReceiver.kt              # boot / update / clock-change recovery
└── ui/                              # Compose screens: onboarding, home, profiles, apps, settings
```

**How the filter works:** a touch-transparent `TYPE_APPLICATION_OVERLAY` window paints two layers – a warm color tint whose alpha is the intensity, and a black dimming layer. A foreground service keeps it alive, wakes exactly at the next schedule boundary (no exact-alarm permission needed), and animates fades on a 1-second tick.

## Permissions & why

| Permission | Why |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | draw the tint over other apps (the whole point) |
| `FOREGROUND_SERVICE` + `SPECIAL_USE` | keep the filter alive reliably |
| `RECEIVE_BOOT_COMPLETED` | resume after reboot |
| `POST_NOTIFICATIONS` | pause/resume/stop controls in the notification |
| `ACCESS_COARSE/FINE_LOCATION` | one-time sunset/sunrise calculation; manual coordinates work without it |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | offers the user a one-tap exemption dialog |

## Testing

`app/src/test/` contains JVM unit tests for the solar calculator (validated against timeanddate.com values for New York and Delhi), the color-temperature mapping and the schedule evaluator (window logic, midnight wrap, offsets, polar edge cases).

## Roadmap ideas

- Bedtime reminder & wind-down tint
- Smart bulb integration (HUE / Matter)
- Home-screen widget
- Locale translations

## License

MIT. Concept inspired by Urbandroid's *Twilight*; all code, artwork and design in this repository are original. Sun-position math is the standard public-domain NOAA/USNO algorithm.
