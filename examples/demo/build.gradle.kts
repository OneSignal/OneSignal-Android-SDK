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
        classpath("com.android.tools.build:gradle:8.8.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("com.google.gms:google-services:4.3.10")
        classpath("com.huawei.agconnect:agcp:1.9.1.304")
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
