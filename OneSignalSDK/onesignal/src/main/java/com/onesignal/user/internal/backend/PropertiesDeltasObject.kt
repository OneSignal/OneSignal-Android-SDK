package com.onesignal.user.internal.backend

import java.math.BigDecimal

class PropertiesDeltasObject(
    val sessionTime: Long? = null,
    val sessionCounts: Int? = null,
    val amountSpent: BigDecimal? = null,
    val purchases: List<PurchaseObject>? = null
)

class PurchaseObject(
    val sku: String,
    val iso: String,
    val amount: BigDecimal
)
