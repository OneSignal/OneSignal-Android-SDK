/**
 * Modified MIT License
 *
 * Copyright 2019 OneSignal
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
package com.onesignal.iam.internal.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection

internal object OneSignalChromeTab {
    private fun hasChromeTabLibrary(): Boolean {
        return try {
            // noinspection ConstantConditions
            CustomTabsServiceConnection::class.java != null
        } catch (e: Throwable) {
            false
        }
    }

    internal fun open(url: String, openActivity: Boolean, context: Context): Boolean {
        if (!hasChromeTabLibrary()) return false
        val connection: CustomTabsServiceConnection =
            OneSignalCustomTabsServiceConnection(url, openActivity, context)
        return CustomTabsClient.bindCustomTabsService(
            context,
            "com.android.chrome",
            connection
        )
    }

    private class OneSignalCustomTabsServiceConnection internal constructor(
        private val url: String,
        private val openActivity: Boolean,
        private val context: Context
    ) : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(
            componentName: ComponentName,
            customTabsClient: CustomTabsClient
        ) {
            customTabsClient.warmup(0)
            val session = customTabsClient.newSession(null) ?: return
            val uri = Uri.parse(url)
            session.mayLaunchUrl(uri, null, null)

            // Shows tab as it's own Activity
            if (openActivity) {
                val mBuilder = CustomTabsIntent.Builder(session)
                val customTabsIntent = mBuilder.build()
                customTabsIntent.intent.data = uri
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    context.startActivity(
                        customTabsIntent.intent,
                        customTabsIntent.startAnimationBundle
                    )
                } else {
                    context.startActivity(customTabsIntent.intent)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }
}
