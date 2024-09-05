package com.onesignal.common.consistency.enums

import com.onesignal.common.consistency.models.IConsistencyKeyEnum

/**
 * Each enum is a key that we use to keep track of offsets that function as read-your-write tokens.
 */
enum class IamFetchOffsetKey : IConsistencyKeyEnum {
    USER_UPDATE,
    SUBSCRIPTION_UPDATE,
}
