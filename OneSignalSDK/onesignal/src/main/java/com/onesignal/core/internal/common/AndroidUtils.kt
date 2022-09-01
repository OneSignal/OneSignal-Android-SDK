package com.onesignal.core.internal.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import androidx.annotation.Keep
import com.onesignal.core.internal.logging.Logging
import java.util.Random

object AndroidUtils {
    fun getRandomDelay(minDelay: Int, maxDelay: Int): Int {
        return Random().nextInt(maxDelay + 1 - minDelay) + minDelay
    }

    fun isStringNotEmpty(body: String?): Boolean {
        return !TextUtils.isEmpty(body)
    }

    // Ensures the Activity is fully ready by;
    //   1. Ensure it is attached to a top-level Window by checking if it has an IBinder
    //   2. If Android M or higher ensure WindowInsets exists on the root window also
    fun isActivityFullyReady(activity: Activity): Boolean {
        val hasToken = activity.window.decorView.applicationWindowToken != null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return hasToken
        val decorView = activity.window.decorView
        val insetsAttached = decorView.rootWindowInsets != null
        return hasToken && insetsAttached
    }

    fun sleep(ms: Int) {
        try {
            Thread.sleep(ms.toLong())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun hasConfigChangeFlag(activity: Activity, configChangeFlag: Int): Boolean {
        var hasFlag = false
        try {
            val configChanges =
                activity.packageManager.getActivityInfo(activity.componentName, 0).configChanges
            val flagInt = configChanges and configChangeFlag
            hasFlag = flagInt != 0
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return hasFlag
    }

    fun getManifestMeta(context: Context, metaName: String?): String? {
        val bundle = getManifestMetaBundle(context)
        return bundle?.getString(metaName)
    }

    fun getManifestMetaBoolean(context: Context, metaName: String?): Boolean {
        val bundle = getManifestMetaBundle(context)
        return bundle?.getBoolean(metaName) ?: false
    }

    fun getManifestMetaBundle(context: Context): Bundle? {
        val ai: ApplicationInfo
        try {
            ai = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            return ai.metaData
        } catch (e: PackageManager.NameNotFoundException) {
            Logging.error("Manifest application info not found", e)
        }
        return null
    }

    fun getResourceString(context: Context, key: String?, defaultStr: String?): String? {
        val resources = context.resources
        val bodyResId = resources.getIdentifier(key, "string", context.packageName)
        return if (bodyResId != 0) resources.getString(bodyResId) else defaultStr
    }

    fun isValidResourceName(name: String?): Boolean {
        return name != null && !name.matches("^[0-9]".toRegex())
    }

    fun getRootCauseThrowable(subjectThrowable: Throwable): Throwable {
        var throwable = subjectThrowable
        while (throwable.cause != null && throwable.cause !== throwable) {
            throwable = throwable.cause!!
        }
        return throwable
    }

    fun getRootCauseMessage(throwable: Throwable): String? {
        return getRootCauseThrowable(throwable).message
    }

    fun isRunningOnMainThread(): Boolean {
        return Thread.currentThread() == Looper.getMainLooper().thread
    }

    fun getTargetSdkVersion(context: Context): Int {
        val packageName = context.packageName
        val packageManager = context.packageManager
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            return applicationInfo.targetSdkVersion
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
    }

    fun openURLInBrowser(appContext: Context, url: String) {
        openURLInBrowser(appContext, Uri.parse(url.trim { it <= ' ' }))
    }

    fun openURLInBrowser(appContext: Context, uri: Uri) {
        val intent = openURLInBrowserIntent(uri)
        appContext.startActivity(intent)
    }

    fun openURLInBrowserIntent(uri: Uri): Intent {
        var uri = uri
        var type = if (uri.scheme != null) SchemaType.fromString(uri.scheme) else null

        if (type == null) {
            type = SchemaType.HTTP
            if (!uri.toString().contains("://")) {
                uri = Uri.parse("http://$uri")
            }
        }
        val intent: Intent
        when (type) {
            SchemaType.DATA -> {
                intent =
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
                intent.data = uri
            }
            SchemaType.HTTPS, SchemaType.HTTP -> intent = Intent(Intent.ACTION_VIEW, uri)
            else -> intent = Intent(Intent.ACTION_VIEW, uri)
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
        return intent
    }

    // Interim method that works around Proguard's overly aggressive assumenosideeffects which
    // ignores keep rules.
    // This is specifically designed to address Proguard removing catches for NoClassDefFoundError
    // when the config has "-assumenosideeffects" with
    // java.lang.Class.getName() & java.lang.Object.getClass().
    // This @Keep annotation is key so this method does not get removed / inlined.
    // Addresses issue https://github.com/OneSignal/OneSignal-Android-SDK/issues/1423
    @Keep
    fun opaqueHasClass(_class: Class<*>): Boolean {
        return true
    }

    enum class SchemaType(private val text: String) {
        DATA("data"), HTTPS("https"), HTTP("http");

        companion object {
            fun fromString(text: String?): SchemaType? {
                for (type in SchemaType.values()) {
                    if (type.text.equals(text, ignoreCase = true)) {
                        return type
                    }
                }
                return null
            }
        }
    }
}
