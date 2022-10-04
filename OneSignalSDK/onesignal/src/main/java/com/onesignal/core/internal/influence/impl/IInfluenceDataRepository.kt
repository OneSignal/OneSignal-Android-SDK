package com.onesignal.core.internal.influence.impl

import com.onesignal.core.internal.influence.InfluenceType
import org.json.JSONArray
import org.json.JSONException

internal interface IInfluenceDataRepository {
    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    val notificationCachedInfluenceType: InfluenceType

    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    val iamCachedInfluenceType: InfluenceType

    /**
     * Get the current cached notification id, null if not direct
     */
    val cachedNotificationOpenId: String?

    @get:Throws(JSONException::class)
    val lastNotificationsReceivedData: JSONArray

    @get:Throws(JSONException::class)
    val lastIAMsReceivedData: JSONArray
    val notificationLimit: Int
    val iamLimit: Int
    val notificationIndirectAttributionWindow: Int
    val iamIndirectAttributionWindow: Int
    val isDirectInfluenceEnabled: Boolean
    val isIndirectInfluenceEnabled: Boolean
    val isUnattributedInfluenceEnabled: Boolean

    /**
     * Cache a influence type enum for Notification as a string
     */
    fun cacheNotificationInfluenceType(influenceType: InfluenceType)

    /**
     * Cache a influence type enum for IAM as a string
     */
    fun cacheIAMInfluenceType(influenceType: InfluenceType)

    /**
     * Cache attributed notification opened
     */
    fun cacheNotificationOpenId(id: String?)
    fun saveNotifications(notifications: JSONArray)
    fun saveIAMs(iams: JSONArray)
}
