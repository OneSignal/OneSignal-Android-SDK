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
 * OneSignal API service for testing purposes.
 * Provides methods to send notifications and fetch user data via the REST API.
 * 
 * Note: This approach is for testing purposes only. In production, notifications
 * should be sent from your backend server.
 */
object OneSignalService {
    
    private const val TAG = "OneSignalService"
    private const val ONESIGNAL_API_URL = "https://onesignal.com/api/v1/notifications"
    private const val ONESIGNAL_API_BASE_URL = "https://api.onesignal.com"
    
    private var appId: String = ""
    private var restApiKey: String = ""
    
    fun setAppId(appId: String) {
        this.appId = appId
    }
    
    fun getAppId(): String = appId
    
    /**
     * Set the REST API key for sending notifications.
     * This is required for sending notifications via the REST API.
     */
    fun setRestApiKey(key: String) {
        this.restApiKey = key
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
        
        if (restApiKey.isEmpty()) {
            Log.w(TAG, "Cannot send notification - REST API key not set")
            return@withContext false
        }
        
        try {
            val notificationJson = JSONObject().apply {
                put("app_id", appId)
                put("include_subscription_ids", org.json.JSONArray().put(subscriptionId))
                put("headings", JSONObject().put("en", type.notificationTitle))
                put("contents", JSONObject().put("en", type.notificationBody))
                put("android_group", type.title)
                put("android_led_color", "FFE9444E")
                put("android_accent_color", "FFE9444E")
                // Add big picture if available
                type.bigPicture?.let { 
                    put("big_picture", it)
                    Log.d(TAG, "Adding big_picture: $it")
                }
            }
            
            Log.d(TAG, "Sending notification: ${notificationJson.toString(2)}")
            
            val connection = (URL(ONESIGNAL_API_URL).openConnection() as HttpURLConnection).apply {
                useCaches = false
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Basic $restApiKey")
                requestMethod = "POST"
                doOutput = true
                doInput = true
            }
            
            val outputBytes = notificationJson.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(outputBytes.size)
            connection.outputStream.write(outputBytes)
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_ACCEPTED || responseCode == HttpURLConnection.HTTP_CREATED) {
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
        
        if (restApiKey.isEmpty()) {
            Log.w(TAG, "Cannot send notification - REST API key not set")
            return@withContext false
        }
        
        try {
            val notificationJson = JSONObject().apply {
                put("app_id", appId)
                put("include_subscription_ids", org.json.JSONArray().put(subscriptionId))
                put("headings", JSONObject().put("en", title))
                put("contents", JSONObject().put("en", body))
                put("android_led_color", "FFE9444E")
                put("android_accent_color", "FFE9444E")
            }
            
            val connection = (URL(ONESIGNAL_API_URL).openConnection() as HttpURLConnection).apply {
                useCaches = false
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Basic $restApiKey")
                requestMethod = "POST"
                doOutput = true
                doInput = true
            }
            
            val outputBytes = notificationJson.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(outputBytes.size)
            connection.outputStream.write(outputBytes)
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_ACCEPTED || responseCode == HttpURLConnection.HTTP_CREATED) {
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
    
    /**
     * Fetch user data from OneSignal API.
     * Note: This endpoint does not require authentication.
     * 
     * @param onesignalId The OneSignal user ID
     * @return UserData object containing aliases, tags, emails, and SMS numbers, or null on error
     */
    suspend fun fetchUser(onesignalId: String): UserData? = withContext(Dispatchers.IO) {
        if (onesignalId.isEmpty()) {
            Log.w(TAG, "Cannot fetch user - onesignalId is empty")
            return@withContext null
        }
        
        if (appId.isEmpty()) {
            Log.w(TAG, "Cannot fetch user - appId not set")
            return@withContext null
        }
        
        try {
            val url = "$ONESIGNAL_API_BASE_URL/apps/$appId/users/by/onesignal_id/$onesignalId"
            Log.d(TAG, "Fetching user data from: $url")
            
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                useCaches = false
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/json")
                requestMethod = "GET"
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "User data fetched successfully, parsing response...")
                try {
                    val userData = parseUserResponse(response)
                    Log.d(TAG, "Parsed user data: aliases=${userData.aliases.size}, tags=${userData.tags.size}, emails=${userData.emails.size}, sms=${userData.smsNumbers.size}")
                    return@withContext userData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user response", e)
                    return@withContext null
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Failed to fetch user (HTTP $responseCode): $errorResponse")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user", e)
            return@withContext null
        }
    }
    
    private fun parseUserResponse(json: String): UserData {
        val jsonObject = JSONObject(json)
        
        // Parse aliases from identity object (filter out external_id and onesignal_id)
        val aliases = mutableMapOf<String, String>()
        if (jsonObject.has("identity")) {
            val identity = jsonObject.getJSONObject("identity")
            identity.keys().forEach { key ->
                if (key != "external_id" && key != "onesignal_id") {
                    aliases[key] = identity.getString(key)
                }
            }
        }
        
        // Parse external_id separately
        val externalId = if (jsonObject.has("identity")) {
            val identity = jsonObject.getJSONObject("identity")
            if (identity.has("external_id")) identity.getString("external_id") else null
        } else null
        
        // Parse tags from properties object
        val tags = mutableMapOf<String, String>()
        if (jsonObject.has("properties")) {
            val properties = jsonObject.getJSONObject("properties")
            if (properties.has("tags")) {
                val tagsObj = properties.getJSONObject("tags")
                tagsObj.keys().forEach { key ->
                    tags[key] = tagsObj.getString(key)
                }
            }
        }
        
        // Parse subscriptions for emails and SMS
        val emails = mutableListOf<String>()
        val smsNumbers = mutableListOf<String>()
        if (jsonObject.has("subscriptions")) {
            val subscriptions = jsonObject.getJSONArray("subscriptions")
            for (i in 0 until subscriptions.length()) {
                val subscription = subscriptions.getJSONObject(i)
                val type = subscription.optString("type", "")
                val token = subscription.optString("token", "")
                
                when (type) {
                    "Email" -> if (token.isNotEmpty()) emails.add(token)
                    "SMS" -> if (token.isNotEmpty()) smsNumbers.add(token)
                }
            }
        }
        
        return UserData(
            aliases = aliases,
            tags = tags,
            emails = emails,
            smsNumbers = smsNumbers,
            externalId = externalId
        )
    }
}

/**
 * Data class representing user data fetched from the OneSignal API.
 */
data class UserData(
    val aliases: Map<String, String>,
    val tags: Map<String, String>,
    val emails: List<String>,
    val smsNumbers: List<String>,
    val externalId: String?
)
