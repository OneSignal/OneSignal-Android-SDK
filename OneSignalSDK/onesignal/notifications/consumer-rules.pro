# The notification service extension class is named in the app's AndroidManifest <meta-data> and is
# reflectively instantiated (NotificationLifecycleService -> Class.forName(name).newInstance()), so the
# class name and its no-arg constructor must be preserved.
-keep class ** implements com.onesignal.notifications.INotificationServiceExtension {
   <init>();
   void onNotificationReceived(com.onesignal.notifications.INotificationReceivedEvent);
}

# Home-screen badgers are instantiated via newInstance() from a list of class literals
# (ShortcutBadger.initBadger), so each implementation needs its constructor kept.
-keep class * implements com.onesignal.notifications.internal.badges.impl.shortcutbadger.Badger {
    <init>(...);
}

# Optional / excludable push providers are referenced directly by the provider-specific
# registrators. firebase-messaging is api (pulled in by default) but Huawei apps routinely
# exclude it; play-services-base, HMS, and ADM are compileOnly. When a consuming app omits a
# provider, those classes are absent at R8 time: Huawei-only apps drop firebase-messaging (and
# typically have no Play services), so PushRegistratorFCM's com.google.firebase.** /
# com.google.android.gms.tasks.** and GooglePlayServicesUpgradePrompt's
# com.google.android.gms.common.** are missing; GMS apps omit HMS (com.huawei.**) and ADM
# (com.amazon.**). Suppress the missing-class errors for all of them.
-dontwarn com.huawei.**
-dontwarn com.amazon.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ADM handlers are instantiated by name from the app manifest AND their on* lifecycle callbacks
# (onMessage/onRegistered/onRegistrationError/onUnregistered) are invoked by the ADM framework, not
# the SDK, so keep both constructors and those methods. (Amazon-device-only path, untestable in CI.)
-keep public class com.onesignal.notifications.services.ADMMessageHandler { <init>(...); void on*(...); }
-keep public class com.onesignal.notifications.services.ADMMessageHandlerJob { <init>(...); void on*(...); }

# Notification service implementations are instantiated via reflective constructor selection
# (ServiceRegistrationReflection). Keep only their constructors.
-keepclassmembers class com.onesignal.notifications.** {
    <init>(...);
}

# WorkManager instantiates workers via reflection using the (Context, WorkerParameters) constructor.
-keep class com.onesignal.notifications.internal.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# WorkManager instantiates InputMerger classes via reflection (InputMerger.fromClassName).
# R8 full mode (AGP 8+) strips no-arg constructors, causing:
# java.lang.NoSuchMethodException: androidx.work.OverwritingInputMerger.<init>()
# Keep all InputMerger subclasses (OverwritingInputMerger, ArrayCreatingInputMerger, etc.)
-keep class * extends androidx.work.InputMerger {
    public <init>();
}

# Room resolves WorkManager's generated database implementation by name and invokes its no-arg
# constructor reflectively. R8 full mode can remove that constructor when using WorkManager 2.8.x.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
