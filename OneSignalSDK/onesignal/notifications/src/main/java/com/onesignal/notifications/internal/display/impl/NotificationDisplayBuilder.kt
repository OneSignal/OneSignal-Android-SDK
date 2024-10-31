package com.onesignal.notifications.internal.display.impl

import android.R.drawable
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.onesignal.common.AndroidUtils
import com.onesignal.core.R
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.bundle.impl.NotificationBundleProcessor
import com.onesignal.notifications.internal.channels.INotificationChannelManager
import com.onesignal.notifications.internal.common.NotificationConstants
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.display.INotificationDisplayBuilder
import com.onesignal.notifications.receivers.NotificationDismissReceiver
import org.json.JSONException
import org.json.JSONObject
import java.math.BigInteger
import java.net.URL
import java.util.Arrays

internal class NotificationDisplayBuilder(
    private val _applicationService: IApplicationService,
    private val _notificationChannelManager: INotificationChannelManager,
) : INotificationDisplayBuilder {
    private val notificationDismissedClass: Class<*> = NotificationDismissReceiver::class.java

    private val contextResources: Resources?
        get() = _applicationService.appContext.resources

    private val currentContext: Context
        get() = _applicationService.appContext

    private val packageName: String?
        get() = _applicationService.appContext.packageName

    // NotificationCompat unfortunately doesn't correctly support some features
    // such as sounds and heads-up notifications with GROUP_ALERT_CHILDREN on
    // Android 6.0 and older.
    // This includes:
    //    Android 6.0 - No Sound or heads-up
    //    Android 5.0 - Sound, but no heads-up
    override fun getGroupAlertBehavior(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) NotificationCompat.GROUP_ALERT_CHILDREN else NotificationCompat.GROUP_ALERT_SUMMARY
    }

    override fun getTitle(fcmJson: JSONObject): CharSequence {
        val title: CharSequence? = fcmJson.optString("title", null)
        return title
            ?: currentContext!!.packageManager.getApplicationLabel(
                currentContext!!.applicationInfo,
            )
    }

    override fun getNewDismissActionPendingIntent(
        requestCode: Int,
        intent: Intent,
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            currentContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun getNewBaseDismissIntent(notificationId: Int): Intent {
        return Intent(currentContext, notificationDismissedClass)
            .putExtra(NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notificationId)
            .putExtra("dismissed", true)
    }

    override fun getBaseOneSignalNotificationBuilder(notificationJob: NotificationGenerationJob): OneSignalNotificationBuilder {
        val fcmJson: JSONObject = notificationJob.jsonPayload!!
        val oneSignalNotificationBuilder = OneSignalNotificationBuilder()
        val channelId = _notificationChannelManager.createNotificationChannel(notificationJob)
        val notificationBuilder = NotificationCompat.Builder(currentContext, channelId)
        val message = fcmJson.optString("alert", null)
        notificationBuilder
            .setAutoCancel(true)
            .setSmallIcon(getSmallIconId(fcmJson))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentText(message)
            .setTicker(message)

        // If title is blank; Set to app name if less than Android 7.
        //    Android 7.0 always displays the app title now in it's own section
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
            fcmJson.optString("title") != ""
        ) {
            notificationBuilder.setContentTitle(getTitle(fcmJson))
        }
        try {
            val accentColor = getAccentColor(fcmJson)
            if (accentColor != null) notificationBuilder.color = accentColor.toInt()
        } catch (t: Throwable) {
        } // Can throw if an old android support lib is used.
        try {
            var lockScreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            if (fcmJson.has("vis")) lockScreenVisibility = fcmJson.optString("vis").toInt()
            notificationBuilder.setVisibility(lockScreenVisibility)
        } catch (t: Throwable) {
        } // Can throw if an old android support lib is used or parse error
        val largeIcon = getLargeIcon(fcmJson)
        if (largeIcon != null) {
            oneSignalNotificationBuilder.hasLargeIcon = true
            notificationBuilder.setLargeIcon(largeIcon)
        }
        val bigPictureIcon = getBitmap(fcmJson.optString("bicon", null))
        if (bigPictureIcon != null) {
            notificationBuilder.setStyle(
                NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(message),
            )
        }
        if (notificationJob.shownTimeStamp != null) {
            try {
                notificationBuilder.setWhen(notificationJob.shownTimeStamp!! * 1000L)
            } catch (t: Throwable) {
            } // Can throw if an old android support lib is used.
        }
        setAlertnessOptions(fcmJson, notificationBuilder)
        oneSignalNotificationBuilder.compatBuilder = notificationBuilder
        return oneSignalNotificationBuilder
    }

    // Sets visibility options including; Priority, LED, Sounds, and Vibration.
    private fun setAlertnessOptions(
        fcmJson: JSONObject,
        notifBuilder: NotificationCompat.Builder,
    ) {
        val payloadPriority = fcmJson.optInt("pri", 6)
        val androidPriority = convertOSToAndroidPriority(payloadPriority)
        notifBuilder.priority = androidPriority
        val lowDisplayPriority = androidPriority < NotificationCompat.PRIORITY_DEFAULT

        // If notification is a low priority don't set Sound, Vibration, or LED
        if (lowDisplayPriority) return
        var notificationDefaults = 0
        if (fcmJson.has("ledc") && fcmJson.optInt("led", 1) == 1) {
            try {
                val ledColor = BigInteger(fcmJson.optString("ledc"), 16)
                notifBuilder.setLights(ledColor.toInt(), 2000, 5000)
            } catch (t: Throwable) {
                notificationDefaults = notificationDefaults or Notification.DEFAULT_LIGHTS
            } // Can throw if an old android support lib is used or parse error.
        } else {
            notificationDefaults = notificationDefaults or Notification.DEFAULT_LIGHTS
        }
        if (fcmJson.optInt("vib", 1) == 1) {
            if (fcmJson.has("vib_pt")) {
                val vibrationPattern: LongArray? = NotificationHelper.parseVibrationPattern(fcmJson)
                if (vibrationPattern != null) notifBuilder.setVibrate(vibrationPattern)
            } else {
                notificationDefaults = notificationDefaults or Notification.DEFAULT_VIBRATE
            }
        }
        if (isSoundEnabled(fcmJson)) {
            val soundUri: Uri? = NotificationHelper.getSoundUri(currentContext!!, fcmJson.optString("sound", null))
            if (soundUri != null) {
                notifBuilder.setSound(soundUri)
            } else {
                notificationDefaults =
                    notificationDefaults or Notification.DEFAULT_SOUND
            }
        }
        notifBuilder.setDefaults(notificationDefaults)
    }

    override fun removeNotifyOptions(builder: NotificationCompat.Builder?) {
        builder!!.setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setTicker(null)
    }

    // Xiaomi requires the following to show a custom notification icons.
    // Without this MIUI 8 will only show the app icon on the left.
    //  When a large icon is set the small icon will no longer show.
    override fun addXiaomiSettings(
        oneSignalNotificationBuilder: OneSignalNotificationBuilder?,
        notification: Notification,
    ) {
        // Don't use unless a large icon is set.
        // The small white notification icon is hard to see with MIUI default light theme.
        if (!oneSignalNotificationBuilder!!.hasLargeIcon) return
        try {
            val miuiNotification = Class.forName("android.app.MiuiNotification").newInstance()
            val customizedIconField = miuiNotification.javaClass.getDeclaredField("customizedIcon")
            customizedIconField.isAccessible = true
            customizedIconField[miuiNotification] = true
            val extraNotificationField = notification.javaClass.getField("extraNotification")
            extraNotificationField.isAccessible = true
            extraNotificationField[notification] = miuiNotification
        } catch (t: Throwable) {
        } // Ignore if not a Xiaomi device
    }

    private fun getLargeIcon(fcmJson: JSONObject): Bitmap? {
        var bitmap = getBitmap(fcmJson.optString("licon"))
        if (bitmap == null) {
            bitmap =
                getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default")
        }
        return if (bitmap == null) {
            null
        } else {
            resizeBitmapForLargeIconArea(
                bitmap,
            )
        }
    }

    override val defaultLargeIcon: Bitmap?
        get() {
            val bitmap = getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default")
            return resizeBitmapForLargeIconArea(bitmap)
        }

    // Resize to prevent extra cropping and boarders.
    private fun resizeBitmapForLargeIconArea(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        try {
            val systemLargeIconHeight =
                contextResources!!.getDimension(android.R.dimen.notification_large_icon_height).toInt()
            val systemLargeIconWidth =
                contextResources!!.getDimension(android.R.dimen.notification_large_icon_width).toInt()
            val bitmapHeight = bitmap.height
            val bitmapWidth = bitmap.width
            if (bitmapWidth > systemLargeIconWidth || bitmapHeight > systemLargeIconHeight) {
                var newWidth = systemLargeIconWidth
                var newHeight = systemLargeIconHeight
                if (bitmapHeight > bitmapWidth) {
                    val ratio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
                    newWidth = (newHeight * ratio).toInt()
                } else if (bitmapWidth > bitmapHeight) {
                    val ratio = bitmapHeight.toFloat() / bitmapWidth.toFloat()
                    newHeight = (newWidth * ratio).toInt()
                }
                return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            }
        } catch (t: Throwable) {
        }
        return bitmap
    }

    private fun getBitmapFromAssetsOrResourceName(bitmapStr: String): Bitmap? {
        try {
            var bitmap: Bitmap? = null
            try {
                bitmap = BitmapFactory.decodeStream(currentContext!!.assets.open(bitmapStr))
            } catch (t: Throwable) {
            }
            if (bitmap != null) return bitmap
            val imageExtensions = Arrays.asList(".png", ".webp", ".jpg", ".gif", ".bmp")
            for (extension in imageExtensions) {
                try {
                    bitmap =
                        BitmapFactory.decodeStream(currentContext!!.assets.open(bitmapStr + extension))
                } catch (t: Throwable) {
                }
                if (bitmap != null) return bitmap
            }
            val bitmapId = getResourceIcon(bitmapStr)
            if (bitmapId != 0) return BitmapFactory.decodeResource(contextResources, bitmapId)
        } catch (t: Throwable) {
        }
        return null
    }

    private fun getBitmapFromURL(location: String): Bitmap? {
        try {
            return BitmapFactory.decodeStream(URL(location).openConnection().getInputStream())
        } catch (t: Throwable) {
            Logging.warn("Could not download image!", t)
        }
        return null
    }

    private fun getBitmap(name: String?): Bitmap? {
        if (name == null) return null
        val trimmedName = name.trim { it <= ' ' }
        return if (trimmedName.startsWith("http://") || trimmedName.startsWith("https://")) {
            getBitmapFromURL(
                trimmedName,
            )
        } else {
            getBitmapFromAssetsOrResourceName(
                name,
            )
        }
    }

    private fun getResourceIcon(iconName: String?): Int {
        if (iconName == null) return 0
        val trimmedIconName = iconName.trim { it <= ' ' }
        if (!AndroidUtils.isValidResourceName(trimmedIconName)) return 0
        val notificationIcon = getDrawableId(trimmedIconName)
        if (notificationIcon != 0) return notificationIcon

        // Get system icon resource
        try {
            return drawable::class.java.getField(iconName).getInt(null)
        } catch (t: Throwable) {
        }
        return 0
    }

    private fun getSmallIconId(fcmJson: JSONObject): Int {
        val notificationIcon = getResourceIcon(fcmJson.optString("sicon", null))
        return if (notificationIcon != 0) notificationIcon else defaultSmallIconId
    }

    override val defaultSmallIconId: Int
        get() {
            var notificationIcon = getDrawableId("ic_stat_onesignal_default")
            if (notificationIcon != 0) return notificationIcon
            notificationIcon = getDrawableId("corona_statusbar_icon_default")
            if (notificationIcon != 0) return notificationIcon
            notificationIcon = getDrawableId("ic_os_notification_fallback_white_24dp")
            return if (notificationIcon != 0) notificationIcon else drawable.ic_popup_reminder
        }

    private fun getDrawableId(name: String): Int {
        return contextResources!!.getIdentifier(name, "drawable", packageName)
    }

    private fun isSoundEnabled(fcmJson: JSONObject): Boolean {
        val sound = fcmJson.optString("sound", null)
        return "null" != sound && "nil" != sound
    }

    // Android 5.0 accent color to use, only works when AndroidManifest.xml is targetSdkVersion >= 21
    private fun getAccentColor(fcmJson: JSONObject): BigInteger? {
        try {
            if (fcmJson.has("bgac")) return BigInteger(fcmJson.optString("bgac", null), 16)
        } catch (t: Throwable) {
        } // Can throw a parse error.

        // Try to get "onesignal_notification_accent_color" from resources
        // This will get the correct color for day and dark modes
        try {
            val defaultColor: String? =
                AndroidUtils.getResourceString(_applicationService.appContext, "onesignal_notification_accent_color", null)
            if (defaultColor != null) {
                return BigInteger(defaultColor, 16)
            }
        } catch (t: Throwable) {
        } // Can throw a parse error.

        // Get accent color from Manifest
        try {
            val defaultColor: String? =
                AndroidUtils.getManifestMeta(
                    _applicationService.appContext,
                    "com.onesignal.NotificationAccentColor.DEFAULT",
                )
            if (defaultColor != null) {
                return BigInteger(defaultColor, 16)
            }
        } catch (t: Throwable) {
        } // Can throw a parse error.
        return null
    }

    override fun addNotificationActionButtons(
        fcmJson: JSONObject,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        mBuilder: NotificationCompat.Builder?,
        notificationId: Int,
        groupSummary: String?,
    ) {
        try {
            val customJson = JSONObject(fcmJson.optString("custom"))
            if (!customJson.has("a")) return
            val additionalDataJSON = customJson.getJSONObject("a")
            if (!additionalDataJSON.has("actionButtons")) return
            val buttons = additionalDataJSON.getJSONArray("actionButtons")
            for (i in 0 until buttons.length()) {
                val button = buttons.optJSONObject(i)
                val bundle = JSONObject(fcmJson.toString())
                val buttonIntent: Intent = intentGenerator.getNewBaseIntent(notificationId)
                buttonIntent.action = "" + i // Required to keep each action button from replacing extras of each other
                buttonIntent.putExtra("action_button", true)
                bundle.put(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID, button.optString("id"))
                buttonIntent.putExtra(NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA, bundle.toString())
                if (groupSummary != null) {
                    buttonIntent.putExtra(
                        "summary",
                        groupSummary,
                    )
                } else if (fcmJson.has("grp")) {
                    buttonIntent.putExtra(
                        "grp",
                        fcmJson.optString("grp"),
                    )
                }
                val buttonPIntent: PendingIntent? =
                    intentGenerator.getNewActionPendingIntent(notificationId, buttonIntent)
                var buttonIcon = 0
                if (button.has("icon")) buttonIcon = getResourceIcon(button.optString("icon"))
                mBuilder!!.addAction(buttonIcon, button.optString("text"), buttonPIntent)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun addAlertButtons(
        context: Context,
        fcmJson: JSONObject,
        buttonsLabels: MutableList<String>,
        buttonsIds: MutableList<String>,
    ) {
        try {
            addCustomAlertButtons(fcmJson, buttonsLabels, buttonsIds)
        } catch (t: Throwable) {
            Logging.error("Failed to parse JSON for custom buttons for alert dialog.", t)
        }
        if (buttonsLabels.size == 0 || buttonsLabels.size < 3) {
            buttonsLabels.add(
                AndroidUtils.getResourceString(
                    context,
                    "onesignal_in_app_alert_ok_button_text",
                    "Ok",
                )!!,
            )
            buttonsIds.add(NotificationBundleProcessor.DEFAULT_ACTION)
        }
    }

    @Throws(JSONException::class)
    private fun addCustomAlertButtons(
        fcmJson: JSONObject,
        buttonsLabels: MutableList<String>,
        buttonsIds: MutableList<String>,
    ) {
        val customJson = JSONObject(fcmJson.optString("custom"))
        if (!customJson.has("a")) return
        val additionalDataJSON = customJson.getJSONObject("a")
        if (!additionalDataJSON.has("actionButtons")) return
        val buttons = additionalDataJSON.optJSONArray("actionButtons")
        for (i in 0 until buttons.length()) {
            val button = buttons.getJSONObject(i)
            buttonsLabels.add(button.optString("text"))
            buttonsIds.add(button.optString("id"))
        }
    }

    private fun convertOSToAndroidPriority(priority: Int): Int {
        if (priority > 9) return NotificationCompat.PRIORITY_MAX
        if (priority > 7) return NotificationCompat.PRIORITY_HIGH
        if (priority > 4) return NotificationCompat.PRIORITY_DEFAULT
        return if (priority > 2) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN
    }

    internal class OneSignalNotificationBuilder {
        var compatBuilder: NotificationCompat.Builder? = null
        var hasLargeIcon = false
    }
}
