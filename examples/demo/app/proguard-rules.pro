# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# No app-level -dontwarn for OneSignal OTel here: when com.onesignal:core pulls in com.onesignal:otel
# (implementation dependency), AGP merges otel's consumer-rules.pro for R8 (SDK-4207 / #2596).
# Older SDK lines without otel never put those optional classes on the classpath, so duplicates are unnecessary.
