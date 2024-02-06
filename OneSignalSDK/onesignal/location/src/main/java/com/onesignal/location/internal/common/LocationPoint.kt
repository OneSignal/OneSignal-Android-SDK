package com.onesignal.location.internal.common

internal class LocationPoint {
    var lat: Double? = null
    var log: Double? = null
    var accuracy: Float? = null
    var type: Int? = null
    var bg: Boolean? = null
    var timeStamp: Long? = null

    override fun toString(): String {
        return "LocationPoint{" +
            "lat=" + lat +
            ", log=" + log +
            ", accuracy=" + accuracy +
            ", type=" + type +
            ", bg=" + bg +
            ", timeStamp=" + timeStamp +
            '}'
    }
}
