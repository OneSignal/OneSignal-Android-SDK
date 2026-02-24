plugins {
    id("com.android.application")
    id("kotlin-android")
}

// Apply GMS or Huawei plugin based on build variant
// Check at configuration time, not when task graph is ready
val taskRequests = gradle.startParameter.taskRequests.toString().lowercase()
if (taskRequests.contains("gms")) {
    apply(plugin = "com.google.gms.google-services")
} else if (taskRequests.contains("huawei")) {
    apply(plugin = "com.huawei.agconnect")
}

// OneSignal SDK version - can be overridden via gradle property SDK_VERSION
val sdkVersion: String = rootProject.findProperty("SDK_VERSION") as? String ?: "5.6.1"

android {
    namespace = "com.onesignal.sdktest"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    flavorDimensions += "default"

    productFlavors {
        create("gms") {
            dimension = "default"
            applicationId = "com.onesignal.sdktest"
        }
        create("huawei") {
            dimension = "default"
            minSdk = 21
            applicationId = "com.onesignal.sdktest"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("androidx.core:core-ktx:1.9.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity & Lifecycle Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Google Play Services
    implementation("com.google.android.gms:play-services-location:21.0.0")

    // OneSignal - Google Play Builds
    "gmsImplementation"("com.onesignal:OneSignal:$sdkVersion")

    // OneSignal - Huawei Builds
    "huaweiImplementation"("com.onesignal:OneSignal:$sdkVersion") {
        exclude(group = "com.google.android.gms", module = "play-services-gcm")
        exclude(group = "com.google.android.gms", module = "play-services-analytics")
        exclude(group = "com.google.android.gms", module = "play-services-location")
        exclude(group = "com.google.firebase", module = "firebase-messaging")
    }
    "huaweiImplementation"("com.huawei.hms:push:6.3.0.304")
    "huaweiImplementation"("com.huawei.hms:location:4.0.0.300")
}
