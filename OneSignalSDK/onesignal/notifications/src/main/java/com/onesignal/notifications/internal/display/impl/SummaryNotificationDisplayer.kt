package com.onesignal.notifications.internal.display.impl

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onesignal.common.safeString
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.notifications.internal.common.NotificationConstants
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.display.INotificationDisplayBuilder
import com.onesignal.notifications.internal.display.ISummaryNotificationDisplayer
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Random

internal class SummaryNotificationDisplayer(
    private val _applicationService: IApplicationService,
    private val _dataController: INotificationRepository,
    private val _notificationDisplayBuilder: INotificationDisplayBuilder,
) : ISummaryNotificationDisplayer {
    private val currentContext: Context
        get() = _applicationService.appContext

    override fun createGenericPendingIntentsForGroup(
        notifBuilder: NotificationCompat.Builder?,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        gcmBundle: JSONObject,
        group: String,
        notificationId: Int,
    ) {
        val random: Random = SecureRandom()
        val contentIntent: PendingIntent? =
            intentGenerator.getNewActionPendingIntent(
                random.nextInt(),
                intentGenerator.getNewBaseIntent(notificationId)
                    .putExtra(NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA, gcmBundle.toString()).putExtra("grp", group),
            )
        notifBuilder!!.setContentIntent(contentIntent)
        val deleteIntent =
            _notificationDisplayBuilder.getNewDismissActionPendingIntent(
                random.nextInt(),
                _notificationDisplayBuilder.getNewBaseDismissIntent(notificationId).putExtra("grp", group),
            )
        notifBuilder.setDeleteIntent(deleteIntent)
        notifBuilder.setGroup(group)
        try {
            notifBuilder.setGroupAlertBehavior(_notificationDisplayBuilder.getGroupAlertBehavior())
        } catch (t: Throwable) {
            // do nothing in this case...Android support lib 26 isn't in the project
        }
    }

    // Removes custom sound set from the extender from non-summary notification before building it.
    //   This prevents the sound from playing twice or both the default sound + a custom one.
    override fun createSingleNotificationBeforeSummaryBuilder(
        notificationJob: NotificationGenerationJob,
        notifBuilder: NotificationCompat.Builder?,
    ): Notification {
        // Includes Android 4.3 through 6.0.1. Android 7.1 handles this correctly without this.
        // Android 4.2 and older just post the summary only.
        val singleNotifWorkArounds =
            Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.N && !notificationJob.isRestoring
        if (singleNotifWorkArounds) {
            if ((notificationJob.overriddenSound != null) &&
                !notificationJob.overriddenSound!!
                    .equals(notificationJob.orgSound)
            ) {
                notifBuilder!!.setSound(null)
            }
        }
        val notification = notifBuilder!!.build()
        if (singleNotifWorkArounds) notifBuilder.setSound(notificationJob.overriddenSound)
        return notification
    }

    override suspend fun updateSummaryNotification(notificationJob: NotificationGenerationJob) {
        createSummaryNotification(notificationJob, null, _notificationDisplayBuilder.getGroupAlertBehavior())
    }

    // This summary notification will be visible instead of the normal one on pre-Android 7.0 devices.
    override suspend fun createSummaryNotification(
        notificationJob: NotificationGenerationJob,
        notifBuilder: NotificationDisplayBuilder.OneSignalNotificationBuilder?,
        groupAlertBehavior: Int,
    ) {
        val updateSummary: Boolean = notificationJob.isRestoring
        var fcmJson: JSONObject = notificationJob.jsonPayload!!
        val intentGenerator = IntentGeneratorForAttachingToNotifications(currentContext!!)
        val group = fcmJson.optString("grp", null)
        val random = SecureRandom()
        val summaryDeleteIntent =
            _notificationDisplayBuilder.getNewDismissActionPendingIntent(
                random.nextInt(),
                _notificationDisplayBuilder.getNewBaseDismissIntent(0).putExtra("summary", group),
            )
        val summaryNotification: Notification
        var summaryNotificationId: Int? = null
        var firstFullData: String? = null
        var summaryList: MutableCollection<SpannableString?>? = null

        summaryNotificationId = _dataController.getAndroidIdForGroup(group, true)

        if (summaryNotificationId == null) {
            summaryNotificationId = random.nextInt()

            _dataController.createSummaryNotification(summaryNotificationId!!, group)
        }

        val notifications = _dataController.listNotificationsForGroup(group)
        var spannableString: SpannableString
        summaryList = ArrayList()

        for (notification in notifications) {
            if (!updateSummary && notification.androidId == notificationJob.androidId) {
                continue
            }

            var title = notification.title
            if (title == null) title = "" else title += " "
            spannableString = SpannableString(title + notification.message)
            if (title.length > 0) {
                spannableString.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            }

            summaryList.add(spannableString)
            if (firstFullData == null) {
                firstFullData = notification.fullData
            }
        }

        val summaryContentIntent: PendingIntent? =
            intentGenerator.getNewActionPendingIntent(
                random.nextInt(),
                createBaseSummaryIntent(summaryNotificationId!!, intentGenerator, fcmJson, group),
            )

        // 2 or more notifications with a group received, group them together as a single notification.
        if (updateSummary && summaryList.size > 1 ||
            !updateSummary && summaryList.size > 0
        ) {
            val notificationCount = summaryList.size + if (updateSummary) 0 else 1
            var summaryMessage = fcmJson.safeString("grp_msg")
            summaryMessage = summaryMessage?.replace("$[notif_count]", "" + notificationCount)
                ?: "$notificationCount new messages"
            val summaryBuilder = _notificationDisplayBuilder.getBaseOneSignalNotificationBuilder(notificationJob).compatBuilder
            if (updateSummary) {
                _notificationDisplayBuilder.removeNotifyOptions(summaryBuilder)
            } else {
                if (notificationJob.overriddenSound != null) {
                    summaryBuilder!!.setSound(
                        notificationJob.overriddenSound,
                    )
                }
                if (notificationJob.overriddenFlags != null) {
                    summaryBuilder!!.setDefaults(
                        notificationJob.overriddenFlags!!,
                    )
                }
            }

            // The summary is designed to fit all notifications.
            //   Default small and large icons are used instead of the payload options to enforce this.
            summaryBuilder!!.setContentIntent(summaryContentIntent)
                .setDeleteIntent(summaryDeleteIntent)
                .setContentTitle(
                    currentContext!!.packageManager.getApplicationLabel(
                        currentContext!!.applicationInfo,
                    ),
                )
                .setContentText(summaryMessage)
                .setNumber(notificationCount)
                .setSmallIcon(_notificationDisplayBuilder.defaultSmallIconId)
                .setLargeIcon(_notificationDisplayBuilder.defaultLargeIcon)
                .setOnlyAlertOnce(updateSummary)
                .setAutoCancel(false)
                .setGroup(group)
                .setGroupSummary(true)
            try {
                summaryBuilder.setGroupAlertBehavior(groupAlertBehavior)
            } catch (t: Throwable) {
                // do nothing in this case...Android support lib 26 isn't in the project
            }
            if (!updateSummary) summaryBuilder.setTicker(summaryMessage)
            val inboxStyle = NotificationCompat.InboxStyle()

            // Add the latest notification to the summary
            if (!updateSummary) {
                var line1Title: String? = null
                if (notificationJob.title != null) {
                    line1Title =
                        notificationJob.title.toString()
                }
                if (line1Title == null) line1Title = "" else line1Title += " "

                val message: String = notificationJob.body?.toString() ?: ""
                val spannableString = SpannableString(line1Title + message)
                if (line1Title.length > 0) {
                    spannableString.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        line1Title.length,
                        0,
                    )
                }
                inboxStyle.addLine(spannableString)
            }
            for (line in summaryList) inboxStyle.addLine(line)
            inboxStyle.setBigContentTitle(summaryMessage)
            summaryBuilder.setStyle(inboxStyle)

            if (notificationJob.overriddenColor != null) {
                summaryBuilder.setColor(
                    notificationJob.overriddenColor!!,
                )
            }

            summaryNotification = summaryBuilder.build()
        } else {
            // First notification with this group key, post like a normal notification.
            val summaryBuilder = notifBuilder!!.compatBuilder

            // TODO: We are re-using the notifBuilder from the normal notification so if a developer as an
            //  extender setup all the settings will carry over.
            //  Note: However their buttons will not carry over as we need to be setup with this new summaryNotificationId.
            summaryBuilder!!.mActions.clear()
            _notificationDisplayBuilder.addNotificationActionButtons(
                fcmJson,
                intentGenerator,
                summaryBuilder,
                summaryNotificationId!!,
                group,
            )
            summaryBuilder.setContentIntent(summaryContentIntent)
                .setDeleteIntent(summaryDeleteIntent)
                .setOnlyAlertOnce(updateSummary)
                .setAutoCancel(false)
                .setGroup(group)
                .setGroupSummary(true)
            try {
                summaryBuilder.setGroupAlertBehavior(groupAlertBehavior)
            } catch (t: Throwable) {
                // do nothing in this case...Android support lib 26 isn't in the project
            }

            if (notificationJob.overriddenColor != null) {
                summaryBuilder.setColor(
                    notificationJob.overriddenColor!!,
                )
            }

            summaryNotification = summaryBuilder.build()
            _notificationDisplayBuilder.addXiaomiSettings(notifBuilder, summaryNotification)
        }
        NotificationManagerCompat.from(currentContext!!)
            .notify(summaryNotificationId!!, summaryNotification)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override suspend fun createGrouplessSummaryNotification(
        notificationJob: NotificationGenerationJob,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        grouplessNotifCount: Int,
        groupAlertBehavior: Int,
    ) {
        val fcmJson: JSONObject = notificationJob.jsonPayload!!
        val summaryNotification: Notification
        val random = SecureRandom()
        val group: String = NotificationHelper.GROUPLESS_SUMMARY_KEY
        val summaryMessage = "$grouplessNotifCount new messages"
        val summaryNotificationId: Int = NotificationHelper.GROUPLESS_SUMMARY_ID
        _dataController.createSummaryNotification(summaryNotificationId!!, group)
        val summaryContentIntent: PendingIntent? =
            intentGenerator.getNewActionPendingIntent(
                random.nextInt(),
                createBaseSummaryIntent(summaryNotificationId, intentGenerator, fcmJson, group),
            )
        val summaryDeleteIntent =
            _notificationDisplayBuilder.getNewDismissActionPendingIntent(
                random.nextInt(),
                _notificationDisplayBuilder.getNewBaseDismissIntent(0).putExtra("summary", group),
            )
        val summaryBuilder = _notificationDisplayBuilder.getBaseOneSignalNotificationBuilder(notificationJob).compatBuilder
        if (notificationJob.overriddenSound != null) summaryBuilder!!.setSound(notificationJob.overriddenSound)
        if (notificationJob.overriddenFlags != null) {
            summaryBuilder!!.setDefaults(
                notificationJob.overriddenFlags!!,
            )
        }
        if (notificationJob.overriddenColor != null) {
            summaryBuilder!!.setColor(
                notificationJob.overriddenColor!!,
            )
        }

        // The summary is designed to fit all notifications.
        //   Default small and large icons are used instead of the payload options to enforce this.
        summaryBuilder!!.setContentIntent(summaryContentIntent)
            .setDeleteIntent(summaryDeleteIntent)
            .setContentTitle(
                currentContext!!.packageManager.getApplicationLabel(
                    currentContext!!.applicationInfo,
                ),
            )
            .setContentText(summaryMessage)
            .setNumber(grouplessNotifCount)
            .setSmallIcon(_notificationDisplayBuilder.defaultSmallIconId)
            .setLargeIcon(_notificationDisplayBuilder.defaultLargeIcon)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setGroup(group)
            .setGroupSummary(true)
        try {
            summaryBuilder.setGroupAlertBehavior(groupAlertBehavior)
        } catch (t: Throwable) {
            // Do nothing in this case... Android support lib 26 isn't in the project
        }
        val inboxStyle = NotificationCompat.InboxStyle()
        inboxStyle.setBigContentTitle(summaryMessage)
        summaryBuilder.setStyle(inboxStyle)
        summaryNotification = summaryBuilder.build()
        NotificationManagerCompat.from(currentContext!!)
            .notify(summaryNotificationId, summaryNotification)
    }

    private fun createBaseSummaryIntent(
        summaryNotificationId: Int,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        fcmJson: JSONObject,
        group: String,
    ): Intent {
        return intentGenerator.getNewBaseIntent(summaryNotificationId).putExtra(
            NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA,
            fcmJson.toString(),
        ).putExtra("summary", group)
    }
}
