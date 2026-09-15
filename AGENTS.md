# AGENTS.md

## Cursor Cloud specific instructions

### Project overview
This is the **OneSignal Android SDK** — a multi-module Gradle project (Kotlin/Java) that provides push notifications, in-app messaging, SMS, and email integration for Android apps. There are no backend services or databases; this is a pure client-side library tested entirely via JVM unit tests (Robolectric).

### Prerequisites
- **JDK 17+** (JDK 21 works fine; Gradle 8.10.2 requires JDK 17+)
- **Android SDK** with platform 34 and build-tools installed
- Environment variables: `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`, and `$ANDROID_HOME/cmdline-tools/latest/bin` on `PATH`

### Key paths
- SDK modules: `OneSignalSDK/onesignal/{core,notifications,in-app-messages,location,otel,testhelpers}/`
- Demo app: `examples/demo/app/`
- Gradle wrapper: `OneSignalSDK/gradlew` (run all commands from `OneSignalSDK/`)

### Build, lint, and test commands
All commands run from `OneSignalSDK/`:
- **Build all modules + demo app:** `./gradlew assembleRelease`
- **Build demo app debug (GMS):** `./gradlew :app:assembleGmsDebug`
- **Lint (formatting):** `./gradlew spotlessCheck`
- **Lint (static analysis):** `./gradlew detekt`
- **Unit tests:** `./gradlew testDebugUnitTest`
- **Apply formatting fixes:** `./gradlew spotlessApply`

### Gotchas
- The `settings.gradle` includes the demo app as `:app` and performs dependency substitution so local SDK source is used. The `SDK_VERSION` property must be set (it is in `gradle.properties`).
- First Gradle run auto-downloads additional build-tools (e.g., build-tools 35) and Android SDK components as needed.
- There are a small number of **pre-existing test failures** (4 out of ~982) in `SDKInitTests` and `RecoverFromDroppedLoginBugTests` on `main`. These are not environment issues.
- Tests use Robolectric and run on the JVM — no emulator or physical device is needed.
- The `--no-daemon` flag is recommended for CI-like runs; for interactive development `--daemon` (the default) is faster.
- Detekt reports some pre-existing findings (warnings), but the task succeeds (does not fail the build).
