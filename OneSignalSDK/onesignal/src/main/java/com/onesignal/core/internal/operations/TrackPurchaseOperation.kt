package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.UserOperationExecutor
import java.math.BigDecimal

/**
 * An [Operation] to track the purchase of one or more items within an app by a specific
 * user.
 */
internal class TrackPurchaseOperation(
    /**
     * The OneSignal appId the purchase was captured under.
     */
    val appId: String,

    /**
     * The OneSignal ID the purchase was captured under.
     */
    val userId: String,

    /**
     * Whether to treat new purchases as an existing purchase.
     */
    val treatNewAsExisting: Boolean,

    /**
     * The list of purchases that have been made.
     */
    val purchases: List<PurchaseInfo>
) : Operation(UserOperationExecutor.TRACK_PURCHASE)

/**
 * Information about a specific purchase.
 */
internal class PurchaseInfo(
    val sku: String,
    val iso: String,
    val amount: BigDecimal
)
