-dontwarn com.onesignal.**

-dontwarn com.amazon.**

-keepclassmembers class com.onesignal.core.** { *; }

-keepclassmembers class com.onesignal.session.** { *; }

-keepclassmembers class com.onesignal.user.** { *; }

-keepclassmembers class com.onesignal.internal.** { *; }

-keepclassmembers class com.onesignal.debug.** { *; }

-keepclassmembers class com.onesignal.common.** { *; }

-keep class ** implements com.onesignal.common.modules.IModule { *; }