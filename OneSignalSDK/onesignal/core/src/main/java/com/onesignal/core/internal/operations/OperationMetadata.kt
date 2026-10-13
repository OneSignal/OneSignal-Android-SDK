package com.onesignal.core.internal.operations

/** Executor-specific wait payload. Add a subtype when a new enqueue-and-wait needs extra fields. */
sealed interface OperationMetadata

internal data class LoginWaitMetadata(
    val onesignalId: String,
    val emailSubscriptionId: String? = null,
    val smsSubscriptionId: String? = null,
) : OperationMetadata
