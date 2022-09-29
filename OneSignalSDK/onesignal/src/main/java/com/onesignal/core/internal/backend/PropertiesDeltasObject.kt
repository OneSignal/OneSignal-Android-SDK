package com.onesignal.core.internal.backend

import java.math.BigDecimal

internal class PropertiesDeltasObject(
    val sessionTime: Long? = null,
    val sessionCounts: Int? = null,
    val amountSpent: BigDecimal? = null,
    val purchases: List<PurchaseObject>? = null
)

internal class PurchaseObject(
    val sku: String,
    val iso: String,
    val amount: BigDecimal
)
