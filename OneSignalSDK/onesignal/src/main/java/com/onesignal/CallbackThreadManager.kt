/**
 * Modified MIT License
 * <p>
 * Copyright 2023 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal

import kotlin.concurrent.thread

/**
 * Provides a public API to allow changing which thread callbacks and observers
 * should fire on.
 *
 * Initial motivation for this is to allow the OneSignal-Unity-SDK to config
 * the SDK to fire off the main thread. This is to avoid cases where Unity may
 * cause the main UI thread to wait on a background thread when calling back
 * into Unity.
 *
 * Usage: CallbackThreadManager.preference = UseThread.Background
 */
class CallbackThreadManager {
    enum class UseThread {
        MainUI,
        Background
    }

    companion object {
        var preference = UseThread.MainUI

        fun runOnPreferred(runnable: Runnable) {
            when (preference) {
                UseThread.MainUI -> OSUtils.runOnMainUIThread(runnable)
                UseThread.Background -> thread { runnable.run() }
            }
        }
    }
}
