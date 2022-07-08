package com.onesignal.onesignal.internal.notification.generation

import android.content.Intent
import android.app.PendingIntent
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.text.style.StyleSpan
import org.json.JSONException
import android.content.ContentValues
import android.widget.RemoteViews
import android.graphics.BitmapFactory
import android.R.drawable
import android.app.Notification
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onesignal.R
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.common.AndroidSupportV4Compat
import com.onesignal.onesignal.internal.common.AndroidUtils
import com.onesignal.onesignal.internal.common.exceptions.MainThreadException
import com.onesignal.onesignal.internal.database.IDatabase
import com.onesignal.onesignal.internal.database.OneSignalDbContract
import com.onesignal.onesignal.internal.notification.NotificationHelper
import com.onesignal.onesignal.internal.notification.work.NotificationBundleProcessor
import com.onesignal.onesignal.internal.notification.work.NotificationGenerationJob
import com.onesignal.onesignal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigInteger
import java.net.URL
import java.security.SecureRandom
import java.util.*

internal class GenerateNotification(
    private val _applicationService: IApplicationService,
    private val _notificationChannelManager: NotificationChannelManager,
    private val _notificationLimitManager: NotificationLimitManager,
    private val _database: IDatabase
) : IGenerateNotification {
    companion object {
        const val OS_SHOW_NOTIFICATION_THREAD = "OS_SHOW_NOTIFICATION_THREAD"
        const val BUNDLE_KEY_ANDROID_NOTIFICATION_ID = "androidNotificationId"
        const val BUNDLE_KEY_ACTION_ID = "actionId"

        // Bundle key the whole OneSignal payload will be placed into as JSON and attached to the
        //   notification Intent.
        const val BUNDLE_KEY_ONESIGNAL_DATA = "onesignalData"
    }

    private val notificationOpenedClass: Class<*> = NotificationOpenedReceiver::class.java
    private val notificationDismissedClass: Class<*> = NotificationDismissReceiver::class.java
    private var contextResources: Resources? = null
    private var currentContext: Context? = null
    private var packageName: String? = null
    private var groupAlertBehavior: Int? = null

    // NotificationCompat unfortunately doesn't correctly support some features
    // such as sounds and heads-up notifications with GROUP_ALERT_CHILDREN on
    // Android 6.0 and older.
    // This includes:
    //    Android 6.0 - No Sound or heads-up
    //    Android 5.0 - Sound, but no heads-up
    private fun initGroupAlertBehavior() {
        groupAlertBehavior =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) NotificationCompat.GROUP_ALERT_CHILDREN else NotificationCompat.GROUP_ALERT_SUMMARY
    }

    private fun setStatics(inContext: Context) {
        currentContext = inContext
        packageName = currentContext!!.packageName
        contextResources = currentContext!!.resources
    }

    override suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean {
        setStatics(notificationJob.context)
        isRunningOnMainThreadCheck
        initGroupAlertBehavior()
        return showNotification(notificationJob)
    }

    suspend fun displayIAMPreviewNotification(notificationJob: NotificationGenerationJob): Boolean {
        setStatics(notificationJob.context)
        return showNotification(notificationJob)
    }// Runtime check against showing the notification from the main thread

    /**
     * For shadow test purpose
     */
    val isRunningOnMainThreadCheck: Unit
        get() {
            // Runtime check against showing the notification from the main thread
            if (AndroidUtils.isRunningOnMainThread()) throw MainThreadException("Process for showing a notification should never been done on Main Thread!")
        }

    private fun getTitle(fcmJson: JSONObject): CharSequence {
        val title: CharSequence? = fcmJson.optString("title", null)
        return title
            ?: currentContext!!.packageManager.getApplicationLabel(
                currentContext!!.applicationInfo
            )
    }

    private fun getNewDismissActionPendingIntent(requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            currentContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNewBaseDismissIntent(notificationId: Int): Intent {
        return Intent(currentContext, notificationDismissedClass)
            .putExtra(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notificationId)
            .putExtra("dismissed", true)
    }

    private fun getBaseOneSignalNotificationBuilder(notificationJob: NotificationGenerationJob): OneSignalNotificationBuilder {
        val fcmJson: JSONObject = notificationJob.jsonPayload!!
        val oneSignalNotificationBuilder = OneSignalNotificationBuilder()
        val notificationBuilder: NotificationCompat.Builder
        notificationBuilder = try {
            val channelId: String =
                _notificationChannelManager.createNotificationChannel(notificationJob)
            // Will throw if app is using 26.0.0-beta1 or older of the support library.
            NotificationCompat.Builder(currentContext!!, channelId)
        } catch (t: Throwable) {
            NotificationCompat.Builder(currentContext!!)
        }
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
        ) notificationBuilder.setContentTitle(getTitle(fcmJson))
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
        if (bigPictureIcon != null) notificationBuilder.setStyle(
            NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(message)
        )
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
    private fun setAlertnessOptions(fcmJson: JSONObject, notifBuilder: NotificationCompat.Builder) {
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
            } else notificationDefaults = notificationDefaults or Notification.DEFAULT_VIBRATE
        }
        if (isSoundEnabled(fcmJson)) {
            val soundUri: Uri? = NotificationHelper.getSoundUri(currentContext!!, fcmJson.optString("sound", null))
            if (soundUri != null) notifBuilder.setSound(soundUri) else notificationDefaults =
                notificationDefaults or Notification.DEFAULT_SOUND
        }
        notifBuilder.setDefaults(notificationDefaults)
    }

    private fun removeNotifyOptions(builder: NotificationCompat.Builder?) {
        builder!!.setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setTicker(null)
    }

    // Put the message into a notification and post it.
    private suspend fun showNotification(notificationJob: NotificationGenerationJob): Boolean {
        val notificationId: Int = notificationJob.androidId
        val fcmJson: JSONObject = notificationJob.jsonPayload!!
        var group = fcmJson.optString("grp", null)
        val intentGenerator = IntentGeneratorForAttachingToNotifications(currentContext!!)
        var grouplessNotifs = ArrayList<StatusBarNotification>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            /* Android 7.0 auto groups 4 or more notifications so we find these groupless active
          * notifications and add a generic group to them */
            grouplessNotifs = NotificationHelper.getActiveGrouplessNotifications(currentContext!!)

            // If the null this makes the 4th notification and we want to check that 3 or more active groupless exist
            if (group == null && grouplessNotifs.size >= 3) {
                group = NotificationHelper.grouplessSummaryKey
                NotificationHelper.assignGrouplessNotifications(
                    currentContext,
                    grouplessNotifs
                )
            }
        }
        val oneSignalNotificationBuilder = getBaseOneSignalNotificationBuilder(notificationJob)
        val notifBuilder = oneSignalNotificationBuilder.compatBuilder
        addNotificationActionButtons(
            fcmJson,
            intentGenerator,
            notifBuilder,
            notificationId,
            null
        )
        try {
            addBackgroundImage(fcmJson, notifBuilder)
        } catch (t: Throwable) {
            Logging.error("Could not set background notification image!", t)
        }
        applyNotificationExtender(notificationJob, notifBuilder)

        // Keeps notification from playing sound + vibrating again
        if (notificationJob.isRestoring) removeNotifyOptions(notifBuilder)
        var makeRoomFor = 1
        if (group != null) makeRoomFor = 2
        _notificationLimitManager.clearOldestOverLimit(currentContext!!, makeRoomFor)
        val notification: Notification
        if (group != null) {
            createGenericPendingIntentsForGroup(
                notifBuilder,
                intentGenerator,
                fcmJson,
                group,
                notificationId
            )
            notification =
                createSingleNotificationBeforeSummaryBuilder(notificationJob, notifBuilder)

            // Create PendingIntents for notifications in a groupless or defined summary
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && group == NotificationHelper.grouplessSummaryKey) {
                createGrouplessSummaryNotification(
                    notificationJob,
                    intentGenerator,
                    grouplessNotifs.size + 1
                )
            } else createSummaryNotification(notificationJob, oneSignalNotificationBuilder)
        } else {
            notification = createGenericPendingIntentsForNotif(
                notifBuilder,
                intentGenerator,
                fcmJson,
                notificationId
            )
        }
        // NotificationManagerCompat does not auto omit the individual notification on the device when using
        //   stacked notifications on Android 4.2 and older
        // The benefits of calling notify for individual notifications in-addition to the summary above it is shows
        //   each notification in a stack on Android Wear and each one is actionable just like the Gmail app does per email.
        //   Note that on Android 7.0 this is the opposite. Only individual notifications will show and mBundle / group is
        //     created by Android itself.
        if (group == null || Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
            addXiaomiSettings(oneSignalNotificationBuilder, notification)
            NotificationManagerCompat.from(currentContext!!).notify(notificationId, notification)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) NotificationHelper.areNotificationsEnabled(
            currentContext!!, notification.channelId
        ) else true
    }

    private fun createGenericPendingIntentsForNotif(
        notifBuilder: NotificationCompat.Builder?,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        gcmBundle: JSONObject,
        notificationId: Int
    ): Notification {
        val random: Random = SecureRandom()
        val contentIntent: PendingIntent? = intentGenerator.getNewActionPendingIntent(
            random.nextInt(),
            intentGenerator.getNewBaseIntent(notificationId)
                .putExtra(BUNDLE_KEY_ONESIGNAL_DATA, gcmBundle.toString())
        )
        notifBuilder!!.setContentIntent(contentIntent)
        val deleteIntent = getNewDismissActionPendingIntent(
            random.nextInt(),
            getNewBaseDismissIntent(notificationId)
        )
        notifBuilder.setDeleteIntent(deleteIntent)
        return notifBuilder.build()
    }

    private fun createGenericPendingIntentsForGroup(
        notifBuilder: NotificationCompat.Builder?,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        gcmBundle: JSONObject,
        group: String,
        notificationId: Int
    ) {
        val random: Random = SecureRandom()
        val contentIntent: PendingIntent? = intentGenerator.getNewActionPendingIntent(
            random.nextInt(),
            intentGenerator.getNewBaseIntent(notificationId)
                .putExtra(BUNDLE_KEY_ONESIGNAL_DATA, gcmBundle.toString()).putExtra("grp", group)
        )
        notifBuilder!!.setContentIntent(contentIntent)
        val deleteIntent = getNewDismissActionPendingIntent(
            random.nextInt(),
            getNewBaseDismissIntent(notificationId).putExtra("grp", group)
        )
        notifBuilder.setDeleteIntent(deleteIntent)
        notifBuilder.setGroup(group)
        try {
            notifBuilder.setGroupAlertBehavior(groupAlertBehavior!!)
        } catch (t: Throwable) {
            //do nothing in this case...Android support lib 26 isn't in the project
        }
    }

    private fun applyNotificationExtender(
        notificationJob: NotificationGenerationJob,
        notificationBuilder: NotificationCompat.Builder?
    ) {
        if (!notificationJob.hasExtender()) return
        try {
            val mNotificationField =
                NotificationCompat.Builder::class.java.getDeclaredField("mNotification")
            mNotificationField.isAccessible = true
            var mNotification = mNotificationField[notificationBuilder] as Notification
            notificationJob.orgFlags = mNotification.flags
            notificationJob.orgSound = mNotification.sound
            notificationBuilder!!.extend(notificationJob.notification!!.notificationExtender!!)
            mNotification = mNotificationField[notificationBuilder] as Notification
            val mContentTextField =
                NotificationCompat.Builder::class.java.getDeclaredField("mContentText")
            mContentTextField.isAccessible = true
            val mContentText = mContentTextField[notificationBuilder] as CharSequence
            val mContentTitleField =
                NotificationCompat.Builder::class.java.getDeclaredField("mContentTitle")
            mContentTitleField.isAccessible = true
            val mContentTitle = mContentTitleField[notificationBuilder] as CharSequence
            notificationJob.overriddenBodyFromExtender = mContentText
            notificationJob.overriddenTitleFromExtender = mContentTitle
            if (!notificationJob.isRestoring) {
                notificationJob.overriddenFlags = mNotification.flags
                notificationJob.overriddenSound = mNotification.sound
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    // Removes custom sound set from the extender from non-summary notification before building it.
    //   This prevents the sound from playing twice or both the default sound + a custom one.
    private fun createSingleNotificationBeforeSummaryBuilder(
        notificationJob: NotificationGenerationJob,
        notifBuilder: NotificationCompat.Builder?
    ): Notification {
        // Includes Android 4.3 through 6.0.1. Android 7.1 handles this correctly without this.
        // Android 4.2 and older just post the summary only.
        val singleNotifWorkArounds =
            Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.N && !notificationJob.isRestoring
        if (singleNotifWorkArounds) {
            if ((notificationJob.overriddenSound != null) && !notificationJob.overriddenSound!!
                    .equals(notificationJob.orgSound)
            ) notifBuilder!!.setSound(null)
        }
        val notification = notifBuilder!!.build()
        if (singleNotifWorkArounds) notifBuilder.setSound(notificationJob.overriddenSound)
        return notification
    }

    // Xiaomi requires the following to show a custom notification icons.
    // Without this MIUI 8 will only show the app icon on the left.
    //  When a large icon is set the small icon will no longer show.
    private fun addXiaomiSettings(
        oneSignalNotificationBuilder: OneSignalNotificationBuilder?,
        notification: Notification
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

    override suspend fun updateSummaryNotification(notificationJob: NotificationGenerationJob) {
        setStatics(notificationJob.context)
        createSummaryNotification(notificationJob, null)
    }

    // This summary notification will be visible instead of the normal one on pre-Android 7.0 devices.
    private suspend fun createSummaryNotification(
        notificationJob: NotificationGenerationJob,
        notifBuilder: OneSignalNotificationBuilder?
    ) = coroutineScope {
        val updateSummary: Boolean = notificationJob.isRestoring
        var fcmJson: JSONObject = notificationJob.jsonPayload!!
        val intentGenerator = IntentGeneratorForAttachingToNotifications(currentContext!!)
        val group = fcmJson.optString("grp", null)
        val random = SecureRandom()
        val summaryDeleteIntent = getNewDismissActionPendingIntent(
            random.nextInt(),
            getNewBaseDismissIntent(0).putExtra("summary", group)
        )
        val summaryNotification: Notification
        var summaryNotificationId: Int? = null
        var firstFullData: String? = null
        var summaryList: MutableCollection<SpannableString?>? = null

        var dbJob = launch(Dispatchers.Default) {
            var cursor: Cursor? = null
            try {
                val retColumn = arrayOf<String>(
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA,
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY,
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE,
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE
                )
                var whereStr: String =
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID.toString() + " = ? AND " +  // Where String
                            OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                            OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0"
                val whereArgs = arrayOf(group)

                // Make sure to omit any old existing matching android ids in-case we are replacing it.
                if (!updateSummary) whereStr += " AND " + OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID.toString() + " <> " + notificationJob.androidId
                cursor = _database.query(
                    OneSignalDbContract.NotificationTable.TABLE_NAME,
                    retColumn,
                    whereStr,
                    whereArgs,
                    null,  // group by
                    null, BaseColumns._ID.toString() + " DESC"
                )
                if (cursor.moveToFirst()) {
                    var spannableString: SpannableString
                    summaryList = ArrayList()
                    do {
                        if (cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY)) == 1) summaryNotificationId =
                            cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)) else {
                            var title =
                                cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE))
                            if (title == null) title = "" else title += " "
                            val msg =
                                cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE))
                            spannableString = SpannableString(title + msg)
                            if (title.length > 0) spannableString.setSpan(
                                StyleSpan(Typeface.BOLD),
                                0,
                                title.length,
                                0
                            )
                            summaryList!!.add(spannableString)
                            if (firstFullData == null) firstFullData =
                                cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA))
                        }
                    } while (cursor.moveToNext())
                    if (updateSummary && firstFullData != null) {
                        try {
                            fcmJson = JSONObject(firstFullData)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            } finally {
                if (cursor != null && !cursor.isClosed) cursor.close()
            }
            if (summaryNotificationId == null) {
                summaryNotificationId = random.nextInt()
                createSummaryIdDatabaseEntry(group, summaryNotificationId!!)
            }
        }

        dbJob.join()

        val summaryContentIntent: PendingIntent? = intentGenerator.getNewActionPendingIntent(
            random.nextInt(),
            createBaseSummaryIntent(summaryNotificationId!!, intentGenerator, fcmJson, group)
        )

        // 2 or more notifications with a group received, group them together as a single notification.
        if (summaryList != null &&
            (updateSummary && summaryList!!.size > 1 ||
                    !updateSummary && summaryList!!.size > 0)
        ) {
            val notificationCount = summaryList!!.size + if (updateSummary) 0 else 1
            var summaryMessage = fcmJson.optString("grp_msg", null)
            summaryMessage = summaryMessage?.replace("$[notif_count]", "" + notificationCount)
                ?: "$notificationCount new messages"
            val summaryBuilder = getBaseOneSignalNotificationBuilder(notificationJob).compatBuilder
            if (updateSummary) removeNotifyOptions(summaryBuilder) else {
                if (notificationJob.overriddenSound != null) summaryBuilder!!.setSound(
                    notificationJob.overriddenSound
                )
                if (notificationJob.overriddenFlags != null) summaryBuilder!!.setDefaults(
                    notificationJob.overriddenFlags!!
                )
            }

            // The summary is designed to fit all notifications.
            //   Default small and large icons are used instead of the payload options to enforce this.
            summaryBuilder!!.setContentIntent(summaryContentIntent)
                .setDeleteIntent(summaryDeleteIntent)
                .setContentTitle(
                    currentContext!!.packageManager.getApplicationLabel(
                        currentContext!!.applicationInfo
                    )
                )
                .setContentText(summaryMessage)
                .setNumber(notificationCount)
                .setSmallIcon(defaultSmallIconId)
                .setLargeIcon(defaultLargeIcon)
                .setOnlyAlertOnce(updateSummary)
                .setAutoCancel(false)
                .setGroup(group)
                .setGroupSummary(true)
            try {
                summaryBuilder.setGroupAlertBehavior(groupAlertBehavior!!)
            } catch (t: Throwable) {
                //do nothing in this case...Android support lib 26 isn't in the project
            }
            if (!updateSummary) summaryBuilder.setTicker(summaryMessage)
            val inboxStyle = NotificationCompat.InboxStyle()

            // Add the latest notification to the summary
            if (!updateSummary) {
                var line1Title: String? = null
                if (notificationJob.title != null) line1Title =
                    notificationJob.title.toString()
                if (line1Title == null) line1Title = "" else line1Title += " "
                val message: String = notificationJob.body.toString()
                val spannableString = SpannableString(line1Title + message)
                if (line1Title.length > 0) spannableString.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    line1Title.length,
                    0
                )
                inboxStyle.addLine(spannableString)
            }
            for (line in summaryList!!) inboxStyle.addLine(line)
            inboxStyle.setBigContentTitle(summaryMessage)
            summaryBuilder.setStyle(inboxStyle)
            summaryNotification = summaryBuilder.build()
        } else {
            // First notification with this group key, post like a normal notification.
            val summaryBuilder = notifBuilder!!.compatBuilder

            // TODO: We are re-using the notifBuilder from the normal notification so if a developer as an
            //  extender setup all the settings will carry over.
            //  Note: However their buttons will not carry over as we need to be setup with this new summaryNotificationId.
            summaryBuilder!!.mActions.clear()
            addNotificationActionButtons(
                fcmJson,
                intentGenerator,
                summaryBuilder,
                summaryNotificationId!!,
                group
            )
            summaryBuilder.setContentIntent(summaryContentIntent)
                .setDeleteIntent(summaryDeleteIntent)
                .setOnlyAlertOnce(updateSummary)
                .setAutoCancel(false)
                .setGroup(group)
                .setGroupSummary(true)
            try {
                summaryBuilder.setGroupAlertBehavior(groupAlertBehavior!!)
            } catch (t: Throwable) {
                //do nothing in this case...Android support lib 26 isn't in the project
            }
            summaryNotification = summaryBuilder.build()
            addXiaomiSettings(notifBuilder, summaryNotification)
        }
        NotificationManagerCompat.from(currentContext!!)
            .notify(summaryNotificationId!!, summaryNotification)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun createGrouplessSummaryNotification(
        notificationJob: NotificationGenerationJob,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        grouplessNotifCount: Int
    ) {
        val fcmJson: JSONObject = notificationJob.jsonPayload!!
        val summaryNotification: Notification
        val random = SecureRandom()
        val group: String = NotificationHelper.grouplessSummaryKey
        val summaryMessage = "$grouplessNotifCount new messages"
        val summaryNotificationId: Int = NotificationHelper.grouplessSummaryId
        val summaryContentIntent: PendingIntent? = intentGenerator.getNewActionPendingIntent(
            random.nextInt(),
            createBaseSummaryIntent(summaryNotificationId, intentGenerator, fcmJson, group)
        )
        val summaryDeleteIntent = getNewDismissActionPendingIntent(
            random.nextInt(),
            getNewBaseDismissIntent(0).putExtra("summary", group)
        )
        val summaryBuilder = getBaseOneSignalNotificationBuilder(notificationJob).compatBuilder
        if (notificationJob.overriddenSound != null) summaryBuilder!!.setSound(notificationJob.overriddenSound)
        if (notificationJob.overriddenFlags != null) summaryBuilder!!.setDefaults(
            notificationJob.overriddenFlags!!
        )

        // The summary is designed to fit all notifications.
        //   Default small and large icons are used instead of the payload options to enforce this.
        summaryBuilder!!.setContentIntent(summaryContentIntent)
            .setDeleteIntent(summaryDeleteIntent)
            .setContentTitle(
                currentContext!!.packageManager.getApplicationLabel(
                    currentContext!!.applicationInfo
                )
            )
            .setContentText(summaryMessage)
            .setNumber(grouplessNotifCount)
            .setSmallIcon(defaultSmallIconId)
            .setLargeIcon(defaultLargeIcon)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setGroup(group)
            .setGroupSummary(true)
        try {
            summaryBuilder.setGroupAlertBehavior(groupAlertBehavior!!)
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
        group: String
    ): Intent {
        return intentGenerator.getNewBaseIntent(summaryNotificationId).putExtra(
            BUNDLE_KEY_ONESIGNAL_DATA, fcmJson.toString()
        ).putExtra("summary", group)
    }

    private suspend fun createSummaryIdDatabaseEntry(group: String, id: Int) {
        // There currently isn't a visible notification from for this group_id.
        // Save the group summary notification id so it can be updated later.
        val values = ContentValues()
        values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, id)
        values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID, group)
        values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY, 1)
        _database.insertOrThrow(OneSignalDbContract.NotificationTable.TABLE_NAME, null, values)
    }

    // Keep 'throws Throwable' as 'onesignal_bgimage_notif_layout' may not be available
    //    This maybe the case if a jar is used instead of an aar.
    @Throws(Throwable::class)
    private fun addBackgroundImage(fcmJson: JSONObject, notifBuilder: NotificationCompat.Builder?) {
        // Not adding Background Images to API Versions < 16 or >= 31
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            Logging.verbose("Cannot use background images in notifications for device on version: " + Build.VERSION.SDK_INT)
            return
        }
        var bg_image: Bitmap? = null
        var jsonBgImage: JSONObject? = null
        val jsonStrBgImage = fcmJson.optString("bg_img", null)
        if (jsonStrBgImage != null) {
            jsonBgImage = JSONObject(jsonStrBgImage)
            bg_image = getBitmap(jsonBgImage.optString("img", null))
        }
        if (bg_image == null) bg_image =
            getBitmapFromAssetsOrResourceName("onesignal_bgimage_default_image")
        if (bg_image != null) {
            val customView =
                RemoteViews(currentContext!!.packageName, R.layout.onesignal_bgimage_notif_layout)
            customView.setTextViewText(R.id.os_bgimage_notif_title, getTitle(fcmJson))
            customView.setTextViewText(R.id.os_bgimage_notif_body, fcmJson.optString("alert"))
            setTextColor(
                customView,
                jsonBgImage,
                R.id.os_bgimage_notif_title,
                "tc",
                "onesignal_bgimage_notif_title_color"
            )
            setTextColor(
                customView,
                jsonBgImage,
                R.id.os_bgimage_notif_body,
                "bc",
                "onesignal_bgimage_notif_body_color"
            )
            var alignSetting: String? = null
            if (jsonBgImage != null && jsonBgImage.has("img_align")) alignSetting =
                jsonBgImage.getString("img_align") else {
                val iAlignSetting = contextResources!!.getIdentifier(
                    "onesignal_bgimage_notif_image_align",
                    "string",
                    packageName
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
                    0
                )
                customView.setImageViewBitmap(R.id.os_bgimage_notif_bgimage_right_aligned, bg_image)
                customView.setViewVisibility(
                    R.id.os_bgimage_notif_bgimage_right_aligned,
                    View.VISIBLE
                ) // visible
                customView.setViewVisibility(R.id.os_bgimage_notif_bgimage, View.GONE) // gone
            } else customView.setImageViewBitmap(R.id.os_bgimage_notif_bgimage, bg_image)
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
        colorDefaultResource: String
    ) {
        val color = safeGetColorFromHex(fcmJson, colorPayloadKey)
        if (color != null) customView.setTextColor(viewId, color) else {
            val colorId =
                contextResources!!.getIdentifier(colorDefaultResource, "color", packageName)
            if (colorId != 0) customView.setTextColor(
                viewId, AndroidSupportV4Compat.ContextCompat.getColor(
                    currentContext!!, colorId
                )
            )
        }
    }

    private fun safeGetColorFromHex(fcmJson: JSONObject?, colorKey: String): Int? {
        try {
            if (fcmJson != null && fcmJson.has(colorKey)) {
                return BigInteger(fcmJson.optString(colorKey), 16).toInt()
            }
        } catch (t: Throwable) {
        }
        return null
    }

    private fun getLargeIcon(fcmJson: JSONObject): Bitmap? {
        var bitmap = getBitmap(fcmJson.optString("licon"))
        if (bitmap == null) bitmap =
            getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default")
        return if (bitmap == null) null else resizeBitmapForLargeIconArea(
            bitmap
        )
    }

    private val defaultLargeIcon: Bitmap?
        private get() {
            val bitmap = getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default")
            return resizeBitmapForLargeIconArea(bitmap)
        }

    // Resize to prevent extra cropping and boarders.
    private fun resizeBitmapForLargeIconArea(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        try {
            val systemLargeIconHeight =
                contextResources!!.getDimension(R.dimen.notification_large_icon_height).toInt()
            val systemLargeIconWidth =
                contextResources!!.getDimension(R.dimen.notification_large_icon_width).toInt()
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
            val image_extensions = Arrays.asList(".png", ".webp", ".jpg", ".gif", ".bmp")
            for (extension in image_extensions) {
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
        return if (trimmedName.startsWith("http://") || trimmedName.startsWith("https://")) getBitmapFromURL(
            trimmedName
        ) else getBitmapFromAssetsOrResourceName(
            name
        )
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

    private val defaultSmallIconId: Int
        private get() {
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
    fun getAccentColor(fcmJson: JSONObject): BigInteger? {
        try {
            if (fcmJson.has("bgac")) return BigInteger(fcmJson.optString("bgac", null), 16)
        } catch (t: Throwable) {
        } // Can throw a parse error.

        // Try to get "onesignal_notification_accent_color" from resources
        // This will get the correct color for day and dark modes
        try {
            val defaultColor: String? =
                AndroidUtils.getResourceString(_applicationService.appContext!!, "onesignal_notification_accent_color", null)
            if (defaultColor != null) {
                return BigInteger(defaultColor, 16)
            }
        } catch (t: Throwable) {
        } // Can throw a parse error.

        // Get accent color from Manifest
        try {
            val defaultColor: String? = AndroidUtils.getManifestMeta(_applicationService.appContext!!, "com.onesignal.NotificationAccentColor.DEFAULT")
            if (defaultColor != null) {
                return BigInteger(defaultColor, 16)
            }
        } catch (t: Throwable) {
        } // Can throw a parse error.
        return null
    }

    private fun addNotificationActionButtons(
        fcmJson: JSONObject,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        mBuilder: NotificationCompat.Builder?,
        notificationId: Int,
        groupSummary: String?
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
                buttonIntent.action =
                    "" + i // Required to keep each action button from replacing extras of each other
                buttonIntent.putExtra("action_button", true)
                bundle.put(BUNDLE_KEY_ACTION_ID, button.optString("id"))
                buttonIntent.putExtra(BUNDLE_KEY_ONESIGNAL_DATA, bundle.toString())
                if (groupSummary != null) buttonIntent.putExtra(
                    "summary",
                    groupSummary
                ) else if (fcmJson.has("grp")) buttonIntent.putExtra(
                    "grp",
                    fcmJson.optString("grp")
                )
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
        buttonsIds: MutableList<String>
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
                    "Ok"
                )!!
            )
            buttonsIds.add(NotificationBundleProcessor.DEFAULT_ACTION)
        }
    }

    @Throws(JSONException::class)
    private fun addCustomAlertButtons(
        fcmJson: JSONObject,
        buttonsLabels: MutableList<String>,
        buttonsIds: MutableList<String>
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

    private class OneSignalNotificationBuilder {
        var compatBuilder: NotificationCompat.Builder? = null
        var hasLargeIcon = false
    }
}