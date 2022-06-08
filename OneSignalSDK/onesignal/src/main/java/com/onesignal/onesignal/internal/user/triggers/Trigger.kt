package com.onesignal.onesignal.internal.user.triggers

import com.onesignal.onesignal.internal.collections.OSKeyedItem

/**
 * A trigger is a key/value pair that is used to drive IAM.
 */
class Trigger(
        override val key: String,
        val value: Any
) : OSKeyedItem<String>