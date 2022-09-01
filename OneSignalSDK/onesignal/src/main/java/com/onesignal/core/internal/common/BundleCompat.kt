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
package com.onesignal.core.internal.common

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.PersistableBundle
import androidx.annotation.RequiresApi

internal interface BundleCompat<T> {
    fun putString(key: String?, value: String?)
    fun putInt(key: String?, value: Int?)
    fun putLong(key: String?, value: Long?)
    fun putBoolean(key: String?, value: Boolean?)
    fun getString(key: String?): String?
    fun getInt(key: String?): Int
    fun getLong(key: String?): Long
    fun getBoolean(key: String?): Boolean
    fun getBoolean(key: String?, value: Boolean): Boolean
    fun containsKey(key: String?): Boolean
    fun getBundle(): T
    fun setBundle(bundle: Parcelable)
}

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
internal class BundleCompatPersistableBundle : BundleCompat<PersistableBundle> {
    private var mBundle: PersistableBundle

    constructor() {
        mBundle = PersistableBundle()
    }

    constructor(bundle: PersistableBundle) {
        mBundle = bundle
    }

    override fun putString(key: String?, value: String?) {
        mBundle.putString(key, value)
    }

    override fun putInt(key: String?, value: Int?) {
        mBundle.putInt(key, value!!)
    }

    override fun putLong(key: String?, value: Long?) {
        mBundle.putLong(key, value!!)
    }

    override fun putBoolean(key: String?, value: Boolean?) {
        mBundle.putBoolean(key, value!!)
    }

    override fun getString(key: String?): String? {
        return mBundle.getString(key)
    }

    override fun getInt(key: String?): Int {
        return mBundle.getInt(key)
    }

    override fun getLong(key: String?): Long {
        return mBundle.getLong(key)
    }

    override fun getBoolean(key: String?): Boolean {
        return mBundle.getBoolean(key)
    }

    override fun getBoolean(key: String?, value: Boolean): Boolean {
        return mBundle.getBoolean(key, value)
    }

    override fun containsKey(key: String?): Boolean {
        return mBundle.containsKey(key)
    }

    override fun getBundle(): PersistableBundle {
        return mBundle
    }

    override fun setBundle(bundle: Parcelable) {
        mBundle = bundle as PersistableBundle
    }
}

internal class BundleCompatBundle : BundleCompat<Bundle?> {
    private var mBundle: Bundle?

    constructor() {
        mBundle = Bundle()
    }

    constructor(bundle: Bundle?) {
        mBundle = bundle
    }

    constructor(intent: Intent) {
        mBundle = intent.extras
    }

    override fun putString(key: String?, value: String?) {
        mBundle!!.putString(key, value)
    }

    override fun putInt(key: String?, value: Int?) {
        mBundle!!.putInt(key, value!!)
    }

    override fun putLong(key: String?, value: Long?) {
        mBundle!!.putLong(key, value!!)
    }

    override fun putBoolean(key: String?, value: Boolean?) {
        mBundle!!.putBoolean(key, value!!)
    }

    override fun getString(key: String?): String? {
        return mBundle!!.getString(key)
    }

    override fun getInt(key: String?): Int {
        return mBundle!!.getInt(key)
    }

    override fun getLong(key: String?): Long {
        return mBundle!!.getLong(key)
    }

    override fun getBoolean(key: String?): Boolean {
        return mBundle!!.getBoolean(key)
    }

    override fun containsKey(key: String?): Boolean {
        return mBundle!!.containsKey(key)
    }

    override fun getBundle(): Bundle? {
        return mBundle
    }

    override fun setBundle(bundle: Parcelable) {
        mBundle = bundle as Bundle
    }

    override fun getBoolean(key: String?, value: Boolean): Boolean {
        return mBundle!!.getBoolean(key, value)
    }
}

internal object BundleCompatFactory {
    val instance: BundleCompat<*>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) BundleCompatPersistableBundle() else BundleCompatBundle()
}
