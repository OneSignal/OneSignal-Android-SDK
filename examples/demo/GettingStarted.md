# Red App — OneSignal Android Sample App

A Jetpack Compose demo app that exercises the full OneSignal Android SDK surface. Use it to test push notifications, in-app messages, user management, and more on a real device or emulator.

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Android Studio | Ladybug (2024.2+) recommended |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | API 34 (compileSdk / targetSdk) |
| Min device / emulator | API 21 (Android 5.0) |
| Physical device | Recommended for push notification testing |

## Quick Start

### 1. Clone the repo

```bash
git clone git@github.com:OneSignal/OneSignal-Android-SDK.git
cd OneSignal-Android-SDK
```

### 2. Open in Android Studio

There are two ways to open the project depending on your goal:

**Option A — Red App only (recommended for feature testing)**

1. In Android Studio, go to **File → Open**.
2. Navigate to the `examples/demo/` directory inside the cloned repo and click **Open**.
3. Android Studio will treat it as a standalone project and pull the OneSignal SDK from Maven.

**Option B — Red App + local SDK source (for SDK development)**

1. In Android Studio, go to **File → Open**.
2. Navigate to the `OneSignalSDK/` directory inside the cloned repo and click **Open**.
3. This project already includes the Red App via `settings.gradle` and substitutes the published OneSignal dependency with your local source modules.
4. You **must** pass `-PSDK_VERSION=<version>` (e.g. `5.6.1`) when building — see [Overriding the SDK Version](#overriding-the-sdk-version).

Wait for the Gradle sync to finish before proceeding.

### 3. Select build variant

The project has two product flavors:

| Flavor | Use case |
|--------|----------|
| **gms** | Google Play Services / FCM — use this on most devices and emulators |
| **huawei** | Huawei HMS — only needed for Huawei devices without GMS |

In Android Studio: **Build → Select Build Variant** → pick `gmsDebug` (default).

### 4. Build & run

Click **Run ▶** or:

```bash
./gradlew :app:installGmsDebug
```

The app ships with a default OneSignal App ID (`77e32082-ea27-42e3-a898-c72e141824ef`). To use your own, change it in the **App** section at the top of the running app.

## Features

The Red App is organized into collapsible sections, each exercising a different area of the SDK.

### App
Configure the OneSignal **App ID** at runtime, toggle **privacy consent required**, and grant/revoke **privacy consent**.

### User
**Login** with an external user ID or **Logout** to switch between identified and anonymous users. Displays the current OneSignal ID and external user ID.

### Push Notifications
View the **Push Subscription ID**, toggle push **enabled/disabled**, and **prompt** the user for notification permission (Android 13+).

### Send Push
Fire test notifications from the device via the OneSignal REST API:
- **Simple** — basic text notification
- **With Image** — includes a big picture
- **Custom** — fully customizable title, body, and additional data

### In-App Messaging
**Pause / resume** in-app message display.

### Send In-App Message
Trigger pre-configured in-app message layouts by setting SDK triggers:
- **Top Banner**
- **Bottom Banner**
- **Center Modal**
- **Full Screen**

> These require matching in-app messages configured in your OneSignal dashboard.

### Aliases
Add a single or multiple **alias key-value pairs** to the current user. View and remove existing aliases.

### Emails
Add or remove **email addresses** associated with the current user.

### SMS
Add or remove **SMS numbers** associated with the current user.

### Tags
Add, bulk-add, or remove **data tags** on the current user. Tags are displayed in a list for inspection.

### Outcomes
Send **outcome events** to measure results:
- Standard outcome
- Unique outcome (counted once per session)
- Outcome with a numeric value

### Triggers
Manage **in-app message triggers** — add, bulk-add, remove individual triggers, or clear all. Useful for testing in-app message targeting rules.

### Track Event
Send a named **event** with optional JSON properties for advanced analytics.

### Location
Toggle **location sharing** on or off and **prompt** for location permission.

### Secondary Activity
Navigate to a second screen with buttons to **simulate a crash** (`RuntimeException`) and **simulate an ANR** (10-second main-thread block) — useful for testing crash and ANR reporting.

### Log Viewer
A collapsible log panel at the top of the screen that surfaces real-time SDK log output, lifecycle callbacks, and observer events directly in the app.

## Overriding the SDK Version

When opening the standalone `examples/demo/` project, the SDK version defaults to `5.6.1`. Override it with:

```bash
./gradlew :app:installGmsDebug -PSDK_VERSION=5.7.0
```

When building from `OneSignalSDK/`, the `SDK_VERSION` property is **required** and the published dependency is automatically substituted with local source modules.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `google-services.json` error | Make sure the `gms` variant is selected. The file is already checked in. |
| Push not received on emulator | Use a device with Google Play Services. Some emulator images lack FCM support. |
| `SDK_VERSION is not defined` | You're building from `OneSignalSDK/`. Pass `-PSDK_VERSION=X.Y.Z`. |
| Gradle sync fails on Huawei deps | Switch to `gmsDebug` unless you specifically need HMS testing. |
