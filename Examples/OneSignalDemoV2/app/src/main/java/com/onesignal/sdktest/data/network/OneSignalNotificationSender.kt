package com.onesignal.sdktest.data.network

import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.sdktest.data.model.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends push notifications to the current device via the OneSignal REST API.
 * 
 * Note: This approach is for testing purposes only. In production, notifications
 * should be sent from your backend server which can securely provide the API key.
 */
object OneSignalNotificationSender {
    
    private const val TAG = "OneSignalNotificationSender"
    private const val ONESIGNAL_API_URL = "https://onesignal.com/api/v1/notifications"
    
    private var appId: String = ""
    
    fun setAppId(appId: String) {
        this.appId = appId
    }
    
    /**
     * Send a notification to this device.
     */
    suspend fun sendNotification(type: NotificationType): Boolean = withContext(Dispatchers.IO) {
        val subscription = OneSignal.User.pushSubscription
        
        if (!subscription.optedIn) {
            Log.w(TAG, "Cannot send notification - user not opted in")
            return@withContext false
        }
        
        val subscriptionId = subscription.id
        if (subscriptionId.isNullOrEmpty()) {
            Log.w(TAG, "Cannot send notification - no subscription ID")
            return@withContext false
        }
        
        try {
            val notificationJson = JSONObject().apply {
                put("app_id", appId)
                put("include_player_ids", org.json.JSONArray().put(subscriptionId))
                put("headings", JSONObject().put("en", type.notificationTitle))
                put("contents", JSONObject().put("en", type.notificationBody))
                put("android_group", type.title)
                put("android_led_color", "FFE9444E")
                put("android_accent_color", "FFE9444E")
            }
            
            val connection = (URL(ONESIGNAL_API_URL).openConnection() as HttpURLConnection).apply {
                useCaches = false
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/vnd.onesignal.v1+json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                requestMethod = "POST"
                doOutput = true
                doInput = true
            }
            
            val outputBytes = notificationJson.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(outputBytes.size)
            connection.outputStream.write(outputBytes)
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_ACCEPTED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Notification sent successfully: $response")
                return@withContext true
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Failed to send notification (HTTP $responseCode): $errorResponse")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
            return@withContext false
        }
    }
    
    /**
     * Send a custom notification with title and body.
     */
    suspend fun sendCustomNotification(title: String, body: String): Boolean = withContext(Dispatchers.IO) {
        val subscription = OneSignal.User.pushSubscription
        
        if (!subscription.optedIn) {
            Log.w(TAG, "Cannot send notification - user not opted in")
            return@withContext false
        }
        
        val subscriptionId = subscription.id
        if (subscriptionId.isNullOrEmpty()) {
            Log.w(TAG, "Cannot send notification - no subscription ID")
            return@withContext false
        }
        
        try {
            val notificationJson = JSONObject().apply {
                put("app_id", appId)
                put("include_player_ids", org.json.JSONArray().put(subscriptionId))
                put("headings", JSONObject().put("en", title))
                put("contents", JSONObject().put("en", body))
                put("android_led_color", "FFE9444E")
                put("android_accent_color", "FFE9444E")
            }
            
            val connection = (URL(ONESIGNAL_API_URL).openConnection() as HttpURLConnection).apply {
                useCaches = false
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/vnd.onesignal.v1+json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                requestMethod = "POST"
                doOutput = true
                doInput = true
            }
            
            val outputBytes = notificationJson.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(outputBytes.size)
            connection.outputStream.write(outputBytes)
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_ACCEPTED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Custom notification sent successfully: $response")
                return@withContext true
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Failed to send custom notification (HTTP $responseCode): $errorResponse")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending custom notification", e)
            return@withContext false
        }
    }
}
