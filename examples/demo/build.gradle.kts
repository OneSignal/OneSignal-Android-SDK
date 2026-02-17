// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Huawei maven
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:${Versions.androidGradlePlugin}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("com.google.gms:google-services:${Versions.googleServices}")
        classpath("com.huawei.agconnect:agcp:${Versions.huaweiAgcp}")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // Huawei maven
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
