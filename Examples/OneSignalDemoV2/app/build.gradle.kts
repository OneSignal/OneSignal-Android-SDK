plugins {
    id(Plugins.androidApplication)
    id(Plugins.kotlinAndroid)
}

// Apply GMS or Huawei plugin based on build variant
// Check at configuration time, not when task graph is ready
val taskRequests = gradle.startParameter.taskRequests.toString().lowercase()
if (taskRequests.contains("gms")) {
    apply(plugin = Plugins.googleServices)
} else if (taskRequests.contains("huawei")) {
    apply(plugin = Plugins.huaweiAgconnect)
}

// OneSignal SDK version - can be overridden via gradle property SDK_VERSION
val sdkVersion: String = rootProject.findProperty("SDK_VERSION") as? String ?: Versions.oneSignalSdk

android {
    namespace = AppConfig.applicationId
    compileSdk = Versions.compileSdk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionCode = Versions.versionCode
        versionName = Versions.versionName
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.composeCompiler
    }

    flavorDimensions += "default"

    productFlavors {
        create("gms") {
            dimension = "default"
            applicationId = AppConfig.applicationId
        }
        create("huawei") {
            dimension = "default"
            minSdk = Versions.minSdk
            applicationId = AppConfig.applicationId
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
        create("profileable") {
            initWith(getByName("release"))
            isDebuggable = false
            isProfileable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packaging {
        resources {
            excludes += "androidsupportmultidexversion.txt"
        }
    }
}

dependencies {
    // Kotlin
    implementation(Dependencies.kotlinStdlib)
    implementation(Dependencies.coroutinesAndroid)

    // AndroidX
    implementation(Dependencies.multidex)
    implementation(Dependencies.appcompat)
    implementation(Dependencies.coreKtx)

    // Compose BOM
    implementation(platform(Dependencies.composeBom))
    implementation(Dependencies.composeUi)
    implementation(Dependencies.composeUiGraphics)
    implementation(Dependencies.composeUiToolingPreview)
    implementation(Dependencies.composeMaterial3)
    implementation(Dependencies.composeMaterialIcons)
    implementation(Dependencies.composeRuntime)
    implementation(Dependencies.composeRuntimeLivedata)
    debugImplementation(Dependencies.composeUiTooling)

    // Activity & Lifecycle Compose
    implementation(Dependencies.activityCompose)
    implementation(Dependencies.lifecycleViewModelCompose)
    implementation(Dependencies.lifecycleRuntimeCompose)

    // Lifecycle
    implementation(Dependencies.lifecycleViewModelKtx)
    implementation(Dependencies.lifecycleRuntimeKtx)

    // Google Play Services
    implementation(Dependencies.playServicesLocation)

    // OneSignal - Google Play Builds
    "gmsImplementation"("com.onesignal:OneSignal:$sdkVersion")

    // OneSignal - Huawei Builds
    "huaweiImplementation"("com.onesignal:OneSignal:$sdkVersion") {
        exclude(group = "com.google.android.gms", module = "play-services-gcm")
        exclude(group = "com.google.android.gms", module = "play-services-analytics")
        exclude(group = "com.google.android.gms", module = "play-services-location")
        exclude(group = "com.google.firebase", module = "firebase-messaging")
    }
    "huaweiImplementation"(Dependencies.huaweiPush)
    "huaweiImplementation"(Dependencies.huaweiLocation)
}
