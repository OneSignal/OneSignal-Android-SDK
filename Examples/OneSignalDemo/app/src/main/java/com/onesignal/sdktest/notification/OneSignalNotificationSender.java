package com.onesignal.sdktest.notification;

import android.util.Log;

import com.onesignal.onesignal.core.Continue;
import com.onesignal.onesignal.core.OneSignal;
import com.onesignal.onesignal.core.user.subscriptions.IPushSubscription;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.type.Notification;

import org.json.JSONException;
import org.json.JSONObject;

public class OneSignalNotificationSender {

    public static void sendDeviceNotification(final Notification notification) {
        new Thread(() -> {
            IPushSubscription subscription = OneSignal.getUser().getSubscriptions().getPush();

            if (subscription == null || !subscription.getEnabled())
                return;

            int pos = notification.getTemplatePos();
            try {
                JSONObject notificationContent = new JSONObject("{'include_player_ids': ['" + subscription.getId() + "']," +
                        "'headings': {'en': '" + notification.getTitle(pos) + "'}," +
                        "'contents': {'en': '" + notification.getMessage(pos) + "'}," +
                        "'small_icon': '" + notification.getSmallIconRes() + "'," +
                        "'large_icon': '" + notification.getLargeIconUrl(pos) + "'," +
                        "'big_picture': '" + notification.getBigPictureUrl(pos) + "'," +
                        "'android_group': '" + notification.getGroup() + "'," +
                        "'buttons': " + notification.getButtons() + "," +
                        "'android_led_color': 'FFE9444E'," +
                        "'android_accent_color': 'FFE9444E'," +
                        "'android_sound': 'nil'}");

                OneSignal.getNotifications().postNotification(notificationContent, Continue.with(r -> {
                    if(r.isSuccess())
                    {
                        Log.d(Tag.DEBUG, "Success sending notification: " + r.getData().toString());
                    }
                    else
                    {
                        Log.d(Tag.ERROR, "Failure sending notification: " + r.getData().toString());
                    }
                }));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }).start();
    }

}
