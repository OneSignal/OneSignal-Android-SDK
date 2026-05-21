# OneSignal Android Sample App - Build Guide

This document extends the shared build guide with Android-specific details.

**Read the shared guide first:**
https://raw.githubusercontent.com/OneSignal/sdk-shared/refs/heads/main/demo/build.md

Replace `{{PLATFORM}}` with `Android` everywhere in that guide. Everything below either overrides or supplements sections from the shared guide.

---

## Project Setup

The Android demo lives at `examples/demo/` and is a Gradle Kotlin DSL project. There is no `setup.sh` / `cap sync` step — `./gradlew` builds the SDK from local source (or downloads it from Maven) and installs the demo in one command.

The project can be opened two ways depending on the goal:

- **`examples/demo/`** — standalone project. SDK is pulled from Maven and the version is overridable via `-PSDK_VERSION=X.Y.Z` (defaults to `5.6.1`).
- **`OneSignalSDK/`** — wraps `examples/demo/` via `settings.gradle` and substitutes the published OneSignal dependency with the local source modules. `-PSDK_VERSION=X.Y.Z` is required here.

### `local.properties` overrides (Android's `.env`)

The demo uses **`examples/demo/local.properties`** as its Capacitor-style override file (gitignored by default). Copy `local.properties.example` to `local.properties` and fill in the values you want:

```properties
ONESIGNAL_APP_ID=
ONESIGNAL_ANDROID_CHANNEL_ID=
```

