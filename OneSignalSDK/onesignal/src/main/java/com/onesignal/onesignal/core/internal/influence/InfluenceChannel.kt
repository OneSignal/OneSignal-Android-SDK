package com.onesignal.onesignal.core.internal.influence

enum class InfluenceChannel(private val nameValue: String) {
    IAM("iam"),
    NOTIFICATION("notification"),
    ;

    fun equalsName(otherName: String) = nameValue == otherName

    override fun toString() = nameValue

    companion object {
        @JvmStatic
        fun fromString(value: String?) = value?.let {
            values().findLast { it.equalsName(value) }
        } ?: NOTIFICATION
    }
}