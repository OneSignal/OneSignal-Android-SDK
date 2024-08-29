package com.onesignal.common.consistency.enums

/**
 * Each enum is a key that we use to keep track of offsets that function as read-your-write tokens.
 */
enum class IamFetchOffsetKey {
    USER_UPDATE,
    SUBSCRIPTION_UPDATE,
}
