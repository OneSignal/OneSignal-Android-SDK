package com.onesignal.user.internal.backend

import java.math.BigDecimal

class PropertiesDeltasObject(
    val sessionTime: Long? = null,
    val sessionCount: Int? = null,
    val amountSpent: BigDecimal? = null,
    val purchases: List<PurchaseObject>? = null
) {
    val hasAtLeastOnePropertySet : Boolean
        get() = sessionTime != null || sessionCount != null || amountSpent != null || purchases != null
}

class PurchaseObject(
    val sku: String,
    val iso: String,
    val amount: BigDecimal
)
