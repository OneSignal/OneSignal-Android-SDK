package com.onesignal.notifications.internal.channels.impl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.channels.INotificationChannelManager
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.common.NotificationHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigInteger
import java.util.regex.Pattern

internal class NotificationChannelManager(
    private val _applicationService: IApplicationService,
    private val _languageContext: ILanguageContext,
) : INotificationChannelManager {
    companion object {
        // Can't create a channel with the id 'miscellaneous' as an exception is thrown.
        // Using it results in the notification not being displayed.
        // private static final String DEFAULT_CHANNEL_ID = "miscellaneous"; // NotificationChannel.DEFAULT_CHANNEL_ID;
        private const val DEFAULT_CHANNEL_ID = "fcm_fallback_notification_channel"
        private const val RESTORE_CHANNEL_ID = "restored_OS_notifications"
        private const val CHANNEL_PREFIX = "OS_"
    }

    private val hexPattern = Pattern.compile("^([A-Fa-f0-9]{8})$")

    override fun createNotificationChannel(notificationJob: NotificationGenerationJob): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return DEFAULT_CHANNEL_ID
        val context = _applicationService.appContext
        val jsonPayload = notificationJob.jsonPayload!!
        val notificationManager = NotificationHelper.getNotificationManager(context)
        if (notificationJob.isRestoring) return createRestoreChannel(notificationManager)

        // Allow channels created outside the SDK
        if (jsonPayload.has("oth_chnl")) {
            val otherChannel = jsonPayload.optString("oth_chnl")
            if (notificationManager.getNotificationChannel(otherChannel) != null) return otherChannel
        }
        if (!jsonPayload.has("chnl")) return createDefaultChannel(notificationManager)
        try {
            return createChannel(context, notificationManager, jsonPayload)
        } catch (e: JSONException) {
            Logging.error("Could not create notification channel due to JSON payload error!", e)
        }
        return DEFAULT_CHANNEL_ID
    }

    // Creates NotificationChannel and NotificationChannelGroup based on a json payload.
    // Returns channel id after it is created.
    // Language dependent fields will be passed localized
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Throws(
        JSONException::class,
    )
    private fun createChannel(
        context: Context,
        notificationManager: NotificationManager,
        payload: JSONObject,
    ): String {
        // 'chnl' will be a string if coming from FCM and it will be a JSONObject when coming from
        //   a cold start sync.
        val objChannelPayload = payload.opt("chnl")
        var channelPayload: JSONObject? = null
        channelPayload =
            if (objChannelPayload is String) {
                JSONObject(
                    objChannelPayload,
                )
            } else {
                objChannelPayload as JSONObject
            }
        var channelId = channelPayload!!.optString("id", DEFAULT_CHANNEL_ID)
        // Ensure we don't try to use the system reserved id
        if (channelId == NotificationChannel.DEFAULT_CHANNEL_ID) channelId = DEFAULT_CHANNEL_ID
        var payloadWithText = channelPayload
        if (channelPayload.has("langs")) {
            val langList = channelPayload.getJSONObject("langs")
            val language = _languageContext.language
            if (langList.has(language)) payloadWithText = langList.optJSONObject(language)
        }
        val channelName = payloadWithText!!.optString("nm", "Miscellaneous")
        val importance = priorityToImportance(payload.optInt("pri", 6))
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.description = payloadWithText.optString("dscr", null)
        if (channelPayload.has("grp_id")) {
            val groupId = channelPayload.optString("grp_id")
            val groupName: CharSequence = payloadWithText.optString("grp_nm")
            notificationManager.createNotificationChannelGroup(
                NotificationChannelGroup(
                    groupId,
                    groupName,
                ),
            )
            channel.group = groupId
        }
        if (payload.has("ledc")) {
            var ledc = payload.optString("ledc")
            val matcher = hexPattern.matcher(ledc)
            val ledColor: BigInteger
            if (!matcher.matches()) {
                Logging.warn("OneSignal LED Color Settings: ARGB Hex value incorrect format (E.g: FF9900FF)")
                ledc = "FFFFFFFF"
            }
            try {
                ledColor = BigInteger(ledc, 16)
                channel.lightColor = ledColor.toInt()
            } catch (t: Throwable) {
                Logging.warn("Couldn't convert ARGB Hex value to BigInteger:", t)
            }
        }
        channel.enableLights(payload.optInt("led", 1) == 1)
        if (payload.has("vib_pt")) {
            val vibrationPattern = NotificationHelper.parseVibrationPattern(payload)
            if (vibrationPattern != null) channel.vibrationPattern = vibrationPattern
        }
        channel.enableVibration(payload.optInt("vib", 1) == 1)
        if (payload.has("sound")) {
            // Sound will only play if Importance is set to High or Urgent
            val sound = payload.optString("sound", null)
            val uri = NotificationHelper.getSoundUri(context, sound)
            if (uri != null) {
                channel.setSound(
                    uri,
                    null,
                )
            } else if ("null" == sound || "nil" == sound) {
                channel.setSound(null, null)
            }
            // null = None for a sound.
        }
        // Setting sound to null makes it 'None' in the Settings.
        // Otherwise not calling setSound makes it the default notification sound.
        channel.lockscreenVisibility = payload.optInt("vis", Notification.VISIBILITY_PRIVATE)
        channel.setShowBadge(payload.optInt("bdg", 1) == 1)
        channel.setBypassDnd(payload.optInt("bdnd", 0) == 1)
        Logging.verbose("Creating notification channel with channel:\n$channel")

        try {
            notificationManager.createNotificationChannel(channel)
        } catch (e: IllegalArgumentException) {
            // TODO: Remove this try-catch once it is figured out which argument is causing Issue #895
            //    try-catch added to prevent crashing from the illegal argument
            //    Added logging above this try-catch so we can evaluate the payload of the next victim
            //    to report a stacktrace
            //    https://github.com/OneSignal/OneSignal-Android-SDK/issues/895
            e.printStackTrace()
        }
        return channelId
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createDefaultChannel(notificationManager: NotificationManager): String {
        val channel =
            NotificationChannel(
                DEFAULT_CHANNEL_ID,
                "Miscellaneous",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        channel.enableLights(true)
        channel.enableVibration(true)
        notificationManager.createNotificationChannel(channel)
        return DEFAULT_CHANNEL_ID
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createRestoreChannel(notificationManager: NotificationManager): String {
        val channel =
            NotificationChannel(
                RESTORE_CHANNEL_ID,
                "Restored",
                NotificationManager.IMPORTANCE_LOW,
            )
        notificationManager.createNotificationChannel(channel)
        return RESTORE_CHANNEL_ID
    }

    override fun processChannelList(list: JSONArray?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (list == null || list.length() == 0) return
        val notificationManager = NotificationHelper.getNotificationManager(_applicationService.appContext)
        val syncedChannelSet: MutableSet<String> = HashSet()
        val jsonArraySize = list.length()
        for (i in 0 until jsonArraySize) {
            try {
                syncedChannelSet.add(
                    createChannel(
                        _applicationService.appContext,
                        notificationManager,
                        list.getJSONObject(i),
                    ),
                )
            } catch (e: JSONException) {
                Logging.error("Could not create notification channel due to JSON payload error!", e)
            }
        }
        if (syncedChannelSet.isEmpty()) return
        var existingChannels: List<NotificationChannel> = ArrayList()
        try {
            existingChannels = notificationManager.notificationChannels
        } catch (e: NullPointerException) {
            // Catch issue caused by "Attempt to invoke virtual method 'boolean android.app.NotificationChannel.isDeleted()' on a null object reference"
            // https://github.com/OneSignal/OneSignal-Android-SDK/issues/1291
            Logging.warn("Error when trying to delete notification channel: " + e.message)
        }

        // Delete old channels - Payload will include all changes for the app. Any extra OS_ ones must
        //                       have been deleted from the dashboard and should be removed.
        for (existingChannel in existingChannels) {
            val id = existingChannel.id
            if (id.startsWith(CHANNEL_PREFIX) && !syncedChannelSet.contains(id)) {
                notificationManager.deleteNotificationChannel(
                    id,
                )
            }
        }
    }

    private fun priorityToImportance(priority: Int): Int {
        if (priority > 9) return NotificationManagerCompat.IMPORTANCE_MAX
        if (priority > 7) return NotificationManagerCompat.IMPORTANCE_HIGH
        if (priority > 5) return NotificationManagerCompat.IMPORTANCE_DEFAULT
        if (priority > 3) return NotificationManagerCompat.IMPORTANCE_LOW
        return if (priority > 1) NotificationManagerCompat.IMPORTANCE_MIN else NotificationManagerCompat.IMPORTANCE_NONE
    }
}
