package com.test.onesignal

import android.os.Bundle
import com.onesignal.OneSignalPackagePrivateHelper
import com.onesignal.ShadowNotificationManagerCompat
import com.onesignal.ShadowOSUtils
import com.onesignal.ShadowOneSignalRestClient
import com.onesignal.ShadowPushRegistratorGPS
import com.onesignal.StaticResetHelper

object TestHelpers {

    @JvmStatic fun betweenTestsCleanup() {
        StaticResetHelper.restSetStaticFields()

        ShadowOneSignalRestClient.lastPost = null
        ShadowOneSignalRestClient.nextSuccessResponse = null
        ShadowOneSignalRestClient.failNext = false
        ShadowOneSignalRestClient.failNextPut = false
        ShadowOneSignalRestClient.failAll = false
        ShadowOneSignalRestClient.networkCallCount = 0

        ShadowPushRegistratorGPS.skipComplete = false
        ShadowPushRegistratorGPS.fail = false
        ShadowPushRegistratorGPS.lastProjectNumber = null

        ShadowNotificationManagerCompat.enabled = true

        ShadowOSUtils.subscribableStatus = 1

        // DB seems to be cleaned up on it's own.
        /*
      SQLiteDatabase writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      writableDb.delete(OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME, null, null);
      writableDb.close();
      */
    }

    @Throws(Exception::class)
    @JvmStatic fun threadAndTaskWait() {
        var createdNewThread: Boolean
        do {
            createdNewThread = false
            var joinedAThread: Boolean
            do {
                joinedAThread = false
                val threadSet = Thread.getAllStackTraces().keys

                for (thread in threadSet) {
                    if (thread.name.startsWith("OS_")) {
                        thread.join()
                        createdNewThread = true
                        joinedAThread = createdNewThread
                    }
                }
            } while (joinedAThread)

            var advancedRunnables = OneSignalPackagePrivateHelper.runAllNetworkRunnables()
            advancedRunnables = OneSignalPackagePrivateHelper.runFocusRunnables() || advancedRunnables

            if (advancedRunnables)
                createdNewThread = true
        } while (createdNewThread)
    }

    val notifDefaultMessage = "Robo test message"

    @JvmStatic val baseNotifBundle: Bundle
        get() = getBaseNotifBundle("UUID")

    @JvmStatic fun getBaseNotifBundle(id: String): Bundle {
        val bundle = Bundle()
        bundle.putString("alert", notifDefaultMessage)
        bundle.putString("custom", "{\"i\": \"$id\"}")

        return bundle
    }
}
