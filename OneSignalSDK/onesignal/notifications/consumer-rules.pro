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

-dontwarn com.huawei.**
-dontwarn com.amazon.**

# ADM handlers are instantiated by name from the app manifest AND their on* lifecycle callbacks
# (onMessage/onRegistered/onRegistrationError/onUnregistered) are invoked by the ADM framework, not
# the SDK, so keep both constructors and those methods. (Amazon-device-only path, untestable in CI.)
-keep public class com.onesignal.notifications.services.ADMMessageHandler { <init>(...); void on*(...); }
-keep public class com.onesignal.notifications.services.ADMMessageHandlerJob { <init>(...); void on*(...); }

# Legacy v4 job shim is referenced by name (older scheduled jobs); keep its constructors.
-keep class com.onesignal.JobIntentService$* { <init>(...); }

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
