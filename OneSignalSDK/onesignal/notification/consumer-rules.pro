-dontwarn com.onesignal.notification.**

# These 2 methods are called with reflection.
-keep class com.google.android.gms.common.api.GoogleApiClient {
    void connect();
    void disconnect();
}

# Need to keep as these 2 methods are called with reflection from com.onesignal.PushRegistratorFCM
-keep class com.google.firebase.iid.FirebaseInstanceId {
    static com.google.firebase.iid.FirebaseInstanceId getInstance(com.google.firebase.FirebaseApp);
    java.lang.String getToken(java.lang.String, java.lang.String);
}

-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.AdwHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.ApexHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.AsusHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.DefaultBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.EverythingMeHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.HuaweiHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.LGHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.NewHtcHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.NovaHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.OPPOHomeBader { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.SamsungHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.SonyHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.VivoHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.XiaomiHomeBadger { <init>(...); }
-keep class com.onesignal.notification.internal.badges.impl.shortcutbadger.impl.ZukHomeBadger { <init>(...); }

-dontwarn com.huawei.**
-dontwarn com.amazon.**

# Proguard ends up removing this class even if it is used in AndroidManifest.xml so force keeping it.
-keep public class com.onesignal.notification.services.ADMMessageHandler {*;}

-keep public class com.onesignal.notification.services.ADMMessageHandlerJob {*;}

-keep class com.onesignal.JobIntentService$* {*;}