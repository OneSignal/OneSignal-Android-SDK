# Location service implementations are instantiated via reflective constructor selection
# (ServiceRegistrationReflection). Keep only their constructors.
-keepclassmembers class com.onesignal.location.** {
    <init>(...);
}
