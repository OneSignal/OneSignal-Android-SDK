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

#### Build types

- `release` -- `isMinifyEnabled = true`, `isShrinkResources = true`, consumes the SDK's published ProGuard rules.
- `profileable` -- declared via `create("profileable") { initWith(release); ... }` so it inherits R8/shrinker settings while staying profileable on-device (used for Macrobenchmark / Android Studio profiling).
- `flavorDimensions += "default"` with `gms` and `huawei` flavors (see table above).
- BuildConfig fields: `ONESIGNAL_APP_ID` and `ONESIGNAL_ANDROID_CHANNEL_ID`, both populated through the `demoOverride(...)` helper (`-P` -> `local.properties` -> default).

#### Root Gradle toolchain

The root `examples/demo/build.gradle.kts` pins:

- Android Gradle Plugin `8.8.2`
- Kotlin `1.9.25`
- Huawei AGCP classpath (`com.huawei.agconnect:agcp`) so the `huawei` flavor can apply `com.huawei.agconnect` at configuration time.

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

The demo is 100% Jetpack Compose (no XML layouts). Dependencies are pulled via the Compose BOM (`2024.02.00`) with `material3`, `material-icons-extended`, `runtime-livedata` (so `LiveData` integrates with Compose's `observeAsState`), and `activity-compose` + `lifecycle-*-compose` for the Compose entry point.

---

## State Management

The Android demo **overrides the shared guide's "no repository wrapper" rule**. Instead it uses:

- `MainApplication.kt` — initializes the SDK before any UI renders, restores cached consent + IAM-paused + location-shared state, and registers `IInAppMessageLifecycleListener`, `IInAppMessageClickListener`, `INotificationClickListener`, and `INotificationLifecycleListener` (with `event.preventDefault()` so async display can be exercised).
- `MainViewModel : AndroidViewModel` — central state with `LiveData<T>` for every UI value. Implements `IPushSubscriptionObserver`, `IPermissionObserver`, `IUserStateObserver`, and `IUserJwtInvalidatedListener`. Holds a monotonic `private var fetchRequestSequence = 0L` that maps to the shared guide's `requestSequence` for stale-result protection in `fetchUserDataFromApi`.
- `OneSignalRepository.kt` — only some methods are `suspend` + `withContext(Dispatchers.IO)`; many are synchronous wrappers and the ViewModel wraps calls in `viewModelScope.launch(Dispatchers.IO)` itself.
- `OneSignalService.kt` (`object`) — REST API client described in the shared guide's Prompt 1.4.
- `SharedPreferenceUtil.kt` — backs the shared guide's PreferencesService (consent required, privacy consent, external user id, location shared, IAM paused, cached JWT token, cached identity-verification toggle).

`fetchUserDataFromApi` loading sequence: the sequence is incremented first, then early returns may set `_isLoading = false` before `_isLoading = true` is set (see `MainViewModel.kt` lines ~167–192). Stale-fetch guards themselves are correct -- results are dropped when `requestId != fetchRequestSequence`, and the same guard wraps the catch branch and the final `_isLoading.value = false`.

#### `MainApplication` observer

Both `MainApplication` and `MainViewModel` register `IUserStateObserver`. The Application's observer only logs (no UI side effects) and exists so user-state changes are captured even before the first `MainActivity` is created.

---

## Android-Specific UI Details

### Notification Permission

`MainScreen`'s entry `LaunchedEffect(Unit) { viewModel.autoPromptPushOnce() }` fires the prompt once after the UI is on screen. `MainViewModel.autoPromptPushOnce` is guarded by a one-shot flag so config changes (rotation, theme, font-scale) don't re-prompt; `promptPush` itself calls `OneSignal.Notifications.requestPermission(true)`. The shared guide's `prompt_push_button` is the manual fallback when the user denies the first request.

### Loading state

Inline only — no full-screen overlay. The four list sections (Aliases, Emails, SMS, Tags) accept a `loading: Boolean = viewModel.isLoading` parameter. When the list is empty and `loading` is true, `LoadingState` (centered `CircularProgressIndicator`, `testTag = "${sectionKey}_loading"`) replaces `EmptyState` in the same slot. Login/Logout buttons set `enabled = !isLoading` instead of showing a spinner.

### Snackbar / Toast

- `SnackbarController` (`ui/components/SnackbarController.kt`) wraps Compose's `SnackbarHostState` and exposes a `show(message)` method with replace-on-show behavior.
- `MainScreen` creates `val snackbarController = rememberSnackbarController(snackbarHostState)` and exposes it down the tree via `CompositionLocalProvider(LocalSnackbarController provides snackbarController) { ... }` wrapping the sections column. The host is mounted on `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })`. The `Snackbar` applies `Modifier.testTag("snackbar_toast")` so the shared Appium suite finds it.
- Section composables read the controller with `val snackbar = LocalSnackbarController.current` and call `snackbar.show(...)` from action callbacks. Only Outcomes, Custom Events, and Location check trigger the snackbar.
- Most other actions write to `android.util.Log.i(TAG, ...)` in `MainViewModel` (add/remove aliases/tags/emails/SMS/triggers, toggles, send push/IAM, etc.). Login and logout are intentionally silent — no snackbar and no `Log.i`. The User section reflects auth state via `externalUserId` LiveData ("Logged In" / "Anonymous"), matching the other wrapper demos.
- Replace-on-show: `show(...)` cancels any in-flight `showJob` and `dismissJob`, dismisses `hostState.currentSnackbarData`, then launches a new pair where the dismiss coroutine runs `delay(DemoLayout.toastDurationMs)` and the show coroutine calls `hostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)`.
- Duration is the shared constant `DemoLayout.toastDurationMs = 3_000L` (milliseconds).
- `MainViewModel` must not hold any toast state, expose a `Channel<String>`/`Flow<String>` of toast messages, or own a `showToast` helper. There is no `android.widget.Toast` bridge in `MainActivity` either -- the snackbar host is the only feedback surface.

### Modals

- `MainScreen` owns layout + the tooltip dialog only (`var showTooltipDialog by remember { mutableStateOf<String?>(null) }` + `TooltipDialog`). Each section's `onInfoClick` callback sets the tooltip key.
- Sections own action dialog state via `var *Open by remember { mutableStateOf(false) }` and render shared dialog composables (from `ui/components/Dialogs.kt`) inside the section. Dialog confirm handlers call SDK methods through callbacks passed from `MainScreen`, then close the dialog; for snackbar-emitting actions they also call `LocalSnackbarController.current.show(...)`.
- `MainViewModel` must not hold any action dialog visibility flags or input drafts.
- Shared composables in `ui/components/Dialogs.kt`: `SingleInputDialog`, `MultiSelectRemoveDialog`, `LoginDialog`, `OutcomeDialog`, `TrackEventDialog`, `CustomNotificationDialog`, `TooltipDialog` use `AlertDialog`. `PairInputDialog` and `MultiPairInputDialog` use `Dialog` + `Surface` (for the wider two-column layout).
- Dialog state naming: `OutcomeSection` and `CustomEventsSection` use `var open by remember { mutableStateOf(false) }` (singular `open`) because each owns a single dialog. Other sections name their flags per dialog (`loginOpen`, `addOpen`, etc.).

### Accessibility (Appium)

Apply test ids via `Modifier.testTag("...")`. `Modifier.applyTestTag(tag: String?)` exists as private duplicate helpers in `Dialogs.kt` and `ActionButton.kt` (each ~5 lines) that noop when the tag is null -- not a single shared module-level utility today. `ToggleRow`, `SectionCard`, and `ListComponents` apply `testTag` inline.

All identifiers match the shared guide's table exactly (`login_user_button`, `consent_required_toggle`, `snackbar_toast`, etc.). The dynamic patterns from the shared guide (`{sectionKey}_section`, `{sectionKey}_info_icon`, `{sectionKey}_pair_key_{key}`, `{sectionKey}_loading`, `{sectionKey}_empty`, `{sectionKey}_remove_{key}`) are driven by `SectionCard(sectionKey = "...")` and the list composables in `ListComponents.kt`.

#### Test-tag patterns

Patterns used by this demo beyond the shared guide's table:

- `{sectionKey}_pair_key_{key}` and `{sectionKey}_pair_value_{key}` -- two-column rows expose both halves so Appium can assert key and value independently.
- `{sectionKey}_value_{value}` -- list rows whose key is implicit (single-column lists).
- `main_scroll_view` -- the root `LazyColumn` in `MainScreen`, used by Appium swipe gestures.

#### Appium / UiAutomator: `testTagsAsResourceId`

- `MainActivity` sets `semantics(mergeDescendants = false) { testTagsAsResourceId = true }` on the root `Surface` so Appium `id=` selectors map onto Compose `testTag` values (`MainActivity.kt` lines ~41–43).
- `Dialogs.kt` re-applies the same via an `exposeTestTagsAsResourceId()` helper inside each dialog because Compose dialogs render in a separate window -- required for dialog-scoped test tags to be visible to UiAutomator.

### SDK log forwarding

`MainApplication` registers `OneSignal.Debug.addLogListener` and forwards each entry to `android.util.Log` under the `OneSignalSDK` tag, so SDK output shows up alongside app output in Android Studio's Logcat (filter `package:mine` to see both). There is no in-app log viewer — match the shared guide and other wrapper SDK demos by relying on Logcat.

---

## Android Additions Beyond the Shared Guide

The Android demo exercises a few SDK features that are not described in the shared guide today. They are wired into the User section so the shared Appium contract stays stable:

- **Identity Verification toggle** (`UserSection`, `testTag = "identity_verification_toggle"`) — persisted via `SharedPreferenceUtil.cacheIdentityVerification(...)`. When ON, `fetchUserDataFromApi` uses the cached external_id + JWT; when OFF it falls back to the onesignal_id endpoint.
- **JWT field in `LoginDialog`** — optional `"JWT Token (optional)"` input under the external-user-id field. `testTag = "login_user_jwt_input"`. `MainViewModel.loginUser(externalUserId, jwtToken)` threads the token into `OneSignal.login(externalUserId, jwtToken)` and caches it via `SharedPreferenceUtil.cacheJwtToken(...)`.
- **UPDATE USER JWT button** (`UserSection`, `testTag = "update_user_jwt_button"`) — opens a `PairInputDialog` (External User Id + JWT Token) and calls `viewModel.updateUserJwt(...)` → `OneSignal.updateUserJwt(...)`.
- **`IUserJwtInvalidatedListener`** — registered by `MainViewModel`; surfaces a log entry via `Log.i(TAG, ...)` when the SDK reports an invalidated JWT. Per Prompt 7.6 the snackbar is no longer fired from this listener.

---

## Platform Config

### Permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_BACKGROUND_LOCATION`
- `com.android.vending.BILLING`
- `android.permission.WAKE_LOCK`
- ADM / Amazon push wiring (`com.amazon.device.messaging.permission.RECEIVE`, plus the app-scoped `com.onesignal.example.permission.RECEIVE_ADM_MESSAGE` and matching intent category).

Contributed via manifest merge from the OneSignal SDK:

- `android.permission.INTERNET`
- `android.permission.POST_NOTIFICATIONS`
- FCM-related permissions (e.g. `com.google.android.c2dm.permission.RECEIVE`)
- Badge permissions (Samsung / Sony / HTC / etc.)
- `android.permission.RECEIVE_BOOT_COMPLETED` and other SDK-side wiring

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
examples/
├── build.md                                   # this file
└── demo/
    ├── build.gradle.kts                       # root project, plugin classpath
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── local.properties.example               # template for local.properties
    └── app/
        ├── build.gradle.kts                   # namespace = com.onesignal.example, gms/huawei flavors
        ├── proguard-rules.pro
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
            │   │   │   ├── components/        # SectionCard (with DemoSection),
            │   │   │   │                      # ToggleRow, ActionButton, ListComponents,
            │   │   │   │                      # CardKvRow, DemoAppBar, Dialogs,
            │   │   │   │                      # SnackbarController
            │   │   │   ├── main/              # MainActivity, MainScreen, Sections, MainViewModel
            │   │   │   ├── secondary/SecondaryActivity.kt
            │   │   │   └── theme/             # Theme.kt, DemoLayout.kt
            │   │   └── util/                  # SharedPreferenceUtil, TooltipHelper
            │   └── res/
            │       ├── values/{strings,colors,styles}.xml
            │       ├── raw/                   # vine_boom.wav
            │       ├── mipmap-*/              # launcher icons (hdpi..xxxhdpi)
            │       ├── drawable-*/            # density-bucketed drawables (hdpi..xxxhdpi)
            │       └── drawable-nodpi/onesignal_rectangle.png
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
