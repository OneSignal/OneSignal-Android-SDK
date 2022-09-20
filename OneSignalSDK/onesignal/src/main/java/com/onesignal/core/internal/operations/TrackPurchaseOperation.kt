package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import org.json.JSONArray
import org.json.JSONObject
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
     * The OneSignal ID the purchase was captured under. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    val onesignalId: String,

    /**
     * Whether to treat new purchases as an existing purchase.
     */
    val treatNewAsExisting: Boolean,

    /**
     * The amount spent by the user.
     */
    val amountSpent: BigDecimal,

    /**
     * The list of purchases that have been made.
     */
    val purchases: List<PurchaseInfo>
) : Operation(UserOperationExecutor.TRACK_PURCHASE) {

    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        val purchaseArray = JSONArray()
        for (purchase in purchases) {
            purchaseArray.put(
                JSONObject()
                    .put(PurchaseInfo::sku.name, purchase.sku)
                    .put(PurchaseInfo::iso.name, purchase.iso)
                    .put(PurchaseInfo::amount.name, purchase.amount)
            )
        }
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::treatNewAsExisting.name, treatNewAsExisting)
            .put(::amountSpent.name, amountSpent)
            .put(::purchases.name, purchaseArray)
    }
}

/**
 * Information about a specific purchase.
 */
internal class PurchaseInfo(
    val sku: String,
    val iso: String,
    val amount: BigDecimal
)
