# Location service implementations are instantiated via reflective constructor selection
# (ServiceRegistrationReflection). Keep only their constructors.
-keepclassmembers class com.onesignal.location.** {
    <init>(...);
}

# GoogleApiClientCompatProxy invokes these GMS methods reflectively by string name (for
# compatibility across Google Play services versions), so their names must be preserved.
-keepclassmembers class com.google.android.gms.common.api.GoogleApiClient {
    com.google.android.gms.common.ConnectionResult blockingConnect();
    void connect();
    void disconnect();
}
