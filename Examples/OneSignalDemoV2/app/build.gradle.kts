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
        viewBinding = true
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
    implementation(Dependencies.cardview)
    implementation(Dependencies.appcompat)
    implementation(Dependencies.vectorDrawable)
    implementation(Dependencies.coreKtx)
    implementation(Dependencies.constraintLayout)
    implementation(Dependencies.activityKtx)

    // Lifecycle (MVVM)
    implementation(Dependencies.lifecycleViewModelKtx)
    implementation(Dependencies.lifecycleLiveDataKtx)
    implementation(Dependencies.lifecycleRuntimeKtx)

    // Material Design
    implementation(Dependencies.material)

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
