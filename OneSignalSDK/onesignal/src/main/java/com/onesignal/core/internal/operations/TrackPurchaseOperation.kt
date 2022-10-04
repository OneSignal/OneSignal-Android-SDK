package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.modeling.Model
import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import org.json.JSONArray
import java.math.BigDecimal

/**
 * An [Operation] to track the purchase of one or more items within an app by a specific
 * user.
 */
internal class TrackPurchaseOperation() : Operation(UserOperationExecutor.TRACK_PURCHASE) {
    /**
     * The OneSignal appId the purchase was captured under.
     */
    var appId: String
        get() = getProperty(::appId.name)
        private set(value) { setProperty(::appId.name, value) }

    /**
     * The OneSignal ID the purchase was captured under. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    var onesignalId: String
        get() = getProperty(::onesignalId.name)
        private set(value) { setProperty(::onesignalId.name, value) }

    /**
     * Whether to treat new purchases as an existing purchase.
     */
    var treatNewAsExisting: Boolean
        get() = getProperty(::treatNewAsExisting.name)
        private set(value) { setProperty(::treatNewAsExisting.name, value) }

    /**
     * The amount spent by the user.
     */
    var amountSpent: BigDecimal
        get() = getProperty(::amountSpent.name)
        private set(value) { setProperty(::amountSpent.name, value) }

    /**
     * The list of purchases that have been made.
     */
    var purchases: List<PurchaseInfo>
        get() = getProperty(::purchases.name)
        private set(value) { setProperty(::purchases.name, value) }

    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, treatNewAsExisting: Boolean, amountSpent: BigDecimal, purchases: List<PurchaseInfo>) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.treatNewAsExisting = treatNewAsExisting
        this.amountSpent = amountSpent
        this.purchases = purchases
    }

    override fun createListForProperty(property: String, jsonArray: JSONArray): List<*>? {
        if (property == ::purchases.name) {
            val listOfPurchases = mutableListOf<PurchaseInfo>()
            for (item in 0 until jsonArray.length()) {
                val purchaseModel = PurchaseInfo()
                purchaseModel.initializeFromJson(jsonArray.getJSONObject(item))
                listOfPurchases.add(purchaseModel)
            }
            return listOfPurchases
        }

        return null
    }
}

/**
 * Information about a specific purchase.
 */
internal class PurchaseInfo() : Model() {
    var sku: String
        get() = getProperty(::sku.name)
        private set(value) { setProperty(::sku.name, value) }

    var iso: String
        get() = getProperty(::iso.name)
        private set(value) { setProperty(::iso.name, value) }

    var amount: BigDecimal
        get() = getProperty(::amount.name)
        private set(value) { setProperty(::amount.name, value) }

    constructor(sku: String, iso: String, amount: BigDecimal) : this() {
        this.sku = sku
        this.iso = iso
        this.amount = amount
    }
}
