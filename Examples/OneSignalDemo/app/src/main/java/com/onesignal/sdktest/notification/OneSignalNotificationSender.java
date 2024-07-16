package com.onesignal.sdktest.notification;

import android.os.Build;
import android.util.Log;

import com.onesignal.OneSignal;
import com.onesignal.user.subscriptions.IPushSubscription;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.type.Notification;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class OneSignalNotificationSender {

    public static void setAppId(String appId) {
        _appId = appId;
    }
    private static String _appId = "";

    /**
     * Send a notification to this device through the OneSignal Platform.  Note this form of the
     * API should not be used in a production setting as it is not safe.  Rather, the device should
     * make an API call to it's own backend, which should handle the API call to OneSignal (where it
     * can safely provide the application API Key).
     *
     * @param notification The notification that is to be sent.
     */
    public static void sendDeviceNotification(final Notification notification) {
        new Thread(() -> {
            IPushSubscription subscription = OneSignal.getUser().getPushSubscription();

            if (!subscription.getOptedIn())
                return;

            int pos = notification.getTemplatePos();
            try {
                JSONObject notificationContent = new JSONObject("{'app_id': '" + _appId + "', 'include_player_ids': ['" + subscription.getId() + "']," +
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

                HttpURLConnection con = (HttpURLConnection) new URL("https://onesignal.com/api/v1/notifications").openConnection();

                con.setUseCaches(false);
                con.setConnectTimeout(30000);
                con.setReadTimeout(30000);
                con.setRequestProperty("Accept", "application/vnd.onesignal.v1+json");
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setDoInput(true);

                byte[] outputBytes = notificationContent.toString().getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(outputBytes.length);
                con.getOutputStream().write(outputBytes);

                int httpResponse = con.getResponseCode();

                if(httpResponse == HttpURLConnection.HTTP_ACCEPTED || httpResponse == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = con.getInputStream();
                    Scanner scanner = new Scanner(inputStream, "UTF-8");
                    String responseStr = "";
                    if (scanner.useDelimiter("\\A").hasNext())
                        responseStr = scanner.next();
                    scanner.close();
                    Log.d(Tag.LOG_TAG, "Success sending notification: " + responseStr);
                }
                else {
                    InputStream inputStream = con.getErrorStream();
                    Scanner scanner = new Scanner(inputStream, "UTF-8");
                    String responseStr = "";
                    if (scanner.useDelimiter("\\A").hasNext())
                        responseStr = scanner.next();
                    scanner.close();
                    Log.d(Tag.LOG_TAG, "Failure sending notification: " + responseStr);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
