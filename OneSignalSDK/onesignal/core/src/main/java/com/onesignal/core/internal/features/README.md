# Startup flags

`FeatureManager` owns every feature flag. For `APP_STARTUP` flags it also keeps a tiny prefs file,
`OneSignalStartupFlags`, so code that runs before SDK init can read them without loading the whole
OneSignal prefs file (operations queue included) on the main thread. Typical pre-init readers make a
choice that lives for the whole process, such as which thread pools or logging pipeline to build.
`OneSignalDispatchers` is one: it picks its pools from `sdk_dispatcher_bootstrap`.

## Adding a startup flag

1. Add it to `FeatureFlag` in the KMP catalog with `FeatureActivationMode.APP_STARTUP`.
2. Read it:
   - After init (anything with an injected `IFeatureManager`): `featureManager.isEnabled(flag)`.
   - Before init (no service container yet): `FeatureManager.startupFlags { context }.isEnabled(flag)`.

Nothing else. Both calls return the same value for the life of the process.

## Where the logic lives

The snapshot, key filtering, write skipping, and latching are in KMP (`StartupFlagsLatch`,
`StartupFlags`, `FeatureManager.refreshAtStartup`), so iOS gets the same behavior by implementing
`IStartupFlagsStore`. Android only provides that store (`PrefsStartupFlagsStore`) and the
process-wide holder (`StartupFlagsCache`).

## How it works

1. Process starts. The first reader, before or after init, triggers a single read of the file. That
   snapshot is the process value for every APP_STARTUP flag.
2. `FeatureManager` is created and latches APP_STARTUP flags from the snapshot, IMMEDIATE flags from
   `ConfigModel`.
3. `FeatureManager.start()` (an `IStartableService`, so off main) copies the APP_STARTUP subset of
   `ConfigModel.sdkRemoteFeatureFlags` into the file.
4. A flags fetch updates `ConfigModel`. IMMEDIATE flags apply now. APP_STARTUP changes, on or off,
   are logged as deferred, and the file is rewritten for the next process. The write uses `commit()`,
   so it is on disk before the process can die.

## Rules

- **Only APP_STARTUP flags.** IMMEDIATE flags change mid-run and the file lags the store, so they are
  never written and `StartupFlags.isEnabled` rejects them.
- **The snapshot is the process value.** Telemetry tags and `isEnabled` must match what early code
  latched, so APP_STARTUP flags never come from the store at runtime.
- **Missing or unreadable means off.** A missing file, a read that throws, or no application context
  all give an empty snapshot. The source (`STORE`, `MISSING`, `UNREADABLE`, `NO_STORE`) is kept for
  logging.
- **One value per process, even when unreadable.** The first read wins, including an `UNREADABLE`
  one. Later readers in the same process (sync jobs, FCM receivers, prewarm) never re-read and get a
  different answer, so code that already latched "off" never disagrees with code that runs later.
- **Device-protected storage.** On API 24+ the file lives in device-protected storage
  (`createDeviceProtectedStorageContext()`), so receivers and jobs that start before first unlock
  read real values instead of `UNREADABLE`. Below 24 there is no direct boot, so the normal context
  is used. Never put anything secret in this file: it is readable while the device is locked.
- **One launch behind after an upgrade.** The file does not exist on the first launch of the version
  that introduces it, so APP_STARTUP flags are off for that launch even if the store already has them.
- **No writes before `start()`.** `FeatureManager` can be constructed on main; the file is only written
  after start.
- **Main thread cost.** The first reader opens one small XML file. Cheap, but StrictMode can still report
  it as a disk read when that reader is on main.

## Tests

- `FeatureManager.resetStartupFlagsForTest()` forgets the snapshot, as a new process would.
  `OneSignalTestProjectConfig` calls it around every spec, and `OneSignalDispatchers.resetForTest()`
  calls it before building a new pool generation.
- `StartupFlagsCache.setSnapshotForTest(...)` installs a fixed snapshot; `readFile` reads the file
  without touching the snapshot.
- `FeatureManager.localFeatureOverrides` does not reach the snapshot. Pre-init readers need their own
  test hooks (for example `OneSignalDispatchers.bootstrapEnabledForTest`).
