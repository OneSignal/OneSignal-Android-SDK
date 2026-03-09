package com.onesignal.notifications.internal.channels

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.channels.impl.NotificationChannelManager
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
import java.math.BigInteger

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowRoboNotificationManager::class],
    sdk = [26],
)
@RobolectricTest
class NotificationChannelManagerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("createNotificationChannel should return default channel with empty payload") {
        // Given
        val mockTime = MockHelper.time(1111)

        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())

        // When
        val response = notificationChannelManager.createNotificationChannel(NotificationGenerationJob(JSONObject(), mockTime))

        // Then
        response shouldBe "fcm_fallback_notification_channel"

        val lastChannel = ShadowRoboNotificationManager.lastChannel
        lastChannel shouldNotBe null
        lastChannel!!.id shouldBe "fcm_fallback_notification_channel"
        lastChannel.sound shouldNotBe null
        lastChannel.shouldShowLights() shouldBe true
        lastChannel.shouldVibrate() shouldBe true
    }

    test("createNotificationChannel should create basic channel") {
        // Given
        val mockTime = MockHelper.time(1111)

        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())
        val payload =
            JSONObject()
                .put(
                    "chnl",
                    JSONObject()
                        .put("id", "test_id"),
                )

        // When
        val response = notificationChannelManager.createNotificationChannel(NotificationGenerationJob(payload, mockTime))

        // Then
        response shouldBe "test_id"

        val lastChannel = ShadowRoboNotificationManager.lastChannel
        lastChannel shouldNotBe null
        lastChannel!!.id shouldBe "test_id"
        lastChannel.sound shouldNotBe null
        lastChannel.shouldShowLights() shouldBe true
        lastChannel.shouldVibrate() shouldBe true
    }

    test("createNotificationChannel with all options") {
        // Given
        val mockTime = MockHelper.time(1111)

        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())
        val payload =
            JSONObject()
                .put("pri", 10)
                .put("led", 0)
                .put("ledc", "FFFF0000")
                .put("vib", 0)
                .put("vib_pt", JSONArray("[1,2,3,4]"))
                .put("sound", "notification")
                .put("vis", Notification.VISIBILITY_SECRET)
                .put("bdg", 1)
                .put("bdnd", 1)
                .put(
                    "chnl",
                    JSONObject()
                        .put("id", "test_id")
                        .put("nm", "Test Name")
                        .put("dscr", "Some description")
                        .put("grp_id", "grp_id")
                        .put("grp_nm", "Group Name"),
                )

        // When
        val response = notificationChannelManager.createNotificationChannel(NotificationGenerationJob(payload, mockTime))

        // Then
        response shouldBe "test_id"

        val lastChannel = ShadowRoboNotificationManager.lastChannel
        val lastGroup = ShadowRoboNotificationManager.lastChannelGroup

        lastChannel shouldNotBe null
        lastChannel!!.id shouldBe "test_id"
        lastChannel.name shouldBe "Test Name"
        lastChannel.description shouldBe "Some description"
        lastChannel.group shouldBe "grp_id"

        lastGroup shouldNotBe null
        lastGroup!!.id shouldBe "grp_id"
        lastGroup.name shouldBe "Group Name"

        lastChannel.sound shouldNotBe null
        lastChannel.shouldShowLights() shouldBe false // Setting a led color should NOT override enableLights

        lastChannel.lightColor.toLong() shouldBe -65536
        lastChannel.shouldVibrate() shouldBe false // Setting a pattern should NOT override enableVibration

        lastChannel.vibrationPattern shouldBe longArrayOf(1, 2, 3, 4)

        lastChannel.importance.toLong() shouldBe NotificationManager.IMPORTANCE_MAX
        lastChannel.sound.toString() shouldBe "content://settings/system/notification_sound"
        lastChannel.lockscreenVisibility.toLong() shouldBe Notification.VISIBILITY_SECRET.toLong()
        lastChannel.canShowBadge() shouldBe true
        lastChannel.canBypassDnd() shouldBe true
    }

    test("createNotificationChannel use other channel when available") {
        // Given
        val mockTime = MockHelper.time(1111)

        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())
        val payload =
            JSONObject()
                .put("oth_chnl", "existing_id")
                .put("chnl", JSONObject().put("id", "test_id"))

        // When
        val response1 = notificationChannelManager.createNotificationChannel(NotificationGenerationJob(payload, mockTime))
        createChannel("existing_id", ApplicationProvider.getApplicationContext())
        val response2 = notificationChannelManager.createNotificationChannel(NotificationGenerationJob(payload, mockTime))

        // Then
        response1 shouldBe "test_id"
        response2 shouldBe "existing_id"
    }

    test("createNotificationChannel with invalid color should revert to FFFFFFFF") {
        // Given
        val mockTime = MockHelper.time(1111)

        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())
        val payload =
            JSONObject()
                .put("ledc", "FFFFFFFFY")
                .put(
                    "chnl",
                    JSONObject()
                        .put("id", "test_id")
                        .put("nm", "Test Name"),
                )

        // When
        val response = notificationChannelManager.createNotificationChannel(NotificationGenerationJob(payload, mockTime))

        // Then
        val lastChannel = ShadowRoboNotificationManager.lastChannel
        response shouldBe "test_id"
        lastChannel shouldNotBe null
        lastChannel!!.lightColor shouldBe BigInteger("FFFFFFFF", 16).toInt()
    }

    test("processChannelList with no channel list should keep existing channels") {
        // Given
        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())

        createChannel("local_existing_id", ApplicationProvider.getApplicationContext())
        createChannel("OS_existing_id", ApplicationProvider.getApplicationContext())

        // When
        notificationChannelManager.processChannelList(null)

        // Then
        getChannel("local_existing_id", ApplicationProvider.getApplicationContext()) shouldNotBe null
        getChannel("OS_existing_id", ApplicationProvider.getApplicationContext()) shouldNotBe null
    }

    test("processChannelList with existing local channel should not delete local channel") {
        // Given
        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())

        createChannel("local_existing_id", ApplicationProvider.getApplicationContext())

        val payload =
            JSONArray()
                .put(
                    JSONObject()
                        .put("chnl", JSONObject().put("id", "OS_id1")),
                )

        // When
        notificationChannelManager.processChannelList(payload)

        // Then
        getChannel("local_existing_id", ApplicationProvider.getApplicationContext()) shouldNotBe null
        getChannel("OS_id1", ApplicationProvider.getApplicationContext()) shouldNotBe null
    }

    test("processChannelList with existing OS channel should delete old OS channel when it is not in channel list") {
        // Given
        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())

        createChannel("local_existing_id", ApplicationProvider.getApplicationContext())
        createChannel("OS_existing_id", ApplicationProvider.getApplicationContext())

        val payload =
            JSONArray()
                .put(
                    JSONObject()
                        .put("chnl", JSONObject().put("id", "OS_id1")),
                )

        // When
        notificationChannelManager.processChannelList(payload)

        // Then
        getChannel("local_existing_id", ApplicationProvider.getApplicationContext()) shouldNotBe null
        getChannel("OS_existing_id", ApplicationProvider.getApplicationContext()) shouldBe null
        getChannel("OS_id1", ApplicationProvider.getApplicationContext()) shouldNotBe null
    }

    fun createChannelWithPri(pri: Int): Int {
        val mockTime = MockHelper.time(1111)
        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())
        val channelId = "test_pri_$pri"
        val payload =
            JSONObject()
                .put("pri", pri)
                .put(
                    "chnl",
                    JSONObject()
                        .put("id", channelId),
                )
        notificationChannelManager.createNotificationChannel(NotificationGenerationJob(payload, mockTime))
        val notificationManager =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.getNotificationChannel(channelId)!!.importance
    }

    test("createNotificationChannel with pri 10 should have IMPORTANCE_MAX") {
        createChannelWithPri(10) shouldBe NotificationManager.IMPORTANCE_MAX
    }

    test("createNotificationChannel with pri 9 should have IMPORTANCE_MAX") {
        createChannelWithPri(9) shouldBe NotificationManager.IMPORTANCE_MAX
    }

    test("createNotificationChannel with pri 8 should have IMPORTANCE_HIGH") {
        createChannelWithPri(8) shouldBe NotificationManager.IMPORTANCE_HIGH
    }

    test("createNotificationChannel with pri 7 should have IMPORTANCE_HIGH") {
        createChannelWithPri(7) shouldBe NotificationManager.IMPORTANCE_HIGH
    }

    test("createNotificationChannel with pri 6 should have IMPORTANCE_DEFAULT") {
        createChannelWithPri(6) shouldBe NotificationManager.IMPORTANCE_DEFAULT
    }

    test("createNotificationChannel with pri 5 should have IMPORTANCE_DEFAULT") {
        createChannelWithPri(5) shouldBe NotificationManager.IMPORTANCE_DEFAULT
    }

    test("createNotificationChannel with pri 4 should have IMPORTANCE_LOW") {
        createChannelWithPri(4) shouldBe NotificationManager.IMPORTANCE_LOW
    }

    test("createNotificationChannel with pri 3 should have IMPORTANCE_LOW") {
        createChannelWithPri(3) shouldBe NotificationManager.IMPORTANCE_LOW
    }

    test("createNotificationChannel with pri 2 should have IMPORTANCE_MIN") {
        createChannelWithPri(2) shouldBe NotificationManager.IMPORTANCE_MIN
    }

    test("createNotificationChannel with pri 1 should have IMPORTANCE_MIN") {
        createChannelWithPri(1) shouldBe NotificationManager.IMPORTANCE_MIN
    }

    // Regression: pri=9 previously mapped to IMPORTANCE_HIGH due to strict > 9 check.
    // The backend sends pri=9 for the highest dashboard priority, so the channel must
    // be created with IMPORTANCE_MAX.
    test("regression - createNotificationChannel with pri 9 must not have IMPORTANCE_HIGH") {
        createChannelWithPri(9) shouldBe NotificationManager.IMPORTANCE_MAX
        createChannelWithPri(9) shouldNotBe NotificationManager.IMPORTANCE_HIGH
    }

    test("processChannelList multilanguage") {
        // Given
        val notificationChannelManager = NotificationChannelManager(AndroidMockHelper.applicationService(), MockHelper.languageContext())

        val payload =
            JSONArray()
                .put(
                    JSONObject()
                        .put(
                            "chnl",
                            JSONObject()
                                .put("id", "OS_id1")
                                .put("grp_id", "grp_id1")
                                .put(
                                    "langs",
                                    JSONObject()
                                        .put(
                                            "en",
                                            JSONObject()
                                                .put("nm", "en_nm")
                                                .put("dscr", "en_dscr")
                                                .put("grp_nm", "en_grp_nm"),
                                        ),
                                ),
                        ),
                )

        // When
        notificationChannelManager.processChannelList(payload)

        // Then
        val lastGroup = ShadowRoboNotificationManager.lastChannelGroup
        val channel = getChannel("OS_id1", ApplicationProvider.getApplicationContext())
        channel shouldNotBe null
        channel!!.name shouldBe "en_nm"
        channel.description shouldBe "en_dscr"
        lastGroup shouldNotBe null
        lastGroup!!.name shouldBe "en_grp_nm"
    }
})

fun createChannel(
    id: String,
    context: Context,
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(id, "name", NotificationManager.IMPORTANCE_DEFAULT)
    notificationManager.createNotificationChannel(channel)
}

private fun getChannel(
    id: String,
    context: Context,
): NotificationChannel? {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return notificationManager.getNotificationChannel(id)
}
