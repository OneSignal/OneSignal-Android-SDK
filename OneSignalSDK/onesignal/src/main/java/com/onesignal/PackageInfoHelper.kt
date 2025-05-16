package com.onesignal

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.DeadSystemException

data class GetPackageInfoResult(
    // Check this value first, if false ignore other properties
    //   - If false this means Android throw an error, so it's not possible
    //     to know if the app we are checking is even installed.
    val successful: Boolean,

    // Raw PackageInfo from Android API
    // NOTE: Ignore this value if successful == false
    // Will be null if package is not installed.
    val packageInfo: PackageInfo?,
)

class PackageInfoHelper {
    companion object {
        @TargetApi(24)
        fun getInfo(appContext: Context, packageName: String, flags: Int): GetPackageInfoResult {
            val packageManager = appContext.packageManager
            return try {
                GetPackageInfoResult(
                    true,
                    packageManager.getPackageInfo(
                        packageName,
                        flags,
                    ),
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Expected if package is not installed on the device.
                GetPackageInfoResult(true, null)
            } catch (e: RuntimeException) {
                // Android internally throws this via RemoteException.rethrowFromSystemServer()
                // so we must catch RuntimeException and check the cause.

                // Suppressing DeadSystemException as the app is already dying for
                // another reason and allowing this exception to bubble up would
                // create a red herring for app developers. We still re-throw
                // others, as we don't want to silently hide other issues.
                if (e.cause is DeadSystemException) {
                    GetPackageInfoResult(false, null)
                } else {
                    throw e
                }
            }
        }
    }
}
