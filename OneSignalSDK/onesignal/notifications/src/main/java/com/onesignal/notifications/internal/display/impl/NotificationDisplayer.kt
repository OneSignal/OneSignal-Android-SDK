package com.onesignal.notifications.internal.display.impl

import android.R.drawable
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.onesignal.common.AndroidUtils
import com.onesignal.common.exceptions.MainThreadException
import com.onesignal.common.safeString
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.R
import com.onesignal.notifications.internal.common.NotificationConstants
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.display.INotificationDisplayBuilder
import com.onesignal.notifications.internal.display.INotificationDisplayer
import com.onesignal.notifications.internal.display.ISummaryNotificationDisplayer
import com.onesignal.notifications.internal.limiting.INotificationLimitManager
import org.json.JSONObject
import java.math.BigInteger
import java.net.URL
import java.security.SecureRandom
import java.util.Arrays
import java.util.Random

internal class NotificationDisplayer(
    private val _applicationService: IApplicationService,
    private val _notificationLimitManager: INotificationLimitManager,
    private val _summaryNotificationDisplayer: ISummaryNotificationDisplayer,
    private val _notificationDisplayBuilder: INotificationDisplayBuilder,
) : INotificationDisplayer {
    private val contextResources: Resources?
        get() = _applicationService.appContext.resources

    private val currentContext: Context
        get() = _applicationService.appContext

    private val packageName: String?
        get() = _applicationService.appContext.packageName

    override suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean {
        isRunningOnMainThreadCheck
        return showNotification(notificationJob)
    }

    /**
     * For shadow test purpose
     */
    val isRunningOnMainThreadCheck: Unit
        get() {
            // Runtime check against showing the notification from the main thread
            if (AndroidUtils.isRunningOnMainThread()) {
                throw MainThreadException(
                    "Process for showing a notification should never been done on Main Thread!",
                )
            }
        }

    // Put the message into a notification and post it.
    private suspend fun showNotification(notificationJob: NotificationGenerationJob): Boolean {
        val notificationId: Int = notificationJob.androidId
        val fcmJson: JSONObject = notificationJob.jsonPayload!!
        var group: String? = fcmJson.safeString("grp")
        val intentGenerator = IntentGeneratorForAttachingToNotifications(currentContext)
        var grouplessNotifs = ArrayList<StatusBarNotification>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            /* Android 7.0 auto groups 4 or more notifications so we find these groupless active
             * notifications and add a generic group to them */
            grouplessNotifs = NotificationHelper.getActiveGrouplessNotifications(currentContext)

            // If the null this makes the 4th notification and we want to check that 3 or more active groupless exist
            if (group == null && grouplessNotifs.size >= 3) {
                group = NotificationHelper.GROUPLESS_SUMMARY_KEY
                NotificationHelper.assignGrouplessNotifications(
                    currentContext,
                    grouplessNotifs,
                )
            }
        }

        val oneSignalNotificationBuilder = _notificationDisplayBuilder.getBaseOneSignalNotificationBuilder(notificationJob)
        val notifBuilder = oneSignalNotificationBuilder.compatBuilder

        _notificationDisplayBuilder.addNotificationActionButtons(
            fcmJson,
            intentGenerator,
            notifBuilder,
            notificationId,
            null,
        )

        try {
            addBackgroundImage(fcmJson, notifBuilder)
        } catch (t: Throwable) {
            Logging.error("Could not set background notification image!", t)
        }

        applyNotificationExtender(notificationJob, notifBuilder)

        // Keeps notification from playing sound + vibrating again
        if (notificationJob.isRestoring) {
            _notificationDisplayBuilder.removeNotifyOptions(notifBuilder)
        }

        val makeRoomFor = if (group == null) 1 else 2
        _notificationLimitManager.clearOldestOverLimit(makeRoomFor)

        val notification: Notification
        if (group != null) {
            _summaryNotificationDisplayer.createGenericPendingIntentsForGroup(
                notifBuilder,
                intentGenerator,
                fcmJson,
                group,
                notificationId,
            )
            notification = _summaryNotificationDisplayer.createSingleNotificationBeforeSummaryBuilder(notificationJob, notifBuilder)

            // Create PendingIntents for notifications in a groupless or defined summary
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && group == NotificationHelper.GROUPLESS_SUMMARY_KEY) {
                _summaryNotificationDisplayer.createGrouplessSummaryNotification(
                    notificationJob,
                    intentGenerator,
                    grouplessNotifs.size + 1,
                    _notificationDisplayBuilder.getGroupAlertBehavior(),
                )
            } else {
                _summaryNotificationDisplayer.createSummaryNotification(
                    notificationJob,
                    oneSignalNotificationBuilder,
                    _notificationDisplayBuilder.getGroupAlertBehavior(),
                )
            }
        } else {
            notification =
                createGenericPendingIntentsForNotif(
                    notifBuilder,
                    intentGenerator,
                    fcmJson,
                    notificationId,
                )
        }

        // The benefits of calling notify for individual notifications in-addition to the summary above it is shows
        //   each notification in a stack on Android Wear and each one is actionable just like the Gmail app does per email.
        //   Note that on Android 7.0 this is the opposite. Only individual notifications will show and mBundle / group is
        //     created by Android itself.
        _notificationDisplayBuilder.addXiaomiSettings(oneSignalNotificationBuilder, notification)
        NotificationManagerCompat.from(currentContext!!).notify(notificationId, notification)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.areNotificationsEnabled(currentContext!!, notification.channelId)
        } else {
            true
        }
    }

    private fun createGenericPendingIntentsForNotif(
        notifBuilder: NotificationCompat.Builder?,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        gcmBundle: JSONObject,
        notificationId: Int,
    ): Notification {
        val random: Random = SecureRandom()
        val contentIntent: PendingIntent? =
            intentGenerator.getNewActionPendingIntent(
                random.nextInt(),
                intentGenerator.getNewBaseIntent(notificationId)
                    .putExtra(NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA, gcmBundle.toString()),
            )
        notifBuilder!!.setContentIntent(contentIntent)
        val deleteIntent =
            _notificationDisplayBuilder.getNewDismissActionPendingIntent(
                random.nextInt(),
                _notificationDisplayBuilder.getNewBaseDismissIntent(notificationId),
            )
        notifBuilder.setDeleteIntent(deleteIntent)
        return notifBuilder.build()
    }

    private fun applyNotificationExtender(
        notificationJob: NotificationGenerationJob,
        notificationBuilder: NotificationCompat.Builder?,
    ) {
        if (!notificationJob.hasExtender()) return
        try {
            val mNotificationField =
                NotificationCompat.Builder::class.java.getDeclaredField("mNotification")
            mNotificationField.isAccessible = true
            var mNotification = mNotificationField[notificationBuilder] as Notification
            notificationJob.orgFlags = mNotification.flags
            notificationJob.orgSound = mNotification.sound
            notificationJob.orgColor = mNotification.color

            notificationBuilder!!.extend(notificationJob.notification!!.notificationExtender!!)
            mNotification = mNotificationField[notificationBuilder] as Notification
            val mContentTextField =
                NotificationCompat.Builder::class.java.getDeclaredField("mContentText")
            mContentTextField.isAccessible = true
            val mContentText = mContentTextField[notificationBuilder] as CharSequence?
            val mContentTitleField =
                NotificationCompat.Builder::class.java.getDeclaredField("mContentTitle")
            mContentTitleField.isAccessible = true
            val mContentTitle = mContentTitleField[notificationBuilder] as CharSequence?
            notificationJob.overriddenBodyFromExtender = mContentText
            notificationJob.overriddenTitleFromExtender = mContentTitle

            val mColor =
                NotificationCompat.Builder::class.java.getDeclaredField("mColor")
            mColor.isAccessible = true
            val color = mColor[notificationBuilder] as Int?
            if (color != notificationJob.orgColor) {
                notificationJob.overriddenColor = color
            }

            if (!notificationJob.isRestoring) {
                notificationJob.overriddenFlags = mNotification.flags
                notificationJob.overriddenSound = mNotification.sound
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    // Keep 'throws Throwable' as 'onesignal_bgimage_notif_layout' may not be available
    //    This maybe the case if a jar is used instead of an aar.
    @Throws(Throwable::class)
    private fun addBackgroundImage(
        fcmJson: JSONObject,
        notifBuilder: NotificationCompat.Builder?,
    ) {
        // Not adding Background Images to API Versions >= 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Logging.verbose("Cannot use background images in notifications for device on version: " + Build.VERSION.SDK_INT)
            return
        }
        var bgImage: Bitmap? = null
        var jsonBgImage: JSONObject? = null
        val jsonStrBgImage = fcmJson.optString("bg_img", null)
        if (jsonStrBgImage != null) {
            jsonBgImage = JSONObject(jsonStrBgImage)
            bgImage = getBitmap(jsonBgImage.optString("img", null))
        }
        if (bgImage == null) {
            bgImage =
                getBitmapFromAssetsOrResourceName("onesignal_bgimage_default_image")
        }
        if (bgImage != null) {
            val customView =
                RemoteViews(currentContext!!.packageName, R.layout.onesignal_bgimage_notif_layout)
            customView.setTextViewText(R.id.os_bgimage_notif_title, _notificationDisplayBuilder.getTitle(fcmJson))
            customView.setTextViewText(R.id.os_bgimage_notif_body, fcmJson.optString("alert"))
            setTextColor(
                customView,
                jsonBgImage,
                R.id.os_bgimage_notif_title,
                "tc",
                "onesignal_bgimage_notif_title_color",
            )
            setTextColor(
                customView,
                jsonBgImage,
                R.id.os_bgimage_notif_body,
                "bc",
                "onesignal_bgimage_notif_body_color",
            )
            var alignSetting: String? = null
            if (jsonBgImage != null && jsonBgImage.has("img_align")) {
                alignSetting =
                    jsonBgImage.getString("img_align")
            } else {
                val iAlignSetting =
                    contextResources!!.getIdentifier(
                        "onesignal_bgimage_notif_image_align",
                        "string",
                        packageName,
                    )
                if (iAlignSetting != 0) alignSetting = contextResources!!.getString(iAlignSetting)
            }
            if ("right" == alignSetting) {
                // Use os_bgimage_notif_bgimage_right_aligned instead of os_bgimage_notif_bgimage
                //    which has scaleType="fitEnd" set.
                // This is required as setScaleType can not be called through RemoteViews as it is an enum.
                customView.setViewPadding(
                    R.id.os_bgimage_notif_bgimage_align_layout,
                    -5000,
                    0,
                    0,
                    0,
                )
                customView.setImageViewBitmap(R.id.os_bgimage_notif_bgimage_right_aligned, bgImage)
                customView.setViewVisibility(
                    R.id.os_bgimage_notif_bgimage_right_aligned,
                    View.VISIBLE,
                ) // visible
                customView.setViewVisibility(R.id.os_bgimage_notif_bgimage, View.GONE) // gone
            } else {
                customView.setImageViewBitmap(R.id.os_bgimage_notif_bgimage, bgImage)
            }
            notifBuilder!!.setContent(customView)

            // Remove style to prevent expanding by the user.
            // Future: Create an extended image option, will need its own layout.
            notifBuilder.setStyle(null)
        }
    }

    private fun setTextColor(
        customView: RemoteViews,
        fcmJson: JSONObject?,
        viewId: Int,
        colorPayloadKey: String,
        colorDefaultResource: String,
    ) {
        val color = safeGetColorFromHex(fcmJson, colorPayloadKey)
        if (color != null) {
            customView.setTextColor(viewId, color)
        } else {
            val colorId =
                contextResources!!.getIdentifier(colorDefaultResource, "color", packageName)
            if (colorId != 0) {
                customView.setTextColor(
                    viewId,
                    ContextCompat.getColor(currentContext, colorId),
                )
            }
        }
    }

    private fun safeGetColorFromHex(
        fcmJson: JSONObject?,
        colorKey: String,
    ): Int? {
        try {
            if (fcmJson != null && fcmJson.has(colorKey)) {
                return BigInteger(fcmJson.optString(colorKey), 16).toInt()
            }
        } catch (t: Throwable) {
        }
        return null
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

    private fun getDrawableId(name: String): Int {
        return contextResources!!.getIdentifier(name, "drawable", packageName)
    }
}
