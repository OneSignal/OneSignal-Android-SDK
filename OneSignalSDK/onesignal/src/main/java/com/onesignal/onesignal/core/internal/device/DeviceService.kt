package com.onesignal.onesignal.core.internal.device

import android.content.pm.PackageManager
import androidx.annotation.Keep
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationListener
import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.location.LocationCallback
import com.onesignal.onesignal.core.internal.application.IApplicationService

class DeviceService(private val _applicationService: IApplicationService) : IDeviceService {
    companion object {
        private const val HMS_CORE_SERVICES_PACKAGE = "com.huawei.hwid" // = HuaweiApiAvailability.SERVICES_PACKAGE
        private const val HMS_AVAILABLE_SUCCESSFUL = 0
        private const val DEVICE_TYPE_ANDROID = 1
        private const val DEVICE_TYPE_FIREOS = 2
        private const val DEVICE_TYPE_HUAWEI = 13
    }

    override val isAndroidDeviceType: Boolean
        get() = getDeviceType() == DEVICE_TYPE_ANDROID

    override val isFireOSDeviceType: Boolean
        get() = getDeviceType() == DEVICE_TYPE_FIREOS

    override val isHuaweiDeviceType: Boolean
        get() = getDeviceType() == DEVICE_TYPE_HUAWEI

    override val isGooglePlayServicesAvailable: Boolean
        get() = isAndroidDeviceType && hasGMSLocationLibrary()

    override val isHMSAvailable: Boolean
        get() = isHuaweiDeviceType && hasHMSLocationLibrary()

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
    override fun getDeviceType(): Int {
        if (supportsADM()) return DEVICE_TYPE_FIREOS
        if (supportsGooglePush()) return DEVICE_TYPE_ANDROID

        // Some Huawei devices have both FCM & HMS support, but prefer FCM (Google push) over HMS
        if (supportsHMS()) return DEVICE_TYPE_HUAWEI

        // Start - Fallback logic
        //    Libraries in the app (Google:FCM, HMS:PushKit) + Device may not have a valid combo
        // Example: App with only the FCM library in it and a Huawei device with only HMS Core
        if (isGMSInstalledAndEnabled()) return DEVICE_TYPE_ANDROID
        return if (isHMSCoreInstalledAndEnabledFallback()) DEVICE_TYPE_HUAWEI else DEVICE_TYPE_ANDROID

        // Last fallback
        // Fallback to device_type 1 (Android) if there are no supported push channels on the device
    }

    override val isGooglePlayStoreInstalled: Boolean
        get() {
            try {
                val pm: PackageManager = _applicationService.appContext!!.getPackageManager()
                val info = pm.getPackageInfo(
                    GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE,
                    PackageManager.GET_META_DATA
                )
                val label = info.applicationInfo.loadLabel(pm) as String
                return label != "Market"
            } catch (e: PackageManager.NameNotFoundException) {
                // Google Play Store might not be installed, ignore exception if so
            }
            return false
        }

    private fun supportsGooglePush(): Boolean {
        // 1. If app does not have the FCM library it won't support Google push
        return if (!hasFCMLibrary()) false else isGMSInstalledAndEnabled()

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
    override fun isGMSInstalledAndEnabled(): Boolean {
        return packageInstalledAndEnabled(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE)
    }

    private fun packageInstalledAndEnabled(packageName: String): Boolean {
        return try {
            val pm = _applicationService.appContext!!.packageManager
            val info = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            info.applicationInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun hasGMSLocationLibrary(): Boolean {
        return try {
            opaqueHasClass(LocationListener::class.java)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    private fun hasHMSLocationLibrary(): Boolean {
        return try {
            opaqueHasClass(LocationCallback::class.java)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    override fun hasFCMLibrary(): Boolean {
        return try {
            opaqueHasClass(FirebaseMessaging::class.java)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    private fun supportsHMS(): Boolean {
        // 1. App should have the HMSAvailability for best detection and must have PushKit libraries
        return if (!hasHMSAvailabilityLibrary() || !hasAllHMSLibrariesForPushKit()) false else isHMSCoreInstalledAndEnabled()

        // 2. Device must have HMS Core installed and enabled
    }

    private fun isHMSCoreInstalledAndEnabled(): Boolean {
        val availability = HuaweiApiAvailability.getInstance()
        return availability.isHuaweiMobileServicesAvailable(_applicationService.appContext) == HMS_AVAILABLE_SUCCESSFUL
    }

    override fun hasAllHMSLibrariesForPushKit(): Boolean {
        // NOTE: hasHMSAvailabilityLibrary technically is not required,
        //   just used as recommend way to detect if "HMS Core" app exists and is enabled
        return hasHMSAGConnectLibrary() && hasHMSPushKitLibrary()
    }

    private fun hasHMSAGConnectLibrary(): Boolean {
        return try {
            opaqueHasClass(AGConnectServicesConfig::class.java)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    private fun hasHMSPushKitLibrary(): Boolean {
        return try {
            opaqueHasClass(HmsInstanceId::class.java)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    private fun hasHMSAvailabilityLibrary(): Boolean {
        return try {
            opaqueHasClass(HuaweiApiAvailability::class.java)
        } catch (e: NoClassDefFoundError) {
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

    // Interim method that works around Proguard's overly aggressive assumenosideeffects which
    // ignores keep rules.
    // This is specifically designed to address Proguard removing catches for NoClassDefFoundError
    // when the config has "-assumenosideeffects" with
    // java.lang.Class.getName() & java.lang.Object.getClass().
    // This @Keep annotation is key so this method does not get removed / inlined.
    // Addresses issue https://github.com/OneSignal/OneSignal-Android-SDK/issues/1423
    @Keep
    private fun opaqueHasClass(_class: Class<*>): Boolean {
        return true
    }
}