Each key resolves in this order at build time: `-PKEY=value` from the CLI → `local.properties` → a sensible built-in default. The values are surfaced through `BuildConfig.ONESIGNAL_APP_ID` and `BuildConfig.ONESIGNAL_ANDROID_CHANNEL_ID` (see `app/build.gradle.kts`'s `demoOverride(...)` helper).

| Key | Used by | Capacitor counterpart |
|-----|---------|-----------------------|
| `ONESIGNAL_APP_ID` | `MainApplication.onCreate` + `MainViewModel.loadInitialState` | `VITE_ONESIGNAL_APP_ID` |
| `ONESIGNAL_ANDROID_CHANNEL_ID` | `OneSignalService.sendNotification` (WITH SOUND payload) | `VITE_ONESIGNAL_ANDROID_CHANNEL_ID` |

> Changing `ONESIGNAL_APP_ID` no longer needs an uninstall — the value is read straight from `BuildConfig` on every launch, no SharedPreferences cache.

### Product flavors

Two flavors are declared on the `default` dimension:

| Flavor | Use case |
|--------|----------|
| `gms`  | Google Play Services / FCM — applies the `com.google.gms.google-services` plugin and pulls `play-services-location`. |
| `huawei` | Huawei HMS — applies `com.huawei.agconnect`, excludes the GMS transitive deps from the OneSignal artifact, and pulls `com.huawei.hms:push` + `com.huawei.hms:location`. |

The plugin (`com.google.gms.google-services` vs `com.huawei.agconnect`) is selected at configuration time by inspecting `gradle.startParameter.taskRequests` so a clean `./gradlew tasks` doesn't require both Google and Huawei configuration files.

### `build.gradle.kts` essentials

```kotlin
android {
    namespace = "com.onesignal.example"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        multiDexEnabled = true

        // demoOverride() walks -P, then local.properties, then the built-in default.
        val onesignalAppId = demoOverride("ONESIGNAL_APP_ID")
            ?: "77e32082-ea27-42e3-a898-c72e141824ef"
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"$onesignalAppId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    productFlavors {
        create("gms")    { dimension = "default"; applicationId = "com.onesignal.example" }
        create("huawei") { dimension = "default"; applicationId = "com.onesignal.example" }
    }
}
```

### Build & run scripts

```bash
# Standalone, default SDK version
./gradlew :app:installGmsDebug

# Override SDK version (required when opened from OneSignalSDK/)
./gradlew :app:installGmsDebug -PSDK_VERSION=5.9.2

# Huawei flavor
./gradlew :app:installHuaweiDebug
```

### Compose stack

The demo is 100% Jetpack Compose (no XML layouts). Dependencies are pulled via the Compose BOM (`2024.02.00`) with `material3`, `material-icons-extended` (used for the Send-IAM icons that the shared guide references), `runtime-livedata` (so `LiveData` integrates with Compose's `observeAsState`), and `activity-compose` + `lifecycle-*-compose` for the Compose entry point.

---

## State Management

The Android demo **overrides the shared guide's "no repository wrapper" rule**. Instead it uses:

- `MainApplication.kt` — initializes the SDK before any UI renders, restores cached consent + IAM-paused + location-shared state, and registers `IInAppMessageLifecycleListener`, `IInAppMessageClickListener`, `INotificationClickListener`, and `INotificationLifecycleListener` (with `event.preventDefault()` so async display can be exercised).
- `MainViewModel : AndroidViewModel` — central state with `LiveData<T>` for every UI value. Implements `IPushSubscriptionObserver`, `IPermissionObserver`, `IUserStateObserver`, and `IUserJwtInvalidatedListener`. Holds a monotonic `private var fetchRequestSequence = 0L` that maps to the shared guide's `requestSequence` for stale-result protection in `fetchUserDataFromApi`.
- `OneSignalRepository.kt` — thin coroutine wrapper that bounces every SDK call onto `Dispatchers.IO`. Exists so the ViewModel can stay on the main dispatcher and individual SDK call sites don't have to repeat `withContext`.
- `OneSignalService.kt` (`object`) — REST API client described in the shared guide's Prompt 1.4.
- `SharedPreferenceUtil.kt` — backs the shared guide's PreferencesService (consent required, privacy consent, external user id, location shared, IAM paused, cached JWT token, cached identity-verification toggle).

`fetchUserDataFromApi` follows the shared guide exactly: bump `++fetchRequestSequence`, capture as `requestId`, flip `_isLoading.value = true`, then after every suspend point bail with `return@withContext` when `requestId != fetchRequestSequence`. Same guard wraps the catch branch and the final `_isLoading.value = false`.

---

## Android-Specific UI Details

### Notification Permission

`MainActivity.onCreate` calls `viewModel.promptPush()` at the end so the prompt appears after the UI is on screen. `MainViewModel.promptPush` calls `OneSignal.Notifications.requestPermission(true)`. The shared guide's `prompt_push_button` is the manual fallback when the user denies the first request.

### Loading state

Inline only — no full-screen overlay. The four list sections (Aliases, Emails, SMS, Tags) accept a `loading: Boolean = viewModel.isLoading` parameter. When the list is empty and `loading` is true, `LoadingState` (centered `CircularProgressIndicator`, `testTag = "${sectionKey}_loading"`) replaces `EmptyState` in the same slot. Login/Logout buttons set `enabled = !isLoading` instead of showing a spinner.

### Snackbar / Toast

Compose's `SnackbarHostState`, mounted in `MainScreen`'s `Scaffold(snackbarHost = { SnackbarHost(...) })`. The `Snackbar` applies `Modifier.testTag("snackbar_toast")` so the shared Appium suite finds it.

A `LaunchedEffect(viewModel.toastMessage)` calls `snackbarHostState.showSnackbar(...)` then `viewModel.clearToast()`. The host is the only feedback surface — there is no `android.widget.Toast` bridge in `MainActivity`. Snackbar usage matches the shared guide's allowed set (login/logout, outcomes, custom event, location check, JWT invalidation); every other action only writes to `android.util.Log.i(TAG, ...)`, matching the shared guide's "use the platform's built-in logging primitive directly" rule.

### Send In-App Message icons

Use `androidx.compose.material.icons.filled.*` from `material-icons-extended`: `VerticalAlignTop`, `VerticalAlignBottom`, `CropSquare`, `Fullscreen`. Icons sit on the LEFT of each red full-width button per the shared guide.

### Modals

`Dialogs.kt` defines AlertDialog-based composables: `SingleInputDialog`, `PairInputDialog`, `MultiPairInputDialog`, `MultiSelectRemoveDialog`, `LoginDialog`, `OutcomeDialog`, `TrackEventDialog`, `CustomNotificationDialog`, `TooltipDialog`.

### Accessibility (Appium)

Apply test ids via `Modifier.testTag("...")`. A small `Modifier.applyTestTag(tag: String?)` extension noops when the tag is null, so reusable components (`SectionCard`, `ToggleRow`, `ActionButton`, the list widgets, every dialog) take a nullable tag parameter.

All identifiers match the shared guide's table exactly (`login_user_button`, `consent_required_toggle`, `snackbar_toast`, etc.). The dynamic patterns from the shared guide (`{sectionKey}_section`, `{sectionKey}_info_icon`, `{sectionKey}_pair_key_{key}`, `{sectionKey}_loading`, `{sectionKey}_empty`, `{sectionKey}_remove_{key}`) are driven by `SectionCard(sectionKey = "...")` and the list composables in `ListComponents.kt`.

### SDK log forwarding

`MainApplication` registers `OneSignal.Debug.addLogListener` and forwards each entry to `android.util.Log` under the `OneSignalSDK` tag, so SDK output shows up alongside app output in Android Studio's Logcat (filter `package:mine` to see both). There is no in-app log viewer — match the shared guide and other wrapper SDK demos by relying on Logcat.

---

## Android Additions Beyond the Shared Guide

The Android demo exercises a few SDK features that are not described in the shared guide today. They are wired into the User section so the shared Appium contract stays stable:

- **Identity Verification toggle** (`UserSection`, `testTag = "identity_verification_toggle"`) — persisted via `SharedPreferenceUtil.cacheIdentityVerification(...)`. When ON, `fetchUserDataFromApi` uses the cached external_id + JWT; when OFF it falls back to the onesignal_id endpoint.
- **JWT field in `LoginDialog`** — optional `"JWT Token (optional)"` input under the external-user-id field. `testTag = "login_user_jwt_input"`. `MainViewModel.loginUser(externalUserId, jwtToken)` threads the token into `OneSignal.login(externalUserId, jwtToken)` and caches it via `SharedPreferenceUtil.cacheJwtToken(...)`.
- **UPDATE USER JWT button** (`UserSection`, `testTag = "update_user_jwt_button"`) — opens a `PairInputDialog` (External User Id + JWT Token) and calls `viewModel.updateUserJwt(...)` → `OneSignal.updateUserJwt(...)`.
- **`IUserJwtInvalidatedListener`** — registered by `MainViewModel`; surfaces a snackbar + log entry when the SDK reports an invalidated JWT.

---

## Platform Config

### Permissions (`src/main/AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

The ADM permission name and intent category use the application's package, so they reference `com.onesignal.example` (not the SDK's package).

### Custom notification sound

The `vine_boom.wav` file from [`sdk-shared/assets`](https://github.com/OneSignal/sdk-shared/tree/main/assets) lives at `app/src/main/res/raw/vine_boom.wav` and is referenced from the **Send Push → WITH SOUND** payload as `android_sound = "vine_boom"` (no extension) plus `android_channel_id` from `BuildConfig.ONESIGNAL_ANDROID_CHANNEL_ID`.

`ONESIGNAL_ANDROID_CHANNEL_ID` is sourced through the [`local.properties` override system](#localproperties-overrides-androids-env) (`-P...`, then `local.properties`, then the demo default `b3b015d9-c050-4042-8548-dcc34aa44aa4`) and exposed as `BuildConfig.ONESIGNAL_ANDROID_CHANNEL_ID`.

### Service config files

The shared default `applicationId` is `com.onesignal.example`. Both service configs must agree:

- `app/google-services.json` → `package_name = "com.onesignal.example"` for the `gms` flavor.
- `app/agconnect-services.json` → every `package_name` entry set to `com.onesignal.example` for the `huawei` flavor.

If the package changes you must regenerate these from the Firebase / Huawei AppGallery consoles.

### Huawei flavor

`src/huawei/` overlays the main source set:

- `src/huawei/AndroidManifest.xml` — declares `HmsMessageServiceAppLevel` with `android:name="com.onesignal.example.notification.HmsMessageServiceAppLevel"`.
- `src/huawei/java/com/onesignal/example/notification/HmsMessageServiceAppLevel.kt` — minimal `HmsMessageService` subclass that forwards messages to OneSignal.

---

## File Structure

```
examples/demo/
├── build.gradle.kts                       # root project, plugin classpath
├── settings.gradle.kts
├── gradle.properties
├── build.md                               # this file
└── app/
    ├── build.gradle.kts                   # namespace = com.onesignal.example, gms/huawei flavors
    ├── google-services.json               # package_name = com.onesignal.example
    ├── agconnect-services.json            # package_name = com.onesignal.example
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/onesignal/example/
        │   │   ├── application/MainApplication.kt
        │   │   ├── data/
        │   │   │   ├── model/{NotificationType,InAppMessageType}.kt
        │   │   │   ├── network/OneSignalService.kt
        │   │   │   └── repository/OneSignalRepository.kt
        │   │   ├── ui/
        │   │   │   ├── components/        # SectionCard, ToggleRow, ActionButton,
        │   │   │   │                      # ListComponents, Dialogs
        │   │   │   ├── main/              # MainActivity, MainScreen, Sections, MainViewModel
        │   │   │   ├── secondary/SecondaryActivity.kt
        │   │   │   └── theme/Theme.kt
        │   │   └── util/                  # SharedPreferenceUtil, TooltipHelper
        │   └── res/values/{strings,colors,styles}.xml
        └── huawei/
            ├── AndroidManifest.xml
            └── java/com/onesignal/example/notification/HmsMessageServiceAppLevel.kt
```

---

## Android Best Practices

- Run with the `gms` flavor by default; switch to `huawei` only when validating HMS-specific code paths.
- Always pass `-PSDK_VERSION=X.Y.Z` when building from `OneSignalSDK/`. The standalone `examples/demo/` build is the path of least resistance for feature testing.
- Changing the App ID via `local.properties` only requires a rebuild — the demo reads `BuildConfig.ONESIGNAL_APP_ID` on every launch, no SharedPreferences cache. (The SDK itself still stores per-app data, so swapping App IDs mid-test may still need `./gradlew :app:uninstallGmsDebug` to clear OneSignal's own state.)
- Keep `isMinifyEnabled = true` on the `release` build type — it exercises R8 + the SDK's published ProGuard rules and protects against regressions like SDK-4185.
- Don't reintroduce `LoadingOverlay`. Loading is per-section by design (matches the shared guide's "Loading State" rules in styles.md).
- When adding a new section or interactive element, give it a `testTag` that matches the shared guide's table (or follows the `{sectionKey}_*` pattern). Cross-platform Appium runs in `sdk-shared/appium/tests/` depend on it.
