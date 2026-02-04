object Dependencies {
    // Kotlin
    const val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"
    const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"

    // AndroidX
    const val appcompat = "androidx.appcompat:appcompat:${Versions.appcompat}"
    const val coreKtx = "androidx.core:core-ktx:${Versions.coreKtx}"
    const val constraintLayout = "androidx.constraintlayout:constraintlayout:${Versions.constraintLayout}"
    const val cardview = "androidx.cardview:cardview:${Versions.cardview}"
    const val vectorDrawable = "androidx.vectordrawable:vectordrawable:${Versions.vectorDrawable}"
    const val multidex = "androidx.multidex:multidex:${Versions.multidex}"
    const val activityKtx = "androidx.activity:activity-ktx:${Versions.activityKtx}"

    // Lifecycle
    const val lifecycleViewModelKtx = "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}"
    const val lifecycleLiveDataKtx = "androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycle}"
    const val lifecycleRuntimeKtx = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}"

    // Material
    const val material = "com.google.android.material:material:${Versions.material}"

    // Google Play Services
    const val playServicesLocation = "com.google.android.gms:play-services-location:${Versions.playServicesLocation}"

    // Huawei
    const val huaweiPush = "com.huawei.hms:push:${Versions.huaweiPush}"
    const val huaweiLocation = "com.huawei.hms:location:${Versions.huaweiLocation}"
}

object Plugins {
    const val androidApplication = "com.android.application"
    const val kotlinAndroid = "kotlin-android"
    const val googleServices = "com.google.gms.google-services"
    const val huaweiAgconnect = "com.huawei.agconnect"
}

object AppConfig {
    const val applicationId = "com.onesignal.sdktest"
}
