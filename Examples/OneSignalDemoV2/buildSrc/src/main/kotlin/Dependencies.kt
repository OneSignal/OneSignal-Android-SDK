object Dependencies {
    // Kotlin
    const val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"
    const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"

    // AndroidX
    const val appcompat = "androidx.appcompat:appcompat:${Versions.appcompat}"
    const val coreKtx = "androidx.core:core-ktx:${Versions.coreKtx}"
    const val multidex = "androidx.multidex:multidex:${Versions.multidex}"

    // Compose BOM
    const val composeBom = "androidx.compose:compose-bom:${Versions.composeBom}"
    
    // Compose (versions managed by BOM)
    const val composeUi = "androidx.compose.ui:ui"
    const val composeUiGraphics = "androidx.compose.ui:ui-graphics"
    const val composeUiToolingPreview = "androidx.compose.ui:ui-tooling-preview"
    const val composeMaterial3 = "androidx.compose.material3:material3"
    const val composeMaterialIcons = "androidx.compose.material:material-icons-extended"
    const val composeUiTooling = "androidx.compose.ui:ui-tooling"
    const val composeRuntime = "androidx.compose.runtime:runtime"
    const val composeRuntimeLivedata = "androidx.compose.runtime:runtime-livedata"
    
    // Activity Compose
    const val activityCompose = "androidx.activity:activity-compose:${Versions.activityCompose}"
    
    // Lifecycle Compose
    const val lifecycleViewModelCompose = "androidx.lifecycle:lifecycle-viewmodel-compose:${Versions.lifecycleCompose}"
    const val lifecycleRuntimeCompose = "androidx.lifecycle:lifecycle-runtime-compose:${Versions.lifecycleCompose}"

    // Lifecycle
    const val lifecycleViewModelKtx = "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}"
    const val lifecycleRuntimeKtx = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}"

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
