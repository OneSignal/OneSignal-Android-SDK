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
package com.onesignal.location.shadows

import android.content.Intent
import android.os.IBinder
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Feature
import com.google.android.gms.common.api.Api
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.internal.BaseGmsClient
import com.google.android.gms.common.internal.IAccountAccessor
import io.mockk.InternalPlatformDsl.toArray
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.TimeUnit

@Implements(value = GoogleApiClient.Builder::class, looseSignatures = true)
class ShadowGoogleApiClientBuilder {
    @Implementation
    fun build(): GoogleApiClient? {
        return ShadowGoogleApiClient()
    }
}

class ShadowGoogleApiClient : GoogleApiClient() {
    override fun hasConnectedApi(p0: Api<*>): Boolean = connected

    override fun getConnectionResult(p0: Api<*>): ConnectionResult = ConnectionResult(0)

    override fun connect() {
        connected = true
    }

    override fun <C : Api.Client?> getClient(p0: Api.AnyClientKey<C>): C {
        return object : Api.Client {
            override fun connect(p0: BaseGmsClient.ConnectionProgressReportCallbacks) { }

            override fun disconnect(p0: String) {}

            override fun disconnect() { }

            override fun isConnected(): Boolean = true

            override fun isConnecting(): Boolean = false

            override fun getRemoteService(
                p0: IAccountAccessor?,
                p1: MutableSet<Scope>?,
            ) {}

            override fun requiresSignIn(): Boolean = false

            override fun onUserSignOut(p0: BaseGmsClient.SignOutCallbacks) { }

            override fun requiresAccount(): Boolean = false

            override fun requiresGooglePlayServices(): Boolean = false

            override fun providesSignIn(): Boolean = false

            override fun getSignInIntent(): Intent = Intent()

            override fun dump(
                p0: String,
                p1: FileDescriptor?,
                p2: PrintWriter,
                p3: Array<out String>?,
            ) { }

            override fun getServiceBrokerBinder(): IBinder? = null

            override fun getRequiredFeatures(): Array<Feature> = listOf<Feature>().toArray() as Array<Feature>

            override fun getEndpointPackageName(): String = ""

            override fun getMinApkVersion(): Int = 0

            override fun getAvailableFeatures(): Array<Feature> = listOf<Feature>().toArray() as Array<Feature>

            override fun getScopesForConnectionlessNonSignIn(): MutableSet<Scope> = mutableSetOf()

            override fun getLastDisconnectMessage(): String? = null
        } as C
    }

    override fun blockingConnect(): ConnectionResult {
        connected = true
        return ConnectionResult(0)
    }

    override fun blockingConnect(
        p0: Long,
        p1: TimeUnit,
    ): ConnectionResult {
        connected = true
        return ConnectionResult(0)
    }

    override fun disconnect() {
        connected = false
    }

    override fun reconnect() {
        connected = true
    }

    override fun clearDefaultAccountAndReconnect(): PendingResult<Status> {
        return object : PendingResult<Status>() {
            override fun await(): Status = Status.RESULT_SUCCESS

            override fun await(
                p0: Long,
                p1: TimeUnit,
            ): Status = Status.RESULT_SUCCESS

            override fun cancel() { }

            override fun isCanceled(): Boolean = false

            override fun setResultCallback(p0: ResultCallback<in Status>) { }

            override fun setResultCallback(
                p0: ResultCallback<in Status>,
                p1: Long,
                p2: TimeUnit,
            ) {
            }
        }
    }

    override fun stopAutoManage(p0: FragmentActivity) { }

    override fun isConnected(): Boolean = connected

    override fun isConnecting(): Boolean = false

    override fun registerConnectionCallbacks(p0: ConnectionCallbacks) { }

    override fun isConnectionCallbacksRegistered(p0: ConnectionCallbacks): Boolean = true

    override fun unregisterConnectionCallbacks(p0: ConnectionCallbacks) { }

    override fun registerConnectionFailedListener(p0: OnConnectionFailedListener) { }

    override fun isConnectionFailedListenerRegistered(p0: OnConnectionFailedListener): Boolean = false

    override fun unregisterConnectionFailedListener(p0: OnConnectionFailedListener) { }

    override fun dump(
        p0: String,
        p1: FileDescriptor,
        p2: PrintWriter,
        p3: Array<out String>,
    ) { }

    companion object {
        var connected: Boolean = false
    }
}
