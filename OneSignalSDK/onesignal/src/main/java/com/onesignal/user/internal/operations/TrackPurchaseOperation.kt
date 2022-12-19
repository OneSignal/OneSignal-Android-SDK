package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.common.modeling.Model
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor
import org.json.JSONArray
import java.math.BigDecimal

/**
 * An [Operation] to track the purchase of one or more items within an app by a specific
 * user.
 */
class TrackPurchaseOperation() : Operation(UpdateUserOperationExecutor.TRACK_PURCHASE) {
    /**
     * The OneSignal appId the purchase was captured under.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) { setStringProperty(::appId.name, value) }

    /**
     * The OneSignal ID the purchase was captured under. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) { setStringProperty(::onesignalId.name, value) }

    /**
     * Whether to treat new purchases as an existing purchase.
     */
    var treatNewAsExisting: Boolean
        get() = getBooleanProperty(::treatNewAsExisting.name)
        private set(value) { setBooleanProperty(::treatNewAsExisting.name, value) }

    /**
     * The amount spent by the user.
     */
    var amountSpent: BigDecimal
        get() = getBigDecimalProperty(::amountSpent.name)
        private set(value) { setBigDecimalProperty(::amountSpent.name, value) }

    /**
     * The list of purchases that have been made.
     */
    var purchases: List<PurchaseInfo>
        get() = getListProperty(::purchases.name)
        private set(value) { setListProperty(::purchases.name, value) }

    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId)

    constructor(appId: String, onesignalId: String, treatNewAsExisting: Boolean, amountSpent: BigDecimal, purchases: List<PurchaseInfo>) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.treatNewAsExisting = treatNewAsExisting
        this.amountSpent = amountSpent
        this.purchases = purchases
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
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
class PurchaseInfo() : Model() {
    var sku: String
        get() = getStringProperty(::sku.name)
        private set(value) { setStringProperty(::sku.name, value) }

    var iso: String
        get() = getStringProperty(::iso.name)
        private set(value) { setStringProperty(::iso.name, value) }

    var amount: BigDecimal
        get() = getBigDecimalProperty(::amount.name)
        private set(value) { setBigDecimalProperty(::amount.name, value) }

    constructor(sku: String, iso: String, amount: BigDecimal) : this() {
        this.sku = sku
        this.iso = iso
        this.amount = amount
    }
}
