package com.onesignal.core.internal.device.impl

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IFidEnv
import com.onesignal.debug.internal.logging.Logging
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

internal const val HTTP_FID_ENV_HEADER_KEY = "OneSignal-Fid-Env"

internal data class FidEnvSnapshot(
    val googleServices: Boolean,
    val agpVersion: String?,
    val fidFlag: Boolean,
    val defaultFirebaseApp: Boolean,
    val firebaseInitProvider: Boolean,
    val minSdk: Int?,
    val targetSdk: Int?,
    val senderMatch: Boolean?,
) {
    fun toHeaderValue(): String =
        listOf(
            "gs=${googleServices.toBit()}",
            "agp=${agpVersion.sanitized()}",
            "flag=${fidFlag.toBit()}",
            "def=${defaultFirebaseApp.toBit()}",
            "prov=${firebaseInitProvider.toBit()}",
            "min=${minSdk?.toString() ?: "-"}",
            "tgt=${targetSdk?.toString() ?: "-"}",
            "snd=${senderMatch.toBitOrDash()}",
        ).joinToString(";")
}

internal fun sanitizeToken(value: String): String =
    value.filter { it in TOKEN_CHARS }.take(MAX_TOKEN_CHARS).ifEmpty { "-" }

@Suppress("TooGenericExceptionCaught")
internal fun parseAgpVersion(propertiesText: String): String? {
    return try {
        val props = Properties()
        props.load(propertiesText.reader())
        props.getProperty("androidGradlePluginVersion")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

internal fun senderMatch(
    resourceSender: String?,
    dashboardSender: String?,
): Boolean? =
    when {
        dashboardSender.isNullOrBlank() -> null
        resourceSender.isNullOrBlank() -> false
        else -> resourceSender == dashboardSender
    }

internal class AndroidFidEnvReader(
    private val context: Context,
) {
    private val gcmSenderId: String? by lazy {
        AndroidUtils.getResourceString(context, GCM_SENDER_ID, null)
    }

    private val staticProbe: FidEnvSnapshot by lazy { collectStatic() }

    fun collect(dashboardSenderId: String?): FidEnvSnapshot =
        staticProbe.copy(
            defaultFirebaseApp = hasDefaultFirebaseApp(),
            senderMatch = senderMatch(gcmSenderId, dashboardSenderId),
        )

    private fun collectStatic(): FidEnvSnapshot {
        val googleAppId = AndroidUtils.getResourceString(context, GOOGLE_APP_ID, null)
        return FidEnvSnapshot(
            googleServices = !googleAppId.isNullOrBlank(),
            agpVersion = readApkEntry(AGP_METADATA_PATH)?.let { parseAgpVersion(it) },
            fidFlag = AndroidUtils.getManifestMetaBoolean(context, FID_FLAG),
            defaultFirebaseApp = false,
            firebaseInitProvider = hasFirebaseInitProvider(),
            minSdk = minSdk(),
            targetSdk = context.applicationInfo.targetSdkVersion,
            senderMatch = null,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun readApkEntry(path: String): String? {
        try {
            context.classLoader.getResourceAsStream(path)?.use { return it.bufferedReader().readText() }
        } catch (_: Exception) {
        }
        return readFromApkZip(path)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun readFromApkZip(path: String): String? {
        val sourceDir = context.applicationInfo.sourceDir ?: return null
        return try {
            readZipEntry(sourceDir, path)
        } catch (_: Exception) {
            null
        }
    }

    private fun readZipEntry(
        sourceDir: String,
        path: String,
    ): String? {
        ZipFile(sourceDir).use { zip ->
            val entry = zip.getEntry(path) ?: return null
            return zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
    }

    @Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")
    private fun hasDefaultFirebaseApp(): Boolean {
        return try {
            val clazz = Class.forName(FIREBASE_APP)
            val apps =
                clazz.getMethod("getApps", Context::class.java).invoke(null, context) as? List<*>
                    ?: return false
            val getName = clazz.getMethod("getName")
            apps.any { getName.invoke(it) == DEFAULT_APP_NAME }
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("TooGenericExceptionCaught", "DEPRECATION")
    private fun hasFirebaseInitProvider(): Boolean {
        return try {
            val pkg =
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PROVIDERS,
                )
            pkg.providers?.any { it.name == FIREBASE_INIT_PROVIDER } == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun minSdk(): Int? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.applicationInfo.minSdkVersion
        } else {
            null
        }
    }

    companion object {
        private const val GOOGLE_APP_ID = "google_app_id"
        private const val GCM_SENDER_ID = "gcm_defaultSenderId"
        private const val FID_FLAG = "firebase_messaging_installation_id_enabled"
        private const val FIREBASE_INIT_PROVIDER = "com.google.firebase.provider.FirebaseInitProvider"
        private const val AGP_METADATA_PATH = "META-INF/com/android/build/gradle/app-metadata.properties"
        private const val FIREBASE_APP = "com.google.firebase.FirebaseApp"
        private const val DEFAULT_APP_NAME = "[DEFAULT]"
    }
}

internal class FidEnvService(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
) : IFidEnv {
    private val reader by lazy { AndroidFidEnvReader(_applicationService.appContext) }
    private val logged = AtomicBoolean(false)

    @Suppress("TooGenericExceptionCaught")
    override fun headerValue(): String {
        return try {
            val model = _configModelStore.model
            val dashboardSender =
                if (model.isInitializedWithRemote) model.googleProjectNumber else null
            val value = reader.collect(dashboardSender).toHeaderValue()
            if (logged.compareAndSet(false, true)) {
                Logging.debug("HttpClient: $HTTP_FID_ENV_HEADER_KEY $value")
            }
            value
        } catch (t: Throwable) {
            Logging.debug("HttpClient: $HTTP_FID_ENV_HEADER_KEY probe failed", t)
            ""
        }
    }
}

private const val MAX_TOKEN_CHARS = 32
private const val TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._+-"

private fun Boolean.toBit(): String = if (this) "1" else "0"

private fun Boolean?.toBitOrDash(): String = this?.toBit() ?: "-"

private fun String?.sanitized(): String = this?.let { sanitizeToken(it) } ?: "-"
