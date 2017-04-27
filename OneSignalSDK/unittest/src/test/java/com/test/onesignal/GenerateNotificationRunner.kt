/**
 * Modified MIT License

 * Copyright 2016 OneSignal

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.

 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.test.onesignal

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.database.Observable
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle

import com.onesignal.BuildConfig
import com.onesignal.GcmBroadcastReceiver
import com.onesignal.GcmIntentService
import com.onesignal.NotificationExtenderService
import com.onesignal.OSNotification
import com.onesignal.OSNotificationOpenResult
import com.onesignal.OSNotificationPayload
import com.onesignal.OSNotificationReceivedResult
import com.onesignal.OneSignal
import com.onesignal.OneSignalDbHelper
import com.onesignal.OneSignalPackagePrivateHelper
import com.onesignal.ShadowBadgeCountUpdater
import com.onesignal.ShadowNotificationManagerCompat
import com.onesignal.ShadowNotificationRestorer
import com.onesignal.ShadowOneSignal
import com.onesignal.ShadowOneSignalRestClient
import com.onesignal.ShadowRoboNotificationManager
import com.onesignal.ShadowRoboNotificationManager.PostedNotification
import com.onesignal.StaticResetHelper
import com.onesignal.example.BlankActivity
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable
import com.onesignal.OneSignalPackagePrivateHelper.NotificationRestorer

import junit.framework.Assert

import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ActivityController
import org.robolectric.util.ServiceController

import com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService
import com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap
import com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext
import com.onesignal.OneSignalPackagePrivateHelper.NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved
import com.onesignal.OneSignalPackagePrivateHelper.createInternalPayloadBundle
import com.test.onesignal.TestHelpers.baseNotifBundle
import com.test.onesignal.TestHelpers.getBaseNotifBundle
import com.test.onesignal.TestHelpers.notifDefaultMessage
import com.test.onesignal.TestHelpers.threadAndTaskWait
import org.robolectric.Shadows.shadowOf
import kotlin.reflect.KClass

@Config(packageName = "com.onesignal.example",
        constants = BuildConfig::class,
        instrumentedPackages = arrayOf("com.onesignal"),
        shadows = arrayOf(ShadowRoboNotificationManager::class,
                ShadowOneSignalRestClient::class,
                ShadowBadgeCountUpdater::class,
                ShadowNotificationManagerCompat::class),
        sdk = intArrayOf(21))
@RunWith(RobolectricTestRunner::class)
class GenerateNotificationRunner {

    private var blankActivity: Activity? = null

    @Before // Before each test
    @Throws(Exception::class)
    fun beforeEachTest() {
        // Robolectric mocks System.currentTimeMillis() to 0, we need the current real time to match our SQL records.
        ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis())

        blankActivityController = Robolectric.buildActivity(BlankActivity::class.java).create()
        blankActivity = blankActivityController!!.get()
        blankActivity!!.applicationInfo.name = "UnitTestApp"

        overrideNotificationId = -1

        TestHelpers.betweenTestsCleanup()

        val notificationManager = blankActivity!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    private fun createOpenIntent(bundle: Bundle): Intent {
        return createOpenIntent(ShadowRoboNotificationManager.lastNotifId, bundle)
    }

    @Test
    @Throws(Exception::class)
    fun shouldSetTitleCorrectly() {
        // Should use app's Title by default
        var bundle = baseNotifBundle
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        Assert.assertEquals("UnitTestApp", ShadowRoboNotificationManager.getLastShadowNotif().contentTitle)
        Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount)

        // Should allow title from GCM payload.
        bundle = getBaseNotifBundle("UUID2")
        bundle.putString("title", "title123")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        Assert.assertEquals("title123", ShadowRoboNotificationManager.getLastShadowNotif().contentTitle)
        Assert.assertEquals(2, ShadowBadgeCountUpdater.lastCount)
    }

    @Test
    @Throws(Exception::class)
    fun shouldProcessRestore() {
        val bundle = createInternalPayloadBundle(baseNotifBundle)
        bundle.putInt("android_notif_id", 0)
        bundle.putBoolean("restoring", true)

        NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap(blankActivity, bundle, null)
        Assert.assertEquals("UnitTestApp", ShadowRoboNotificationManager.getLastShadowNotif().contentTitle)
    }

    @Test
    @Throws(Exception::class)
    fun shouldContainPayloadWhenOldSummaryNotificationIsOpened() {
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba") { result -> lastOpenResult = result }

        // Display 2 notifications that will be grouped together.
        var bundle = getBaseNotifBundle("UUID1")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        bundle = getBaseNotifBundle("UUID2")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        // Go forward 4 weeks
        ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 2419202L * 1000L)

        // Display a 3 normal notification.
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle("UUID3"), null)


        val postedNotifs = ShadowRoboNotificationManager.notifications

        // Open the summary notification
        val postedNotifsIterator = postedNotifs.entries.iterator()
        val postedNotification = postedNotifsIterator.next().value
        val intent = Shadows.shadowOf(postedNotification.notif.contentIntent).savedIntent
        NotificationOpenedProcessor_processFromContext(blankActivity, intent)

        // Make sure we get a payload when it is opened.
        Assert.assertNotNull(lastOpenResult!!.notification.payload)
    }

    @Test
    @Config(shadows = arrayOf(ShadowNotificationRestorer::class))
    @Throws(Exception::class)
    fun shouldCancelNotificationAndUpdateSummary() {
        // Setup - Init
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba")
        threadAndTaskWait()

        // Setup - Display 3 notifications that will be grouped together.
        var bundle = getBaseNotifBundle("UUID1")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        bundle = getBaseNotifBundle("UUID2")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        bundle = getBaseNotifBundle("UUID3")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)


        var postedNotifs: Map<Int, PostedNotification> = ShadowRoboNotificationManager.notifications
        var postedNotifsIterator = postedNotifs.entries.iterator()

        // Test - 3 notifis + 1 summary
        Assert.assertEquals(4, postedNotifs.size)


        // Test - First notification should be the summary
        var postedSummaryNotification = postedNotifsIterator.next().value
        Assert.assertEquals("3 new messages", postedSummaryNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)

        // Setup - Let's cancel a child notification.
        var postedNotification = postedNotifsIterator.next().value
        OneSignal.cancelNotification(postedNotification.id)

        // Test - It should update summary text to say 2 notifications
        postedNotifs = ShadowRoboNotificationManager.notifications
        Assert.assertEquals(3, postedNotifs.size)       // 2 notifis + 1 summary
        postedNotifsIterator = postedNotifs.entries.iterator()
        postedSummaryNotification = postedNotifsIterator.next().value
        Assert.assertEquals("2 new messages", postedSummaryNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)

        // Setup - Let's cancel a 2nd child notification.
        postedNotification = postedNotifsIterator.next().value
        OneSignal.cancelNotification(postedNotification.id)

        // Test - It should update summary notification to be the text of the last remaining one.
        postedNotifs = ShadowRoboNotificationManager.notifications
        Assert.assertEquals(2, postedNotifs.size) // 1 notifis + 1 summary
        postedNotifsIterator = postedNotifs.entries.iterator()
        postedSummaryNotification = postedNotifsIterator.next().value
        Assert.assertEquals(notifDefaultMessage, postedSummaryNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)

        // Test - Let's make sure we will have our last notification too
        postedNotification = postedNotifsIterator.next().value
        Assert.assertEquals(notifDefaultMessage, postedNotification.shadow.contentText)

        // Setup - Let's cancel our 3rd and last child notification.
        OneSignal.cancelNotification(postedNotification.id)

        // Test - No more notifications! :)
        postedNotifs = ShadowRoboNotificationManager.notifications
        Assert.assertEquals(0, postedNotifs.size)
    }

    @Test
    fun shouldUpdateBadgesWhenDismissingNotification() {
        val bundle = baseNotifBundle
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        Assert.assertEquals(notifDefaultMessage, ShadowRoboNotificationManager.getLastShadowNotif().contentText)
        Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount)

        val postedNotifs = ShadowRoboNotificationManager.notifications
        val postedNotifsIterator = postedNotifs.entries.iterator()
        val postedNotification = postedNotifsIterator.next().value
        val intent = Shadows.shadowOf(postedNotification.notif.deleteIntent).savedIntent
        NotificationOpenedProcessor_processFromContext(blankActivity, intent)

        Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount)
    }

    @Test
    @Throws(Exception::class)
    fun shouldNotSetBadgesWhenNotificationPermissionIsDisabled() {
        ShadowNotificationManagerCompat.enabled = false
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba")
        threadAndTaskWait()

        val bundle = baseNotifBundle
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount)
    }

    @Test
    @Throws(Exception::class)
    fun shouldUpdateNormalNotificationDisplayWhenReplacingANotification() {
        // Setup - init
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba")
        threadAndTaskWait()

        // Setup - Display 2 notifications with the same group and collapse_id
        var bundle = getBaseNotifBundle("UUID1")
        bundle.putString("grp", "test1")
        bundle.putString("collapse_key", "1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        bundle = getBaseNotifBundle("UUID2")
        bundle.putString("grp", "test1")
        bundle.putString("collapse_key", "1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)


        // Test - Summary created and sub notification. Summary will look the same as the normal notification.
        var postedNotifs: Map<Int, PostedNotification> = ShadowRoboNotificationManager.notifications
        var postedNotifsIterator = postedNotifs.entries.iterator()
        Assert.assertEquals(2, postedNotifs.size)
        val postedSummaryNotification = postedNotifsIterator.next().value
        Assert.assertEquals(notifDefaultMessage, postedSummaryNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)

        val lastNotifId = postedNotifsIterator.next().value.id
        ShadowRoboNotificationManager.notifications.clear()

        // Setup - Restore
        bundle = getBaseNotifBundle("UUID2")
        bundle.putString("grp", "test1")
        bundle = createInternalPayloadBundle(bundle)
        bundle.putInt("android_notif_id", lastNotifId)
        bundle.putBoolean("restoring", true)
        NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap(blankActivity, bundle, null)

        // Test - Restored notifications display exactly the same as they did when recevied.
        postedNotifs = ShadowRoboNotificationManager.notifications
        postedNotifsIterator = postedNotifs.entries.iterator()
        // Test - 1 notifi + 1 summary
        Assert.assertEquals(2, postedNotifs.size)
        Assert.assertEquals(notifDefaultMessage, postedSummaryNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)
    }


    @Test
    @Throws(Exception::class)
    fun shouldHandleBasicNotifications() {
        // Make sure the notification got posted and the content is correct.
        var bundle = baseNotifBundle
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        Assert.assertEquals(notifDefaultMessage, ShadowRoboNotificationManager.getLastShadowNotif().contentText)
        Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount)

        // Should have 1 DB record with the correct time stamp
        var readableDb = OneSignalDbHelper.getInstance(blankActivity).readableDatabase
        var cursor = readableDb.query(NotificationTable.TABLE_NAME, arrayOf("created_time"), null, null, null, null, null)
        Assert.assertEquals(1, cursor.count)
        // Time stamp should be set and within a small range.
        val currentTime = System.currentTimeMillis() / 1000
        cursor.moveToFirst()
        Assert.assertTrue(cursor.getLong(0) > currentTime - 2 && cursor.getLong(0) <= currentTime)
        cursor.close()

        // Should get marked as opened.
        NotificationOpenedProcessor_processFromContext(blankActivity, createOpenIntent(bundle))
        cursor = readableDb.query(NotificationTable.TABLE_NAME, arrayOf("opened", "android_notification_id"), null, null, null, null, null)
        cursor.moveToFirst()
        Assert.assertEquals(1, cursor.getInt(0))
        Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount)
        cursor.close()

        // Should not display a duplicate notification, count should still be 1
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        readableDb = OneSignalDbHelper.getInstance(blankActivity).readableDatabase
        cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null)
        Assert.assertEquals(1, cursor.count)
        Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount)
        cursor.close()

        // Display a second notification
        bundle = getBaseNotifBundle("UUID2")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        // Go forward 4 weeks
        // Note: Does not effect the SQL function strftime
        ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 2419201L * 1000L)

        // Display a 3rd notification
        // Should of been added for a total of 2 records now.
        // First opened should of been cleaned up, 1 week old non opened notification should stay, and one new record.
        bundle = getBaseNotifBundle("UUID3")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        readableDb = OneSignalDbHelper.getInstance(blankActivity).readableDatabase
        cursor = readableDb.query(NotificationTable.TABLE_NAME, arrayOf("android_notification_id", "created_time"), null, null, null, null, null)

        Assert.assertEquals(1, cursor.count)
        Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount)

        cursor.close()
    }

    @Test
    @Throws(Exception::class)
    fun shouldRestoreNotifications() {
        NotificationRestorer.restore(blankActivity)
        NotificationRestorer.restored = false

        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, baseNotifBundle, null)

        NotificationRestorer.restore(blankActivity)
        NotificationRestorer.restored = false
        val intent = Shadows.shadowOf(blankActivity).nextStartedService
        Assert.assertEquals(GcmIntentService::class.java.name, intent.component.className)

        // Go forward 1 week
        // Note: Does not effect the SQL function strftime
        ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 604801L * 1000L)

        // Restorer should not fire service since the notification is over 1 week old.
        NotificationRestorer.restore(blankActivity)
        NotificationRestorer.restored = false
        Assert.assertNull(Shadows.shadowOf(blankActivity).nextStartedService)
    }

    @Test
    @Throws(Exception::class)
    fun shouldGenerate2BasicGroupNotifications() {
        // Make sure the notification got posted and the content is correct.
        var bundle = baseNotifBundle
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        var postedNotifs: Map<Int, PostedNotification> = ShadowRoboNotificationManager.notifications
        Assert.assertEquals(2, postedNotifs.size)

        // Test summary notification
        var postedNotifsIterator = postedNotifs.entries.iterator()
        var postedNotification = postedNotifsIterator.next().value

        Assert.assertEquals(notifDefaultMessage, postedNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)

        // Test Android Wear notification
        postedNotification = postedNotifsIterator.next().value
        Assert.assertEquals(notifDefaultMessage, postedNotification.shadow.contentText)
        Assert.assertEquals(0, postedNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)
        // Badge count should only be one as only one notification is visible in the notification area.
        Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount)


        // Should be 2 DB entries (summary and individual)
        var readableDb = OneSignalDbHelper.getInstance(blankActivity).readableDatabase
        var cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null)
        Assert.assertEquals(2, cursor.count)
        cursor.close()


        // Add another notification to the group.
        ShadowRoboNotificationManager.notifications.clear()
        bundle = Bundle()
        bundle.putString("alert", "Notif test 2")
        bundle.putString("custom", "{\"i\": \"UUID2\"}")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        postedNotifs = ShadowRoboNotificationManager.notifications
        Assert.assertEquals(2, postedNotifs.size)
        Assert.assertEquals(2, ShadowBadgeCountUpdater.lastCount)

        postedNotifsIterator = postedNotifs.entries.iterator()
        postedNotification = postedNotifsIterator.next().value
        Assert.assertEquals("2 new messages", postedNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)

        // Test Android Wear notification
        postedNotification = postedNotifsIterator.next().value
        Assert.assertEquals("Notif test 2", postedNotification.shadow.contentText)
        Assert.assertEquals(0, postedNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)


        // Should be 3 DB entries (summary and 2 individual)
        readableDb = OneSignalDbHelper.getInstance(blankActivity).readableDatabase
        cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null)
        Assert.assertEquals(3, cursor.count)


        // Open summary notification
        postedNotifsIterator = postedNotifs.entries.iterator()
        postedNotification = postedNotifsIterator.next().value
        val intent = createOpenIntent(postedNotification.id, bundle).putExtra("summary", "test1")
        NotificationOpenedProcessor_processFromContext(blankActivity, intent)
        Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount)
        // 2 open calls should fire.
        Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount)
        ShadowRoboNotificationManager.notifications.clear()

        // Send 3rd notification
        bundle = Bundle()
        bundle.putString("alert", "Notif test 3")
        bundle.putString("custom", "{\"i\": \"UUID3\"}")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        postedNotifsIterator = postedNotifs.entries.iterator()
        postedNotification = postedNotifsIterator.next().value
        Assert.assertEquals("Notif test 3", postedNotification.shadow.contentText)
        Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.flags and Notification.FLAG_GROUP_SUMMARY)
        Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount)
        cursor.close()
    }

    @Test
    @Throws(Exception::class)
    fun shouldHandleOpeningInAppAlertWithGroupKeySet() {
        val writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).writableDatabase
        NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, writableDb, "some_group", false)
    }

    @Test
    @Throws(Exception::class)
    fun shouldNotDisplaySummaryWhenDismissingAnInAppAlertIfOneDidntAlreadyExist() {
        // Setup - init
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.InAppAlert)
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba")
        threadAndTaskWait()

        // Setup1 - Display a notification with a group set
        var bundle = getBaseNotifBundle("UUID1")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        // Test1 - Manually trigger a refresh on grouped notification.
        var writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).writableDatabase
        NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, writableDb, "test1", false)
        Assert.assertEquals(0, ShadowRoboNotificationManager.notifications.size)


        // Setup2 - Display a 2nd notification with the same group key
        bundle = getBaseNotifBundle("UUID2")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        // Test2 - Manually trigger a refresh on grouped notification.
        writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).writableDatabase
        NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, writableDb, "test1", false)
        Assert.assertEquals(0, ShadowRoboNotificationManager.notifications.size)
    }


    @Test
    @Throws(Exception::class)
    fun shouldCorrectlyDisplaySummaryWithMixedInAppAlertsAndNotifications() {
        // Setup - init
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.InAppAlert)
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba")
        threadAndTaskWait()

        // Setup - Display a notification with a group set
        var bundle = getBaseNotifBundle("UUID1")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        Assert.assertEquals(0, ShadowRoboNotificationManager.notifications.size)

        // Setup - Background app
        blankActivityController!!.pause()
        threadAndTaskWait()

        // Setup - Send 2 more notifications with the same group
        bundle = getBaseNotifBundle("UUID2")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)
        bundle = getBaseNotifBundle("UUID3")
        bundle.putString("grp", "test1")
        NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null)

        // Test - equals 3 - Should be 2 notifications + 1 summary.
        //         Alert should stay as an in-app alert.
        Assert.assertEquals(3, ShadowRoboNotificationManager.notifications.size)
    }


    @Test
    @Throws(Exception::class)
    fun shouldSetButtonsCorrectly() {
        val intentGcm = Intent()
        intentGcm.action = "com.google.android.c2dm.intent.RECEIVE"
        intentGcm.putExtra("message_type", "gcm")
        val bundle = baseNotifBundle
        bundle.putString("o", "[{\"n\": \"text1\", \"i\": \"id1\"}]")
        intentGcm.putExtras(bundle)

        val gcmBroadcastReceiver = GcmBroadcastReceiver()
        try {
            gcmBroadcastReceiver.onReceive(blankActivity, intentGcm)
        } // setResultCode throws this error due to onReceive not designed to be called manually.
        catch (e: java.lang.IllegalStateException) {
        }

        val intent = Shadows.shadowOf(blankActivity).nextStartedService
        Assert.assertEquals("com.onesignal.GcmIntentService", intent.component.className)

        val jsonPayload = JSONObject(intent.getStringExtra("json_payload"))

        Assert.assertEquals(null, jsonPayload.optString("o", null))
        val customJson = JSONObject(jsonPayload.optString("custom"))
        val additionalData = JSONObject(customJson.getString("a"))
        Assert.assertEquals("id1", additionalData.getJSONArray("actionButtons").getJSONObject(0).getString("id"))
    }

    private var lastNotificationReceived: OSNotification? = null
    @Test
    @Throws(Exception::class)
    fun shouldStillFireReceivedHandlerWhenNotificationExtenderServiceIsUsed() {
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.None)
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", null, OneSignal.NotificationReceivedHandler { notification -> lastNotificationReceived = notification })
        threadAndTaskWait()

        startNotificationExtender(createInternalPayloadBundle(baseNotifBundle),
                NotificationExtenderServiceTestReturnFalse::class as KClass<Service>)

        Assert.assertNotNull(lastNotificationReceived)
    }

    @Test
    @Throws(Exception::class)
    fun notificationExtenderServiceOverrideShouldOverrideAndroidNotificationId() {
        overrideNotificationId = 1

        startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle("NewUUID1")),
                NotificationExtenderServiceTest::class as KClass<Service>)
        startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle("NewUUID2")),
                NotificationExtenderServiceTest::class as KClass<Service>)
        Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount)
    }


    private fun startNotificationExtender(bundlePayload: Bundle, serviceClass: KClass<Service>): NotificationExtenderServiceTest {
        val controller = Robolectric.buildService(serviceClass.java)
        val service = controller.attach().create().get()
        val testIntent = Intent(RuntimeEnvironment.application, NotificationExtenderServiceTestReturnFalse::class.java)
        testIntent.putExtras(bundlePayload)
        controller.withIntent(testIntent).startCommand(0, 0)

        return service as NotificationExtenderServiceTest
    }

    @Test
    @Config(shadows = arrayOf(ShadowOneSignal::class))
    @Throws(Exception::class)
    fun shouldFireNotificationExtenderService() {
        // Test that GCM receiver starts the NotificationExtenderServiceTest when it is in the AndroidManifest.xml
        val bundle = baseNotifBundle

        val serviceIntent = Intent()
        serviceIntent.`package` = "com.onesignal.example"
        serviceIntent.action = "com.onesignal.NotificationExtender"
        val resolveInfo = ResolveInfo()
        resolveInfo.serviceInfo = ServiceInfo()
        resolveInfo.serviceInfo.name = "com.onesignal.example.NotificationExtenderServiceTest"
        RuntimeEnvironment.getRobolectricPackageManager().addResolveInfoForIntent(serviceIntent, resolveInfo)

        val ret = OneSignalPackagePrivateHelper.GcmBroadcastReceiver_processBundle(blankActivity, bundle)
        Assert.assertEquals(true, ret)

        val intent = Shadows.shadowOf(blankActivity).nextStartedService
        Assert.assertEquals("com.onesignal.NotificationExtender", intent.action)


        // Test that all options are set.
        val service = startNotificationExtender(createInternalPayloadBundle(bundleWithAllOptionsSet),
                NotificationExtenderServiceTest::class as KClass<Service>)

        val notificationReceived = service.notification
        val notificationPayload = notificationReceived.payload
        Assert.assertEquals("Test H", notificationPayload.title)
        Assert.assertEquals("Test B", notificationPayload.body)
        Assert.assertEquals("9764eaeb-10ce-45b1-a66d-8f95938aaa51", notificationPayload.notificationID)

        Assert.assertEquals(0, notificationPayload.lockScreenVisibility)
        Assert.assertEquals("FF0000FF", notificationPayload.smallIconAccentColor)
        Assert.assertEquals("703322744261", notificationPayload.fromProjectNumber)
        Assert.assertEquals("FFFFFF00", notificationPayload.ledColor)
        Assert.assertEquals("big_picture", notificationPayload.bigPicture)
        Assert.assertEquals("large_icon", notificationPayload.largeIcon)
        Assert.assertEquals("small_icon", notificationPayload.smallIcon)
        Assert.assertEquals("test_sound", notificationPayload.sound)
        Assert.assertEquals("You test $[notif_count] MSGs!", notificationPayload.groupMessage)
        Assert.assertEquals("http://google.com", notificationPayload.launchURL)
        Assert.assertEquals(10, notificationPayload.priority)
        Assert.assertEquals("a_key", notificationPayload.collapseId)

        Assert.assertEquals("id1", notificationPayload.actionButtons[0].id)
        Assert.assertEquals("button1", notificationPayload.actionButtons[0].text)
        Assert.assertEquals("ic_menu_share", notificationPayload.actionButtons[0].icon)
        Assert.assertEquals("id2", notificationPayload.actionButtons[1].id)
        Assert.assertEquals("button2", notificationPayload.actionButtons[1].text)
        Assert.assertEquals("ic_menu_send", notificationPayload.actionButtons[1].icon)

        Assert.assertEquals("test_image_url", notificationPayload.backgroundImageLayout.image)
        Assert.assertEquals("FF000000", notificationPayload.backgroundImageLayout.titleTextColor)
        Assert.assertEquals("FFFFFFFF", notificationPayload.backgroundImageLayout.bodyTextColor)

        val additionalData = notificationPayload.additionalData
        Assert.assertEquals("myValue", additionalData.getString("myKey"))
        Assert.assertEquals("nValue", additionalData.getJSONObject("nested").getString("nKey"))

        Assert.assertNotSame(-1, service.notificationId)


        // Test a basic notification without anything special.
        startNotificationExtender(createInternalPayloadBundle(baseNotifBundle), NotificationExtenderServiceTest::class as KClass<Service>)
        Assert.assertFalse(ShadowOneSignal.messages.contains("Error assigning"))

        // Test that a notification is still displayed if the developer's code in onNotificationProcessing throws an Exception.
        NotificationExtenderServiceTest.throwInAppCode = true
        startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle("NewUUID1")), NotificationExtenderServiceTest::class as KClass<Service>)

        Assert.assertTrue(ShadowOneSignal.messages.contains("onNotificationProcessing throw an exception"))
        val postedNotifs = ShadowRoboNotificationManager.notifications
        Assert.assertEquals(3, postedNotifs.size)
    }

    open class NotificationExtenderServiceTest : NotificationExtenderService() {
        lateinit var notification: OSNotificationReceivedResult
        var notificationId = -1

        // Override onStart to manually call onHandleIntent on the main thread.
        override fun onStart(intent: Intent?, startId: Int) {
            onHandleIntent(intent)
            stopSelf(startId)
        }

        override fun onNotificationProcessing(notification: OSNotificationReceivedResult): Boolean {
            if (throwInAppCode)
                throw NullPointerException()

            this.notification = notification

            val overrideSettings = NotificationExtenderService.OverrideSettings()
            if (overrideNotificationId != -1)
                overrideSettings.androidNotificationId = overrideNotificationId

            notificationId = displayNotification(overrideSettings)!!.androidNotificationId

            return true
        }

        companion object {
            var throwInAppCode: Boolean = false
        }
    }

    class NotificationExtenderServiceTestReturnFalse : NotificationExtenderServiceTest() {
        override fun onNotificationProcessing(notification: OSNotificationReceivedResult): Boolean {
            return false
        }
    }

    companion object {
        private var blankActivityController: ActivityController<BlankActivity>? = null

        @BeforeClass // Runs only once, before any tests
        @Throws(Exception::class)
        @JvmStatic fun setUpClass() {
            ShadowLog.stream = System.out
            StaticResetHelper.saveStaticValues()
        }

        @AfterClass
        @JvmStatic fun afterEverything() {
            StaticResetHelper.restSetStaticFields()
        }

        private fun createOpenIntent(notifId: Int, bundle: Bundle): Intent {
            return Intent()
                    .putExtra("notificationId", notifId)
                    .putExtra("onesignal_data", OneSignalPackagePrivateHelper.bundleAsJSONObject(bundle).toString())
        }


        private var lastOpenResult: OSNotificationOpenResult? = null

        private // GCM sets this to 'do_not_collapse' when not set.
        val bundleWithAllOptionsSet: Bundle
            get() {
                val bundle = Bundle()

                bundle.putString("title", "Test H")
                bundle.putString("alert", "Test B")
                bundle.putString("bgn", "1")
                bundle.putString("vis", "0")
                bundle.putString("bgac", "FF0000FF")
                bundle.putString("from", "703322744261")
                bundle.putString("ledc", "FFFFFF00")
                bundle.putString("bicon", "big_picture")
                bundle.putString("licon", "large_icon")
                bundle.putString("sicon", "small_icon")
                bundle.putString("sound", "test_sound")
                bundle.putString("grp_msg", "You test $[notif_count] MSGs!")
                bundle.putString("collapse_key", "a_key")
                bundle.putString("bg_img", "{\"img\": \"test_image_url\"," +
                        "\"tc\": \"FF000000\"," +
                        "\"bc\": \"FFFFFFFF\"}")

                bundle.putInt("pri", 10)
                bundle.putString("custom",
                        "{\"a\": {" +
                                "        \"myKey\": \"myValue\"," +
                                "        \"nested\": {\"nKey\": \"nValue\"}," +
                                "        \"actionButtons\": [{\"id\": \"id1\", \"text\": \"button1\", \"icon\": \"ic_menu_share\"}," +
                                "                            {\"id\": \"id2\", \"text\": \"button2\", \"icon\": \"ic_menu_send\"}" +
                                "        ]," +
                                "         \"actionSelected\": \"__DEFAULT__\"" +
                                "      }," +
                                "\"u\":\"http://google.com\"," +
                                "\"i\":\"9764eaeb-10ce-45b1-a66d-8f95938aaa51\"" +
                                "}")

                return bundle
            }


        internal var overrideNotificationId: Int = 0
    }
}
