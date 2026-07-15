# In-app-messages service implementations are instantiated via reflective constructor selection
# (ServiceRegistrationReflection). Keep only their constructors.
-keepclassmembers class com.onesignal.inAppMessages.** {
    <init>(...);
}

# IAM display Position is resolved via Position.valueOf(serverString) (WebViewManager) and the
# IllegalArgumentException on a renamed constant is uncaught. Keep enum constant names.
-keepclassmembers enum com.onesignal.inAppMessages.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
