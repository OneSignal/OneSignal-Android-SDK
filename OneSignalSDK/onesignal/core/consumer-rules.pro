# Amazon ADM is a compileOnly dependency; suppress missing-class warnings when the app omits it.
-dontwarn com.amazon.**

# Service implementations are instantiated by reflectively selecting a constructor
# (ServiceRegistrationReflection.resolve -> constructor.newInstance). The classes are retained via
# ::class.java literals in the modules, so only their constructors need protecting here.
-keepclassmembers class com.onesignal.** {
    <init>(...);
}

# Models are populated from JSON by matching getter method *names* via reflection
# (Model.initializeFromJson looks up "get<Property>"/"is<Property>"). Getters must survive and keep
# their names, so this must run without allowobfuscation.
-keepclassmembers class * extends com.onesignal.common.modeling.Model {
    *** get*();
    *** is*();
}

# Optional feature modules are loaded by fully-qualified name (OneSignalImp loads them via
# Class.forName(...).newInstance()), so the name must be preserved and a no-arg constructor kept.
-keep class ** implements com.onesignal.common.modules.IModule {
    <init>();
}

# Fallback "misconfigured" managers are reflectively constructed when an optional module is absent.
-keepclassmembers @com.onesignal.core.internal.minification.KeepStub class * { <init>(...); }
