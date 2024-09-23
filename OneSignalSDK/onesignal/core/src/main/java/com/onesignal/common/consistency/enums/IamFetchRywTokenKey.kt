package com.onesignal.common.consistency.enums

import com.onesignal.common.consistency.models.IConsistencyKeyEnum

/**
 * Each enum is a key that we use to keep track of read-your-write tokens.
 * Although the enums are named with "UPDATE", they serve as keys for tokens from both PATCH & POST
 */
enum class IamFetchRywTokenKey : IConsistencyKeyEnum {
    USER_UPDATE,
    SUBSCRIPTION_UPDATE,
}
