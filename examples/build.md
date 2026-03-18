# OneSignal Android Sample App - Build Guide

This document extends the shared build guide with Android-specific details.

**Read the shared guide first:**
https://raw.githubusercontent.com/OneSignal/sdk-shared/refs/heads/main/demo/build.md

Replace `{{PLATFORM}}` with `Android` everywhere in that guide. Everything below either overrides or supplements sections from the shared guide.

---

## Project Foundation

- **Language**: Kotlin with Coroutines (`Dispatchers.IO`, `Dispatchers.Main`)
- **UI**: Jetpack Compose with Material3
- **Architecture**: MVVM (`MainViewModel` with `LiveData`)
- **Build system**: Gradle Kotlin DSL with inline dependency versions (no `buildSrc`, so it works when included from the SDK project)
- **Product flavors**: Google FCM and Huawei HMS (matching existing OneSignalDemo setup)
- **Package name**: `com.onesignal.sdktest` (must match `google-services.json` and `agconnect-services.json`)
- **App bar**: `CenterAlignedTopAppBar` (Material3)

---

## Jetpack Compose Setup

`build.gradle.kts` (app):

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
}

buildFeatures { compose = true }
```

Dependencies (via BOM `2024.02.00`):

- `composeUi`, `composeUiGraphics`, `composeUiToolingPreview`
- `composeMaterial3`
- `composeMaterialIconsExtended` (for IAM type icons)
- `composeRuntime`, `composeRuntimeLivedata`
- `activityCompose`
- `lifecycleViewModelCompose`, `lifecycleRuntimeCompose`

---

## SDK Initialization (MainApplication.kt)

Register Android-specific listener interfaces:

- `IInAppMessageLifecycleListener` (`onWillDisplay`, `onDidDisplay`, `onWillDismiss`, `onDidDismiss`)
- `IInAppMessageClickListener`
- `INotificationClickListener`
- `INotificationLifecycleListener` (with `preventDefault()` for async display testing)
- `IUserStateObserver` (log when user state changes)

Initialization order:

```
OneSignal.consentRequired = SharedPreferenceUtil.getCachedConsentRequired(context)
OneSignal.consentGiven = SharedPreferenceUtil.getUserPrivacyConsent(context)
OneSignal.initWithContext(this, appId)
OneSignal.InAppMessages.paused = SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(context)
OneSignal.Location.isShared = SharedPreferenceUtil.getCachedLocationSharedStatus(context)
```

SDK log capture:

```kotlin
OneSignal.Debug.addLogListener { event ->
    LogManager.log("SDK", event.entry, level)
}
```

## ViewModel Observers (MainViewModel.kt)

- `IPushSubscriptionObserver` — react to push subscription changes
- `IPermissionObserver` — react to notification permission changes
- `IUserStateObserver` — call `fetchUserDataFromApi()` when user changes

`loadInitialState()` reads from the SDK (not SharedPreferences):

- `_consentRequired` from `repository.getConsentRequired()` (reads `OneSignal.consentRequired`)
- `_privacyConsentGiven` from `repository.getPrivacyConsent()` (reads `OneSignal.consentGiven`)
- `_inAppMessagesPaused` from `repository.isInAppMessagesPaused()` (reads `OneSignal.InAppMessages.paused`)
- `_locationShared` from `repository.isLocationShared()` (reads `OneSignal.Location.isShared`)
- `_externalUserId` from `OneSignal.User.externalId` (empty string = no user logged in)
- `_appId` from `SharedPreferenceUtil` (app-level config, no SDK getter)

---

## Data Persistence (SharedPreferenceUtil.kt)

Stored in `SharedPreferences`:

- OneSignal App ID
- Consent required status
- Privacy consent status
- External user ID (for login state restoration)
- Location shared status
- In-app messaging paused status

---

## Android-Specific Implementation Notes

### Notification Permission

Auto-request via `LaunchedEffect(Unit) { viewModel.promptPush() }` in `MainScreen` composable.

### Loading Indicator

Use `kotlinx.coroutines.delay(100)` after setting all `LiveData` values before dismissing the loader.

### JSON Parsing

Use `JSONObject` to parse Track Event properties and convert to `Map<String, Any?>`.

### Toast Messages

- `MainViewModel` exposes `toastMessage: LiveData<String?>`
- `MainActivity` observes and shows Android `Toast`
- `LaunchedEffect` triggers on `toastMessage` change

### LogManager

- Pass-through to Android `logcat` AND UI display
- Thread-safe (posts to main thread for Compose state)
- API: `LogManager.d/i/w/e(tag, message)` mimics `android.util.Log`

### Tooltip Helper

- `TooltipHelper` is a Kotlin `object` (singleton)
- Fetches on `CoroutineScope(Dispatchers.IO)` to avoid blocking startup
- Uses `HttpURLConnection` for the network request
- `init(context: Context)` takes Android `Context`

### WITH SOUND Notification

- `vine_boom.wav` placed in `res/raw/`
- `NotificationType.WITH_SOUND` sends with `android_channel_id` in the REST API payload
- The OneSignal SDK handles channel creation when the notification is received

---

## File Structure

```
examples/demo/
├── app/
│   ├── src/main/
│   │   ├── java/com/onesignal/sdktest/
│   │   │   ├── application/
│   │   │   │   └── MainApplication.kt
│   │   │   ├── data/
│   │   │   │   ├── model/
│   │   │   │   │   ├── NotificationType.kt
│   │   │   │   │   └── InAppMessageType.kt
│   │   │   │   ├── network/
│   │   │   │   │   └── OneSignalService.kt
│   │   │   │   └── repository/
│   │   │   │       └── OneSignalRepository.kt
│   │   │   ├── ui/
│   │   │   │   ├── components/
│   │   │   │   │   ├── SectionCard.kt
│   │   │   │   │   ├── ToggleRow.kt
│   │   │   │   │   ├── ActionButton.kt
│   │   │   │   │   ├── ListComponents.kt
│   │   │   │   │   ├── LoadingOverlay.kt
│   │   │   │   │   ├── LogView.kt
│   │   │   │   │   └── Dialogs.kt
│   │   │   │   ├── main/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── MainScreen.kt
│   │   │   │   │   ├── Sections.kt
│   │   │   │   │   └── MainViewModel.kt
│   │   │   │   ├── secondary/
│   │   │   │   │   └── SecondaryActivity.kt
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt
│   │   │   └── util/
│   │   │       ├── SharedPreferenceUtil.kt
│   │   │       ├── LogManager.kt
│   │   │       └── TooltipHelper.kt
│   │   └── res/
│   │       ├── raw/
│   │       │   └── vine_boom.wav
│   │       └── values/
│   │           ├── strings.xml
│   │           ├── colors.xml
│   │           └── styles.xml
│   └── src/huawei/
│       └── java/com/onesignal/sdktest/notification/
│           └── HmsMessageServiceAppLevel.kt
├── google-services.json
├── agconnect-services.json
└── build.md (this file)
```

---

## Configuration

### strings.xml

```xml
<string name="onesignal_app_id">77e32082-ea27-42e3-a898-c72e141824ef</string>
```

### Package Name

The package name MUST be `com.onesignal.sdktest` to work with the existing `google-services.json` (Firebase) and `agconnect-services.json` (Huawei). Changing it requires updating those files with your own project configuration.
