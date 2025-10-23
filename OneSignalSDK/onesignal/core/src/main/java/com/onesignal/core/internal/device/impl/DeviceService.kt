package com.onesignal.core.internal.device.impl

import android.content.Context
import android.content.pm.PackageManager
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IDeviceService

internal class DeviceService(private val _applicationService: IApplicationService) :
    IDeviceService {
    companion object {
        private const val HMS_CORE_SERVICES_PACKAGE = "com.huawei.hwid" // = HuaweiApiAvailability.SERVICES_PACKAGE
        private const val GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms" // = GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE
        private const val PREFER_HMS_METADATA_NAME = "com.onesignal.preferHMS"
        private const val HMS_AVAILABLE_SUCCESSFUL = 0
    }

    override val isAndroidDeviceType: Boolean
        get() = deviceType == IDeviceService.DeviceType.Android

    override val isFireOSDeviceType: Boolean
        get() = deviceType == IDeviceService.DeviceType.Fire

    override val isHuaweiDeviceType: Boolean
        get() = deviceType == IDeviceService.DeviceType.Huawei

    /**
     * Device type is determined by the push channel(s) the device supports.
     * Since a player_id can only support one we attempt to select the one that is native to the device
     * 1. ADM - This can NOT be side loaded on the device, if it has it then it is native
     * 2. FCM - If this is available then most likely native.
     * - Prefer over HMS as FCM has more features on older Huawei devices.
     * 3. HMS - Huawei devices only.
     * - New 2020 Huawei devices don't have FCM support, HMS only
     * - Technically works for non-Huawei devices if you side load the Huawei AppGallery.
     * i. "Notification Message" pushes are very bare bones. (title + body)
     * ii. "Data Message" works as expected.
     */
    override val deviceType: IDeviceService.DeviceType
        get() {
            if (supportsADM()) return IDeviceService.DeviceType.Fire

            val supportsHMS = supportsHMS
            val supportsFCM = supportsGooglePush()

            if (supportsFCM && supportsHMS) {
                val context = _applicationService.appContext
                val preferHMS = AndroidUtils.getManifestMetaBoolean(context, PREFER_HMS_METADATA_NAME)
                return if (preferHMS) IDeviceService.DeviceType.Huawei else IDeviceService.DeviceType.Android
            }

            if (supportsFCM) return IDeviceService.DeviceType.Android

            // Some Huawei devices have both FCM & HMS support, but prefer FCM (Google push) over HMS
            if (supportsHMS) return IDeviceService.DeviceType.Huawei

            // Start - Fallback logic
            //    Libraries in the app (Google:FCM, HMS:PushKit) + Device may not have a valid combo
            // Example: App with only the FCM library in it and a Huawei device with only HMS Core
            if (isGMSInstalledAndEnabled) return IDeviceService.DeviceType.Android
            return if (isHMSCoreInstalledAndEnabledFallback()) IDeviceService.DeviceType.Huawei else IDeviceService.DeviceType.Android

            // Last fallback
            // Fallback to device_type 1 (Android) if there are no supported push channels on the device
        }

    override val jetpackLibraryStatus: IDeviceService.JetpackLibraryStatus
        get() {
            val hasNotificationManagerCompat: Boolean = AndroidUtils.hasNotificationManagerCompat()
            if (!hasNotificationManagerCompat) {
                return IDeviceService.JetpackLibraryStatus.MISSING
            }

            return IDeviceService.JetpackLibraryStatus.OK
        }

    override fun supportsGooglePush(): Boolean {
        // 1. If app does not have the FCM library it won't support Google push
        return if (!hasFCMLibrary) false else isGMSInstalledAndEnabled

        // 2. "Google Play services" must be installed and enabled
    }

    // HuaweiApiAvailability is the recommend way to detect if "HMS Core" is available but this fallback
    //   works even if the app developer doesn't include any HMS libraries in their app.
    private fun isHMSCoreInstalledAndEnabledFallback(): Boolean {
        return packageInstalledAndEnabled(HMS_CORE_SERVICES_PACKAGE)
    }

    // TODO: Maybe able to switch to GoogleApiAvailability.isGooglePlayServicesAvailable to simplify
    // However before doing so we need to test with an old version of the "Google Play services"
    //   on the device to make sure it would still be counted as "SUCCESS".
    // Or if we get back "SERVICE_VERSION_UPDATE_REQUIRED" then we may want to count that as successful too.
    override val isGMSInstalledAndEnabled: Boolean
        get() {
            return packageInstalledAndEnabled(GOOGLE_PLAY_SERVICES_PACKAGE)
        }

    private fun packageInstalledAndEnabled(packageName: String): Boolean {
        return try {
            val pm = _applicationService.appContext.packageManager
            val info = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            info.applicationInfo?.enabled ?: false
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override val hasFCMLibrary: Boolean
        get() {
            return try {
                Class.forName("com.google.firebase.messaging.FirebaseMessaging")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }

    override val supportsHMS: Boolean
        get() {
            // 1. App should have the HMSAvailability for best detection and must have PushKit libraries
            return if (!hasHMSAvailabilityLibrary() || !hasAllHMSLibrariesForPushKit) false else isHMSCoreInstalledAndEnabled()

            // 2. Device must have HMS Core installed and enabled
        }

    private fun isHMSCoreInstalledAndEnabled(): Boolean {
        // we use reflection so we don't depend on the library directly.
        return try {
            val clazz = Class.forName("com.huawei.hms.api.HuaweiApiAvailability")
            val newInstanceMethod = clazz.getMethod("getInstance")
            val isHuaweiMobileServicesAvailableMethod =
                clazz.getMethod(
                    "isHuaweiMobileServicesAvailable",
                    android.content.Context::class.java,
                )
            val availabilityInstance = newInstanceMethod.invoke(null)

            val result = isHuaweiMobileServicesAvailableMethod.invoke(availabilityInstance, _applicationService.appContext) as Int

            return result == HMS_AVAILABLE_SUCCESSFUL
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override val hasAllHMSLibrariesForPushKit: Boolean
        get() {
            // NOTE: hasHMSAvailabilityLibrary technically is not required,
            //   just used as recommend way to detect if "HMS Core" app exists and is enabled
            return hasHMSAGConnectLibrary() && hasHMSPushKitLibrary()
        }

    private fun hasHMSAGConnectLibrary(): Boolean {
        return try {
            Class.forName("com.huawei.agconnect.config.AGConnectServicesConfig")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun hasHMSPushKitLibrary(): Boolean {
        return try {
            Class.forName("com.huawei.hms.aaid.HmsInstanceId")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun hasHMSAvailabilityLibrary(): Boolean {
        return try {
            Class.forName("com.huawei.hms.api.HuaweiApiAvailability")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun supportsADM(): Boolean {
        return try {
            // Class only available on the FireOS and only when the following is in the AndroidManifest.xml.
            // <amazon:enable-feature android:name="com.amazon.device.messaging" android:required="false"/>
            Class.forName("com.amazon.device.messaging.ADM")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}
