package com.onesignal.sdktest.application

class TestApplication : android.app.Application() {
    override fun onCreate() {
        // no op; to bypass MainApplication before testing
    }
}
