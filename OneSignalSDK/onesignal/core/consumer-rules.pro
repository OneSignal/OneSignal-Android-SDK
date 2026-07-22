# Amazon ADM is a compileOnly dependency; suppress missing-class warnings when the app omits it.
-dontwarn com.amazon.**

# ServiceRegistrationReflection.resolve matches constructors via genericParameterTypes
# (e.g. List<IBackgroundService>). Keep Signature so R8 cannot strip that metadata.
-keepattributes Signature

# Service implementations are instantiated by reflectively selecting a constructor
# (ServiceRegistrationReflection.resolve -> constructor.newInstance). The classes are retained via
# ::class.java literals in the modules, so only their constructors need protecting here.
-keepclassmembers class com.onesignal.** {
    <init>(...);
}

# Models are populated from JSON by matching getter method *names* via reflection
# (Model.initializeFromJson looks up "get<Property>"). Kotlin boolean properties also expose
# is*() getters that must keep their names. No allowobfuscation.
-keepclassmembers class * extends com.onesignal.common.modeling.Model {
    *** get*();
    *** is*();
}

# Enum constants are looked up by name via valueOf/enumValueOf against server-supplied strings
# (e.g. Model.getOptEnumProperty -> ConfigModel.logLevel from remote-params JSON). Their names must
# survive obfuscation, so keep enum fields and the synthetic values()/valueOf(String) methods.
-keepclassmembers enum com.onesignal.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Optional feature modules are loaded by fully-qualified name (OneSignalImp loads them via
# Class.forName(...).newInstance()), so the name must be preserved and a no-arg constructor kept.
-keep class ** implements com.onesignal.common.modules.IModule {
    <init>();
}

# Fallback "misconfigured" managers are reflectively constructed when an optional module is absent.
-keepclassmembers @com.onesignal.core.internal.minification.KeepStub class * { <init>(...); }
