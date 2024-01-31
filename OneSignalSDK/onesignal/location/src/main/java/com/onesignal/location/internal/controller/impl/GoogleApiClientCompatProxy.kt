/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.location.internal.controller.impl

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient

// Allows compatibility with pre-8.1.0 of GMS via reflection.
// This allows the methods below to be used even if the app developer is using an old version Google Play services.
internal class GoogleApiClientCompatProxy(
    val realInstance: GoogleApiClient
) {

    private val googleApiClientListenerClass: Class<*> = realInstance.javaClass

    fun blockingConnect(): ConnectionResult? {
        try {
            return googleApiClientListenerClass.getMethod("blockingConnect").invoke(realInstance) as ConnectionResult
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return null
    }

    fun connect() {
        try {
            googleApiClientListenerClass.getMethod("connect").invoke(realInstance)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            googleApiClientListenerClass.getMethod("disconnect").invoke(realInstance)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
