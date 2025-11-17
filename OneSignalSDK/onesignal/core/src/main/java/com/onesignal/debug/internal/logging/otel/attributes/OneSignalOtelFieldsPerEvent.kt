package com.onesignal.debug.internal.logging.otel.attributes

import com.onesignal.common.IDManager
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.identity.IdentityModelStore
import com.squareup.wire.internal.toUnmodifiableMap
import java.util.UUID

internal class OneSignalOtelFieldsPerEvent(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
    private val _time: ITime,
) {
    fun getAttributes(): Map<String, String> {
        val attributes: MutableMap<String, String> = mutableMapOf()

        attributes["log.record.uid"] = recordId.toString()

        attributes
            .putIfValueNotNull(
                "$OS_OTEL_NAMESPACE.app_id",
                appId
            ).putIfValueNotNull(
                "$OS_OTEL_NAMESPACE.onesignal_id",
                onesignalId
            ).putIfValueNotNull(
                "$OS_OTEL_NAMESPACE.push_subscription_id",
                subscriptionId
            )

        attributes["android.app.state"] = appState
        attributes["process.uptime"] = processUptime.toString()
        attributes["thread.name"] = currentThreadName

        return attributes.toUnmodifiableMap()
    }

    private val appId: String? get() {
        try {
            return _configModelStore.model.appId
        } catch (_: NullPointerException) {
            Logging.warn("app_id not available to add to crash log")
            return null
        }
    }

    private val onesignalId: String? get() {
        try {
            val onesignalId = _identityModelStore.model.onesignalId
            if (IDManager.isLocalId(onesignalId)) {
                return null
            }
            return onesignalId
        } catch (_: NullPointerException) {
            Logging.warn("onesignalId not available to add to crash log")
            return null
        }
    }

    private val subscriptionId: String? get() {
        try {
            val pushSubscriptionId = _configModelStore.model.pushSubscriptionId
            if (pushSubscriptionId == null ||
                IDManager.isLocalId(pushSubscriptionId)) {
                return null
            }
            return pushSubscriptionId
        } catch (_: NullPointerException) {
            Logging.warn("subscriptionId not available to add to crash log")
            return null
        }
    }

    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/android/
    private val appState: String get() =
        if (_applicationService.isInForeground) "foreground" else "background"

    // https://opentelemetry.io/docs/specs/semconv/system/process-metrics/#metric-processuptime
    private val processUptime: Double get() =
        _time.processUptimeMillis / 1_000.toDouble()

    // https://opentelemetry.io/docs/specs/semconv/general/attributes/#general-thread-attributes
    private val currentThreadName: String get() =
        Thread.currentThread().name

    // idempotency so the backend can filter on duplicate events
    // https://opentelemetry.io/docs/specs/semconv/general/logs/#general-log-identification-attributes
    private val recordId: UUID get() =
        UUID.randomUUID()
}
