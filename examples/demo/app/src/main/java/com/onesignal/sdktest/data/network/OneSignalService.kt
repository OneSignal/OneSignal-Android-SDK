package com.onesignal.sdktest.data.network

import com.onesignal.OneSignal
import com.onesignal.sdktest.data.model.NotificationType
import com.onesignal.sdktest.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OneSignalService {
    
    private const val TAG = "OneSignalService"
    private const val ONESIGNAL_API_URL = "https://onesignal.com/api/v1/notifications"
    private const val ONESIGNAL_API_BASE_URL = "https://api.onesignal.com"
    
    private var appId: String = ""

    fun setAppId(appId: String) {
        this.appId = appId
    }
    
    fun getAppId(): String = appId

    private fun getSubscriptionIdIfOptedIn(): String? {
        val subscription = OneSignal.User.pushSubscription
        if (!subscription.optedIn) {
            LogManager.w(TAG, "Cannot send notification - user not opted in")
            return null
        }
        val id = subscription.id
        if (id.isNullOrEmpty()) {
            LogManager.w(TAG, "Cannot send notification - no subscription ID")
            return null
        }
        return id
    }

    private fun postJson(url: String, json: JSONObject): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            useCaches = false
            connectTimeout = 30000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.onesignal.v1+json")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            requestMethod = "POST"
            doOutput = true
            doInput = true
        }
        val outputBytes = json.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(outputBytes.size)
        connection.outputStream.write(outputBytes)
        val code = connection.responseCode
        val body = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
        }
        return code to body
    }

    private suspend fun sendNotificationPayload(payload: JSONObject, label: String): Boolean = withContext(Dispatchers.IO) {
        try {
            LogManager.d(TAG, "Sending $label: ${payload.toString(2)}")
            val (code, body) = postJson(ONESIGNAL_API_URL, payload)
            if (code in 200..299) {
                LogManager.d(TAG, "$label sent successfully: $body")
                true
            } else {
                LogManager.e(TAG, "Failed to send $label (HTTP $code): $body")
                false
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error sending $label", e)
            false
        }
    }
    
    suspend fun sendNotification(type: NotificationType): Boolean {
        val subscriptionId = getSubscriptionIdIfOptedIn() ?: return false
        val payload = JSONObject().apply {
            put("app_id", appId)
            put("include_subscription_ids", org.json.JSONArray().put(subscriptionId))
            put("headings", JSONObject().put("en", type.notificationTitle))
            put("contents", JSONObject().put("en", type.notificationBody))
            put("android_group", type.title)
            put("android_led_color", "FF595CF2")
            put("android_accent_color", "FF595CF2")
            type.largeIcon?.let { put("large_icon", it) }
            type.bigPicture?.let { put("big_picture", it) }
            type.androidChannelId?.let { put("android_channel_id", it) }
        }
        return sendNotificationPayload(payload, "notification")
    }
    
    suspend fun sendCustomNotification(title: String, body: String): Boolean {
        val subscriptionId = getSubscriptionIdIfOptedIn() ?: return false
        val payload = JSONObject().apply {
            put("app_id", appId)
            put("include_subscription_ids", org.json.JSONArray().put(subscriptionId))
            put("headings", JSONObject().put("en", title))
            put("contents", JSONObject().put("en", body))
            put("android_led_color", "FF595CF2")
            put("android_accent_color", "FF595CF2")
        }
        return sendNotificationPayload(payload, "custom notification")
    }
    
    suspend fun fetchUser(onesignalId: String): UserData? = withContext(Dispatchers.IO) {
        if (onesignalId.isEmpty()) {
            LogManager.w(TAG, "Cannot fetch user - onesignalId is empty")
            return@withContext null
        }
        
        if (appId.isEmpty()) {
            LogManager.w(TAG, "Cannot fetch user - appId not set")
            return@withContext null
        }
        
        try {
            val url = "$ONESIGNAL_API_BASE_URL/apps/$appId/users/by/onesignal_id/$onesignalId"
            LogManager.d(TAG, "Fetching user data from: $url")
            
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
                LogManager.d(TAG, "User data fetched successfully")
                parseUserResponse(response)
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                LogManager.e(TAG, "Failed to fetch user (HTTP $responseCode): $errorResponse")
                null
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching user", e)
            null
        }
    }
    
    private fun parseUserResponse(json: String): UserData {
        val jsonObject = JSONObject(json)
        
        val aliases = mutableMapOf<String, String>()
        val externalId: String?
        
        if (jsonObject.has("identity")) {
            val identity = jsonObject.getJSONObject("identity")
            identity.keys().forEach { key ->
                if (key != "external_id" && key != "onesignal_id") {
                    aliases[key] = identity.getString(key)
                }
            }
            externalId = if (identity.has("external_id")) identity.getString("external_id") else null
        } else {
            externalId = null
        }
        
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

data class UserData(
    val aliases: Map<String, String>,
    val tags: Map<String, String>,
    val emails: List<String>,
    val smsNumbers: List<String>,
    val externalId: String?
)
