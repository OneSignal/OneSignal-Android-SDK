package com.onesignal

import android.app.Activity
import android.app.AlertDialog

object AlertDialogPrepromptForAndroidSettings {

    interface Callback {
        fun onAccept()
        fun onDecline()
    }

    fun show(
        activity: Activity,
        titlePrefix: String,
        previouslyDeniedPostfix: String,
        callback: Callback,
    ) {
        val titleTemplate = activity.getString(R.string.permission_not_available_title)
        val title = titleTemplate.format(titlePrefix)

        val messageTemplate = activity.getString(R.string.permission_not_available_message)
        val message = messageTemplate.format(previouslyDeniedPostfix)

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.permission_not_available_open_settings_option) { dialog, which ->
                callback.onAccept()
            }
            .setNegativeButton(android.R.string.no) { dialog, which ->
                callback.onDecline()
            }
            .show()
    }
}
